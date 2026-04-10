package com.college.feesmanagement.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.college.feesmanagement.entity.ExamFeePayment;
import com.college.feesmanagement.service.PaymentService;
import com.college.feesmanagement.service.ReceiptService;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final ReceiptService receiptService;

    public PaymentController(PaymentService paymentService, ReceiptService receiptService) {
        this.paymentService = paymentService;
        this.receiptService = receiptService;
    }
    
    // Process payment (Student)
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processPayment(@RequestBody Map<String, Object> request) {
        Long studentId = Long.valueOf(request.get("studentId").toString());
        String transactionId = request.containsKey("transactionId") ? 
                request.get("transactionId").toString() : null;
        
        ExamFeePayment payment = paymentService.processPayment(studentId, transactionId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Payment successful",
                "receiptNo", payment.getReceiptNo(),
                "totalAmount", payment.getTotalAmount(),
                "paymentDate", payment.getPaymentDate(),
                "payment", payment
        ));
    }
    
    // Get payment by receipt number
    @GetMapping("/receipt/{receiptNo}")
    public ResponseEntity<ExamFeePayment> getPaymentByReceiptNo(@PathVariable String receiptNo) {
        ExamFeePayment payment = paymentService.getPaymentByReceiptNo(receiptNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found"));
        return ResponseEntity.ok(payment);
    }
    
    // Get student payments
    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<ExamFeePayment>> getStudentPayments(@PathVariable Long studentId) {
        return ResponseEntity.ok(paymentService.getStudentPayments(studentId));
    }
    
    // Get all payments (Admin)
    @GetMapping("/all")
    public ResponseEntity<List<ExamFeePayment>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }
    
    // Get payments by department (Admin dashboard)
    @GetMapping("/department/{deptId}")
    public ResponseEntity<List<ExamFeePayment>> getPaymentsByDepartment(@PathVariable Long deptId) {
        return ResponseEntity.ok(paymentService.getPaymentsByDepartment(deptId));
    }
    
    // Get department-wise collection summary (Admin dashboard)
    @GetMapping("/summary/department-wise")
    public ResponseEntity<Map<String, Double>> getDepartmentWiseCollection() {
        return ResponseEntity.ok(paymentService.getDepartmentWiseCollection());
    }
    
    // Get payment statistics (Admin dashboard)
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getPaymentStatistics() {
        return ResponseEntity.ok(paymentService.getPaymentStatistics());
    }

    /**
     * Download PDF receipt — streams the PDF directly to browser.
     * Called by the ⬇ PDF button in My Receipts modal.
     */
    @GetMapping("/receipt/download/{receiptNo}")
    public ResponseEntity<byte[]> downloadReceipt(@PathVariable String receiptNo) {
        try {
            byte[] pdf = receiptService.generateReceipt(receiptNo);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", receiptNo + ".pdf");
            headers.setContentLength(pdf.length);
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Returns studentIds who have paid SEMESTER fees for a specific semester.
     * Used by HOD dashboard fee status — backend join ensures semester-accurate result.
     */
    @GetMapping("/dept/{deptId}/paid-student-ids")
    public ResponseEntity<List<Long>> getPaidStudentIds(
            @PathVariable Long deptId,
            @RequestParam Integer semester) {
        return ResponseEntity.ok(paymentService.getPaidStudentIdsForSemester(deptId, semester));
    }
}