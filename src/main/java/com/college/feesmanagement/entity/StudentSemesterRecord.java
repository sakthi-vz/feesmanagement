package com.college.feesmanagement.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/**
 * Immutable promotion record — one row per student per semester.
 * Written once at promotion time and never updated.
 * Used for audit trail and history reports.
 */
@Entity
@Table(name = "student_semester_record",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "semester"}))
public class StudentSemesterRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnoreProperties({"semesterRecords"})
    private Student student;

    @Column(nullable = false)
    private Integer semester;

    // ── Attendance at time of promotion ──────────────────────────
    private Double attendancePercentage;

    @Enumerated(EnumType.STRING)
    private Student.EligibilityStatus eligibilityStatus;

    // ── Promotion metadata ────────────────────────────────────────
    private LocalDateTime promotedOn;       // timestamp of promotion
    private String        academicYear;     // e.g. "2025-2026"
    private Integer       promotedToSemester; // semester they moved into

    // ── Payment snapshot at time of promotion ─────────────────────
    private Double  amountPaid;             // fee paid for this semester
    private Double  lateFee;               // late fee if any
    private String  receiptNo;             // payment receipt number
    private String  transactionId;         // UPI / payment transaction ID
    private String  examCycle;             // e.g. "NOV/DEC 2025" or "APR/MAY 2026"
    private Integer subjectCount;          // number of subjects registered

    public StudentSemesterRecord() {}

    // ── Getters & Setters ─────────────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public Integer getSemester() { return semester; }
    public void setSemester(Integer semester) { this.semester = semester; }

    public Double getAttendancePercentage() { return attendancePercentage; }
    public void setAttendancePercentage(Double a) { this.attendancePercentage = a; }

    public Student.EligibilityStatus getEligibilityStatus() { return eligibilityStatus; }
    public void setEligibilityStatus(Student.EligibilityStatus e) { this.eligibilityStatus = e; }

    public LocalDateTime getPromotedOn() { return promotedOn; }
    public void setPromotedOn(LocalDateTime p) { this.promotedOn = p; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String a) { this.academicYear = a; }

    public Integer getPromotedToSemester() { return promotedToSemester; }
    public void setPromotedToSemester(Integer s) { this.promotedToSemester = s; }

    public Double getAmountPaid() { return amountPaid; }
    public void setAmountPaid(Double a) { this.amountPaid = a; }

    public Double getLateFee() { return lateFee; }
    public void setLateFee(Double l) { this.lateFee = l; }

    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String r) { this.receiptNo = r; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String t) { this.transactionId = t; }

    public String getExamCycle() { return examCycle; }
    public void setExamCycle(String e) { this.examCycle = e; }

    public Integer getSubjectCount() { return subjectCount; }
    public void setSubjectCount(Integer s) { this.subjectCount = s; }
}