package com.college.feesmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.college.feesmanagement.entity.Student;
import com.college.feesmanagement.entity.Department;
import com.college.feesmanagement.entity.User;
import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByRollNo(String rollNo);

    Optional<Student> findByUser(User user);

    List<Student> findByDepartment(Department department);

    // HOD view — excludes COMPLETED (graduated) students, by semester
    @Query("SELECT s FROM Student s " +
           "WHERE s.department = :dept " +
           "AND s.currentSemester = :semester " +
           "AND (s.programmeStatus IS NULL OR s.programmeStatus = 'ACTIVE')")
    List<Student> findByDepartmentAndCurrentSemester(
            @Param("dept") Department dept,
            @Param("semester") Integer semester);

    // Eligible for promotion/allocation — excludes COMPLETED
    @Query("SELECT s FROM Student s " +
           "WHERE s.department.deptId = :deptId " +
           "AND s.currentSemester = :semester " +
           "AND s.eligibilityStatus = 'ELIGIBLE' " +
           "AND (s.programmeStatus IS NULL OR s.programmeStatus = 'ACTIVE')")
    List<Student> findEligibleStudents(
            @Param("deptId") Long deptId,
            @Param("semester") Integer semester);

    List<Student> findByEligibilityStatus(Student.EligibilityStatus status);

    // All ACTIVE students in a department — excludes COMPLETED
    @Query("SELECT s FROM Student s " +
           "WHERE s.department.deptId = :deptId " +
           "AND (s.programmeStatus IS NULL OR s.programmeStatus = 'ACTIVE')")
    List<Student> findByDepartmentId(@Param("deptId") Long deptId);
}