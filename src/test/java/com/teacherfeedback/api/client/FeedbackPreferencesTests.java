package com.teacherfeedback.api.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeedbackPreferencesTests {

    @Test
    void briefFeedbackRequiresHighLevelSummaryWithoutReadingDetails() {
        String instruction = new FeedbackPreferences("professional", "brief").promptInstruction();

        assertTrue(instruction.contains("完整保留本节重要知识点"));
        assertTrue(instruction.contains("薄弱处最多保留 1 到 3 个"));
        assertTrue(instruction.contains("没有证据就不要硬说"));
        assertTrue(instruction.contains("删除阅读理解文章情节"));
        assertTrue(instruction.contains("简洁版控制在 3 到 5 句"));
        assertFalse(instruction.contains("只保留最重要的 1 到 3 个知识点类别"));
        assertFalse(instruction.contains("350 到 500 个汉字"));
    }

    @Test
    void standardFeedbackAlsoRequiresKnowledgePointSummary() {
        String instruction = new FeedbackPreferences("professional", "standard").promptInstruction();

        assertTrue(instruction.contains("知识点应使用概括性的短语或类别表达"));
        assertTrue(instruction.contains("不要复述课堂对话"));
        assertFalse(instruction.contains("简洁版最多 3 句"));
    }
}
