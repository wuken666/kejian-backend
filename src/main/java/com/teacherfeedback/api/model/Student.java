package com.teacherfeedback.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "teacher_id", nullable = false)
    private Long teacherId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 30)
    private String grade;

    @Column(nullable = false, length = 30)
    private String subject;

    @Column(name = "learning_goal", nullable = false, length = 255)
    private String learningGoal;

    @Column(nullable = false, length = 255)
    private String address = "";

    @Column(name = "lesson_fee", precision = 8, scale = 2)
    private BigDecimal lessonFee;

    @Column(nullable = false)
    private boolean graduated;

    @Column(nullable = false, length = 80)
    private String status;

    @Column(name = "last_lesson_date")
    private LocalDate lastLessonDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Student() {
    }

    public Student(String name, String grade, String subject, String learningGoal) {
        this(1L, name, grade, subject, learningGoal);
    }

    // 创建学生时从服务端当前登录老师写入归属，前端不能传入或覆盖该字段。
    public Student(Long teacherId, String name, String grade, String subject, String learningGoal) {
        this.teacherId = teacherId == null ? 1L : teacherId;
        this.name = name.strip();
        this.grade = grade.strip();
        this.subject = subject.strip();
        this.learningGoal = normalizeLearningGoal(learningGoal);
        this.status = "新建档案";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void updateProfile(String name, String grade, String subject, String learningGoal) {
        this.name = name.strip();
        this.grade = grade.strip();
        this.subject = subject.strip();
        this.learningGoal = normalizeLearningGoal(learningGoal);
        this.updatedAt = LocalDateTime.now();
    }

    public void recordLessonOn(LocalDate lessonDate) {
        if (lastLessonDate == null || lessonDate.isAfter(lastLessonDate)) {
            lastLessonDate = lessonDate;
        }
        updatedAt = LocalDateTime.now();
    }

    public void setAddress(String address) {
        this.address = address == null ? "" : address.strip();
        this.updatedAt = LocalDateTime.now();
    }

    public void setGraduated(boolean graduated) {
        this.graduated = graduated;
        this.updatedAt = LocalDateTime.now();
    }

    public void setLessonFee(BigDecimal lessonFee) {
        this.lessonFee = lessonFee;
        this.updatedAt = LocalDateTime.now();
    }

    public void refreshLastLessonDate(LocalDate latestLessonDate) {
        this.lastLessonDate = latestLessonDate;
        this.updatedAt = LocalDateTime.now();
    }

    private String normalizeLearningGoal(String learningGoal) {
        return learningGoal == null ? "" : learningGoal.strip();
    }

    public Long getId() { return id; }
    public Long getTeacherId() { return teacherId; }
    public String getName() { return name; }
    public String getGrade() { return grade; }
    public String getSubject() { return subject; }
    public String getLearningGoal() { return learningGoal; }
    public String getAddress() { return address; }
    public BigDecimal getLessonFee() { return lessonFee; }
    public boolean isGraduated() { return graduated; }
    public String getStatus() { return status; }
    public LocalDate getLastLessonDate() { return lastLessonDate; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
