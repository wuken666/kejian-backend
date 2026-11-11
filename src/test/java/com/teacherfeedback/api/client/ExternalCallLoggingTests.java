package com.teacherfeedback.api.client;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.teacherfeedback.api.model.Student;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalCallLoggingTests {

    @Test
    void ocrConfigurationFailureIsTraceableWithoutSensitiveContent() {
        Logger logger = (Logger) LoggerFactory.getLogger(AliyunMarketplaceOcrClient.class);
        ListAppender<ILoggingEvent> appender = startAppender(logger);
        try {
            AliyunMarketplaceOcrClient client = new AliyunMarketplaceOcrClient(
                "https://secret-endpoint.example",
                "",
                "img"
            );

            assertThrows(
                ResponseStatusException.class,
                () -> client.recognize("sensitive-image-content".getBytes())
            );

            String message = onlyMessage(appender);
            assertTrue(message.contains("provider=aliyun_ocr"));
            assertTrue(message.matches(".*call_id=[a-f0-9]{32}.*"));
            assertFalse(message.contains("secret-endpoint"));
            assertFalse(message.contains("sensitive-image-content"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void deepSeekConfigurationFailureIsTraceableWithoutStudentOrPromptContent() {
        Logger logger = (Logger) LoggerFactory.getLogger(DeepSeekLessonStructuringClient.class);
        ListAppender<ILoggingEvent> appender = startAppender(logger);
        try {
            DeepSeekLessonStructuringClient client = new DeepSeekLessonStructuringClient(
                new ObjectMapper(),
                "direct",
                "https://api.deepseek.com",
                "",
                "deepseek-v4-flash",
                ""
            );
            Student student = new Student(
                "sensitive-student-name",
                "初二",
                "英语",
                "sensitive-learning-goal"
            );

            assertThrows(
                ResponseStatusException.class,
                () -> client.structure(
                    student,
                    "sensitive-prompt-content",
                    List.of(),
                    FeedbackPreferences.fromNullable(null, null)
                )
            );

            String message = onlyMessage(appender);
            assertTrue(message.contains("provider=direct"));
            assertTrue(message.contains("operation=structure"));
            assertTrue(message.matches(".*call_id=[a-f0-9]{32}.*"));
            assertFalse(message.contains("sensitive-student-name"));
            assertFalse(message.contains("sensitive-learning-goal"));
            assertFalse(message.contains("sensitive-prompt-content"));
        } finally {
            logger.detachAppender(appender);
        }
    }

    private ListAppender<ILoggingEvent> startAppender(Logger logger) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private String onlyMessage(ListAppender<ILoggingEvent> appender) {
        assertTrue(appender.list.size() == 1);
        return appender.list.getFirst().getFormattedMessage();
    }
}
