package com.college.feesmanagement.repository;

import com.college.feesmanagement.entity.ExamSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ExamScheduleRepository extends JpaRepository<ExamSchedule, Long> {

    // Find schedule entry for a specific subject in the current cycle
    Optional<ExamSchedule> findBySubjectSubjectIdAndExamCycle(Long subjectId, String examCycle);

    // Get all schedule entries for a given cycle
    List<ExamSchedule> findByExamCycle(String examCycle);

    // Get all scheduled (date+session set) entries for a cycle
    @Query("SELECT e FROM ExamSchedule e WHERE e.examCycle = :cycle AND e.examDate IS NOT NULL AND e.session IS NOT NULL")
    List<ExamSchedule> findScheduledByExamCycle(@Param("cycle") String cycle);

    // Get schedule for a specific dept in a cycle
    @Query("SELECT e FROM ExamSchedule e WHERE e.examCycle = :cycle AND e.subject.department.deptId = :deptId")
    List<ExamSchedule> findByExamCycleAndDept(@Param("cycle") String cycle, @Param("deptId") Long deptId);

    // Get schedule for a specific dept + semester in a cycle
    @Query("SELECT e FROM ExamSchedule e WHERE e.examCycle = :cycle AND e.subject.department.deptId = :deptId AND e.subject.semester = :semester")
    List<ExamSchedule> findByExamCycleDeptAndSemester(@Param("cycle") String cycle,
                                                       @Param("deptId") Long deptId,
                                                       @Param("semester") Integer semester);

    // Check if subject is scheduled in current cycle
    @Query("SELECT COUNT(e) > 0 FROM ExamSchedule e WHERE e.subject.subjectId = :subjectId AND e.examCycle = :cycle AND e.examDate IS NOT NULL AND e.session IS NOT NULL")
    boolean isSubjectScheduled(@Param("subjectId") Long subjectId, @Param("cycle") String cycle);

    // ── Reset operations ─────────────────────────────────────────

    // Delete ALL entries for a given exam cycle (semester reset)
    @Modifying
    @Transactional
    @Query("DELETE FROM ExamSchedule e WHERE e.examCycle = :cycle")
    int deleteByExamCycle(@Param("cycle") String cycle);

    // Delete entries for a specific dept in a cycle
    @Modifying
    @Transactional
    @Query("DELETE FROM ExamSchedule e WHERE e.examCycle = :cycle AND e.subject.department.deptId = :deptId")
    int deleteByExamCycleAndDept(@Param("cycle") String cycle, @Param("deptId") Long deptId);

    // Count scheduled vs total for a cycle
    @Query("SELECT COUNT(e) FROM ExamSchedule e WHERE e.examCycle = :cycle")
    long countByExamCycle(@Param("cycle") String cycle);

    @Query("SELECT COUNT(e) FROM ExamSchedule e WHERE e.examCycle = :cycle AND e.examDate IS NOT NULL AND e.session IS NOT NULL")
    long countScheduledByExamCycle(@Param("cycle") String cycle);
}