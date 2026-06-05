package com.teamflow.ai.modules.dashboard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.teamflow.ai.common.cache.DashboardCacheService;
import com.teamflow.ai.modules.dashboard.dto.ChartPoint;
import com.teamflow.ai.modules.dashboard.dto.DashboardOverview;
import com.teamflow.ai.modules.dashboard.dto.DashboardTodoItem;
import com.teamflow.ai.modules.dashboard.mapper.DashboardMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class DashboardService {

    /** 总览/趋势/活跃度等统计变化平缓，较长 TTL 兜底。 */
    private static final Duration STATS_TTL = Duration.ofMinutes(5);
    /** 待办列表变化较频繁，使用更短 TTL，降低陈旧感知。 */
    private static final Duration TODOS_TTL = Duration.ofMinutes(1);

    private static final String KEY_OVERVIEW = "dashboard:overview";
    private static final String KEY_PROJECT_TREND = "dashboard:project-trend";
    private static final String KEY_MEMBER_ACTIVE = "dashboard:member-active";
    private static final String KEY_AI_USAGE = "dashboard:ai-usage";
    private static final String KEY_TODOS = "dashboard:todos";

    private static final TypeReference<DashboardOverview> OVERVIEW_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<ChartPoint>> CHART_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<DashboardTodoItem>> TODO_LIST_TYPE = new TypeReference<>() {};

    private final DashboardMapper dashboardMapper;
    private final DashboardCacheService cache;

    public DashboardService(DashboardMapper dashboardMapper, DashboardCacheService cache) {
        this.dashboardMapper = dashboardMapper;
        this.cache = cache;
    }

    public DashboardOverview overview() {
        return cache.getOrLoad(KEY_OVERVIEW, STATS_TTL, OVERVIEW_TYPE, () -> new DashboardOverview(
                dashboardMapper.countUsers(),
                dashboardMapper.countProjects(),
                dashboardMapper.countTasks(),
                dashboardMapper.countDoneTasks(),
                dashboardMapper.countKnowledgeDocs(),
                dashboardMapper.countAiMessages()
        ));
    }

    public List<ChartPoint> projectTrend() {
        return cache.getOrLoad(KEY_PROJECT_TREND, STATS_TTL, CHART_LIST_TYPE, this::loadProjectTrend);
    }

    private List<ChartPoint> loadProjectTrend() {
        Map<String, Number> counts = dashboardMapper.countTaskCreatedInLastSevenDays()
                .stream()
                .collect(Collectors.toMap(ChartPoint::name, ChartPoint::value));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        LocalDate today = LocalDate.now();
        return IntStream.rangeClosed(0, 6)
                .mapToObj(offset -> today.minusDays(6L - offset))
                .map(date -> {
                    String label = formatter.format(date);
                    return new ChartPoint(label, counts.getOrDefault(label, 0));
                })
                .toList();
    }

    public List<ChartPoint> memberActive() {
        return cache.getOrLoad(KEY_MEMBER_ACTIVE, STATS_TTL, CHART_LIST_TYPE,
                dashboardMapper::countTasksByAssignee);
    }

    public List<ChartPoint> aiUsage() {
        return cache.getOrLoad(KEY_AI_USAGE, STATS_TTL, CHART_LIST_TYPE,
                dashboardMapper::countAiUsageBySessionType);
    }

    public List<DashboardTodoItem> currentTodos() {
        return cache.getOrLoad(KEY_TODOS, TODOS_TTL, TODO_LIST_TYPE,
                dashboardMapper::listCurrentTodos);
    }
}
