package com.college.feesmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.college.feesmanagement.entity.ExamFeePayment;
import com.college.feesmanagement.entity.Student;
import java.util.List;
import java.util.Optional;

public interface ExamFeePaymentRepository extends JpaRepository<ExamFeePayment, Long> {

    List<ExamFeePayment> findByStudent(Student student);
    List<ExamFeePayment> findByStudentStudentId(Long studentId);
    Optional<ExamFeePayment> findByReceiptNo(String receiptNo);

    @Query("SELECT p FROM ExamFeePayment p WHERE p.student.department.deptId = :deptId")
    List<ExamFeePayment> findByDepartmentId(@Param("deptId") Long deptId);

    @Query("SELECT p FROM ExamFeePayment p WHERE p.status = 'COMPLETED'")
    List<ExamFeePayment> findCompletedPayments();

    @Query("SELECT p FROM ExamFeePayment p WHERE p.status = 'PENDING'")
    List<ExamFeePayment> findPendingPayments();

    @Query("SELECT p FROM ExamFeePayment p WHERE p.status = 'COMPLETED' AND p.semester = :semester")
    List<ExamFeePayment> findCompletedPaymentsBySemester(@Param("semester") Integer semester);

    /**
     * Payments for a dept+semester using the semester TAG stamped at payment time.
     * Named with consistent suffix — used by StudentService Step 1 (promotion snapshot).
     */
    @Query("SELECT p FROM ExamFeePayment p " +
           "WHERE p.student.department.deptId = :deptId " +
           "AND p.status = 'COMPLETED' " +
           "AND p.semester = :semester")
    List<ExamFeePayment> findCompletedPaymentsByDeptAndSemester(
            @Param("deptId") Long deptId,
            @Param("semester") Integer semester);

    // Alias kept for any existing callers
    @Query("SELECT p FROM ExamFeePayment p " +
           "WHERE p.student.department.deptId = :deptId " +
           "AND p.status = 'COMPLETED' " +
           "AND p.semester = :semester")
    List<ExamFeePayment> findCompletedPaymentsByDeptAndSemesterTag(
            @Param("deptId") Long deptId,
            @Param("semester") Integer semester);

    /**
     * Paid student IDs — uses p.semester tag directly.
     * Mohamed paid Sem 2 → p.semester=2 forever.
     * Query for Sem 3 → correctly returns nothing for him.
     */
    @Query("SELECT DISTINCT p.student.studentId FROM ExamFeePayment p " +
           "WHERE p.student.department.deptId = :deptId " +
           "AND p.status = 'COMPLETED' " +
           "AND p.semester = :semester")
    List<Long> findPaidStudentIdsForSemester(
            @Param("deptId") Long deptId,
            @Param("semester") Integer semester);
}