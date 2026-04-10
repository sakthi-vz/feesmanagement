package com.college.feesmanagement.service;

import org.springframework.stereotype.Service;
import com.college.feesmanagement.entity.Subject;
import com.college.feesmanagement.entity.Department;
import com.college.feesmanagement.repository.SubjectRepository;
import com.college.feesmanagement.repository.DepartmentRepository;
import java.util.List;
import java.util.Optional;

@Service
public class SubjectService {
    
    private final SubjectRepository subjectRepository;
    private final DepartmentRepository departmentRepository;
    
    public SubjectService(SubjectRepository subjectRepository, DepartmentRepository departmentRepository) {
        this.subjectRepository = subjectRepository;
        this.departmentRepository = departmentRepository;
    }
    
    // Create subject
    public Subject createSubject(Subject subject) {
        return subjectRepository.save(subject);
    }
    
    // Get all subjects
    public List<Subject> getAllSubjects() {
        return subjectRepository.findAll();
    }
    
    // Get subject by ID
    public Optional<Subject> getSubjectById(Long id) {
        return subjectRepository.findById(id);
    }
    
    // Get subjects by department and semester
    public List<Subject> getSubjectsByDeptAndSemester(Long deptId, Integer semester) {
        Department department = departmentRepository.findById(deptId)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        return subjectRepository.findByDepartmentAndSemester(department, semester);
    }
    
    // Update subject
    public Subject updateSubject(Long id, Subject updatedSubject) {
        Subject existing = subjectRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subject not found"));
        
        existing.setName(updatedSubject.getName());
        existing.setDepartment(updatedSubject.getDepartment());
        existing.setSemester(updatedSubject.getSemester());
        existing.setType(updatedSubject.getType());
        
        if (updatedSubject.getFee() != null) {
            existing.setFee(updatedSubject.getFee());
        }
        
        return subjectRepository.save(existing);
    }
    
    // Delete subject
    public void deleteSubject(Long id) {
        subjectRepository.deleteById(id);
    }
}