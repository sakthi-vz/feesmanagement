package com.college.feesmanagement.service;

import org.springframework.stereotype.Service;
import com.college.feesmanagement.entity.Department;
import com.college.feesmanagement.repository.DepartmentRepository;
import java.util.List;
import java.util.Optional;

@Service
public class DepartmentService {
    
    private final DepartmentRepository departmentRepository;
    
    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }
    
    // Create department
    public Department createDepartment(Department department) {
        return departmentRepository.save(department);
    }
    
    // Get all departments
    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }
    
    // Get department by ID
    public Optional<Department> getDepartmentById(Long id) {
        return departmentRepository.findById(id);
    }
    
    // Get department by name
    public Optional<Department> getDepartmentByName(String name) {
        return departmentRepository.findByDeptName(name);
    }
    
    // Update department
    public Department updateDepartment(Long id, Department updatedDepartment) {
        Department existing = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        
        existing.setDeptName(updatedDepartment.getDeptName());
        return departmentRepository.save(existing);
    }
    
    // Delete department
    public void deleteDepartment(Long id) {
        departmentRepository.deleteById(id);
    }

    // Toggle payment enabled for department
    public Department togglePayment(Long id, Boolean enabled) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        dept.setPaymentEnabled(enabled);
        return departmentRepository.save(dept);
    }
}