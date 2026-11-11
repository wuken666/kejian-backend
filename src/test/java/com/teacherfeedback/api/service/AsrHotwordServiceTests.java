package com.teacherfeedback.api.service;

import com.teacherfeedback.api.dao.StudentHotwordRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.model.Student;
import com.teacherfeedback.api.model.StudentHotword;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsrHotwordServiceTests {
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private StudentHotwordRepository hotwordRepository;

    @Test
    void combinesProfileCourseAndSystemTermsWithoutDuplicates() {
        Student student = new Student("江宝", "初二", "英语", "掌握 Unit 1 感叹句");
        assignId(student, 7L);
        when(studentRepository.findById(7L)).thenReturn(Optional.of(student));
        when(hotwordRepository.findByStudentIdOrderByTermAsc(7L)).thenReturn(List.of(
            new StudentHotword(student, "Unit 1"),
            new StudentHotword(student, "易混词组")
        ));

        List<String> terms = service().resolveTerms(7L, "Unit 1 感叹句复习");

        assertEquals(List.of("江宝", "初二", "英语", "Unit 1 感叹句复习"), terms.subList(0, 4));
        assertTrue(terms.containsAll(List.of("自然拼读", "感叹句", "Unit 1", "易混词组")));
        assertEquals(1, terms.stream().filter("易混词组"::equals).count());
    }

    @Test
    void selectsSystemDictionaryFromStudentSubject() {
        assertTrue(AsrHotwordService.subjectTerms("英语").contains("现在完成时"));
        assertTrue(AsrHotwordService.subjectTerms("初中数学").contains("一元二次方程"));
        assertTrue(AsrHotwordService.subjectTerms("语文").contains("文言文"));
        assertEquals(List.of(), AsrHotwordService.subjectTerms("物理"));
    }

    private AsrHotwordService service() {
        return new AsrHotwordService(studentRepository, hotwordRepository);
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
