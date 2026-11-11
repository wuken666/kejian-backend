package com.teacherfeedback.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "student_hotwords",
    uniqueConstraints = @UniqueConstraint(name = "uk_student_hotwords_term", columnNames = {"student_id", "term"})
)
public class StudentHotword {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false, length = 30)
    private String term;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected StudentHotword() {
    }

    public StudentHotword(Student student, String term) {
        this.student = student;
        this.term = term.strip();
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public String getTerm() { return term; }
}
