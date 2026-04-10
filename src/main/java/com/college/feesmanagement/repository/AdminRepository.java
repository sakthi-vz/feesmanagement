package com.college.feesmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.college.feesmanagement.entity.Admin;
import com.college.feesmanagement.entity.User;
import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByEmployeeId(String employeeId);
    Optional<Admin> findByUser(User user);
}