package com.college.feesmanagement.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

@Entity
@Table(name = "subjects")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long subjectId;

    @NotBlank(message = "Subject name is required")
    private String name;

    @Column(unique = true)
    private String subjectCode;

    @ManyToOne
    @JoinColumn(name = "dept_id", nullable = false)
    @JsonIgnoreProperties({"students", "subjects"})
    private Department department;

    private Integer semester;

    @Enumerated(EnumType.STRING)
    private SubjectType type;

    private Double fee;

    // ── Hall Ticket fields ────────────────────────────────────
    @Column(name = "exam_date")
    private LocalDate examDate;

    // AN = Afternoon, FN = Forenoon
    @Column(name = "session", length = 5)
    private String session;

    public enum SubjectType {
        THEORY(400.0),
        PRACTICAL(400.0),
        INTEGRATED(800.0);

        private final Double defaultFee;
        SubjectType(Double defaultFee) { this.defaultFee = defaultFee; }
        public Double getDefaultFee() { return defaultFee; }
    }

    public Subject() {}

    @PrePersist @PreUpdate
    private void setDefaultFee() {
        if (this.type != null && this.fee == null)
            this.fee = this.type.getDefaultFee();
    }

    // Getters and Setters
    public Long getSubjectId() { return subjectId; }
    public void setSubjectId(Long subjectId) { this.subjectId = subjectId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }

    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }

    public Integer getSemester() { return semester; }
    public void setSemester(Integer semester) { this.semester = semester; }

    public SubjectType getType() { return type; }
    public void setType(SubjectType type) { this.type = type; }

    public Double getFee() { return fee; }
    public void setFee(Double fee) { this.fee = fee; }

    public LocalDate getExamDate() { return examDate; }
    public void setExamDate(LocalDate examDate) { this.examDate = examDate; }

    public String getSession() { return session; }
    public void setSession(String session) { this.session = session; }
}