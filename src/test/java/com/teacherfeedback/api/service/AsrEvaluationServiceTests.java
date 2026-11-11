package com.teacherfeedback.api.service;

import com.teacherfeedback.api.client.AsrClient;
import com.teacherfeedback.api.client.AsrClient.TranscriptionResult;
import com.teacherfeedback.api.dao.AsrEvaluationRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.AsrEvaluationResponse;
import com.teacherfeedback.api.model.AsrEvaluation;
import com.teacherfeedback.api.model.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsrEvaluationServiceTests {
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private AsrEvaluationRepository evaluationRepository;
    @Mock
    private AsrHotwordService hotwordService;
    @Mock
    private AsrClient asrClient;

    @Test
    void evaluatesRecognizedTextWithTheResolvedStudentHotwords() {
        Student student = new Student("江宝", "初二", "英语", "");
        assignId(student, 7L);
        MockMultipartFile audio = new MockMultipartFile(
            "audio",
            "lesson.m4a",
            "audio/mp4",
            new byte[] {1, 2, 3}
        );
        when(studentRepository.findById(7L)).thenReturn(Optional.of(student));
        when(hotwordService.resolveTerms(7L, "Unit 1")).thenReturn(List.of("江宝", "Unit 1"));
        when(asrClient.transcribe(audio, List.of("江宝", "Unit 1")))
            .thenReturn(new TranscriptionResult("欢迎大加", 0.42));
        when(evaluationRepository.save(org.mockito.ArgumentMatchers.any(AsrEvaluation.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        AsrEvaluationResponse response = service().evaluate(
            7L,
            "Unit 1",
            "欢迎大家",
            audio
        );

        assertEquals(0.25, response.characterErrorRate(), 0.0001);
        assertEquals(1, response.substitutions());
        assertEquals(0.42, response.inferenceSeconds(), 0.0001);
        assertEquals(List.of("江宝", "Unit 1"), response.hotwords());

        ArgumentCaptor<AsrEvaluation> evaluation = ArgumentCaptor.forClass(AsrEvaluation.class);
        verify(evaluationRepository).save(evaluation.capture());
        assertEquals("lesson.m4a", evaluation.getValue().getOriginalFilename());
    }

    private AsrEvaluationService service() {
        return new AsrEvaluationService(
            studentRepository,
            evaluationRepository,
            hotwordService,
            asrClient
        );
    }

    private void assignId(Student student, long id) {
        try {
            Field field = Student.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(student, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
