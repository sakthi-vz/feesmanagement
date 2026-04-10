package com.college.feesmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.college.feesmanagement.entity.Department;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByDeptName(String deptName);
}