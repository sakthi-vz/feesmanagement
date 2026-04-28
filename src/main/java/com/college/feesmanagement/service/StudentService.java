package com.college.feesmanagement.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.college.feesmanagement.entity.*;
import com.college.feesmanagement.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class StudentService {

    private static final int MAX_SEMESTER = 8;

    private final StudentRepository                studentRepository;
    private final DepartmentRepository             departmentRepository;
    private final StudentSemesterRecordRepository  semesterRecordRepository;
    private final SemesterPaymentSummaryRepository paymentSummaryRepository;
    private final ExamFeePaymentRepository         paymentRepository;
    private final ExamRegistrationRepository       registrationRepository;
    private final StudentSemesterStatusRepository  statusRepository;

    public StudentService(StudentRepository studentRepository,
                          DepartmentRepository departmentRepository,
                          StudentSemesterRecordRepository semesterRecordRepository,
                          SemesterPaymentSummaryRepository paymentSummaryRepository,
                          ExamFeePaymentRepository paymentRepository,
                          ExamRegistrationRepository registrationRepository,
                          StudentSemesterStatusRepository statusRepository) {
        this.studentRepository        = studentRepository;
        this.departmentRepository     = departmentRepository;
        this.semesterRecordRepository = semesterRecordRepository;
        this.paymentSummaryRepository = paymentSummaryRepository;
        this.paymentRepository        = paymentRepository;
        this.registrationRepository   = registrationRepository;
        this.statusRepository         = statusRepository;
    }

    // ── Basic CRUD ────────────────────────────────────────────────

    @Transactional
    public Student createStudent(Student student) {
        Student saved = studentRepository.save(student);
        createOrGetStatus(saved, 1);
        return saved;
    }

    public List<Student> getAllStudents() { return studentRepository.findAll(); }

    public Optional<Student> getStudentById(Long id) { return studentRepository.findById(id); }

    public Optional<Student> getStudentByRollNo(String rollNo) {
        return studentRepository.findByRollNo(rollNo);
    }

    /**
     * FIXED: updateStudent now syncs attendance changes to the semester-status ledger
     * so the HOD view and promotion snapshot are always accurate.
     */
    @Transactional
    public Student updateStudent(Long id, Student updatedStudent) {
        Student existing = studentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        existing.setName(updatedStudent.getName());
        existing.setRollNo(updatedStudent.getRollNo());
        existing.setDepartment(updatedStudent.getDepartment());
        existing.setCurrentSemester(updatedStudent.getCurrentSemester());
        if (updatedStudent.getDateOfBirth() != null)
            existing.setDateOfBirth(updatedStudent.getDateOfBirth());

        if (updatedStudent.getAttendancePercentage() != null) {
            existing.setAttendancePercentage(updatedStudent.getAttendancePercentage());
            // Sync to the ledger — same as updateAttendance()
            StudentSemesterStatus status = createOrGetStatus(existing, existing.getCurrentSemester());
            status.setAttendancePercentage(updatedStudent.getAttendancePercentage());
            status.setEligibilityStatus(
                updatedStudent.getAttendancePercentage() >= 75.0 ? "ELIGIBLE" : "NOT_ELIGIBLE");
            statusRepository.save(status);
        }
        return studentRepository.save(existing);
    }

    @Transactional
    public Student updateAttendance(Long studentId, Double attendancePercentage) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        student.setAttendancePercentage(attendancePercentage);
        StudentSemesterStatus status = createOrGetStatus(student, student.getCurrentSemester());
        status.setAttendancePercentage(attendancePercentage);
        status.setEligibilityStatus(attendancePercentage >= 75.0 ? "ELIGIBLE" : "NOT_ELIGIBLE");
        statusRepository.save(status);
        return studentRepository.save(student);
    }

    /**
     * Bulk semester promotion — safe order of operations:
     */
    @Transactional
    public PromotionResult promoteSemester(Long deptId, Integer currentSemester) {
        Department dept = departmentRepository.findById(deptId)
                .orElseThrow(() -> new RuntimeException("Department not found"));

        String academicYear = buildAcademicYear();

        // Step 1 — collect all students currently on this semester
        List<Student> allStudents = studentRepository
                .findByDepartmentAndCurrentSemester(dept, currentSemester);

        List<Student> eligible = studentRepository
                .findEligibleStudents(deptId, currentSemester);

        List<ExamFeePayment> semesterPayments =
                paymentRepository.findCompletedPaymentsByDeptAndSemester(deptId, currentSemester);
        List<Long> paidIds = semesterPayments.stream()
                .map(p -> p.getStudent().getStudentId()).distinct().collect(Collectors.toList());
        long unpaidCount = allStudents.stream()
                .filter(s -> !paidIds.contains(s.getStudentId())).count();

        // ── CHECKMATE: payment + history guard ────────────────────────
        // A student can be promoted from semester N only if:
        //   1. They are still on currentSemester (not already promoted)
        //   2. They are ACTIVE (not graduated)
        //   3. StudentSemesterStatus for this semester has paid = TRUE
        //      (fee payment is the single source of truth — no payment = no promotion)
        //   4. StudentSemesterStatus.promotedOn is NULL for this semester
        //      (if already stamped, they were already promoted — block double promotion
        //       even if admin moves to next sem and comes back)
        // ──────────────────────────────────────────────────────────────
        eligible = eligible.stream()
                .filter(s -> currentSemester.equals(s.getCurrentSemester()))
                .filter(s -> s.getProgrammeStatus() == Student.ProgrammeStatus.ACTIVE)
                .filter(s -> {
                    // Must have paid for this semester
                    return statusRepository
                            .findByStudentStudentIdAndSemester(s.getStudentId(), currentSemester)
                            .map(status -> Boolean.TRUE.equals(status.getPaid())
                                        && status.getPromotedOn() == null) // not already promoted
                            .orElse(false); // no status row = never paid = block
                })
                .collect(Collectors.toList());

        double totalCollection = semesterPayments.stream()
                .mapToDouble(p -> p.getTotalAmount() != null ? p.getTotalAmount() : 0).sum();

        // Step 2 — save immutable promotion record with full snapshot
        for (Student s : eligible) {
            // Pull payment info from status to copy into the record
            StudentSemesterStatus existingStatus = statusRepository
                    .findByStudentStudentIdAndSemester(s.getStudentId(), currentSemester)
                    .orElse(null);

            StudentSemesterRecord rec = semesterRecordRepository
                    .findByStudentStudentIdAndSemester(s.getStudentId(), currentSemester)
                    .orElse(new StudentSemesterRecord());
            rec.setStudent(s);
            rec.setSemester(currentSemester);
            rec.setAttendancePercentage(s.getAttendancePercentage());
            rec.setEligibilityStatus(s.getEligibilityStatus());
            rec.setPromotedOn(LocalDateTime.now());
            rec.setAcademicYear(academicYear);
            rec.setPromotedToSemester(currentSemester < MAX_SEMESTER ? currentSemester + 1 : currentSemester);
            rec.setExamCycle(buildExamCycle());
            // Copy payment details from status into the permanent record
            if (existingStatus != null) {
                rec.setAmountPaid(existingStatus.getAmountPaid());
                rec.setLateFee(existingStatus.getLateFee());
                rec.setReceiptNo(existingStatus.getReceiptNo());
                rec.setTransactionId(existingStatus.getTransactionId());
                rec.setSubjectCount(existingStatus.getSubjectCount());
            }
            semesterRecordRepository.save(rec);
        }

        // Step 3
        com.college.feesmanagement.entity.SemesterPaymentSummary summary =
            paymentSummaryRepository
                .findByDepartmentDeptIdAndSemesterAndAcademicYear(deptId, currentSemester, academicYear)
                .orElse(new com.college.feesmanagement.entity.SemesterPaymentSummary());
        summary.setDepartment(dept);
        summary.setSemester(currentSemester);
        summary.setAcademicYear(academicYear);
        summary.setExamCycle(buildExamCycle());
        summary.setTotalStudents(allStudents.size());
        summary.setEligibleStudents(eligible.size());
        summary.setPromotedStudents(eligible.size());
        summary.setNotPromotedStudents(allStudents.size() - eligible.size());
        summary.setPaidStudents(paidIds.size());
        summary.setUnpaidStudents((int) unpaidCount);
        summary.setTotalCollection(totalCollection);
        summary.setLateFeeCollection(0.0);
        summary.setSnapshotTakenOn(LocalDateTime.now());
        paymentSummaryRepository.save(summary);

        // Step 4 — freeze the status row: stamp promotedOn so this semester is
        // permanently closed. Any future promotion attempt for this semester
        // will see promotedOn != null and be blocked by the guard above.
        for (Student s : eligible) {
            StudentSemesterStatus status = createOrGetStatus(s, currentSemester);
            status.setAttendancePercentage(s.getAttendancePercentage());
            status.setEligibilityStatus(s.getEligibilityStatus() != null
                    ? s.getEligibilityStatus().name() : "ELIGIBLE");
            status.setAcademicYear(academicYear);
            status.setExamCycle(buildExamCycle());
            status.setPromotedOn(LocalDateTime.now()); // ← LOCKED: blocks re-promotion
            status.setPromotedToSemester(currentSemester < MAX_SEMESTER
                    ? currentSemester + 1 : currentSemester);
            statusRepository.save(status);
        }

        // Step 5
        int promoted = 0, graduated = 0;
        for (Student s : eligible) {
            if (currentSemester >= MAX_SEMESTER) {
                s.setProgrammeStatus(Student.ProgrammeStatus.COMPLETED);
                graduated++;
            } else {
                s.setCurrentSemester(currentSemester + 1);
                // Reset attendance and eligibility for new semester
                s.setAttendancePercentage(0.0);
                s.setEligibilityStatus(Student.EligibilityStatus.NOT_ELIGIBLE);
                createOrGetStatus(s, currentSemester + 1);
                // Clean up ALL unpaid SEMESTER registrations at promotion time.
                // This wipes the old allocation entirely — including HOD-added extra subjects
                // from the completed semester that may have a future subject.semester value
                // (e.g. a Sem 3 subject added as extra during Sem 2 allocation would survive
                // the old deleteStale query since 3 == newSem 3). A full wipe is safe because:
                //   • paid registrations are preserved (payment IS NOT NULL rows kept)
                //   • the new HOD will re-allocate subjects fresh for the new semester
                registrationRepository.deleteAllUnpaidSemesterRegistrations(s.getStudentId());
                promoted++;
            }
        }
        studentRepository.saveAll(eligible);

        return new PromotionResult(promoted, graduated, currentSemester,
                currentSemester < MAX_SEMESTER ? currentSemester + 1 : MAX_SEMESTER,
                totalCollection, paidIds.size(), (int) unpaidCount);
    }

    @Transactional
    public void markSemesterPaid(Long studentId, Integer semester,
                                  Double amount, String receiptNo,
                                  String transactionId, Integer subjectCount) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        StudentSemesterStatus status = createOrGetStatus(student, semester);
        status.setPaid(true);
        status.setAmountPaid(amount != null ? amount : 0.0);
        status.setTotalFee(amount != null ? amount : 0.0);
        status.setLateFee(0.0);
        status.setReceiptNo(receiptNo);
        status.setTransactionId(transactionId);
        status.setPaidOn(LocalDateTime.now());
        status.setSubjectCount(subjectCount != null ? subjectCount : 0);
        status.setExamCycle(buildExamCycle());
        statusRepository.save(status);
    }

    // ── History / Status queries ──────────────────────────────────

    public List<StudentSemesterStatus> getStudentStatusHistory(Long studentId) {
        return statusRepository.findByStudentStudentIdOrderBySemesterAsc(studentId);
    }

    public Optional<StudentSemesterStatus> getStudentStatusForSemester(Long studentId, Integer sem) {
        return statusRepository.findByStudentStudentIdAndSemester(studentId, sem);
    }

    public List<StudentSemesterStatus> getDeptSemesterStatuses(Long deptId, Integer semester) {
        return statusRepository.findByDeptAndSemester(deptId, semester);
    }

    public List<Long> getPaidStudentIds(Long deptId, Integer semester) {
        return statusRepository.findPaidStudentIds(deptId, semester);
    }

    public List<StudentSemesterRecord> getSemesterHistory(Long studentId) {
        return semesterRecordRepository.findByStudentStudentIdOrderBySemesterAsc(studentId);
    }

    public List<StudentSemesterRecord> getDeptSemesterHistory(Long deptId, Integer semester) {
        return semesterRecordRepository.findByDeptAndSemester(deptId, semester);
    }

    public List<com.college.feesmanagement.entity.SemesterPaymentSummary> getAllPaymentSummaries() {
        return paymentSummaryRepository.findAllOrderedByDeptAndSemester();
    }

    public List<com.college.feesmanagement.entity.SemesterPaymentSummary> getPaymentSummariesByDept(Long deptId) {
        return paymentSummaryRepository.findByDepartmentDeptIdOrderBySemesterAsc(deptId);
    }

    public List<com.college.feesmanagement.entity.SemesterPaymentSummary> getPaymentSummariesBySemester(Integer semester) {
        return paymentSummaryRepository.findBySemesterOrderByDepartmentDeptNameAsc(semester);
    }

    public List<Object[]> getCollectionBySemester() {
        return statusRepository.getCollectionBySemester();
    }

    public List<Student> getStudentsByDept(Long deptId) {
        return studentRepository.findByDepartmentId(deptId);
    }

    public List<Student> getStudentsByDeptAndSemester(Long deptId, Integer semester) {
        Department department = departmentRepository.findById(deptId)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        return studentRepository.findByDepartmentAndCurrentSemester(department, semester);
    }

    public List<Student> getEligibleStudents(Long deptId, Integer semester) {
        return studentRepository.findEligibleStudents(deptId, semester);
    }

    public void deleteStudent(Long id) { studentRepository.deleteById(id); }

    // ── Helpers ───────────────────────────────────────────────────

    private StudentSemesterStatus createOrGetStatus(Student student, Integer semester) {
        return statusRepository
                .findByStudentStudentIdAndSemester(student.getStudentId(), semester)
                .orElseGet(() -> {
                    StudentSemesterStatus s = new StudentSemesterStatus();
                    s.setStudent(student);
                    s.setSemester(semester);
                    s.setPaid(false);
                    s.setAmountPaid(0.0);
                    s.setLateFee(0.0);
                    s.setArrearCount(0);
                    s.setArrearPaidCount(0);
                    s.setArrearAmountPaid(0.0);
                    return statusRepository.save(s);
                });
    }

    /**
     * Academic year is derived from the exam cycle month:
     *   Nov/Dec promotions  → start of cycle  e.g. Nov 2025 → "2025-2026"
     *   Apr/May promotions  → end of cycle    e.g. Apr 2026 → "2025-2026"
     *   Both map to the SAME academic year string — no split across sessions.
     *
     * Rule: months Jan–May belong to the previous year's cycle.
     *       months Jun–Dec belong to the current year's cycle.
     */
    private String buildAcademicYear() {
        java.time.LocalDate today = java.time.LocalDate.now();
        int month = today.getMonthValue();
        int year  = today.getYear();
        // Jan–May: still in the cycle that started the previous year
        int startYear = (month <= 5) ? year - 1 : year;
        return startYear + "-" + (startYear + 1);
    }

    /**
     * Exam cycle label — identifies which sitting this promotion belongs to.
     * Nov/Dec (months 10–12) → "NOV/DEC YYYY"
     * Apr/May (months  3– 6) → "APR/MAY YYYY"
     */
    private String buildExamCycle() {
        java.time.LocalDate today = java.time.LocalDate.now();
        int month = today.getMonthValue();
        int year  = today.getYear();
        if (month >= 10 || month <= 1) return "NOV/DEC " + (month >= 10 ? year : year - 1);
        return "APR/MAY " + year;
    }

    public record PromotionResult(int promoted, int graduated,
                                   int fromSemester, int toSemester,
                                   double collectionArchived,
                                   int paidStudents, int unpaidStudents) {}
}