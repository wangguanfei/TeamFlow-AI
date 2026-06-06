package com.teamflow.ai.common.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.ai.common.security.UserPrincipal;
import com.teamflow.ai.common.web.ClientIpResolver;
import com.teamflow.ai.modules.system.entity.OperationLog;
import com.teamflow.ai.modules.system.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Aspect
@Component
public class OperationLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperationLogAspect.class);
    private static final int MAX_PARAMS_LENGTH = 2000;
    private static final Set<String> SENSITIVE_KEYS = Set.of("password", "oldPassword", "newPassword", "token", "accessToken", "refreshToken", "secret");

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    public OperationLogAspect(OperationLogService operationLogService, ObjectMapper objectMapper) {
        this.operationLogService = operationLogService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(com.teamflow.ai.common.log.Log)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startMs = System.currentTimeMillis();
        OperationLog operationLog = buildBase(joinPoint);
        Throwable error = null;
        try {
            Object result = joinPoint.proceed();
            operationLog.setResponseStatus(200);
            return result;
        } catch (Throwable t) {
            error = t;
            operationLog.setResponseStatus(500);
            String msg = t.getMessage();
            if (msg != null && msg.length() > 500) {
                msg = msg.substring(0, 500);
            }
            operationLog.setErrorMessage(msg);
            throw t;
        } finally {
            operationLog.setCostMs(System.currentTimeMillis() - startMs);
            operationLog.setCreatedAt(LocalDateTime.now());
            try {
                operationLogService.asyncSave(operationLog);
            } catch (Exception e) {
                log.warn("操作日志异步写入失败", e);
            }
        }
    }

    private OperationLog buildBase(ProceedingJoinPoint joinPoint) {
        OperationLog operationLog = new OperationLog();

        // 注解元数据
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Log logAnnotation = method.getAnnotation(Log.class);
        operationLog.setModuleName(logAnnotation.module());
        operationLog.setOperationType(logAnnotation.type());

        // 当前用户
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            operationLog.setUserId(principal.getUserId());
            operationLog.setUsername(principal.getUsername());
        }

        // 请求信息
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            operationLog.setRequestMethod(request.getMethod());
            operationLog.setRequestUri(request.getRequestURI());
            operationLog.setClientIp(ClientIpResolver.resolve(request));
        }

        // 请求参数（脱敏 + 截断）
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0) {
            operationLog.setRequestParams(buildParams(args));
        }

        return operationLog;
    }

    private String buildParams(Object[] args) {
        Object[] sanitized = Arrays.stream(args)
                .filter(a -> a != null
                        && !(a instanceof jakarta.servlet.ServletRequest)
                        && !(a instanceof jakarta.servlet.ServletResponse))
                .map(this::sanitizeArg)
                .toArray();
        if (sanitized.length == 0) return null;

        try {
            String json = objectMapper.writeValueAsString(sanitized.length == 1 ? sanitized[0] : sanitized);
            json = desensitize(json);
            if (json.length() > MAX_PARAMS_LENGTH) {
                json = json.substring(0, MAX_PARAMS_LENGTH) + "...(truncated)";
            }
            return json;
        } catch (JsonProcessingException e) {
            // 整体失败时降级：逐参数序列化，跳过仍不可序列化的
            return fallbackParams(sanitized);
        }
    }

    /** 降级：逐参数序列化，标记真正无法序列化的参数 */
    private String fallbackParams(Object[] sanitized) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < sanitized.length; i++) {
            try {
                objectMapper.writeValueAsString(sanitized[i]);
                result.put("arg" + i, sanitized[i]);
            } catch (JsonProcessingException ex) {
                result.put("arg" + i, "(unserializable:" + sanitized[i].getClass().getSimpleName() + ")");
            }
        }
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            return "(params unavailable)";
        }
    }

    /** 将已知不可序列化的参数类型转换为元数据描述 */
    @SuppressWarnings("unchecked")
    private Object sanitizeArg(Object arg) {
        // MultipartFile 单个文件
        if (arg instanceof MultipartFile file) {
            return fileInfo(file);
        }
        // MultipartFile 数组
        if (arg instanceof MultipartFile[] files) {
            return Arrays.stream(files).map(this::fileInfo).toList();
        }
        // Collection（可能含 MultipartFile 或其他不可序列化对象）
        if (arg instanceof Collection<?> col) {
            return col.stream().map(this::sanitizeArg).toList();
        }
        // IO 流
        if (arg instanceof InputStream || arg instanceof OutputStream
                || arg instanceof Reader || arg instanceof Writer) {
            return "(stream:" + arg.getClass().getSimpleName() + ")";
        }
        // java.io.File
        if (arg instanceof java.io.File file) {
            return Map.of("file", file.getName(), "size", file.length());
        }
        // Spring Resource（InputStreamResource 等含流的实现）
        if (arg instanceof Resource res) {
            Map<String, Object> info = new HashMap<>();
            info.put("resourceType", res.getClass().getSimpleName());
            try { info.put("filename", res.getFilename()); } catch (Exception ignored) {}
            return info;
        }
        // Servlet Part（另一种文件上传方式）
        if (arg instanceof jakarta.servlet.http.Part part) {
            return Map.of("partName", part.getName(), "size", part.getSize(), "contentType", part.getContentType());
        }
        // byte[] 只记录长度，避免 Base64 序列化膨胀
        if (arg instanceof byte[] bytes) {
            return Map.of("bytes", bytes.length);
        }
        return arg;
    }

    private Map<String, Object> fileInfo(MultipartFile file) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("filename", file.getOriginalFilename());
        meta.put("size", file.getSize());
        meta.put("contentType", file.getContentType());
        return meta;
    }

    /** 将 JSON 字符串中的敏感字段值替换为 "***" */
    @SuppressWarnings("unchecked")
    private String desensitize(String json) {
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            maskSensitive(parsed);
            return objectMapper.writeValueAsString(parsed);
        } catch (Exception e) {
            return json;
        }
    }

    @SuppressWarnings("unchecked")
    private void maskSensitive(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            Map<Object, Object> mutableMap = (Map<Object, Object>) map;
            for (String key : SENSITIVE_KEYS) {
                if (mutableMap.containsKey(key)) {
                    mutableMap.put(key, "***");
                }
            }
            mutableMap.values().forEach(this::maskSensitive);
        } else if (obj instanceof Iterable<?> iterable) {
            iterable.forEach(this::maskSensitive);
        }
    }
}
