package com.college.feesmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.college.feesmanagement.entity.ExamRegistration;
import com.college.feesmanagement.entity.Student;
import java.util.List;

public interface ExamRegistrationRepository extends JpaRepository<ExamRegistration, Long> {

    List<ExamRegistration> findByStudent(Student student);
    List<ExamRegistration> findByStudentStudentId(Long studentId);

    @Query("SELECT er FROM ExamRegistration er WHERE er.student.studentId = :studentId AND er.payment IS NULL")
    List<ExamRegistration> findUnpaidRegistrations(@Param("studentId") Long studentId);

    @Query("SELECT er FROM ExamRegistration er WHERE er.student.studentId = :studentId AND er.type = 'SEMESTER'")
    List<ExamRegistration> findSemesterRegistrations(@Param("studentId") Long studentId);

    @Query("SELECT er FROM ExamRegistration er WHERE er.student.studentId = :studentId AND er.type = 'ARREAR'")
    List<ExamRegistration> findArrearRegistrations(@Param("studentId") Long studentId);

    @Query("SELECT er FROM ExamRegistration er WHERE er.student.studentId = :studentId AND er.type = 'SEMESTER' AND er.subject.semester = :semester")
    List<ExamRegistration> findUnpaidSemesterRegistrations(@Param("studentId") Long studentId,
                                                            @Param("semester") Integer semester);

    /**
     * FIXED: only deletes rows where payment IS NULL — preserves paid registrations.
     * Previously deleted ALL semester rows regardless of payment status.
     */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM ExamRegistration er " +
           "WHERE er.student.studentId = :studentId " +
           "AND er.type = 'SEMESTER' " +
           "AND er.subject.semester = :semester " +
           "AND er.payment IS NULL")
    void deleteUnpaidSemesterRegistrations(@Param("studentId") Long studentId,
                                            @Param("semester") Integer semester);
}