package com.college.feesmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.college.feesmanagement.entity.Subject;
import com.college.feesmanagement.entity.Department;
import java.util.List;
import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findByDepartment(Department department);
    List<Subject> findByDepartmentAndSemester(Department department, Integer semester);
    Optional<Subject> findBySubjectCode(String subjectCode);

    @Query("SELECT s FROM Subject s WHERE s.department.deptId = :deptId AND s.semester = :semester")
    List<Subject> findByDeptIdAndSemester(@Param("deptId") Long deptId, @Param("semester") Integer semester);

    @Query("SELECT s FROM Subject s LEFT JOIN FETCH s.department")
    List<Subject> findAllWithDepartment();
}