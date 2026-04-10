package com.college.feesmanagement.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

@Entity
@Table(name = "hods")
public class Hod {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long hodId;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Employee ID is required")
    @Column(unique = true)
    private String employeeId;

    @ManyToOne
    @JoinColumn(name = "dept_id", nullable = false)
    @JsonIgnoreProperties({"students", "subjects", "hods"})
    private Department department;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "photo_path")
    private String photoPath;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    @JsonIgnoreProperties({"student", "hod", "admin", "password"})
    private User user;

    public Hod() {}

    public Long getHodId() { return hodId; }
    public void setHodId(Long hodId) { this.hodId = hodId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}