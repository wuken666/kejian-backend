package com.teacherfeedback.api.service;

import com.teacherfeedback.api.dto.AdminDashboardDtos.AdminDashboardResponse;
import com.teacherfeedback.api.dto.AdminDashboardDtos.AdminIncidentRow;
import com.teacherfeedback.api.dto.AdminDashboardDtos.AdminOverview;
import com.teacherfeedback.api.dto.AdminDashboardDtos.AdminSessionRow;
import com.teacherfeedback.api.dto.AdminDashboardDtos.AdminTeacherRow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminDashboardService {
    private static final int RESULT_LIMIT = 100;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminDashboardService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 汇总指定时间范围内的教师、录音和异常数据，供运营后台一次加载。
    public AdminDashboardResponse loadDashboard(int days, Long teacherId) {
        LocalDateTime now = LocalDateTime.now();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("cutoff", now.minusDays(days))
            .addValue("staleCutoff", now.minusHours(2))
            .addValue("teacherId", teacherId, Types.BIGINT)
            .addValue("limit", RESULT_LIMIT);
        return new AdminDashboardResponse(
            days,
            loadOverview(parameters),
            loadTeachers(parameters),
            loadSessions(parameters),
            loadIncidents(parameters),
            now
        );
    }

    // 计算后台顶部核心指标，识别覆盖率按有文本的音频片段占比计算。
    private AdminOverview loadOverview(MapSqlParameterSource parameters) {
        String sql = """
            SELECT
                COUNT(DISTINCT t.id) AS teacher_count,
                COUNT(DISTINCT ls.id) AS session_count,
                COALESCE((
                    SELECT SUM(seg2.duration_seconds)
                    FROM lesson_session_segments seg2
                    JOIN lesson_sessions ls2 ON ls2.id = seg2.lesson_session_id
                    JOIN students stu2 ON stu2.id = ls2.student_id
                    WHERE ls2.started_at >= :cutoff
                      AND (:teacherId IS NULL OR stu2.teacher_id = :teacherId)
                ), 0) AS recorded_seconds,
                (
                    SELECT COUNT(*)
                    FROM client_error_events err2
                    WHERE err2.occurred_at >= :cutoff
                      AND (:teacherId IS NULL OR err2.teacher_id = :teacherId)
                ) + (
                    SELECT COUNT(*)
                    FROM lesson_sessions ls4
                    JOIN students stu4 ON stu4.id = ls4.student_id
                    WHERE ls4.started_at >= :cutoff
                      AND (:teacherId IS NULL OR stu4.teacher_id = :teacherId)
                      AND (
                            (ls4.status <> 'COMPLETED' AND ls4.updated_at < :staleCutoff)
                            OR NOT EXISTS (
                                SELECT 1 FROM lesson_session_segments seg4
                                WHERE seg4.lesson_session_id = ls4.id
                            )
                            OR NOT EXISTS (
                                SELECT 1 FROM lesson_session_segments seg4
                                WHERE seg4.lesson_session_id = ls4.id
                                  AND NULLIF(TRIM(seg4.transcript), '') IS NOT NULL
                            )
                            OR EXISTS (
                                SELECT 1 FROM lesson_session_segments seg4
                                WHERE seg4.lesson_session_id = ls4.id
                                  AND (seg4.asr_status = 'FAILED' OR seg4.audio_quality = 'DISTORTED')
                            )
                            OR (
                                ls4.status <> 'RECORDING'
                                AND ls4.client_captured_seconds IS NOT NULL
                                AND ls4.client_captured_seconds - COALESCE((
                                    SELECT SUM(seg4.duration_seconds)
                                    FROM lesson_session_segments seg4
                                    WHERE seg4.lesson_session_id = ls4.id
                                ), 0) > 15
                            )
                            OR COALESCE((
                                SELECT MAX(seg4.sequence_number) + 1 - COUNT(*)
                                FROM lesson_session_segments seg4
                                WHERE seg4.lesson_session_id = ls4.id
                            ), 0) > 0
                      )
                ) AS incident_count,
                COALESCE((
                    SELECT ROUND(
                        SUM(CASE WHEN NULLIF(TRIM(seg3.transcript), '') IS NOT NULL THEN 1 ELSE 0 END)
                        * 100.0 / NULLIF(COUNT(*), 0),
                        1
                    )
                    FROM lesson_session_segments seg3
                    JOIN lesson_sessions ls3 ON ls3.id = seg3.lesson_session_id
                    JOIN students stu3 ON stu3.id = ls3.student_id
                    WHERE ls3.started_at >= :cutoff
                      AND (:teacherId IS NULL OR stu3.teacher_id = :teacherId)
                ), 0) AS transcription_coverage
            FROM teachers t
            LEFT JOIN students stu ON stu.teacher_id = t.id
            LEFT JOIN lesson_sessions ls
                ON ls.student_id = stu.id AND ls.started_at >= :cutoff
            WHERE (:teacherId IS NULL OR t.id = :teacherId)
            """;
        return jdbcTemplate.queryForObject(sql, parameters, (resultSet, rowNum) -> new AdminOverview(
            resultSet.getLong("teacher_count"),
            resultSet.getLong("session_count"),
            resultSet.getLong("recorded_seconds"),
            resultSet.getLong("incident_count"),
            resultSet.getDouble("transcription_coverage")
        ));
    }

    // 按教师汇总学生数、录音量、异常数和最近活跃时间。
    private List<AdminTeacherRow> loadTeachers(MapSqlParameterSource parameters) {
        String sql = """
            SELECT
                t.id,
                t.name,
                (SELECT COUNT(*) FROM students s WHERE s.teacher_id = t.id) AS student_count,
                (
                    SELECT COUNT(*)
                    FROM lesson_sessions ls
                    JOIN students s ON s.id = ls.student_id
                    WHERE s.teacher_id = t.id AND ls.started_at >= :cutoff
                ) AS session_count,
                (
                    SELECT COALESCE(SUM(seg.duration_seconds), 0)
                    FROM lesson_session_segments seg
                    JOIN lesson_sessions ls ON ls.id = seg.lesson_session_id
                    JOIN students s ON s.id = ls.student_id
                    WHERE s.teacher_id = t.id AND ls.started_at >= :cutoff
                ) AS recorded_seconds,
                (
                    SELECT COUNT(*)
                    FROM client_error_events err
                    WHERE err.teacher_id = t.id AND err.occurred_at >= :cutoff
                ) + (
                    SELECT COUNT(*)
                    FROM lesson_sessions ls
                    JOIN students s ON s.id = ls.student_id
                    WHERE s.teacher_id = t.id
                      AND ls.started_at >= :cutoff
                      AND (
                            (ls.status <> 'COMPLETED' AND ls.updated_at < :staleCutoff)
                            OR NOT EXISTS (
                                SELECT 1 FROM lesson_session_segments seg
                                WHERE seg.lesson_session_id = ls.id
                            )
                            OR NOT EXISTS (
                                SELECT 1 FROM lesson_session_segments seg
                                WHERE seg.lesson_session_id = ls.id
                                  AND NULLIF(TRIM(seg.transcript), '') IS NOT NULL
                            )
                            OR EXISTS (
                                SELECT 1 FROM lesson_session_segments seg
                                WHERE seg.lesson_session_id = ls.id
                                  AND (seg.asr_status = 'FAILED' OR seg.audio_quality = 'DISTORTED')
                            )
                            OR (
                                ls.status <> 'RECORDING'
                                AND ls.client_captured_seconds IS NOT NULL
                                AND ls.client_captured_seconds - COALESCE((
                                    SELECT SUM(seg.duration_seconds)
                                    FROM lesson_session_segments seg
                                    WHERE seg.lesson_session_id = ls.id
                                ), 0) > 15
                            )
                            OR COALESCE((
                                SELECT MAX(seg.sequence_number) + 1 - COUNT(*)
                                FROM lesson_session_segments seg
                                WHERE seg.lesson_session_id = ls.id
                            ), 0) > 0
                      )
                ) AS incident_count,
                (
                    SELECT MAX(ls.updated_at)
                    FROM lesson_sessions ls
                    JOIN students s ON s.id = ls.student_id
                    WHERE s.teacher_id = t.id
                ) AS last_active_at
            FROM teachers t
            WHERE (:teacherId IS NULL OR t.id = :teacherId)
            ORDER BY last_active_at DESC, t.id ASC
            """;
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNum) -> new AdminTeacherRow(
            resultSet.getLong("id"),
            resultSet.getString("name"),
            resultSet.getLong("student_count"),
            resultSet.getLong("session_count"),
            resultSet.getLong("recorded_seconds"),
            resultSet.getLong("incident_count"),
            resultSet.getObject("last_active_at", LocalDateTime.class)
        ));
    }

    // 返回最近的录音会话，并根据停滞、无音频和无转写标记健康状态。
    private List<AdminSessionRow> loadSessions(MapSqlParameterSource parameters) {
        String sql = """
            SELECT
                ls.id,
                t.id AS teacher_id,
                t.name AS teacher_name,
                stu.name AS student_name,
                ls.course_title,
                ls.status,
                ls.started_at,
                ls.updated_at,
                COALESCE(SUM(seg.duration_seconds), 0) AS recorded_seconds,
                ls.client_captured_seconds,
                GREATEST(
                    COALESCE(ls.client_captured_seconds, 0) - COALESCE(SUM(seg.duration_seconds), 0),
                    0
                ) AS missing_audio_seconds,
                COUNT(seg.id) AS segment_count,
                MIN(seg.sequence_number) AS first_sequence_number,
                MAX(seg.sequence_number) AS last_sequence_number,
                CASE
                    WHEN COUNT(seg.id) = 0 THEN 0
                    ELSE GREATEST(MAX(seg.sequence_number) + 1 - COUNT(seg.id), 0)
                END AS sequence_gap_count,
                MAX(seg.created_at) AS last_segment_at,
                SUM(CASE WHEN NULLIF(TRIM(seg.transcript), '') IS NOT NULL THEN 1 ELSE 0 END)
                    AS transcript_segment_count,
                SUM(CASE WHEN seg.asr_status = 'FAILED' THEN 1 ELSE 0 END) AS failed_segment_count,
                SUM(CASE WHEN seg.audio_quality = 'DISTORTED' THEN 1 ELSE 0 END)
                    AS distorted_segment_count,
                SUM(CASE WHEN seg.audio_quality = 'SILENT' THEN 1 ELSE 0 END) AS silent_segment_count,
                CASE
                    WHEN ls.status <> 'COMPLETED' AND ls.updated_at < :staleCutoff THEN 'STALE'
                    WHEN COUNT(seg.id) = 0 THEN 'NO_AUDIO'
                    WHEN ls.status <> 'RECORDING' AND (
                        COALESCE(ls.client_captured_seconds, 0) - COALESCE(SUM(seg.duration_seconds), 0) > 15
                        OR GREATEST(MAX(seg.sequence_number) + 1 - COUNT(seg.id), 0) > 0
                    ) THEN 'INCOMPLETE_AUDIO'
                    WHEN SUM(CASE WHEN seg.asr_status = 'FAILED' THEN 1 ELSE 0 END) > 0 THEN 'ASR_FAILED'
                    WHEN SUM(CASE WHEN seg.audio_quality = 'DISTORTED' THEN 1 ELSE 0 END) > 0
                        THEN 'DISTORTED_AUDIO'
                    WHEN SUM(CASE WHEN seg.audio_quality = 'SILENT' THEN 1 ELSE 0 END) = COUNT(seg.id)
                        THEN 'SILENT_AUDIO'
                    WHEN SUM(CASE WHEN NULLIF(TRIM(seg.transcript), '') IS NOT NULL THEN 1 ELSE 0 END) = 0
                        THEN 'NO_TRANSCRIPT'
                    ELSE 'HEALTHY'
                END AS health
            FROM lesson_sessions ls
            JOIN students stu ON stu.id = ls.student_id
            JOIN teachers t ON t.id = stu.teacher_id
            LEFT JOIN lesson_session_segments seg ON seg.lesson_session_id = ls.id
            WHERE ls.started_at >= :cutoff
              AND (:teacherId IS NULL OR t.id = :teacherId)
            GROUP BY
                ls.id, t.id, t.name, stu.name, ls.course_title, ls.status,
                ls.started_at, ls.updated_at, ls.client_captured_seconds
            ORDER BY ls.started_at DESC
            LIMIT :limit
            """;
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNum) -> new AdminSessionRow(
            resultSet.getLong("id"),
            resultSet.getLong("teacher_id"),
            resultSet.getString("teacher_name"),
            resultSet.getString("student_name"),
            resultSet.getString("course_title"),
            resultSet.getString("status"),
            resultSet.getObject("started_at", LocalDateTime.class),
            resultSet.getObject("updated_at", LocalDateTime.class),
            resultSet.getLong("recorded_seconds"),
            resultSet.getObject("client_captured_seconds", Integer.class),
            resultSet.getLong("missing_audio_seconds"),
            resultSet.getLong("segment_count"),
            resultSet.getObject("first_sequence_number", Integer.class),
            resultSet.getObject("last_sequence_number", Integer.class),
            resultSet.getLong("sequence_gap_count"),
            resultSet.getObject("last_segment_at", LocalDateTime.class),
            resultSet.getLong("transcript_segment_count"),
            resultSet.getLong("failed_segment_count"),
            resultSet.getLong("distorted_segment_count"),
            resultSet.getLong("silent_segment_count"),
            resultSet.getString("health")
        ));
    }

    // 返回最近的客户端异常摘要，不向后台传输课堂正文和录音内容。
    private List<AdminIncidentRow> loadIncidents(MapSqlParameterSource parameters) {
        String sql = """
            SELECT
                events.id,
                events.teacher_id,
                events.teacher_name,
                events.area,
                events.context,
                events.error_name,
                events.message,
                events.user_agent,
                events.occurred_at
            FROM (
                SELECT
                    err.id,
                    err.teacher_id,
                    COALESCE(t.name, '未知老师') AS teacher_name,
                    err.area,
                    err.context,
                    err.error_name,
                    err.message,
                    err.user_agent,
                    err.occurred_at
                FROM client_error_events err
                LEFT JOIN teachers t ON t.id = err.teacher_id
                WHERE err.occurred_at >= :cutoff
                  AND (:teacherId IS NULL OR err.teacher_id = :teacherId)

                UNION ALL

                SELECT
                    -health.id AS id,
                    health.teacher_id,
                    health.teacher_name,
                    'recording' AS area,
                    CASE health.health
                        WHEN 'STALE' THEN 'lesson_session_stale'
                        WHEN 'NO_AUDIO' THEN 'lesson_session_no_audio'
                        WHEN 'INCOMPLETE_AUDIO' THEN 'lesson_session_incomplete_audio'
                        WHEN 'ASR_FAILED' THEN 'lesson_session_asr_failed'
                        WHEN 'DISTORTED_AUDIO' THEN 'lesson_session_distorted_audio'
                        WHEN 'SILENT_AUDIO' THEN 'lesson_session_silent_audio'
                        ELSE 'lesson_session_no_transcript'
                    END AS context,
                    'RecordingHealthAlert' AS error_name,
                    CASE health.health
                        WHEN 'STALE' THEN CONCAT('课堂 #', health.id, ' 长时间未结束，请确认是否需要继续或清理。')
                        WHEN 'NO_AUDIO' THEN CONCAT('课堂 #', health.id, ' 没有保存有效音频。')
                        WHEN 'INCOMPLETE_AUDIO' THEN CONCAT(
                            '课堂 #', health.id, ' 客户端记录 ', health.client_seconds,
                            ' 秒，服务端保存 ', health.recorded_seconds,
                            ' 秒，疑似缺失 ', health.missing_seconds, ' 秒。'
                        )
                        WHEN 'ASR_FAILED' THEN CONCAT('课堂 #', health.id, ' 有 ', health.failed_count, ' 个片段识别调用失败，音频仍已保存。')
                        WHEN 'DISTORTED_AUDIO' THEN CONCAT('课堂 #', health.id, ' 有 ', health.distorted_count, ' 个片段输入失真，请检查录音设备。')
                        WHEN 'SILENT_AUDIO' THEN CONCAT('课堂 #', health.id, ' 的音频片段均接近静音。')
                        ELSE CONCAT('课堂 #', health.id, ' 没有识别出有效文字。')
                    END AS message,
                    '' AS user_agent,
                    health.updated_at AS occurred_at
                FROM (
                    SELECT
                        ls.id,
                        t.id AS teacher_id,
                        t.name AS teacher_name,
                        ls.status,
                        ls.updated_at,
                        ls.client_captured_seconds AS client_seconds,
                        COALESCE(SUM(seg.duration_seconds), 0) AS recorded_seconds,
                        GREATEST(
                            COALESCE(ls.client_captured_seconds, 0) - COALESCE(SUM(seg.duration_seconds), 0),
                            0
                        ) AS missing_seconds,
                        SUM(CASE WHEN seg.asr_status = 'FAILED' THEN 1 ELSE 0 END) AS failed_count,
                        SUM(CASE WHEN seg.audio_quality = 'DISTORTED' THEN 1 ELSE 0 END) AS distorted_count,
                        CASE
                            WHEN ls.status <> 'COMPLETED' AND ls.updated_at < :staleCutoff THEN 'STALE'
                            WHEN COUNT(seg.id) = 0 THEN 'NO_AUDIO'
                            WHEN ls.status <> 'RECORDING' AND (
                                COALESCE(ls.client_captured_seconds, 0) - COALESCE(SUM(seg.duration_seconds), 0) > 15
                                OR GREATEST(MAX(seg.sequence_number) + 1 - COUNT(seg.id), 0) > 0
                            ) THEN 'INCOMPLETE_AUDIO'
                            WHEN SUM(CASE WHEN seg.asr_status = 'FAILED' THEN 1 ELSE 0 END) > 0
                                THEN 'ASR_FAILED'
                            WHEN SUM(CASE WHEN seg.audio_quality = 'DISTORTED' THEN 1 ELSE 0 END) > 0
                                THEN 'DISTORTED_AUDIO'
                            WHEN SUM(CASE WHEN seg.audio_quality = 'SILENT' THEN 1 ELSE 0 END) = COUNT(seg.id)
                                THEN 'SILENT_AUDIO'
                            WHEN SUM(
                                CASE WHEN NULLIF(TRIM(seg.transcript), '') IS NOT NULL THEN 1 ELSE 0 END
                            ) = 0 THEN 'NO_TRANSCRIPT'
                            ELSE 'HEALTHY'
                        END AS health
                    FROM lesson_sessions ls
                    JOIN students stu ON stu.id = ls.student_id
                    JOIN teachers t ON t.id = stu.teacher_id
                    LEFT JOIN lesson_session_segments seg ON seg.lesson_session_id = ls.id
                    WHERE ls.started_at >= :cutoff
                      AND (:teacherId IS NULL OR t.id = :teacherId)
                    GROUP BY
                        ls.id, t.id, t.name, ls.status, ls.updated_at, ls.client_captured_seconds
                ) health
                WHERE health.health <> 'HEALTHY'
            ) events
            ORDER BY events.occurred_at DESC
            LIMIT :limit
            """;
        return jdbcTemplate.query(sql, parameters, (resultSet, rowNum) -> new AdminIncidentRow(
            resultSet.getLong("id"),
            resultSet.getObject("teacher_id", Long.class),
            resultSet.getString("teacher_name"),
            resultSet.getString("area"),
            resultSet.getString("context"),
            resultSet.getString("error_name"),
            resultSet.getString("message"),
            resultSet.getString("user_agent"),
            resultSet.getObject("occurred_at", LocalDateTime.class)
        ));
    }
}
