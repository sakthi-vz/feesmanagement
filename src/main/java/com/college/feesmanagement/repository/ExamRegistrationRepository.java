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

    /**
     * FIXED: For SEMESTER type, returns rows matching the student's current semester
     * OR extra subjects added by HOD (subject.semester != currentSemester but type=SEMESTER).
     * ARREAR type rows are always included regardless of semester.
     */
    @Query("SELECT er FROM ExamRegistration er " +
           "WHERE er.student.studentId = :studentId " +
           "AND er.payment IS NULL " +
           "AND (" +
           "  er.type = 'SEMESTER' " +
           "  OR er.type = 'ARREAR'" +
           ")")
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

    /**
     * Cleans up stale unpaid SEMESTER rows for semesters OTHER than the student's current one.
     * Called after promotion so old unpaid allocations don't pollute fee calculations.
     */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM ExamRegistration er " +
           "WHERE er.student.studentId = :studentId " +
           "AND er.type = 'SEMESTER' " +
           "AND er.subject.semester <> :currentSemester " +
           "AND er.payment IS NULL")
    void deleteStaleUnpaidSemesterRegistrations(@Param("studentId") Long studentId,
                                                 @Param("currentSemester") Integer currentSemester);

    /**
     * Deletes ALL unpaid SEMESTER registrations for a student regardless of subject semester.
     * Called at promotion time to wipe the entire old allocation — including HOD-added
     * extra subjects from the completed semester that may belong to a future semester number.
     * The new HOD will re-allocate fresh subjects for the promoted semester.
     */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("DELETE FROM ExamRegistration er " +
           "WHERE er.student.studentId = :studentId " +
           "AND er.type = 'SEMESTER' " +
           "AND er.payment IS NULL")
    void deleteAllUnpaidSemesterRegistrations(@Param("studentId") Long studentId);
}