package com.college.feesmanagement.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.college.feesmanagement.entity.Student;
import com.college.feesmanagement.entity.StudentSemesterRecord;
import com.college.feesmanagement.entity.SemesterPaymentSummary;
import com.college.feesmanagement.service.StudentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/students")
public class StudentController {

    private final StudentService studentService;

    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    @PostMapping("/create")
    public ResponseEntity<Student> createStudent(@Valid @RequestBody Student student) {
        return ResponseEntity.status(HttpStatus.CREATED).body(studentService.createStudent(student));
    }

    @GetMapping("/all")
    public ResponseEntity<List<Student>> getAllStudents() {
        return ResponseEntity.ok(studentService.getAllStudents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Student> getStudentById(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.getStudentById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found")));
    }

    @GetMapping("/rollno/{rollNo}")
    public ResponseEntity<Student> getStudentByRollNo(@PathVariable String rollNo) {
        return ResponseEntity.ok(studentService.getStudentByRollNo(rollNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found")));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Student> updateStudent(@PathVariable Long id, @RequestBody Student student) {
        return ResponseEntity.ok(studentService.updateStudent(id, student));
    }

    @PutMapping("/{id}/attendance")
    public ResponseEntity<Student> updateAttendance(@PathVariable Long id,
                                                     @RequestBody Map<String, Double> req) {
        Double attendance = req.get("attendancePercentage");
        if (attendance == null || attendance < 0 || attendance > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attendance percentage");
        return ResponseEntity.ok(studentService.updateAttendance(id, attendance));
    }

    @PostMapping("/promote")
    public ResponseEntity<Map<String, Object>> promoteSemester(
            @RequestBody Map<String, Object> request) {
        try {
            Long    deptId     = Long.valueOf(request.get("deptId").toString());
            Integer currentSem = Integer.valueOf(request.get("currentSemester").toString());

            StudentService.PromotionResult r = studentService.promoteSemester(deptId, currentSem);

            String msg = r.fromSemester() >= 8
                ? r.graduated() + " students graduated (Sem 8 completed). Data archived."
                : r.promoted() + " students promoted Sem " + r.fromSemester() +
                  " → " + r.toSemester() +
                  (r.graduated() > 0 ? ", " + r.graduated() + " graduated." : ".");

            return ResponseEntity.ok(Map.of(
                    "message",              msg,
                    "studentsPromoted",     r.promoted(),
                    "studentsGraduated",    r.graduated(),
                    "fromSemester",         r.fromSemester(),
                    "toSemester",           r.toSemester(),
                    "collectionArchived",   r.collectionArchived(),
                    "paidStudents",         r.paidStudents(),
                    "unpaidStudents",       r.unpaidStudents()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/semester-history")
    public ResponseEntity<List<StudentSemesterRecord>> getSemesterHistory(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.getSemesterHistory(id));
    }

    @GetMapping("/dept/{deptId}/history/{semester}")
    public ResponseEntity<List<StudentSemesterRecord>> getDeptSemesterHistory(
            @PathVariable Long deptId, @PathVariable Integer semester) {
        return ResponseEntity.ok(studentService.getDeptSemesterHistory(deptId, semester));
    }

    // ── Semester Payment Summary endpoints ────────────────────────

    @GetMapping("/payment-summaries")
    public ResponseEntity<List<SemesterPaymentSummary>> getAllPaymentSummaries() {
        return ResponseEntity.ok(studentService.getAllPaymentSummaries());
    }

    @GetMapping("/dept/{deptId}/payment-summaries")
    public ResponseEntity<List<SemesterPaymentSummary>> getPaymentSummariesByDept(
            @PathVariable Long deptId) {
        return ResponseEntity.ok(studentService.getPaymentSummariesByDept(deptId));
    }

    @GetMapping("/payment-summaries/semester/{semester}")
    public ResponseEntity<List<SemesterPaymentSummary>> getPaymentSummariesBySemester(
            @PathVariable Integer semester) {
        return ResponseEntity.ok(studentService.getPaymentSummariesBySemester(semester));
    }

    // ── Standard dept/semester queries ────────────────────────────

    @GetMapping("/dept/{deptId}")
    public ResponseEntity<List<Student>> getStudentsByDept(@PathVariable Long deptId) {
        return ResponseEntity.ok(studentService.getStudentsByDept(deptId));
    }

    @GetMapping("/dept/{deptId}/semester/{semester}")
    public ResponseEntity<List<Student>> getStudentsByDeptAndSemester(
            @PathVariable Long deptId, @PathVariable Integer semester) {
        return ResponseEntity.ok(studentService.getStudentsByDeptAndSemester(deptId, semester));
    }

    @GetMapping("/dept/{deptId}/semester/{semester}/eligible")
    public ResponseEntity<List<Student>> getEligibleStudents(
            @PathVariable Long deptId, @PathVariable Integer semester) {
        return ResponseEntity.ok(studentService.getEligibleStudents(deptId, semester));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteStudent(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return ResponseEntity.ok("Student deleted successfully");
    }
}