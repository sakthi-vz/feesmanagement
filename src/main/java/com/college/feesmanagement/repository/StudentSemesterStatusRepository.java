package com.college.feesmanagement.repository;

import com.college.feesmanagement.entity.StudentSemesterStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentSemesterStatusRepository extends JpaRepository<StudentSemesterStatus, Long> {

    // ── Single student lookups ────────────────────────────────

    /** All semester rows for one student — ordered Sem 1 → 8 */
    List<StudentSemesterStatus> findByStudentStudentIdOrderBySemesterAsc(Long studentId);

    /** Specific semester row for a student */
    Optional<StudentSemesterStatus> findByStudentStudentIdAndSemester(Long studentId, Integer semester);

    // ── Department lookups ────────────────────────────────────

    /** All rows for a dept + semester — used by HOD / Admin */
    @Query("SELECT s FROM StudentSemesterStatus s " +
           "WHERE s.student.department.deptId = :deptId " +
           "AND s.semester = :semester")
    List<StudentSemesterStatus> findByDeptAndSemester(
            @Param("deptId") Long deptId,
            @Param("semester") Integer semester);

    /** Paid student IDs for a dept + semester — single column lookup, no joins */
    @Query("SELECT s.student.studentId FROM StudentSemesterStatus s " +
           "WHERE s.student.department.deptId = :deptId " +
           "AND s.semester = :semester " +
           "AND s.paid = true")
    List<Long> findPaidStudentIds(
            @Param("deptId") Long deptId,
            @Param("semester") Integer semester);

    /** Total collection for a dept + semester */
    @Query("SELECT COALESCE(SUM(s.amountPaid), 0) FROM StudentSemesterStatus s " +
           "WHERE s.student.department.deptId = :deptId " +
           "AND s.semester = :semester")
    Double getTotalCollectionByDeptAndSemester(
            @Param("deptId") Long deptId,
            @Param("semester") Integer semester);

    /** All rows for a dept — full history */
    @Query("SELECT s FROM StudentSemesterStatus s " +
           "WHERE s.student.department.deptId = :deptId " +
           "ORDER BY s.semester ASC")
    List<StudentSemesterStatus> findByDeptId(@Param("deptId") Long deptId);

    // ── Admin / global lookups ────────────────────────────────

    /** All rows for a semester across all depts */
    List<StudentSemesterStatus> findBySemester(Integer semester);

    /** Grand total collection across all semesters */
    @Query("SELECT COALESCE(SUM(s.amountPaid), 0) FROM StudentSemesterStatus s WHERE s.paid = true")
    Double getGrandTotalCollection();

    /** Collection per semester — for admin payment reports */
    @Query("SELECT s.semester, COALESCE(SUM(s.amountPaid), 0), COUNT(s) " +
           "FROM StudentSemesterStatus s " +
           "WHERE s.paid = true " +
           "GROUP BY s.semester ORDER BY s.semester")
    List<Object[]> getCollectionBySemester();
}