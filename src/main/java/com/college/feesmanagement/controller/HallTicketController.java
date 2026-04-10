package com.college.feesmanagement.controller;

import com.college.feesmanagement.entity.Student;
import com.college.feesmanagement.repository.StudentRepository;
import com.college.feesmanagement.service.HallTicketService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Hall Ticket Controller — ADMIN only.
 * Only the Exam Controller / Admin role can generate hall tickets.
 * Students cannot access these endpoints directly.
 */
@RestController
@RequestMapping("/hall-ticket")
public class HallTicketController {

    private final HallTicketService hallTicketService;
    private final StudentRepository studentRepository;

    public HallTicketController(HallTicketService hallTicketService,
                                 StudentRepository studentRepository) {
        this.hallTicketService = hallTicketService;
        this.studentRepository = studentRepository;
    }

    /**
     * GET /hall-ticket/check/{studentId}
     * Admin checks if a student is eligible before generating.
     * Returns eligibility status, attendance, fee paid status.
     */
    @GetMapping("/check/{studentId}")
    public ResponseEntity<?> checkEligibility(@PathVariable Long studentId) {
        try {
            hallTicketService.checkEligibility(studentId);
            Student student = studentRepository.findById(studentId).orElseThrow();
            return ResponseEntity.ok(Map.of(
                "eligible",   true,
                "message",    "Student is eligible for hall ticket.",
                "name",       student.getName(),
                "rollNo",     student.getRollNo(),
                "semester",   student.getCurrentSemester() != null ? student.getCurrentSemester() : 1,
                "attendance", student.getAttendancePercentage() != null ? student.getAttendancePercentage() : 0.0,
                "department", student.getDepartment() != null ? student.getDepartment().getDeptName() : "—"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "eligible", false,
                "message",  e.getMessage()
            ));
        }
    }

    /**
     * GET /hall-ticket/download/{studentId}
     * Admin generates and downloads the hall ticket PDF for a student.
     * Restricted to ADMIN role via SecurityConfig.
     */
    @GetMapping("/download/{studentId}")
    public ResponseEntity<byte[]> downloadHallTicket(@PathVariable Long studentId) {
        try {
            byte[] pdf      = hallTicketService.generateHallTicket(studentId);
            Student student = studentRepository.findById(studentId).orElseThrow();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=HallTicket-" + student.getRollNo() + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .header("X-Error-Message", e.getMessage())
                    .build();
        }
    }
}