package com.teacherfeedback.api.service;

import com.teacherfeedback.api.auth.TeacherContext;
import com.teacherfeedback.api.dao.StudentHotwordRepository;
import com.teacherfeedback.api.dao.StudentRepository;
import com.teacherfeedback.api.dto.TeacherFeedbackDtos.AsrHotwordResponse;
import com.teacherfeedback.api.model.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AsrHotwordService {
    private static final int MAX_EFFECTIVE_TERMS = 50;
    private static final List<String> ENGLISH_TERMS = List.of(
        "音标", "自然拼读", "一般现在时", "一般过去时", "现在进行时",
        "现在完成时", "感叹句", "定语从句", "宾语从句", "易混词组"
    );
    private static final List<String> MATH_TERMS = List.of(
        "有理数", "因式分解", "一元一次方程", "一元二次方程", "二次函数",
        "勾股定理", "全等三角形", "相似三角形", "概率统计", "几何证明"
    );
    private static final List<String> CHINESE_TERMS = List.of(
        "文言文", "古诗词", "阅读理解", "中心思想", "修辞手法",
        "病句修改", "写作结构", "人物描写", "环境描写", "议论文"
    );

    private final StudentRepository studentRepository;
    private final StudentHotwordRepository hotwordRepository;

    public AsrHotwordService(
        StudentRepository studentRepository,
        StudentHotwordRepository hotwordRepository
    ) {
        this.studentRepository = studentRepository;
        this.hotwordRepository = hotwordRepository;
    }

    @Transactional(readOnly = true)
    public AsrHotwordResponse findForStudent(Long studentId, String courseTitle) {
        Student student = requireStudent(studentId);
        List<String> automaticTerms = automaticTerms(student, courseTitle);
        List<String> systemTerms = systemTerms(student);
        return new AsrHotwordResponse(
            studentId,
            automaticTerms,
            systemTerms,
            mergeTerms(automaticTerms, systemTerms, MAX_EFFECTIVE_TERMS)
        );
    }

    @Transactional(readOnly = true)
    public List<String> resolveTerms(Long studentId, String courseTitle) {
        if (studentId == null) {
            return normalizeTerms(List.of(courseTitle == null ? "" : courseTitle), MAX_EFFECTIVE_TERMS);
        }
        Student student = requireStudent(studentId);
        return mergeTerms(
            automaticTerms(student, courseTitle),
            systemTerms(student),
            MAX_EFFECTIVE_TERMS
        );
    }

    private List<String> automaticTerms(Student student, String courseTitle) {
        return normalizeTerms(
            List.of(
                student.getName(),
                student.getGrade(),
                student.getSubject(),
                courseTitle == null ? "" : courseTitle
            ),
            MAX_EFFECTIVE_TERMS
        );
    }

    // 学科基础词由系统维护，数据库中的学生专属词也只由后台配置，不交给老师填写。
    private List<String> systemTerms(Student student) {
        List<String> terms = new ArrayList<>(subjectTerms(student.getSubject()));
        terms.addAll(hotwordRepository.findByStudentIdOrderByTermAsc(student.getId())
            .stream()
            .map(hotword -> hotword.getTerm())
            .toList());
        return normalizeTerms(terms, MAX_EFFECTIVE_TERMS);
    }

    static List<String> subjectTerms(String subject) {
        if (subject == null) return List.of();
        String normalized = subject.strip().toLowerCase(Locale.ROOT);
        if (normalized.contains("英语") || normalized.contains("english")) return ENGLISH_TERMS;
        if (normalized.contains("数学") || normalized.contains("math")) return MATH_TERMS;
        if (normalized.contains("语文") || normalized.contains("chinese")) return CHINESE_TERMS;
        return List.of();
    }

    private Student requireStudent(Long studentId) {
        return TeacherContext.requireStudent(studentRepository, studentId);
    }

    private static List<String> mergeTerms(List<String> first, List<String> second, int limit) {
        List<String> combined = new ArrayList<>(first);
        combined.addAll(second);
        return normalizeTerms(combined, limit);
    }

    private static List<String> normalizeTerms(List<String> terms, int limit) {
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        if (terms == null) return List.of();
        for (String candidate : terms) {
            if (candidate == null) continue;
            String term = candidate.strip().replaceAll("\\s+", " ");
            if (term.isBlank() || term.length() > 30) continue;
            unique.putIfAbsent(term.toLowerCase(Locale.ROOT), term);
            if (unique.size() == limit) break;
        }
        return List.copyOf(unique.values());
    }
}
