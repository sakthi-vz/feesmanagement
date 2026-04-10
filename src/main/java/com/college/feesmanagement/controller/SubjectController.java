package com.college.feesmanagement.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.college.feesmanagement.entity.Subject;
import com.college.feesmanagement.service.SubjectService;
import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/subjects")
public class SubjectController {
    
    private final SubjectService subjectService;
    
    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }
    
    // Create subject (Admin only)
    @PostMapping("/create")
    public ResponseEntity<Subject> createSubject(@Valid @RequestBody Subject subject) {
        return ResponseEntity.status(HttpStatus.CREATED).body(subjectService.createSubject(subject));
    }
    
    // Get all subjects
    @GetMapping("/all")
    public ResponseEntity<List<Subject>> getAllSubjects() {
        return ResponseEntity.ok(subjectService.getAllSubjects());
    }
    
    // Get subject by ID
    @GetMapping("/{id}")
    public ResponseEntity<Subject> getSubjectById(@PathVariable Long id) {
        Subject subject = subjectService.getSubjectById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subject not found"));
        return ResponseEntity.ok(subject);
    }
    
    // Get subjects by department and semester
    @GetMapping("/dept/{deptId}/semester/{semester}")
    public ResponseEntity<List<Subject>> getSubjectsByDeptAndSemester(
            @PathVariable Long deptId, @PathVariable Integer semester) {
        return ResponseEntity.ok(subjectService.getSubjectsByDeptAndSemester(deptId, semester));
    }
    
    // Update subject (Admin only)
    @PutMapping("/{id}")
    public ResponseEntity<Subject> updateSubject(@PathVariable Long id, @RequestBody Subject subject) {
        return ResponseEntity.ok(subjectService.updateSubject(id, subject));
    }
    
    // Delete subject (Admin only)
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteSubject(@PathVariable Long id) {
        subjectService.deleteSubject(id);
        return ResponseEntity.ok("Subject deleted successfully");
    }
}