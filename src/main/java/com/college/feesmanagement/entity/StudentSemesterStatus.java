package com.college.feesmanagement.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/**
 * Permanent ledger — one row per student per semester.
 *
 * Written in two places:
 *  1. At PAYMENT time   — paid=true, amountPaid, paidOn, receiptNo, transactionId,
 *                         examCycle, subjectCount, totalFee updated
 *  2. At PROMOTION time — attendancePercentage, eligibilityStatus, academicYear,
 *                         promotedOn, promotedToSemester stamped and LOCKED
 *
 * promotedOn != null means this semester is CLOSED — no re-promotion possible.
 *
 * This table is the single source of truth for:
 *  - Was student X paid for semester N?   → paid column, direct lookup
 *  - Was student X promoted from sem N?   → promotedOn != null
 *  - What did student X pay?              → amountPaid, receiptNo
 *  - Which exam cycle was this?           → examCycle (NOV/DEC or APR/MAY)
 *  - HOD semester-wise student history
 *  - Admin collection report per semester
 *  - Student "My History" view
 */
@Entity
@Table(name = "student_semester_status",
       uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "semester"}))
public class StudentSemesterStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnoreProperties({"user", "department", "examRegistrations"})
    private Student student;

    @Column(nullable = false)
    private Integer semester;                       // 1–8

    // ── Payment snapshot (written when fee is paid) ───────────────
    private Boolean       paid            = false;
    private Double        amountPaid      = 0.0;
    private Double        lateFee         = 0.0;
    private String        receiptNo;               // linked receipt number
    private String        transactionId;
    private LocalDateTime paidOn;
    private Integer       subjectCount    = 0;     // how many subjects paid for
    private Double        totalFee        = 0.0;   // total fee amount at payment time

    // ── Exam cycle (set at payment time) ──────────────────────────
    // e.g. "NOV/DEC 2025" or "APR/MAY 2026"
    // This ties the payment to a specific exam sitting
    @Column(name = "exam_cycle")
    private String examCycle;

    // ── Attendance + promotion snapshot (written at promotion) ────
    private Double        attendancePercentage;
    private String        eligibilityStatus;        // ELIGIBLE / NOT_ELIGIBLE
    private String        academicYear;             // e.g. "2025-2026"
    private LocalDateTime promotedOn;               // null = not yet promoted, non-null = LOCKED
    private Integer       promotedToSemester;       // the semester they moved INTO (currentSemester+1)

    // ── Arrear info (updated when arrear is paid) ─────────────────
    private Integer arrearCount      = 0;           // arrear subjects registered
    private Integer arrearPaidCount  = 0;           // arrear subjects paid
    private Double  arrearAmountPaid = 0.0;

    public StudentSemesterStatus() {}

    // ── Getters & Setters ─────────────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public Integer getSemester() { return semester; }
    public void setSemester(Integer semester) { this.semester = semester; }

    public Boolean getPaid() { return paid; }
    public void setPaid(Boolean paid) { this.paid = paid; }

    public Double getAmountPaid() { return amountPaid; }
    public void setAmountPaid(Double a) { this.amountPaid = a; }

    public Double getLateFee() { return lateFee; }
    public void setLateFee(Double l) { this.lateFee = l; }

    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String r) { this.receiptNo = r; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String t) { this.transactionId = t; }

    public LocalDateTime getPaidOn() { return paidOn; }
    public void setPaidOn(LocalDateTime p) { this.paidOn = p; }

    public Integer getSubjectCount() { return subjectCount; }
    public void setSubjectCount(Integer s) { this.subjectCount = s; }

    public Double getTotalFee() { return totalFee; }
    public void setTotalFee(Double f) { this.totalFee = f; }

    public String getExamCycle() { return examCycle; }
    public void setExamCycle(String e) { this.examCycle = e; }

    public Double getAttendancePercentage() { return attendancePercentage; }
    public void setAttendancePercentage(Double a) { this.attendancePercentage = a; }

    public String getEligibilityStatus() { return eligibilityStatus; }
    public void setEligibilityStatus(String e) { this.eligibilityStatus = e; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String y) { this.academicYear = y; }

    public LocalDateTime getPromotedOn() { return promotedOn; }
    public void setPromotedOn(LocalDateTime p) { this.promotedOn = p; }

    public Integer getPromotedToSemester() { return promotedToSemester; }
    public void setPromotedToSemester(Integer s) { this.promotedToSemester = s; }

    public Integer getArrearCount() { return arrearCount; }
    public void setArrearCount(Integer a) { this.arrearCount = a; }

    public Integer getArrearPaidCount() { return arrearPaidCount; }
    public void setArrearPaidCount(Integer a) { this.arrearPaidCount = a; }

    public Double getArrearAmountPaid() { return arrearAmountPaid; }
    public void setArrearAmountPaid(Double a) { this.arrearAmountPaid = a; }
}