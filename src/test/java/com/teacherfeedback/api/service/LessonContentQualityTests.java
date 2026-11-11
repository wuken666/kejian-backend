package com.teacherfeedback.api.service;

import com.teacherfeedback.api.client.LessonStructuringClient.StructuredLesson;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LessonContentQualityTests {

    @Test
    void rejectsFillerOnlyTranscriptsBeforeCallingAi() {
        assertThrows(
            ResponseStatusException.class,
            () -> LessonContentQuality.requireMeaningfulSource(
                "[说话人0] 嗯，这个那个，然后就是……"
            )
        );
    }

    @Test
    void acceptsShortButConcreteLessonFacts() {
        assertDoesNotThrow(
            () -> LessonContentQuality.requireMeaningfulSource(
                "复习一般现在时，学生三单变化仍会出错。"
            )
        );
    }

    @Test
    void rejectsObviousMojibake() {
        assertThrows(
            ResponseStatusException.class,
            () -> LessonContentQuality.requireMeaningfulSource("è¯¾å è®°å½")
        );
    }

    @Test
    void rejectsRawTranscriptReturnedAsStructuredContent() {
        String source = "[说话人0] 今天复习一般现在时，学生在三单变化和助动词选择上仍会出错。";
        StructuredLesson result = new StructuredLesson(
            "一般现在时",
            60,
            source,
            "",
            "",
            "",
            List.of("performance", "homework"),
            new BigDecimal("0.400"),
            true,
            List.of(),
            List.of()
        );

        assertThrows(
            ResponseStatusException.class,
            () -> LessonContentQuality.requireStructuredResult(source, result)
        );
    }

    @Test
    void rejectsBoilerplateFeedbackWithoutClassroomFacts() {
        assertThrows(
            ResponseStatusException.class,
            () -> LessonContentQuality.requireGeneratedFeedback(
                "梓涵同学，本节课的课堂记录中未呈现明确的教学内容与学习表现，也没有布置课后练习。建议后续课程中明确学习目标。"
            )
        );
    }

    @Test
    void acceptsFeedbackWithConcreteClassroomFacts() {
        assertDoesNotThrow(
            () -> LessonContentQuality.requireGeneratedFeedback(
                "本节复习一般现在时，梓涵能完成基础判断题，但第三人称单数变化仍需在课后练习中巩固。"
            )
        );
    }

    @Test
    void rejectsUnconfirmedHomeworkInGeneratedFeedback() {
        assertThrows(
            ResponseStatusException.class,
            () -> LessonContentQuality.requireGeneratedFeedback(
                "本节复习一般现在时，建议课后完成相关练习。",
                ""
            )
        );
    }

    @Test
    void acceptsHomeworkOnlyWhenTeacherConfirmedIt() {
        assertDoesNotThrow(
            () -> LessonContentQuality.requireGeneratedFeedback(
                "本节复习一般现在时，课后完成练习册第 12 页。",
                "完成练习册第 12 页"
            )
        );
    }

    @Test
    void rejectsPlaceholderLessonFieldsBeforeFeedbackGeneration() {
        assertThrows(
            ResponseStatusException.class,
            () -> LessonContentQuality.requireMeaningfulLesson(
                "英语课",
                "。",
                "未记录",
                "无"
            )
        );
    }

    @Test
    void acceptsExplicitNoHomeworkAfterConcreteLessonDetails() {
        assertDoesNotThrow(
            () -> LessonContentQuality.requireMeaningfulLesson(
                "一般现在时复习",
                "复习第三人称单数变化和助动词选择。",
                "基础题掌握稳定，否定句仍需提示。",
                "本节无作业"
            )
        );
    }

    @Test
    void acceptsCommonTeacherPhrasesForNoHomework() {
        assertDoesNotThrow(
            () -> LessonContentQuality.requireMeaningfulLesson(
                "一般现在时复习",
                "复习第三人称单数变化和助动词选择。",
                "基础题掌握稳定。",
                "本节课未布置课后练习"
            )
        );
    }

    @Test
    void acceptsUnknownPerformanceAndHomeworkAfterConcreteLessonContent() {
        assertDoesNotThrow(
            () -> LessonContentQuality.requireMeaningfulLesson(
                "一般现在时复习",
                "复习第三人称单数变化和助动词选择。",
                "",
                ""
            )
        );
    }
}
