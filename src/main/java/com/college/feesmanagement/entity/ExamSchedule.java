package com.college.feesmanagement.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Exam schedule — one row per subject per exam cycle.
 *
 * This table is REFRESHED every semester (all rows deleted and re-entered).
 * It is intentionally NOT historical — it only holds the CURRENT exam timetable.
 *
 * Separation from subjects table:
 *   - subjects = permanent master data (name, code, dept, semester, type, fee)
 *   - exam_schedule = temporary per-cycle data (date, session, cycle label)
 *
 * Cleared by: POST /exam-controller/schedule/reset
 * Populated by: PUT /exam-controller/subjects/{id}/schedule
 *               POST /exam-controller/subjects/bulk-schedule
 */
@Entity
@Table(name = "exam_schedule",
       uniqueConstraints = @UniqueConstraint(columnNames = {"subject_id", "exam_cycle"}))
public class ExamSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Which subject this schedule entry is for
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    @JsonIgnoreProperties({"examSchedules"})
    private Subject subject;

    // The exam date for this subject in this cycle
    @Column(name = "exam_date")
    private LocalDate examDate;

    // FN = Forenoon (10am–1pm), AN = Afternoon (2pm–5pm)
    @Column(name = "session", length = 5)
    private String session;

    // Which exam cycle this schedule belongs to
    // e.g. "NOV/DEC 2025" or "APR/MAY 2026"
    // Used to identify and clear old schedules when a new semester starts
    @Column(name = "exam_cycle", nullable = false)
    private String examCycle;

    // Academic year this schedule belongs to e.g. "2025-2026"
    @Column(name = "academic_year")
    private String academicYear;

    // When this schedule entry was last updated
    @Column(name = "updated_on")
    private LocalDateTime updatedOn;

    // Who set this schedule (controller name for audit)
    @Column(name = "set_by")
    private String setBy;

    public ExamSchedule() {}

    // ── Getters & Setters ─────────────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Subject getSubject() { return subject; }
    public void setSubject(Subject subject) { this.subject = subject; }

    public LocalDate getExamDate() { return examDate; }
    public void setExamDate(LocalDate examDate) { this.examDate = examDate; }

    public String getSession() { return session; }
    public void setSession(String session) { this.session = session; }

    public String getExamCycle() { return examCycle; }
    public void setExamCycle(String examCycle) { this.examCycle = examCycle; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

    public LocalDateTime getUpdatedOn() { return updatedOn; }
    public void setUpdatedOn(LocalDateTime updatedOn) { this.updatedOn = updatedOn; }

    public String getSetBy() { return setBy; }
    public void setSetBy(String setBy) { this.setBy = setBy; }

    // Convenience: is this entry fully scheduled?
    @Transient
    public boolean isScheduled() {
        return examDate != null && session != null && !session.isBlank();
    }
}