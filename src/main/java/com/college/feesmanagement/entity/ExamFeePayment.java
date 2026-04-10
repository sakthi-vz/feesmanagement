package com.college.feesmanagement.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

@Entity
@Table(name = "exam_fee_payment")
public class ExamFeePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnoreProperties({"payments"})
    private Student student;

    private Double totalAmount;
    private Double lateFee;
    private LocalDateTime paymentDate;

    @Column(unique = true)
    private String receiptNo;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String transactionId;

    // Which semester this payment was made in — set at payment time
    // This is the ONLY accurate way to filter payments by semester
    // (student.currentSemester changes after promotion, making old queries inaccurate)
    @Column(name = "semester")
    private Integer semester;

    public enum PaymentStatus {
        PENDING, COMPLETED, FAILED
    }

    public ExamFeePayment() {}

    @PrePersist
    private void generateReceiptNo() {
        if (this.receiptNo == null) {
            this.receiptNo = "RCP" + System.currentTimeMillis();
        }
        if (this.paymentDate == null) {
            this.paymentDate = LocalDateTime.now();
        }
    }

    // Getters and Setters
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }

    public Student getStudent() { return student; }
    public void setStudent(Student student) { this.student = student; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public Double getLateFee() { return lateFee; }
    public void setLateFee(Double lateFee) { this.lateFee = lateFee; }

    public LocalDateTime getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDateTime paymentDate) { this.paymentDate = paymentDate; }

    public String getReceiptNo() { return receiptNo; }
    public void setReceiptNo(String receiptNo) { this.receiptNo = receiptNo; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public Integer getSemester() { return semester; }
    public void setSemester(Integer semester) { this.semester = semester; }
}