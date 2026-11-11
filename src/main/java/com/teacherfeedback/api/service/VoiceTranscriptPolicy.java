package com.teacherfeedback.api.service;

import com.teacherfeedback.api.client.LessonStructuringClient.StructuredLesson;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class VoiceTranscriptPolicy {
    private static final Pattern EXPLICIT_HOMEWORK = Pattern.compile(
        "课后作业|家庭作业|回家作业|今天(?:的)?作业|本节(?:课)?(?:的)?作业|"
            + "本次(?:的)?作业|本周(?:的)?作业|下次课前.{0,16}(?:完成|做完|提交)|"
            + "回家后?.{0,12}(?:完成|做完)"
    );
    private static final Pattern EXPLICIT_NO_HOMEWORK = Pattern.compile(
        "(?:没有|没|未|无|无需|不用|不需要|不必|未安排|未布置|没有安排|没有布置)"
            + ".{0,10}(?:课后作业|家庭作业|回家作业|课后练习|作业|练习)"
            + "|(?:课后作业|家庭作业|回家作业|课后练习|作业|练习)"
            + ".{0,10}(?:没有|没|无|无需|不用|不需要|不必|未安排|未布置)"
    );
    private static final Pattern SPEAKER_LABEL = Pattern.compile("\\[说话人\\s*\\d+]");
    private static final Pattern HOMEWORK_FEEDBACK_SENTENCE = Pattern.compile(
        "课后作业|家庭作业|回家作业|课后练习|作业为|作业是|请家长.{0,12}督促"
    );
    private static final BigDecimal UNSEPARATED_CONFIDENCE_LIMIT = new BigDecimal("0.650");

    private VoiceTranscriptPolicy() {
    }

    // 语音转写缺少明确证据时，移除 AI 推测的作业并降低未分角色结果的可信度。
    static StructuredLesson apply(String sourceText, StructuredLesson lesson) {
        boolean hasExplicitNoHomework = EXPLICIT_NO_HOMEWORK.matcher(sourceText).find();
        boolean hasExplicitHomework = !hasExplicitNoHomework
            && EXPLICIT_HOMEWORK.matcher(sourceText).find();
        boolean hasSpeakerLabels = SPEAKER_LABEL.matcher(sourceText).find();
        List<String> missingFields = new ArrayList<>(lesson.missingFields());
        List<String> warnings = new ArrayList<>(lesson.warnings());
        String homework = lesson.homework();
        String feedbackDraft = lesson.feedbackDraft();
        BigDecimal confidence = lesson.confidence();

        if (!hasExplicitHomework && homework != null && !homework.isBlank()) {
            homework = "";
            feedbackDraft = removeHomeworkSentences(feedbackDraft);
            addOnce(missingFields, "homework");
            addOnce(warnings, "录音中未识别到明确最终布置的课后作业，已移除 AI 推测内容。");
        }
        if (!hasSpeakerLabels) {
            confidence = confidence == null
                ? UNSEPARATED_CONFIDENCE_LIMIT
                : confidence.min(UNSEPARATED_CONFIDENCE_LIMIT);
            addOnce(warnings, "本节语音未可靠区分老师和学生，课堂表现需由老师确认。");
        }

        return new StructuredLesson(
            lesson.courseTitle(),
            lesson.durationMinutes(),
            lesson.content(),
            lesson.performance(),
            homework,
            feedbackDraft,
            List.copyOf(missingFields),
            confidence,
            lesson.studentMatched(),
            lesson.detectedStudentNames(),
            List.copyOf(warnings)
        );
    }

    // 删除反馈草稿中没有课堂证据支撑的作业句子，避免后续直接发送给家长。
    private static String removeHomeworkSentences(String feedbackDraft) {
        if (feedbackDraft == null || feedbackDraft.isBlank()) return "";
        StringBuilder result = new StringBuilder();
        for (String sentence : feedbackDraft.split("(?<=[。！？；\\n])")) {
            if (!HOMEWORK_FEEDBACK_SENTENCE.matcher(sentence).find()) {
                result.append(sentence);
            }
        }
        return result.toString().strip();
    }

    private static void addOnce(List<String> values, String value) {
        if (!values.contains(value)) values.add(value);
    }
}
