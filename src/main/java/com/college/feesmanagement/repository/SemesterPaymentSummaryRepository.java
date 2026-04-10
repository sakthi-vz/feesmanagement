package com.college.feesmanagement.repository;

import com.college.feesmanagement.entity.SemesterPaymentSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SemesterPaymentSummaryRepository
        extends JpaRepository<SemesterPaymentSummary, Long> {

    // All summaries for one department, ordered by semester
    List<SemesterPaymentSummary> findByDepartmentDeptIdOrderBySemesterAsc(Long deptId);

    // All summaries for a specific semester across all departments
    List<SemesterPaymentSummary> findBySemesterOrderByDepartmentDeptNameAsc(Integer semester);

    // Specific dept + semester record
    Optional<SemesterPaymentSummary> findByDepartmentDeptIdAndSemesterAndAcademicYear(
            Long deptId, Integer semester, String academicYear);

    // All summaries ordered by dept then semester — for full history view
    @Query("SELECT s FROM SemesterPaymentSummary s ORDER BY s.department.deptName, s.semester")
    List<SemesterPaymentSummary> findAllOrderedByDeptAndSemester();

    // Total collection across all archived semesters
    @Query("SELECT COALESCE(SUM(s.totalCollection), 0) FROM SemesterPaymentSummary s")
    Double getTotalArchivedCollection();
}