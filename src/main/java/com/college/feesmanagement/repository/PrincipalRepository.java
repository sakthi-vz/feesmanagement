package com.college.feesmanagement.repository;

import com.college.feesmanagement.entity.Principal;
import com.college.feesmanagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PrincipalRepository extends JpaRepository<Principal, Long> {
    Optional<Principal> findByEmployeeId(String employeeId);
    Optional<Principal> findByUser(User user);
    Optional<Principal> findByUserId(Long userId);
}