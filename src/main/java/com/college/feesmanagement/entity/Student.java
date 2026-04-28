package com.college.feesmanagement.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;

@Entity
@Table(name = "students")
public class Student {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long studentId;

    @NotBlank(message = "Name is required")
    private String name;

    private String middleName;

    private String lastName;

    @NotBlank(message = "Roll number is required")
    @Column(unique = true)
    private String rollNo;

    @ManyToOne
    @JoinColumn(name = "dept_id", nullable = false)
    @JsonIgnoreProperties({"students", "subjects"})
    private Department department;

    private Integer currentSemester;

    @DecimalMin(value = "0.0") @DecimalMax(value = "100.0")
    private Double attendancePercentage;

    @Enumerated(EnumType.STRING)
    private EligibilityStatus eligibilityStatus;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "VARCHAR(20) DEFAULT 'ACTIVE'")
    private ProgrammeStatus programmeStatus = ProgrammeStatus.ACTIVE;

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

    public enum EligibilityStatus { ELIGIBLE, NOT_ELIGIBLE }
    public enum ProgrammeStatus { ACTIVE, COMPLETED }
    public enum Gender { MALE, FEMALE, OTHER }
    public enum BloodGroup { A_POS, A_NEG, B_POS, B_NEG, AB_POS, AB_NEG, O_POS, O_NEG }

    public Student() {}

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long id) { this.studentId = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMiddleName() { return middleName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }
    public Department getDepartment() { return department; }
    public void setDepartment(Department d) { this.department = d; }
    public Integer getCurrentSemester() { return currentSemester; }
    public void setCurrentSemester(Integer s) { this.currentSemester = s; }
    public Double getAttendancePercentage() { return attendancePercentage; }
    public void setAttendancePercentage(Double a) { this.attendancePercentage = a; updateEligibilityStatus(); }
    public EligibilityStatus getEligibilityStatus() { return eligibilityStatus; }
    public void setEligibilityStatus(EligibilityStatus e) { this.eligibilityStatus = e; }
    public ProgrammeStatus getProgrammeStatus() { return programmeStatus; }
    public void setProgrammeStatus(ProgrammeStatus p) { this.programmeStatus = p; }
    public boolean isCompleted() { return ProgrammeStatus.COMPLETED.equals(this.programmeStatus); }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dob) { this.dateOfBirth = dob; }
    public String getPhotoPath() { return photoPath; }
    public void setPhotoPath(String photoPath) { this.photoPath = photoPath; }
    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    public BloodGroup getBloodGroup() { return bloodGroup; }
    public void setBloodGroup(BloodGroup bloodGroup) { this.bloodGroup = bloodGroup; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    private void updateEligibilityStatus() {
        if (attendancePercentage != null)
            this.eligibilityStatus = attendancePercentage >= 75.0
                    ? EligibilityStatus.ELIGIBLE : EligibilityStatus.NOT_ELIGIBLE;
    }
}