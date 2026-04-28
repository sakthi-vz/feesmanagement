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

    private String middleName;

    private String lastName;

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


    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group", nullable = false)
    private BloodGroup bloodGroup;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    @JsonIgnoreProperties({"student", "hod", "admin", "password"})
    private User user;


    public enum Gender { MALE, FEMALE, OTHER }
    public enum BloodGroup { A_POS, A_NEG, B_POS, B_NEG, AB_POS, AB_NEG, O_POS, O_NEG }
    public Hod() {}

    public Long getHodId() { return hodId; }
    public void setHodId(Long hodId) { this.hodId = hodId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department department) { this.department = department; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }
    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    public BloodGroup getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(BloodGroup bloodGroup) { this.bloodGroup = bloodGroup; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}