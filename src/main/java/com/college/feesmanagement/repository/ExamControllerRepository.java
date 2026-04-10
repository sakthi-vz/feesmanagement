package com.college.feesmanagement.repository;

import com.college.feesmanagement.entity.ExamControllerAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExamControllerRepository extends JpaRepository<ExamControllerAdmin, Long> {
    Optional<ExamControllerAdmin> findByEmployeeId(String employeeId);
    Optional<ExamControllerAdmin> findByUserEmail(String email);
    Optional<ExamControllerAdmin> findByUserId(Long userId);
}