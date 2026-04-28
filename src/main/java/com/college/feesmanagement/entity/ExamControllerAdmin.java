package com.college.feesmanagement.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

/**
 * Exam Controller — a special admin role that:
 *   - Sets exam dates and sessions on subjects
 *   - Generates hall tickets for students
 *   - Their signature image appears on the hall ticket PDF
 */
@Entity
@Table(name = "exam_controllers")
public class ExamControllerAdmin {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long controllerId;

    @Column(nullable = false)
    private String name;

    private String middleName;

    private String lastName;

    @Column(name = "employee_id", unique = true, nullable = false)
    private String employeeId;

    private String designation;    // e.g. "Controller of Examinations"

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "photo_path")
    private String photoPath;

    /** Path to the scanned/uploaded signature image — embedded in hall ticket PDF */
    @Column(name = "signature_path")
    private String signaturePath;


    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "blood_group", nullable = false)
    private BloodGroup bloodGroup;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    @JsonIgnoreProperties({"student", "hod", "admin", "examController", "password"})
    private User user;


    public enum Gender { MALE, FEMALE, OTHER }
    public enum BloodGroup { A_POS, A_NEG, B_POS, B_NEG, AB_POS, AB_NEG, O_POS, O_NEG }
    public ExamControllerAdmin() {}

    public Long getControllerId() { return controllerId; }
    public void setControllerId(Long id) { this.controllerId = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dob) { this.dateOfBirth = dob; }
    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }
    public String getSignaturePath() { return signaturePath; }
    public void setSignaturePath(String signaturePath) { this.signaturePath = signaturePath; }
    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    public BloodGroup getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(BloodGroup bloodGroup) { this.bloodGroup = bloodGroup; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}