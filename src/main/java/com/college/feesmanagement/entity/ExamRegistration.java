package com.college.feesmanagement.entity;


import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "exam_registration")
public class ExamRegistration {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long regId;
    
    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnoreProperties({"examRegistrations"})
    private Student student;
    
    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    @JsonIgnoreProperties({"examRegistrations"})
    private Subject subject;
    
    @Enumerated(EnumType.STRING)
    private RegistrationType type;
    
    @Enumerated(EnumType.STRING)
    private Student.EligibilityStatus eligibilityStatus;
    
    @ManyToOne
    @JoinColumn(name = "payment_id")
    @JsonIgnoreProperties({"registrations"})
    private ExamFeePayment payment;
    
    public enum RegistrationType {
        SEMESTER, ARREAR
    }
    
    // Constructors
    public ExamRegistration() {}
    
    // Getters and Setters
    public Long getRegId() {
        return regId;
    }
    
    public void setRegId(Long regId) {
        this.regId = regId;
    }
    
    public Student getStudent() {
        return student;
    }
    
    public void setStudent(Student student) {
        this.student = student;
    }
    
    public Subject getSubject() {
        return subject;
    }
    
    public void setSubject(Subject subject) {
        this.subject = subject;
    }
    
    public RegistrationType getType() {
        return type;
    }
    
    public void setType(RegistrationType type) {
        this.type = type;
    }
    
    public Student.EligibilityStatus getEligibilityStatus() {
        return eligibilityStatus;
    }
    
    public void setEligibilityStatus(Student.EligibilityStatus eligibilityStatus) {
        this.eligibilityStatus = eligibilityStatus;
    }
    
    public ExamFeePayment getPayment() {
        return payment;
    }
    
    public void setPayment(ExamFeePayment payment) {
        this.payment = payment;
    }
}