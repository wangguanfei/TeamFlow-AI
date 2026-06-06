package com.teamflow.ai.modules.deploy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.ai.common.api.PageRequestUtils;
import com.teamflow.ai.common.api.PageResult;
import com.teamflow.ai.common.exception.BusinessException;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.modules.deploy.DeployProperties;
import com.teamflow.ai.modules.deploy.dto.DeployCurrentResponse;
import com.teamflow.ai.modules.deploy.dto.DeployRecordItem;
import com.teamflow.ai.modules.deploy.dto.DeployTriggerRequest;
import com.teamflow.ai.modules.deploy.entity.DeployRecord;
import com.teamflow.ai.modules.deploy.mapper.DeployRecordMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

@Service
public class DeployService {

    private static final Logger log = LoggerFactory.getLogger(DeployService.class);

    private static final long SSE_TIMEOUT_MS = 30L * 60 * 1000;
    private static final long AGENT_WAIT_MS = 30_000;
    private static final long TAIL_POLL_MS = 300;

    private static final Pattern[] SENSITIVE = {
        Pattern.compile("(?i)(password|passwd|secret|api[_-]?key|token|credential)\\s*[=:]\\s*\\S+"),
        Pattern.compile("(?i)\\.env\\s.*"),
    };

    private static final java.util.concurrent.ExecutorService EXECUTOR =
            Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "deploy-tailer");
                t.setDaemon(true);
                return t;
            });

    private final Semaphore deployLock = new Semaphore(1);
    private volatile Long runningDeployId = null;

    private final Map<Long, List<String>> logBuffers = new ConcurrentHashMap<>();
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Object broadcastLock = new Object();

    private final DeployRecordMapper recordMapper;
    private final DeployProperties props;
    private final ObjectMapper objectMapper;

    public DeployService(DeployRecordMapper recordMapper, DeployProperties props, ObjectMapper objectMapper) {
        this.recordMapper = recordMapper;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    // 启动时恢复被中断的部署（容器重启场景）
    @PostConstruct
    void recoverRunningDeploys() {
        List<DeployRecord> running = recordMapper.selectList(
                new LambdaQueryWrapper<DeployRecord>().eq(DeployRecord::getStatus, "RUNNING"));
        for (DeployRecord r : running) {
            File logFile = r.getLogFile() != null ? new File(r.getLogFile()) : null;
            if (logFile != null && logFile.exists()) {
                Integer exitCode = checkExitInFile(logFile);
                if (exitCode != null) {
                    // agent 已完成，直接更新
                    finishRecord(r.getId(), exitCode, r.getStartedAt());
                    log.info("恢复：部署 {} 已完成(exit={})", r.getId(), exitCode);
                } else {
                    // agent 仍在运行，恢复追踪
                    if (deployLock.tryAcquire()) {
                        runningDeployId = r.getId();
                        logBuffers.put(r.getId(), new CopyOnWriteArrayList<>());
                        emitters.put(r.getId(), new CopyOnWriteArrayList<>());
                        EXECUTOR.submit(() -> tailLog(r.getId(), logFile, r.getStartedAt()));
                        log.info("恢复：继续追踪部署 {} 日志", r.getId());
                    }
                }
            } else {
                // 无日志文件，标记失败
                finishRecord(r.getId(), -1, r.getStartedAt());
                log.warn("恢复：部署 {} 无日志文件，标记失败", r.getId());
            }
        }
    }

    public Long triggerDeploy(DeployTriggerRequest request, UserPrincipal principal) {
        if (!props.isEnabled()) {
            throw new BusinessException("部署功能未启用，请配置 DEPLOY_ENABLED=true");
        }
        if (!deployLock.tryAcquire()) {
            throw new BusinessException("已有部署任务正在执行，请等待完成后再试");
        }

        File logDir = new File(props.getLogDir());
        File triggerDir = new File(logDir, "triggers");
        triggerDir.mkdirs();
        logDir.mkdirs();

        DeployRecord record = new DeployRecord();
        record.setTarget(request.target());
        record.setStatus("RUNNING");
        record.setTriggerUserId(principal.getUserId());
        record.setTriggerUsername(principal.getUsername());
        record.setStartedAt(LocalDateTime.now());
        record.setCreatedAt(LocalDateTime.now());

        File logFile = new File(logDir, System.currentTimeMillis() + "-" + request.target() + ".log");
        record.setLogFile(logFile.getAbsolutePath());
        recordMapper.insert(record);

        Long deployId = record.getId();
        runningDeployId = deployId;
        logBuffers.put(deployId, new CopyOnWriteArrayList<>());
        emitters.put(deployId, new CopyOnWriteArrayList<>());

        // 写触发文件，由宿主机 deploy-agent.sh 拾取执行
        writeTrigger(triggerDir, deployId, request);
        EXECUTOR.submit(() -> tailLog(deployId, logFile, record.getStartedAt()));
        return deployId;
    }

    // 追踪日志文件，将新行广播到所有 SSE 连接
    private void tailLog(Long deployId, File logFile, LocalDateTime startedAt) {
        int exitCode = -1;
        try {
            // 等待 agent 创建日志文件
            long waitDeadline = System.currentTimeMillis() + AGENT_WAIT_MS;
            while (!logFile.exists() && System.currentTimeMillis() < waitDeadline) {
                Thread.sleep(500);
            }
            if (!logFile.exists()) {
                broadcast(deployId, "log", "[等待部署代理超时，请确认宿主机 deploy-agent.sh 已启动]");
                return;
            }

            long timeoutMs = (long) props.getTimeoutMinutes() * 60 * 1000;
            long deadline = System.currentTimeMillis() + timeoutMs;

            try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
                while (System.currentTimeMillis() < deadline) {
                    String line = raf.readLine();
                    if (line == null) {
                        Thread.sleep(TAIL_POLL_MS);
                        continue;
                    }
                    if (line.startsWith("__EXIT__:")) {
                        exitCode = parseExit(line);
                        break;
                    }
                    broadcast(deployId, "log", desensitize(line));
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                broadcast(deployId, "log", "[部署超时]");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("追踪日志异常 deployId={}", deployId, e);
            broadcast(deployId, "log", "[日志追踪异常: " + e.getMessage() + "]");
        } finally {
            finishRecord(deployId, exitCode, startedAt);
            String status = exitCode == 0 ? "SUCCESS" : "FAILED";
            broadcastDone(deployId, status, exitCode,
                    System.currentTimeMillis() - toEpochMs(startedAt));
            runningDeployId = null;
            logBuffers.remove(deployId);
            emitters.remove(deployId);
            deployLock.release();
        }
    }

    public SseEmitter streamLog(Long deployId) {
        DeployRecord record = recordMapper.selectById(deployId);
        if (record == null) throw new BusinessException("部署记录不存在");

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        if ("RUNNING".equals(record.getStatus()) && deployId.equals(runningDeployId)) {
            synchronized (broadcastLock) {
                List<String> buf = logBuffers.get(deployId);
                if (buf != null) {
                    for (String line : buf) {
                        try { emitter.send(SseEmitter.event().data(line)); } catch (Exception e) { break; }
                    }
                }
                List<SseEmitter> list = emitters.get(deployId);
                if (list != null) list.add(emitter);
            }
            emitter.onTimeout(emitter::complete);
        } else {
            EXECUTOR.submit(() -> replayFromFile(emitter, record));
        }
        return emitter;
    }

    private void replayFromFile(SseEmitter emitter, DeployRecord record) {
        try {
            File logFile = record.getLogFile() != null ? new File(record.getLogFile()) : null;
            if (logFile != null && logFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("__EXIT__:")) continue; // 过滤内部标记行
                        emitter.send(SseEmitter.event().data(toJson(Map.of("type", "log", "content", desensitize(line)))));
                    }
                }
            }
            emitter.send(SseEmitter.event().data(toJson(Map.of(
                    "type", "done",
                    "status", record.getStatus(),
                    "exitCode", record.getExitCode() != null ? record.getExitCode() : -1,
                    "costMs", record.getCostMs() != null ? record.getCostMs() : 0
            ))));
            emitter.complete();
        } catch (Exception e) {
            log.warn("SSE 回放失败 deployId={}", record.getId(), e);
            try { emitter.completeWithError(e); } catch (Exception ignored) {}
        }
    }

    public DeployCurrentResponse currentRunning() {
        Long id = runningDeployId;
        return new DeployCurrentResponse(id != null, id);
    }

    public PageResult<DeployRecordItem> page(int page, int size) {
        Page<DeployRecord> p = PageRequestUtils.of(page, size);
        recordMapper.selectPage(p, new LambdaQueryWrapper<DeployRecord>()
                .orderByDesc(DeployRecord::getId));
        return new PageResult<>(p.getCurrent(), p.getSize(), p.getTotal(),
                p.getRecords().stream().map(DeployRecordItem::from).toList());
    }

    // ── 内部工具 ─────────────────────────────────────────────────

    private void writeTrigger(File triggerDir, Long deployId, DeployTriggerRequest request) {
        File trigger = new File(triggerDir, deployId + ".trigger");
        try (PrintWriter w = new PrintWriter(new FileWriter(trigger))) {
            w.println("id=" + deployId);
            w.println("target=" + request.target());
            w.println("skipPull=" + request.skipPull());
        } catch (IOException e) {
            log.error("写触发文件失败", e);
            throw new BusinessException("写触发文件失败: " + e.getMessage());
        }
    }

    private void broadcast(Long deployId, String type, String content) {
        String payload = toJson(Map.of("type", type, "content", content));
        synchronized (broadcastLock) {
            List<String> buf = logBuffers.get(deployId);
            if (buf != null) buf.add(payload);
            List<SseEmitter> list = emitters.get(deployId);
            if (list != null) sendToAll(list, payload);
        }
    }

    private void broadcastDone(Long deployId, String status, int exitCode, long costMs) {
        String payload = toJson(Map.of("type", "done", "status", status, "exitCode", exitCode, "costMs", costMs));
        List<SseEmitter> list = emitters.getOrDefault(deployId, List.of());
        sendToAll(list, payload);
        list.forEach(e -> { try { e.complete(); } catch (Exception ignored) {} });
    }

    private void sendToAll(List<SseEmitter> list, String payload) {
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter e : list) {
            try { e.send(SseEmitter.event().data(payload)); }
            catch (Exception ex) { dead.add(e); }
        }
        list.removeAll(dead);
    }

    private void finishRecord(Long deployId, int exitCode, LocalDateTime startedAt) {
        String status = exitCode == 0 ? "SUCCESS" : "FAILED";
        DeployRecord update = new DeployRecord();
        update.setId(deployId);
        update.setStatus(status);
        update.setExitCode(exitCode);
        update.setFinishedAt(LocalDateTime.now());
        update.setCostMs(System.currentTimeMillis() - toEpochMs(startedAt));
        recordMapper.updateById(update);
    }

    private Integer checkExitInFile(File logFile) {
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String last = null, line;
            while ((line = br.readLine()) != null) last = line;
            if (last != null && last.startsWith("__EXIT__:")) return parseExit(last);
        } catch (Exception e) {
            log.warn("检查日志文件失败: {}", e.getMessage());
        }
        return null;
    }

    private int parseExit(String line) {
        try { return Integer.parseInt(line.substring(9).trim()); }
        catch (NumberFormatException e) { return -1; }
    }

    private long toEpochMs(LocalDateTime dt) {
        return dt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String desensitize(String line) {
        for (Pattern p : SENSITIVE) {
            line = p.matcher(line).replaceAll(m -> m.group()
                    .replaceAll("=\\S+", "=***").replaceAll(":\\s*\\S+", ":***"));
        }
        return line;
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }
}
