package com.college.feesmanagement.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.college.feesmanagement.entity.ExamRegistration;
import com.college.feesmanagement.entity.Student;
import com.college.feesmanagement.entity.Subject;
import com.college.feesmanagement.repository.ExamRegistrationRepository;
import com.college.feesmanagement.repository.StudentRepository;
import com.college.feesmanagement.repository.SubjectRepository;
import java.util.List;
import java.util.ArrayList;

@Service
public class ExamRegistrationService {

    private final ExamRegistrationRepository examRegistrationRepository;
    private final StudentRepository studentRepository;
    private final SubjectRepository subjectRepository;

    public ExamRegistrationService(ExamRegistrationRepository examRegistrationRepository,
                                   StudentRepository studentRepository,
                                   SubjectRepository subjectRepository) {
        this.examRegistrationRepository = examRegistrationRepository;
        this.studentRepository = studentRepository;
        this.subjectRepository = subjectRepository;
    }

    // Allocate subjects to eligible students (HOD only)
    @Transactional
    public List<ExamRegistration> allocateSubjects(Long deptId, Integer semester) {
        List<Subject> subjects = subjectRepository.findByDeptIdAndSemester(deptId, semester);
        List<Student> eligibleStudents = studentRepository.findEligibleStudents(deptId, semester);
        List<ExamRegistration> registrations = new ArrayList<>();

        for (Student student : eligibleStudents) {
            // FIXED: only deletes UNPAID semester registrations — paid rows preserved
            examRegistrationRepository.deleteUnpaidSemesterRegistrations(student.getStudentId(), semester);
            for (Subject subject : subjects) {
                ExamRegistration reg = new ExamRegistration();
                reg.setStudent(student);
                reg.setSubject(subject);
                reg.setType(ExamRegistration.RegistrationType.SEMESTER);
                reg.setEligibilityStatus(student.getEligibilityStatus());
                registrations.add(examRegistrationRepository.save(reg));
            }
        }
        return registrations;
    }

    @Transactional
    public List<ExamRegistration> allocateSubjectsWithExtra(Long deptId, Integer semester,
                                                             List<String> extraSubjectCodes) {
        List<Subject> subjects = new ArrayList<>(subjectRepository.findByDeptIdAndSemester(deptId, semester));
        for (String code : extraSubjectCodes) {
            subjectRepository.findBySubjectCode(code).ifPresent(s -> {
                if (subjects.stream().noneMatch(x -> x.getSubjectId().equals(s.getSubjectId())))
                    subjects.add(s);
            });
        }

        List<Student> eligibleStudents = studentRepository.findEligibleStudents(deptId, semester);
        List<ExamRegistration> registrations = new ArrayList<>();

        for (Student student : eligibleStudents) {
            examRegistrationRepository.deleteUnpaidSemesterRegistrations(student.getStudentId(), semester);
            for (Subject subject : subjects) {
                ExamRegistration reg = new ExamRegistration();
                reg.setStudent(student);
                reg.setSubject(subject);
                reg.setType(ExamRegistration.RegistrationType.SEMESTER);
                reg.setEligibilityStatus(student.getEligibilityStatus());
                registrations.add(examRegistrationRepository.save(reg));
            }
        }
        return registrations;
    }

    // Add arrear subject (Student — caller must pass their own studentId from session)
    @Transactional
    public ExamRegistration addArrearSubject(Long studentId, Long subjectId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new RuntimeException("Subject not found"));

        // Bug 4 fix — passed-out students skip the attendance gate;
        // active students still need to be eligible
        if (!student.isCompleted()
                && student.getEligibilityStatus() == Student.EligibilityStatus.NOT_ELIGIBLE)
            throw new RuntimeException("Student not eligible. Attendance: "
                    + student.getAttendancePercentage() + "%. Meet Discipline Committee.");

        // Bug 3 fix — arrear subject must belong to a PREVIOUS semester
        if (!student.isCompleted()) {
            Integer subjectSem = subject.getSemester();
            Integer currentSem = student.getCurrentSemester();
            if (subjectSem != null && currentSem != null && subjectSem >= currentSem)
                throw new RuntimeException(
                    "Cannot register Semester " + subjectSem + " subject as arrear — "
                    + "arrears are only allowed for subjects from previous semesters.");
        }

        // Bug 2 fix — prevent duplicate arrear registrations for the same subject
        boolean alreadyRegistered = examRegistrationRepository
                .findByStudentStudentId(studentId).stream()
                .anyMatch(r -> r.getSubject().getSubjectId().equals(subjectId)
                            && r.getType() == ExamRegistration.RegistrationType.ARREAR
                            && r.getPayment() == null); // unpaid duplicate
        if (alreadyRegistered)
            throw new RuntimeException(
                "Subject '" + subject.getName() + "' is already registered as an arrear.");

        ExamRegistration reg = new ExamRegistration();
        reg.setStudent(student);
        reg.setSubject(subject);
        reg.setType(ExamRegistration.RegistrationType.ARREAR);
        reg.setEligibilityStatus(student.getEligibilityStatus());
        return examRegistrationRepository.save(reg);
    }

    public List<ExamRegistration> getStudentRegistrations(Long studentId) {
        return examRegistrationRepository.findByStudentStudentId(studentId);
    }

    public List<ExamRegistration> getUnpaidRegistrations(Long studentId) {
        return examRegistrationRepository.findUnpaidRegistrations(studentId);
    }

    public Double calculateTotalFee(Long studentId) {
        return getUnpaidRegistrations(studentId).stream()
                .mapToDouble(reg -> reg.getSubject().getFee())
                .sum();
    }

    /** Returns a detailed breakdown: semesterFee, arrearFee, totalFee, counts. */
    public java.util.Map<String, Object> calculateFeeBreakdown(Long studentId) {
        List<ExamRegistration> unpaid = getUnpaidRegistrations(studentId);
        // Check if HOD has allocated ANY subjects (paid or unpaid) for this student
        List<ExamRegistration> allRegs = examRegistrationRepository.findByStudentStudentId(studentId);
        double semesterFee = unpaid.stream()
                .filter(r -> r.getType() == ExamRegistration.RegistrationType.SEMESTER)
                .mapToDouble(r -> r.getSubject().getFee())
                .sum();
        double arrearFee = unpaid.stream()
                .filter(r -> r.getType() == ExamRegistration.RegistrationType.ARREAR)
                .mapToDouble(r -> r.getSubject().getFee())
                .sum();
        long semesterCount = unpaid.stream()
                .filter(r -> r.getType() == ExamRegistration.RegistrationType.SEMESTER)
                .count();
        long arrearCount = unpaid.stream()
                .filter(r -> r.getType() == ExamRegistration.RegistrationType.ARREAR)
                .count();
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("semesterFee",   semesterFee);
        result.put("arrearFee",     arrearFee);
        result.put("totalFee",      semesterFee + arrearFee);
        result.put("semesterCount", semesterCount);
        result.put("arrearCount",   arrearCount);
        result.put("hasAllocations", !allRegs.isEmpty());
        return result;
    }

    public List<ExamRegistration> getAllRegistrations() {
        return examRegistrationRepository.findAll();
    }

    /**
     * FIXED: Ownership-checked delete — studentId must own regId.
     * Pass null for studentId only from an admin/HOD context.
     */
    public void deleteRegistration(Long regId, Long requestingStudentId) {
        ExamRegistration reg = examRegistrationRepository.findById(regId)
                .orElseThrow(() -> new RuntimeException("Registration not found"));

        // If a studentId is provided, enforce ownership
        if (requestingStudentId != null
                && !reg.getStudent().getStudentId().equals(requestingStudentId)) {
            throw new RuntimeException("Not authorised to delete this registration");
        }

        // Prevent deleting already-paid registrations
        if (reg.getPayment() != null)
            throw new RuntimeException("Cannot remove a paid registration");

        examRegistrationRepository.deleteById(regId);
    }
}