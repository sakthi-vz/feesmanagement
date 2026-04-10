package com.college.feesmanagement.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Snapshot of payment collection for a department-semester,
 * saved automatically when admin promotes that semester.
 */
@Entity
@Table(name = "semester_payment_summary",
       uniqueConstraints = @UniqueConstraint(columnNames = {"dept_id", "semester", "academic_year"}))
public class SemesterPaymentSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "dept_id", nullable = false)
    private Department department;

    @Column(nullable = false)
    private Integer semester;

    @Column(nullable = false)
    private String academicYear;        // e.g. "2025-2026"

    // Exam cycle — ties this summary to a specific exam sitting
    // e.g. "NOV/DEC 2025" or "APR/MAY 2026"
    @Column(name = "exam_cycle")
    private String examCycle;

    private Integer totalStudents;      // students in this dept+semester
    private Integer promotedStudents;   // students actually promoted
    private Integer notPromotedStudents;// students who were eligible but not promoted (unpaid)
    private Integer eligibleStudents;   // students who met attendance criteria
    private Integer paidStudents;       // students who paid fees
    private Integer unpaidStudents;     // students who did NOT pay
    private Double  totalCollection;    // total fees collected (Rs.)
    private Double  lateFeeCollection;  // late fee portion

    private LocalDateTime snapshotTakenOn;  // when this record was created

    public SemesterPaymentSummary() {}

    // ── Getters & Setters ─────────────────────────────────────────
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }

    public Integer getSemester() { return semester; }
    public void setSemester(Integer semester) { this.semester = semester; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

    public String getExamCycle() { return examCycle; }
    public void setExamCycle(String examCycle) { this.examCycle = examCycle; }

    public Integer getTotalStudents() { return totalStudents; }
    public void setTotalStudents(Integer totalStudents) { this.totalStudents = totalStudents; }

    public Integer getPromotedStudents() { return promotedStudents; }
    public void setPromotedStudents(Integer promotedStudents) { this.promotedStudents = promotedStudents; }

    public Integer getNotPromotedStudents() { return notPromotedStudents; }
    public void setNotPromotedStudents(Integer n) { this.notPromotedStudents = n; }

    public Integer getEligibleStudents() { return eligibleStudents; }
    public void setEligibleStudents(Integer eligibleStudents) { this.eligibleStudents = eligibleStudents; }

    public Integer getPaidStudents() { return paidStudents; }
    public void setPaidStudents(Integer paidStudents) { this.paidStudents = paidStudents; }

    public Integer getUnpaidStudents() { return unpaidStudents; }
    public void setUnpaidStudents(Integer unpaidStudents) { this.unpaidStudents = unpaidStudents; }

    public Double getTotalCollection() { return totalCollection; }
    public void setTotalCollection(Double totalCollection) { this.totalCollection = totalCollection; }

    public Double getLateFeeCollection() { return lateFeeCollection; }
    public void setLateFeeCollection(Double lateFeeCollection) { this.lateFeeCollection = lateFeeCollection; }

    public LocalDateTime getSnapshotTakenOn() { return snapshotTakenOn; }
    public void setSnapshotTakenOn(LocalDateTime snapshotTakenOn) { this.snapshotTakenOn = snapshotTakenOn; }
}