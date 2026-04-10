package com.college.feesmanagement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import com.college.feesmanagement.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByRollNo(String rollNo);  // For student login with roll number
}