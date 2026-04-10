package com.college.feesmanagement.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.college.feesmanagement.entity.Department;
import com.college.feesmanagement.service.DepartmentService;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/departments")
public class DepartmentController {
    
    private final DepartmentService departmentService;
    
    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }
    
    // Create department (Admin only)
    @PostMapping("/create")
    public ResponseEntity<Department> createDepartment(@Valid @RequestBody Department department) {
        return ResponseEntity.status(HttpStatus.CREATED).body(departmentService.createDepartment(department));
    }
    
    // Get all departments
    @GetMapping("/all")
    public ResponseEntity<List<Department>> getAllDepartments() {
        return ResponseEntity.ok(departmentService.getAllDepartments());
    }
    
    // Get department by ID
    @GetMapping("/{id}")
    public ResponseEntity<Department> getDepartmentById(@PathVariable Long id) {
        Department department = departmentService.getDepartmentById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
        return ResponseEntity.ok(department);
    }
    
    // Get department by name
    @GetMapping("/name/{name}")
    public ResponseEntity<Department> getDepartmentByName(@PathVariable String name) {
        Department department = departmentService.getDepartmentByName(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department not found"));
        return ResponseEntity.ok(department);
    }
    
    // Update department (Admin only)
    @PutMapping("/{id}")
    public ResponseEntity<Department> updateDepartment(@PathVariable Long id, @RequestBody Department department) {
        return ResponseEntity.ok(departmentService.updateDepartment(id, department));
    }
    
    // Delete department (Admin only)
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteDepartment(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.ok("Department deleted successfully");
    }

    // Toggle payment for a department (HOD only)
    @PutMapping("/{id}/payment-toggle")
    public ResponseEntity<Department> togglePayment(@PathVariable Long id, @RequestBody java.util.Map<String, Boolean> request) {
        Boolean enabled = request.get("enabled");
        Department dept = departmentService.togglePayment(id, enabled);
        return ResponseEntity.ok(dept);
    }

    // Check payment status for a department (Student uses this)
    @GetMapping("/{id}/payment-status")
    public ResponseEntity<java.util.Map<String, Boolean>> getPaymentStatus(@PathVariable Long id) {
        Department dept = departmentService.getDepartmentById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Department not found"));
        return ResponseEntity.ok(java.util.Map.of("paymentEnabled", dept.getPaymentEnabled()));
    }
}