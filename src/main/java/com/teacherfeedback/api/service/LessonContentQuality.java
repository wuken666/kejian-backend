package com.teacherfeedback.api.service;

import com.teacherfeedback.api.client.LessonStructuringClient.StructuredLesson;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

final class LessonContentQuality {
    private static final Pattern SPEAKER_LABEL = Pattern.compile("\\[说话人\\s*\\d+\\]");
    private static final Pattern NON_INFORMATION = Pattern.compile("[^\\p{IsHan}A-Za-z0-9]+");
    private static final Pattern COMMON_FILLERS = Pattern.compile(
        "(这个|那个|然后|就是|其实|对吧|嗯+|啊+|呃+|哦+|额+)"
    );
    private static final Pattern MOJIBAKE = Pattern.compile(
        ".*(�|Ã.|Â.|(?:æ|å|ç|è|é)[\\u0080-\\u00ff]).*",
        Pattern.DOTALL
    );
    private static final Pattern EXPLICIT_NO_HOMEWORK = Pattern.compile(
        "^(无|无作业|没有作业|本节无作业|本节课无作业|无需作业|"
            + "本节未布置|本节课未布置|未布置课后作业|未布置课后练习|"
            + "本节未布置课后作业|本节课未布置课后练习)$"
    );
    private static final Pattern BOILERPLATE_FEEDBACK = Pattern.compile(
        "本节课的课堂记录中未呈现明确的教学内容与学习表现"
            + "|未呈现明确的教学内容(?:与|和)学习表现"
            + "|建议后续课程中明确学习目标"
            + "|课堂记录中未呈现明确的教学内容"
    );
    private static final Pattern UNCONFIRMED_HOMEWORK = Pattern.compile(
        "作业|家庭作业|课后(?:练习|任务|作业|完成|复习|提交|打卡)"
            + "|请家长督促|回家(?:完成|练习|复习)"
    );

    private LessonContentQuality() {
    }

    // 在调用 AI 前拒绝空白、纯口头语和明显乱码，避免模型根据无效输入编造课堂事实。
    static void requireMeaningfulSource(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            throw insufficient("没有识别到可整理的课堂内容，请继续录音或补充文字。");
        }
        if (MOJIBAKE.matcher(sourceText).matches()) {
            throw insufficient("识别文字包含明显乱码，请重试转写或手动修正原文。");
        }
        String informative = informativeText(sourceText);
        if (informative.length() < 6) {
            throw insufficient("当前录音只有少量口头语或无效文字，请继续录音后再整理。");
        }
    }

    // 在保存课堂前校验老师确认的字段，防止用标点或套话绕过空输入限制。
    static void requireMeaningfulLesson(
        String courseTitle,
        String content,
        String performance,
        String homework
    ) {
        requireField(courseTitle, 2, "请补充明确的课程主题。");
        requireField(content, 6, "课堂内容不足，请补充实际讲解或练习的知识点。");
        if (performance != null && !performance.isBlank()) {
            requireField(performance, 4, "学生表现不足，请补充已掌握或需要加强的具体情况。");
        }
        String normalizedHomework = homework == null ? "" : homework.strip();
        if (!normalizedHomework.isBlank() && !EXPLICIT_NO_HOMEWORK.matcher(normalizedHomework).matches()) {
            requireField(homework, 2, "请补充课后练习，或明确填写“本节无作业”。");
        }
    }

    // 校验 AI 是否真正完成了结构化，原样返回转写时要求重试而不是伪装成功。
    static void requireStructuredResult(String sourceText, StructuredLesson result) {
        if (result == null) {
            throw insufficient("AI 没有返回课堂整理结果，请重试。");
        }
        String source = informativeText(sourceText);
        String content = informativeText(result.content());
        boolean hasStructuredField = !content.isBlank()
            || !informativeText(result.performance()).isBlank()
            || !informativeText(result.homework()).isBlank();
        if (!hasStructuredField) {
            throw insufficient("AI 未能从原文提取课堂信息，请补充内容后重试。");
        }
        if (source.length() >= 20 && source.equals(content)) {
            throw insufficient("AI 暂未完成整理，当前内容仍是原始转写，请重试。");
        }
        if (result.feedbackDraft() != null && !result.feedbackDraft().isBlank()) {
            requireGeneratedFeedback(result.feedbackDraft());
        }
    }

    // 拦截模型在缺少事实时生成的套话，避免把看似完整的伪反馈直接展示给家长。
    static void requireGeneratedFeedback(String feedback) {
        if (feedback == null || feedback.isBlank()) {
            throw insufficient("AI 未返回有效反馈，请补充课堂内容后重试。");
        }
        String compactFeedback = feedback.replaceAll("\\s+", "");
        if (BOILERPLATE_FEEDBACK.matcher(compactFeedback).find()) {
            throw insufficient("AI 反馈内容不足，请补充课堂信息后重试。");
        }
    }

    // 在有老师确认的课堂字段后再次约束作业事实，不能只依赖模型提示词避免误发作业。
    static void requireGeneratedFeedback(String feedback, String homework) {
        requireGeneratedFeedback(feedback);
        String normalizedHomework = homework == null ? "" : homework.strip();
        boolean homeworkConfirmed = !normalizedHomework.isBlank()
            && !EXPLICIT_NO_HOMEWORK.matcher(normalizedHomework).matches();
        if (!homeworkConfirmed
            && UNCONFIRMED_HOMEWORK.matcher(feedback.replaceAll("\\s+", "")).find()) {
            throw insufficient("AI 反馈疑似加入了未确认的课后作业，请重新生成。");
        }
    }

    // 提取可表达事实的中英文和数字，并移除说话人标签与常见口头语。
    private static String informativeText(String value) {
        if (value == null) return "";
        String withoutLabels = SPEAKER_LABEL.matcher(value).replaceAll(" ");
        String withoutFillers = COMMON_FILLERS.matcher(withoutLabels).replaceAll(" ");
        return NON_INFORMATION.matcher(withoutFillers).replaceAll("");
    }

    // 校验单个课堂字段是否包含足够的有效字符。
    private static void requireField(String value, int minimumLength, String message) {
        if (informativeText(value).length() < minimumLength) {
            throw insufficient(message);
        }
    }

    // 统一返回可由老师修正输入后重试的 422 业务错误。
    private static ResponseStatusException insufficient(String message) {
        return new ResponseStatusException(UNPROCESSABLE_ENTITY, message);
    }
}
