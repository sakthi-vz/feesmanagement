package com.college.feesmanagement.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.college.feesmanagement.entity.ExamRegistration;
import com.college.feesmanagement.service.ExamRegistrationService;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/exam-registration")
public class ExamRegistrationController {

    private final ExamRegistrationService examRegistrationService;

    public ExamRegistrationController(ExamRegistrationService examRegistrationService) {
        this.examRegistrationService = examRegistrationService;
    }

    // Allocate subjects (HOD only)
    @PostMapping("/allocate")
    public ResponseEntity<Map<String, Object>> allocateSubjects(@RequestBody Map<String, Object> request) {
        Long deptId    = Long.valueOf(request.get("deptId").toString());
        Integer semester = Integer.valueOf(request.get("semester").toString());

        List<String> extraSubjectCodes = new java.util.ArrayList<>();
        if (request.containsKey("extraSubjectCodes")) {
            Object raw = request.get("extraSubjectCodes");
            if (raw instanceof List) {
                for (Object code : (List<?>) raw) extraSubjectCodes.add(code.toString());
            }
        }

        List<ExamRegistration> registrations = extraSubjectCodes.isEmpty()
            ? examRegistrationService.allocateSubjects(deptId, semester)
            : examRegistrationService.allocateSubjectsWithExtra(deptId, semester, extraSubjectCodes);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "message",          "Subject allocation successful",
            "totalAllocations", registrations.size(),
            "registrations",    registrations
        ));
    }

    // Add arrear subject (Student)
    @PostMapping("/arrear")
    public ResponseEntity<ExamRegistration> addArrearSubject(@RequestBody Map<String, Long> request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
            examRegistrationService.addArrearSubject(request.get("studentId"), request.get("subjectId"))
        );
    }

    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<ExamRegistration>> getStudentRegistrations(@PathVariable Long studentId) {
        return ResponseEntity.ok(examRegistrationService.getStudentRegistrations(studentId));
    }

    @GetMapping("/student/{studentId}/unpaid")
    public ResponseEntity<List<ExamRegistration>> getUnpaidRegistrations(@PathVariable Long studentId) {
        return ResponseEntity.ok(examRegistrationService.getUnpaidRegistrations(studentId));
    }

    @GetMapping("/student/{studentId}/total-fee")
    public ResponseEntity<Map<String, Object>> calculateTotalFee(@PathVariable Long studentId) {
        return ResponseEntity.ok(examRegistrationService.calculateFeeBreakdown(studentId));
    }

    @GetMapping("/all")
    public ResponseEntity<List<ExamRegistration>> getAllRegistrations() {
        return ResponseEntity.ok(examRegistrationService.getAllRegistrations());
    }

    /**
     * FIXED: requires ownerStudentId query param so the service can enforce ownership.
     * Students must pass their own studentId; admins/HODs pass null to bypass the check.
     */
    @DeleteMapping("/{regId}")
    public ResponseEntity<String> deleteRegistration(
            @PathVariable Long regId,
            @RequestParam(required = false) Long ownerStudentId) {
        examRegistrationService.deleteRegistration(regId, ownerStudentId);
        return ResponseEntity.ok("Registration deleted successfully");
    }
}