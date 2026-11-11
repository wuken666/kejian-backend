package com.teacherfeedback.api.client;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record FeedbackPreferences(String tone, String length) {
    public static final String DEFAULT_TONE = "professional";
    public static final String DEFAULT_LENGTH = "standard";

    private static final Set<String> TONES = Set.of("professional", "encouraging", "direct");
    private static final Set<String> LENGTHS = Set.of("brief", "standard", "detailed");
    private static final Map<String, String> TONE_PROMPTS = Map.of(
        "professional", "专业客观，表达清楚，不夸大也不过度热情",
        "encouraging", "温和鼓励，先肯定具体表现，再说明需要加强之处",
        "direct", "直接清晰，减少铺垫，重点突出课堂结论和下一步行动"
    );
    private static final Map<String, String> LENGTH_PROMPTS = Map.of(
        "brief", "简短摘要，约 100 到 180 个汉字，完整保留本节重要知识点并概括学生整体表现",
        "standard", "标准，约 220 到 320 个汉字",
        "detailed", "详细，约 350 到 500 个汉字，分段清楚但不要重复"
    );

    public FeedbackPreferences {
        tone = normalize(tone, DEFAULT_TONE);
        length = normalize(length, DEFAULT_LENGTH);
        if (!TONES.contains(tone) || !LENGTHS.contains(length)) {
            throw new IllegalArgumentException("不支持的反馈偏好");
        }
    }

    public static FeedbackPreferences fromNullable(String tone, String length) {
        return new FeedbackPreferences(tone, length);
    }

    public String promptInstruction() {
        String summaryRule = "brief".equals(length)
            ? """
                简洁版是摘要，不是删减重点：本节出现的多个重要知识点都要保留，用短语或并列方式压缩表达，不要只挑其中一两个。
                学生薄弱处最多保留 1 到 3 个；只有原文有明确错误、犹豫、混淆或需要提示的证据时才能写，没有证据就不要硬说。
                删除阅读理解文章情节、题目逐题细节、例句、答案过程、老师讲解过程、课堂对话和重复解释。
                简洁版控制在 3 到 5 句；不要为了凑字数补充背景、鼓励套话或细枝末节。
                """
            : """
                无论篇幅如何，知识点应使用概括性的短语或类别表达，不要把阅读文章细节、逐题过程、例句或答案当作知识点。
                学生表现只总结整体掌握、参与状态和有明确证据支持的薄弱点；薄弱点最多 1 到 3 个，没有证据就不要硬说，不要复述课堂对话或按时间顺序记录细节。
                """;
        return """
            家长反馈偏好：
            - 语气：%s
            - 篇幅：%s
            偏好只影响表达方式，不得因此增删、推断或夸大课堂事实。
            %s
            """.formatted(TONE_PROMPTS.get(tone), LENGTH_PROMPTS.get(length), summaryRule).strip();
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.strip().toLowerCase(Locale.ROOT);
    }
}
