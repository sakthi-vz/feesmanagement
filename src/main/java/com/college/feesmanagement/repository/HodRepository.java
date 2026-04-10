package com.college.feesmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.college.feesmanagement.entity.Hod;
import com.college.feesmanagement.entity.Department;
import com.college.feesmanagement.entity.User;
import java.util.List;
import java.util.Optional;

public interface HodRepository extends JpaRepository<Hod, Long> {
    Optional<Hod> findByEmployeeId(String employeeId);
    Optional<Hod> findByUser(User user);
    Optional<Hod> findByDepartment(Department department);
    List<Hod> findAllByDepartment(Department department);
}