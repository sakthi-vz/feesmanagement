package com.college.feesmanagement.controller;

import com.college.feesmanagement.entity.StudentSemesterStatus;
import com.college.feesmanagement.service.StudentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/semester-status")
public class StudentSemesterStatusController {

    private final StudentService studentService;

    public StudentSemesterStatusController(StudentService studentService) {
        this.studentService = studentService;
    }

    /** GET /semester-status/student/{id}
     *  Full 1-8 semester history for a student — used in student "My History" modal */
    @GetMapping("/student/{id}")
    public ResponseEntity<List<StudentSemesterStatus>> getStudentHistory(@PathVariable Long id) {
        return ResponseEntity.ok(studentService.getStudentStatusHistory(id));
    }

    /** GET /semester-status/student/{id}/semester/{sem}
     *  Single semester status — used to check if a student paid for a specific sem */
    @GetMapping("/student/{id}/semester/{sem}")
    public ResponseEntity<?> getStudentSemesterStatus(
            @PathVariable Long id, @PathVariable Integer sem) {
        return studentService.getStudentStatusForSemester(id, sem)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** GET /semester-status/dept/{deptId}/semester/{sem}
     *  All student statuses for a dept+semester — HOD view */
    @GetMapping("/dept/{deptId}/semester/{sem}")
    public ResponseEntity<List<StudentSemesterStatus>> getDeptSemesterStatuses(
            @PathVariable Long deptId, @PathVariable Integer sem) {
        return ResponseEntity.ok(studentService.getDeptSemesterStatuses(deptId, sem));
    }

    /** GET /semester-status/dept/{deptId}/paid-ids?semester=2
     *  Paid student IDs — single column lookup, no joins, replaces old endpoint */
    @GetMapping("/dept/{deptId}/paid-ids")
    public ResponseEntity<List<Long>> getPaidStudentIds(
            @PathVariable Long deptId,
            @RequestParam Integer semester) {
        return ResponseEntity.ok(studentService.getPaidStudentIds(deptId, semester));
    }

    /** GET /semester-status/summary
     *  Collection summary per semester — admin payment reports */
    @GetMapping("/summary")
    public ResponseEntity<List<Map<String, Object>>> getCollectionSummary() {
        // Returns list of {semester, collection, studentCount}
        List<Object[]> raw = studentService.getCollectionBySemester();
        List<Map<String, Object>> result = raw.stream()
                .map(row -> Map.of(
                        "semester",   row[0],
                        "collection", row[1],
                        "count",      row[2]
                ))
                .toList();
        return ResponseEntity.ok(result);
    }
}