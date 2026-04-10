package com.college.feesmanagement.repository;

import com.college.feesmanagement.entity.StudentSemesterRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StudentSemesterRecordRepository
        extends JpaRepository<StudentSemesterRecord, Long> {

    // Full history for a student ordered by semester
    List<StudentSemesterRecord> findByStudentStudentIdOrderBySemesterAsc(Long studentId);

    // Specific semester record for a student
    Optional<StudentSemesterRecord> findByStudentStudentIdAndSemester(
            Long studentId, Integer semester);

    // All records for a department in a given semester
    @Query("SELECT r FROM StudentSemesterRecord r " +
           "WHERE r.student.department.deptId = :deptId " +
           "AND r.semester = :semester " +
           "ORDER BY r.student.rollNo ASC")
    List<StudentSemesterRecord> findByDeptAndSemester(
            @Param("deptId") Long deptId,
            @Param("semester") Integer semester);
}