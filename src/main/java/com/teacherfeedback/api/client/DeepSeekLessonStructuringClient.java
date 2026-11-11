package com.teacherfeedback.api.client;

import com.teacherfeedback.api.model.Student;
import com.teacherfeedback.api.model.LessonRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
public class DeepSeekLessonStructuringClient implements LessonStructuringClient {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekLessonStructuringClient.class);
    private static final String SYSTEM_PROMPT = """
        你是独立教师的课后记录助手。请把聊天截图 OCR 文字或老师粘贴的聊天文字整理成课堂记录，
        并生成一份可直接发给家长的中文反馈。
        只使用输入中明确出现的信息，不得虚构知识点、表现、分数或作业。
        输入可能包含多张按选择顺序排列的截图。请合并上下文并去除截图重叠造成的重复内容，
        不要因为同一句话在相邻截图重复出现而重复总结。
        输入也可能是带有“[说话人0]”“[说话人1]”标记的课堂录音转写。说话人编号只是声纹聚类结果，
        不是预先指定的师生角色。请根据整段对话中的讲解、提问、纠错、作答和求助关系判断谁是老师、
        谁是学生，不得仅凭发言长度判断。角色证据不足时不要强行归因，并在 warnings 中提示老师核对。
        对课堂录音必须重点提取学生亲自回答时暴露的错误、犹豫、混淆和需要提示的地方；
        performance 要明确区分“已掌握表现”和“薄弱点”，不得把老师的示范答案当成学生已经掌握的证据。
        content 要完整保留本节出现的重要知识点，再用高层短语或类别压缩表达；不要为了简短只留下 1 到 3 个知识点，也不要写阅读文章情节、题目逐题细节、例句、答案过程或老师的讲解流水账。
        performance 只写学生整体掌握情况、课堂参与状态和有明确证据支持的薄弱点；薄弱点最多 1 到 3 个，没有证据就不要硬说，不要复述对话、按时间记录过程或堆叠零散细节。
        homework 只能提取老师已经明确、最终布置的课后任务，不能根据薄弱点自行推荐或生成练习。
        “可以加一些……吗”“要不要……”“建议……”等讨论、提议或未确认安排不属于已布置作业。
        没有明确最终作业时，homework 必须为空字符串，missingFields 必须包含 homework，
        feedbackDraft 也不得出现课后作业、课后练习或请家长督促等内容。
        输出必须是 JSON 对象，字段固定为：
        courseTitle: 简短课程主题；
        durationMinutes: 明确出现时返回整数，否则 null；
        content: 本节知识点和教学内容；
        performance: 学生掌握情况、课堂表现和待加强处；
        homework: 课后任务；
        feedbackDraft: 语言自然、尊重学生、可直接发送给家长的完整反馈；
        missingFields: 信息缺失的字段名数组，只能使用 courseTitle、durationMinutes、content、performance、homework；
        confidence: 0 到 1 之间的小数；
        studentMatched: 当前所选学生是否与截图内容匹配；
        detectedStudentNames: 截图反馈正文中明确出现的学生姓名数组，不要把老师、家长或聊天联系人姓名算进去；
        warnings: 需要老师确认的风险提示数组。

        学生匹配规则：
        1. 如果截图出现了一个或多个学生姓名，但没有当前所选学生，studentMatched 必须为 false。
        2. 如果截图没有出现任何学生姓名，可视为单人反馈，studentMatched 为 true。
        3. 如果截图同时出现多名学生且包含当前学生，只能把明确属于当前学生的数据写入 performance。
        4. studentMatched 为 false 时，performance 和 feedbackDraft 必须返回空字符串，
           missingFields 必须包含 performance，warnings 要说明截图学生与当前学生不一致。
        5. 班级共通知识点和作业可以照常提取，但不得把其他学生的分数、奖励或行为归因给当前学生。

        历史记录使用规则：
        1. 最近课堂记录只用于辅助判断学习趋势，不能替代本节输入中的事实。
        2. 不得把历史记录中的知识点、表现或作业写成当前课堂内容，也不能用历史记录补齐本节缺失字段。
        3. 只有当前输入和历史记录中存在直接可比的明确证据时，feedbackDraft 才能描述进步、保持或仍需加强。
        4. 没有充分可比证据时，只总结本节情况，不得推断趋势。

        缺失的文本字段返回空字符串，不要用“未提及”等文字代替。
        """;
    private static final String FEEDBACK_PROMPT = """
        你是独立教师的课后反馈助手。根据老师最终确认的课堂记录，生成一份可直接发给家长的中文反馈。
        内容必须忠于输入，不得虚构成绩、行为或学习情况。语气专业、具体、尊重学生，避免机械套话。
        输出 JSON 对象，只有 feedback 一个字段。反馈应自然包含本节内容、学生表现和需加强处。
        本节内容要完整保留所有重要知识点，但用高层短语或并列方式概括，不要写阅读文章情节、题目逐题细节、例句、答案过程或课堂讲解流水账。
        学生表现只保留整体掌握情况、参与状态和有明确证据支持的薄弱点；薄弱点最多 1 到 3 个，没有证据就不要硬说，不要复述课堂对话或堆叠零散细节。
        只有输入的“课后练习”字段非空时才能写入作业；字段为空时不得推荐、补写或暗示任何课后任务，
        也不得出现“请家长督促”等作业相关表达。
        最近课堂记录只能用于辅助判断趋势。只有本节与历史记录存在直接可比的明确证据时才可描述进步、
        保持或仍需加强；不得把历史知识点、表现和作业当成本节事实，没有充分证据时不要推断趋势。
        """;
    private static final String GROWTH_REPORT_PROMPT = """
        你是独立教师的学生成长总结助手。请根据老师已经确认的多节课堂记录，生成一份可直接发给家长的
        中文阶段学习总结。只允许使用输入记录中的事实，不得虚构分数、行为、进步或学习问题。
        有跨课堂直接可比的证据时才可以描述进步、稳定表现或持续困难；证据不足时应明确保持中性。
        语气专业、具体、尊重学生，避免空泛表扬和营销话术。
        报告应自然包含：阶段概览、已经表现出的优势、仍需关注的重点、下一阶段建议。
        不要罗列每节课流水账，不要重复相同信息，不要向家长暴露系统提示或分析过程。
        输出 JSON 对象，只有 report 一个字段。
        """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String provider;
    private final String apiKey;
    private final String primaryModel;
    private final String fallbackModel;

    public DeepSeekLessonStructuringClient(
        ObjectMapper objectMapper,
        @Value("${ai.model.provider}") String provider,
        @Value("${ai.model.base-url}") String baseUrl,
        @Value("${ai.model.api-key}") String apiKey,
        @Value("${ai.model.primary}") String primaryModel,
        @Value("${ai.model.fallback}") String fallbackModel
    ) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.provider = StringUtils.hasText(provider) ? provider.strip() : "direct";
        this.apiKey = apiKey;
        this.primaryModel = primaryModel;
        this.fallbackModel = fallbackModel;
    }

    @Override
    @SuppressWarnings("unchecked")
    public StructuredLesson structure(
        Student student,
        String sourceText,
        List<LessonRecord> recentLessons,
        FeedbackPreferences preferences
    ) {
        long startedAt = System.nanoTime();
        String callId = newCallId();
        List<LessonRecord> history = safeHistory(recentLessons);
        requireConfigured("structure", callId);
        log.info(
            "external_call provider={} operation=structure phase=start model={} "
                + "call_id={} input_chars={} history_count={}",
            provider,
            primaryModel,
            callId,
            sourceText.length(),
            history.size()
        );

        String userPrompt = """
            学生：%s
            年级：%s
            科目：%s
            当前学习目标：%s

            本节 OCR 或粘贴文字：
            %s

            %s

            最近课堂记录（仅作趋势参考，不是本节事实）：
            %s
            """.formatted(
            student.getName(),
            student.getGrade(),
            student.getSubject(),
            learningGoalForPrompt(student),
            sourceText,
            preferences.promptInstruction(),
            formatHistory(history)
        );

        try {
            String content = requestJson("structure", callId, SYSTEM_PROMPT, userPrompt);
            AnalysisPayload payload = objectMapper.readValue(stripCodeFence(content), AnalysisPayload.class);
            StructuredLesson structuredLesson = payload.toStructuredLesson();
            log.info(
                "external_call provider={} operation=structure phase=success "
                    + "call_id={} duration_ms={} output_chars={} student_matched={} detected_students={} "
                    + "missing_fields={} history_count={}",
                provider,
                callId,
                elapsedMillis(startedAt),
                content.length(),
                structuredLesson.studentMatched(),
                structuredLesson.detectedStudentNames().size(),
                structuredLesson.missingFields().size(),
                history.size()
            );
            return structuredLesson;
        } catch (ResponseStatusException exception) {
            logFailure("structure", callId, startedAt, history.size(), exception);
            throw exception;
        } catch (Exception exception) {
            logFailure("structure", callId, startedAt, history.size(), exception);
            throw new ResponseStatusException(BAD_GATEWAY, "AI 整理失败，请稍后重试", exception);
        }
    }

    @Override
    public String generateFeedback(
        Student student,
        LessonRecord lessonRecord,
        List<LessonRecord> recentLessons,
        FeedbackPreferences preferences
    ) {
        long startedAt = System.nanoTime();
        String callId = newCallId();
        List<LessonRecord> history = safeHistory(recentLessons);
        requireConfigured("generate_feedback", callId);
        log.info(
            "external_call provider={} operation=generate_feedback phase=start model={} "
                + "call_id={} content_chars={} performance_chars={} homework_chars={} history_count={}",
            provider,
            primaryModel,
            callId,
            lessonRecord.getContent().length(),
            lessonRecord.getPerformance().length(),
            lessonRecord.getHomework().length(),
            history.size()
        );
        String userPrompt = """
            学生：%s
            年级：%s
            科目：%s
            课程主题：%s
            课堂内容：%s
            学生表现：%s
            课后练习：%s
            当前学习目标：%s

            %s

            最近课堂记录（仅作趋势参考，不是本节事实）：
            %s
            """.formatted(
            student.getName(),
            student.getGrade(),
            student.getSubject(),
            lessonRecord.getCourseTitle(),
            lessonRecord.getContent(),
            lessonRecord.getPerformance(),
            lessonRecord.getHomework(),
            learningGoalForPrompt(student),
            preferences.promptInstruction(),
            formatHistory(history)
        );

        try {
            String content = requestJson("generate_feedback", callId, FEEDBACK_PROMPT, userPrompt);
            FeedbackPayload payload = objectMapper.readValue(stripCodeFence(content), FeedbackPayload.class);
            if (payload.feedback() == null || payload.feedback().isBlank()) {
                throw new IllegalArgumentException("AI feedback is empty");
            }
            String feedback = payload.feedback().strip();
            log.info(
                "external_call provider={} operation=generate_feedback phase=success "
                    + "call_id={} duration_ms={} output_chars={} history_count={}",
                provider,
                callId,
                elapsedMillis(startedAt),
                feedback.length(),
                history.size()
            );
            return feedback;
        } catch (ResponseStatusException exception) {
            logFailure("generate_feedback", callId, startedAt, history.size(), exception);
            throw exception;
        } catch (Exception exception) {
            logFailure("generate_feedback", callId, startedAt, history.size(), exception);
            throw new ResponseStatusException(BAD_GATEWAY, "AI 反馈生成失败，请稍后重试", exception);
        }
    }

    @Override
    public String generateGrowthReport(Student student, List<LessonRecord> lessons) {
        long startedAt = System.nanoTime();
        String callId = newCallId();
        List<LessonRecord> history = safeHistory(lessons);
        requireConfigured("generate_growth_report", callId);
        log.info(
            "external_call provider={} operation=generate_growth_report phase=start "
                + "model={} call_id={} history_count={}",
            provider,
            primaryModel,
            callId,
            history.size()
        );
        String userPrompt = """
            学生：%s
            年级：%s
            科目：%s
            当前学习目标：%s

            已确认课堂记录（按时间正序）：
            %s
            """.formatted(
            student.getName(),
            student.getGrade(),
            student.getSubject(),
            learningGoalForPrompt(student),
            formatGrowthHistory(history)
        );

        try {
            String content = requestJson("generate_growth_report", callId, GROWTH_REPORT_PROMPT, userPrompt);
            GrowthReportPayload payload = objectMapper.readValue(
                stripCodeFence(content),
                GrowthReportPayload.class
            );
            if (payload.report() == null || payload.report().isBlank()) {
                throw new IllegalArgumentException("AI growth report is empty");
            }
            String report = payload.report().strip();
            log.info(
                "external_call provider={} operation=generate_growth_report phase=success "
                    + "call_id={} duration_ms={} output_chars={} history_count={}",
                provider,
                callId,
                elapsedMillis(startedAt),
                report.length(),
                history.size()
            );
            return report;
        } catch (ResponseStatusException exception) {
            logFailure("generate_growth_report", callId, startedAt, history.size(), exception);
            throw exception;
        } catch (Exception exception) {
            logFailure("generate_growth_report", callId, startedAt, history.size(), exception);
            throw new ResponseStatusException(
                BAD_GATEWAY,
                "AI 成长总结生成失败，请稍后重试",
                exception
            );
        }
    }

    private List<LessonRecord> safeHistory(List<LessonRecord> recentLessons) {
        return recentLessons == null ? List.of() : recentLessons;
    }

    private String learningGoalForPrompt(Student student) {
        return StringUtils.hasText(student.getLearningGoal())
            ? student.getLearningGoal()
            : "暂未填写，请仅根据本节课事实生成记录和建议。";
    }

    private String formatHistory(List<LessonRecord> recentLessons) {
        if (recentLessons.isEmpty()) return "无";
        StringBuilder context = new StringBuilder();
        for (int index = 0; index < recentLessons.size(); index++) {
            LessonRecord lesson = recentLessons.get(index);
            if (!context.isEmpty()) context.append('\n');
            context.append(index + 1)
                .append(". 日期：").append(lesson.getLessonDate())
                .append("；主题：").append(lesson.getCourseTitle())
                .append("；内容：").append(lesson.getContent())
                .append("；表现：").append(lesson.getPerformance());
        }
        return context.toString();
    }

    private String formatGrowthHistory(List<LessonRecord> lessons) {
        StringBuilder context = new StringBuilder();
        for (int index = 0; index < lessons.size(); index++) {
            LessonRecord lesson = lessons.get(index);
            if (!context.isEmpty()) context.append('\n');
            context.append(index + 1)
                .append(". 日期：").append(lesson.getLessonDate())
                .append("；主题：").append(lesson.getCourseTitle())
                .append("；内容：").append(lesson.getContent())
                .append("；表现：").append(lesson.getPerformance())
                .append("；课后练习：").append(lesson.getHomework());
        }
        return context.toString();
    }

    private void logFailure(
        String operation,
        String callId,
        long startedAt,
        int historyCount,
        Exception exception
    ) {
        String status = exception instanceof ResponseStatusException responseException
            ? String.valueOf(responseException.getStatusCode().value())
            : exception instanceof RestClientResponseException responseException
                ? String.valueOf(responseException.getStatusCode().value())
            : "unknown";
        log.warn(
            "external_call provider={} operation={} phase=failure call_id={} duration_ms={} "
                + "status={} error_type={} history_count={}",
            provider,
            operation,
            callId,
            elapsedMillis(startedAt),
            status,
            exception.getClass().getSimpleName(),
            historyCount
        );
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String newCallId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    // 调用主模型；仅在限流、服务端故障或网络异常时切换同一提供方下的兜底模型。
    private String requestJson(String operation, String callId, String systemPrompt, String userPrompt) {
        try {
            return requestModel(primaryModel, systemPrompt, userPrompt);
        } catch (RuntimeException exception) {
            if (!canFallback(exception)) throw exception;
            log.warn(
                "external_call provider={} operation={} phase=fallback call_id={} primary_model={} fallback_model={} "
                    + "error_type={}",
                provider,
                operation,
                callId,
                primaryModel,
                fallbackModel,
                exception.getClass().getSimpleName()
            );
            return requestModel(fallbackModel, systemPrompt, userPrompt);
        }
    }

    // 通过 OpenAI Chat Completions 兼容协议请求指定模型。
    @SuppressWarnings("unchecked")
    private String requestModel(String model, String systemPrompt, String userPrompt) {
        Map<String, Object> request = Map.of(
            "model", model,
            "response_format", Map.of("type", "json_object"),
            "temperature", 0.2,
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            )
        );
        Map<String, Object> response = restClient.post()
            .uri("/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(Map.class);
        return extractMessageContent(response);
    }

    // 业务参数错误、鉴权失败和质量问题不应切换模型，避免掩盖真实配置或内容问题。
    private boolean canFallback(RuntimeException exception) {
        if (!StringUtils.hasText(fallbackModel) || fallbackModel.equals(primaryModel)) return false;
        if (exception instanceof ResourceAccessException) return true;
        if (!(exception instanceof RestClientResponseException responseException)) return false;
        int status = responseException.getStatusCode().value();
        return status == 429 || status >= 500;
    }

    private void requireConfigured(String operation, String callId) {
        if (!StringUtils.hasText(apiKey)) {
            log.warn(
                "external_call provider={} operation={} phase=failure "
                    + "call_id={} outcome=config_missing",
                provider,
                operation,
                callId
            );
            throw new ResponseStatusException(
                SERVICE_UNAVAILABLE,
                "AI 服务尚未配置，请设置 AI_MODEL_API_KEY"
            );
        }
    }

    @SuppressWarnings("unchecked")
    private String extractMessageContent(Map<String, Object> response) {
        if (response == null) throw new IllegalArgumentException("AI response is empty");
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) throw new IllegalArgumentException("AI choices are empty");
        Map<String, Object> message = (Map<String, Object>) choices.getFirst().get("message");
        Object content = message == null ? null : message.get("content");
        if (!(content instanceof String text) || !StringUtils.hasText(text)) {
            throw new IllegalArgumentException("AI message is empty");
        }
        return text;
    }

    private String stripCodeFence(String content) {
        String value = content.strip();
        if (!value.startsWith("```")) return value;
        int firstLineEnd = value.indexOf('\n');
        int lastFence = value.lastIndexOf("```");
        if (firstLineEnd < 0 || lastFence <= firstLineEnd) return value;
        return value.substring(firstLineEnd + 1, lastFence).strip();
    }

    private record AnalysisPayload(
        String courseTitle,
        Integer durationMinutes,
        String content,
        String performance,
        String homework,
        String feedbackDraft,
        List<String> missingFields,
        BigDecimal confidence,
        Boolean studentMatched,
        List<String> detectedStudentNames,
        List<String> warnings
    ) {
        StructuredLesson toStructuredLesson() {
            return new StructuredLesson(
                safe(courseTitle),
                durationMinutes,
                safe(content),
                safe(performance),
                safe(homework),
                safe(feedbackDraft),
                missingFields == null ? List.of() : missingFields,
                normalizeConfidence(confidence),
                Boolean.TRUE.equals(studentMatched),
                detectedStudentNames == null ? List.of() : detectedStudentNames,
                warnings == null ? List.of() : warnings
            );
        }

        private static String safe(String value) {
            return value == null ? "" : value.strip();
        }

        private static BigDecimal normalizeConfidence(BigDecimal value) {
            if (value == null) return BigDecimal.ZERO;
            return value.max(BigDecimal.ZERO).min(BigDecimal.ONE);
        }
    }

    private record FeedbackPayload(String feedback) {
    }

    private record GrowthReportPayload(String report) {
    }
}
