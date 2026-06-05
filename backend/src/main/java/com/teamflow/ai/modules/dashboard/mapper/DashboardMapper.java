package com.teamflow.ai.modules.dashboard.mapper;

import com.teamflow.ai.modules.dashboard.dto.ChartPoint;
import com.teamflow.ai.modules.dashboard.dto.DashboardTodoItem;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface DashboardMapper {

    @Select("SELECT COUNT(*) FROM sys_user WHERE deleted = 0")
    long countUsers();

    @Select("SELECT COUNT(*) FROM project WHERE deleted = 0")
    long countProjects();

    @Select("SELECT COUNT(*) FROM `task` WHERE deleted = 0")
    long countTasks();

    @Select("SELECT COUNT(*) FROM `task` WHERE deleted = 0 AND status IN ('DONE','CLOSED')")
    long countDoneTasks();

    @Select("SELECT COUNT(*) FROM knowledge_doc WHERE deleted = 0")
    long countKnowledgeDocs();

    @Select("SELECT COUNT(*) FROM ai_message")
    long countAiMessages();

    @Select("""
            SELECT DATE_FORMAT(created_at, '%m-%d') AS name, COUNT(*) AS value
            FROM `task`
            WHERE deleted = 0
              AND created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
            GROUP BY DATE(created_at), DATE_FORMAT(created_at, '%m-%d')
            ORDER BY DATE(created_at)
            """)
    List<ChartPoint> countTaskCreatedInLastSevenDays();

    @Select("""
            SELECT COALESCE(NULLIF(u.nickname, ''), u.username, '未分配') AS name, COUNT(t.id) AS value
            FROM `task` t
            LEFT JOIN sys_user u ON u.id = t.assignee_id AND u.deleted = 0
            WHERE t.deleted = 0
            GROUP BY COALESCE(NULLIF(u.nickname, ''), u.username, '未分配')
            ORDER BY value DESC
            LIMIT 6
            """)
    List<ChartPoint> countTasksByAssignee();

    @Select("""
            SELECT CASE session_type_key
                     WHEN 'CHAT' THEN '普通问答'
                     WHEN 'KNOWLEDGE' THEN '知识库问答'
                     WHEN 'SUMMARY' THEN '文档总结'
                     WHEN 'CODE' THEN '代码生成'
                     WHEN 'SQL' THEN 'SQL助手'
                     ELSE session_type_key
                   END AS name,
                   COUNT(*) AS value
            FROM (
              SELECT COALESCE(session_type, 'CHAT') AS session_type_key
              FROM ai_session
              WHERE deleted = 0
            ) normalized
            GROUP BY session_type_key
            ORDER BY value DESC
            """)
    List<ChartPoint> countAiUsageBySessionType();

    @Select("""
            SELECT t.id,
                   t.task_no AS taskNo,
                   t.title,
                   t.status,
                   p.project_name AS projectName,
                   COALESCE(NULLIF(u.nickname, ''), u.username) AS assigneeName,
                   t.due_time AS dueTime
            FROM `task` t
            LEFT JOIN project p ON p.id = t.project_id AND p.deleted = 0
            LEFT JOIN sys_user u ON u.id = t.assignee_id AND u.deleted = 0
            WHERE t.deleted = 0
              AND t.status NOT IN ('DONE', 'CLOSED')
            ORDER BY
              CASE WHEN t.due_time IS NULL THEN 1 ELSE 0 END,
              t.due_time ASC,
              t.updated_at DESC
            LIMIT 8
            """)
    List<DashboardTodoItem> listCurrentTodos();
}
