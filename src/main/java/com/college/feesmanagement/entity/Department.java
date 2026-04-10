package com.college.feesmanagement.entity;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "departments")
public class Department {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long deptId;
    
    @NotBlank(message = "Department name is required")
    @Column(unique = true)
    private String deptName;

    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean paymentEnabled = false;
    
    // Constructors
    public Department() {}
    
    public Department(String deptName) {
        this.deptName = deptName;
    }
    
    // Getters and Setters
    public Long getDeptId() {
        return deptId;
    }
    
    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }
    
    public String getDeptName() {
        return deptName;
    }
    
    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public Boolean getPaymentEnabled() {
        return paymentEnabled != null && paymentEnabled;
    }

    public void setPaymentEnabled(Boolean paymentEnabled) {
        this.paymentEnabled = paymentEnabled;
    }
}