package com.teacherfeedback.api.service;

import com.teacherfeedback.api.client.LessonStructuringClient.StructuredLesson;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceTranscriptPolicyTests {
    @Test
    void removesHomeworkWhenTranscriptOnlyContainsTentativeDiscussion() {
        StructuredLesson result = VoiceTranscriptPolicy.apply(
            "然后作业就是这个。还想说作业给他再加一个，我可以给你加一些完形吗？要不要试着做一下？",
            lesson(
                "完成一篇完形填空。",
                "本节复习语法。课后作业为完成一篇完形填空。请家长协助督促，谢谢！"
            )
        );

        assertEquals("", result.homework());
        assertFalse(result.feedbackDraft().contains("课后作业"));
        assertFalse(result.feedbackDraft().contains("请家长"));
        assertTrue(result.missingFields().contains("homework"));
        assertEquals(new BigDecimal("0.650"), result.confidence());
    }

    @Test
    void keepsHomeworkWhenTeacherClearlyAssignsIt() {
        StructuredLesson result = VoiceTranscriptPolicy.apply(
            "[说话人0] 今天的课后作业是完成练习册第三页。",
            lesson("完成练习册第三页。", "今天学习了语法。课后作业是完成练习册第三页。")
        );

        assertEquals("完成练习册第三页。", result.homework());
        assertFalse(result.missingFields().contains("homework"));
        assertEquals(new BigDecimal("0.820"), result.confidence());
    }

    @Test
    void removesHomeworkWhenTeacherExplicitlySaysThereIsNone() {
        StructuredLesson result = VoiceTranscriptPolicy.apply(
            "今天复习一般过去时，老师说明本节课没有布置课后作业。",
            lesson("完成练习册第三页。", "今天学习了语法。课后作业是完成练习册第三页。")
        );

        assertEquals("", result.homework());
        assertFalse(result.feedbackDraft().contains("课后作业"));
        assertTrue(result.missingFields().contains("homework"));
    }

    private StructuredLesson lesson(String homework, String feedbackDraft) {
        return new StructuredLesson(
            "英语课堂",
            null,
            "复习语法。",
            "部分题目需要加强。",
            homework,
            feedbackDraft,
            List.of("durationMinutes"),
            new BigDecimal("0.820"),
            true,
            List.of(),
            List.of()
        );
    }
}
