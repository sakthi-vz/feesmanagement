package com.college.feesmanagement.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.college.feesmanagement.entity.ExamFeePayment;
import com.college.feesmanagement.entity.ExamRegistration;
import com.college.feesmanagement.entity.Student;
import com.college.feesmanagement.repository.ExamFeePaymentRepository;
import com.college.feesmanagement.repository.ExamRegistrationRepository;
import com.college.feesmanagement.repository.StudentRepository;
import com.college.feesmanagement.repository.SemesterPaymentSummaryRepository;
import com.college.feesmanagement.repository.StudentSemesterStatusRepository;

import java.util.List;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;

@Service
public class PaymentService {

    private final ExamFeePaymentRepository          paymentRepository;
    private final ExamRegistrationRepository        registrationRepository;
    private final StudentRepository                 studentRepository;
    private final SemesterPaymentSummaryRepository  summaryRepository;
    private final StudentSemesterStatusRepository   statusRepository;

    @Autowired @Lazy
    private StudentService studentService;

    public PaymentService(ExamFeePaymentRepository paymentRepository,
                          ExamRegistrationRepository registrationRepository,
                          StudentRepository studentRepository,
                          SemesterPaymentSummaryRepository summaryRepository,
                          StudentSemesterStatusRepository statusRepository) {
        this.paymentRepository    = paymentRepository;
        this.registrationRepository = registrationRepository;
        this.studentRepository    = studentRepository;
        this.summaryRepository    = summaryRepository;
        this.statusRepository     = statusRepository;
    }

    @Transactional
    public ExamFeePayment processPayment(Long studentId, String transactionId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));

        if (student.getEligibilityStatus() == Student.EligibilityStatus.NOT_ELIGIBLE)
            throw new RuntimeException("Payment not allowed. Student not eligible. Meet Discipline Committee.");

        // FIXED: guard against double-payment for the same semester
        Integer currentSem = student.getCurrentSemester();
        if (student.getProgrammeStatus() != Student.ProgrammeStatus.COMPLETED) {
            statusRepository.findByStudentStudentIdAndSemester(studentId, currentSem)
                .ifPresent(status -> {
                    if (Boolean.TRUE.equals(status.getPaid()))
                        throw new RuntimeException(
                            "Semester " + currentSem + " fees already paid. Receipt: " + status.getReceiptNo());
                });
        }

        // Guard: student must have at least one subject allocated by HOD
        List<ExamRegistration> allRegistrations = registrationRepository.findByStudentStudentId(studentId);
        if (allRegistrations.isEmpty())
            throw new RuntimeException(
                "No subjects allocated yet. Your HOD must allocate subjects for Semester "
                + student.getCurrentSemester() + " before you can pay fees.");

        List<ExamRegistration> unpaidRegistrations = registrationRepository.findUnpaidRegistrations(studentId);
        if (unpaidRegistrations.isEmpty())
            throw new RuntimeException("No pending registrations to pay for. Fees may already be paid.");

        // COMPLETED students pay only arrears
        if (student.getProgrammeStatus() == Student.ProgrammeStatus.COMPLETED) {
            unpaidRegistrations = unpaidRegistrations.stream()
                    .filter(r -> r.getType() == ExamRegistration.RegistrationType.ARREAR)
                    .collect(java.util.stream.Collectors.toList());
            if (unpaidRegistrations.isEmpty())
                throw new RuntimeException("No arrear registrations pending. Programme already completed.");
        }

        Double totalFee = unpaidRegistrations.stream()
                .mapToDouble(reg -> reg.getSubject().getFee()).sum();

        ExamFeePayment payment = new ExamFeePayment();
        payment.setStudent(student);
        payment.setTotalAmount(totalFee);
        payment.setLateFee(0.0);
        payment.setTransactionId(transactionId);
        payment.setStatus(ExamFeePayment.PaymentStatus.COMPLETED);
        payment.setSemester(student.getCurrentSemester()); // stamp semester at payment time

        ExamFeePayment savedPayment = paymentRepository.save(payment);

        for (ExamRegistration reg : unpaidRegistrations) {
            reg.setPayment(savedPayment);
            registrationRepository.save(reg);
        }

        studentService.markSemesterPaid(
            student.getStudentId(),
            student.getCurrentSemester(),
            savedPayment.getTotalAmount(),
            savedPayment.getReceiptNo(),
            transactionId,
            unpaidRegistrations.size()
        );

        return savedPayment;
    }

    public Optional<ExamFeePayment> getPaymentByReceiptNo(String receiptNo) {
        return paymentRepository.findByReceiptNo(receiptNo);
    }

    public List<ExamFeePayment> getStudentPayments(Long studentId) {
        return paymentRepository.findByStudentStudentId(studentId);
    }

    public List<ExamFeePayment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public List<ExamFeePayment> getPaymentsByDepartment(Long deptId) {
        return paymentRepository.findByDepartmentId(deptId);
    }

    public Map<String, Double> getDepartmentWiseCollection() {
        Map<String, Double> summary = new HashMap<>();
        for (ExamFeePayment payment : paymentRepository.findCompletedPayments()) {
            String deptName = payment.getStudent().getDepartment().getDeptName();
            summary.merge(deptName, payment.getTotalAmount(), Double::sum);
        }
        return summary;
    }

    public Map<String, Object> getPaymentStatistics() {
        Map<String, Object> stats = new HashMap<>();

        List<ExamFeePayment> all       = paymentRepository.findAll();
        List<ExamFeePayment> completed = paymentRepository.findCompletedPayments();
        List<ExamFeePayment> pending   = paymentRepository.findPendingPayments();

        List<Student> activeStudents = studentRepository.findAll().stream()
                .filter(s -> s.getProgrammeStatus() == null
                          || s.getProgrammeStatus() == Student.ProgrammeStatus.ACTIVE)
                .toList();

        // Determine current semester by mode across active students
        int currentSemester = activeStudents.stream()
                .filter(s -> s.getCurrentSemester() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                    Student::getCurrentSemester, java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(1);

        List<ExamFeePayment> curSemPayments = paymentRepository.findCompletedPaymentsBySemester(currentSemester);
        double curSemCollection = curSemPayments.stream()
                .mapToDouble(p -> p.getTotalAmount() != null ? p.getTotalAmount() : 0).sum();
        long curSemStudentTotal = activeStudents.stream()
                .filter(s -> currentSemester == (s.getCurrentSemester() != null ? s.getCurrentSemester() : 0))
                .count();
        long curSemPaid = curSemPayments.stream()
                .map(p -> p.getStudent().getStudentId()).distinct().count();

        var allSummaries = summaryRepository.findAll();
        double archivedCollection = allSummaries.stream()
                .mapToDouble(s -> s.getTotalCollection() != null ? s.getTotalCollection() : 0).sum();

        stats.put("currentSemester",     currentSemester);
        stats.put("curSemStudents",       curSemStudentTotal);
        stats.put("curSemPaidStudents",   curSemPaid);
        stats.put("curSemUnpaidStudents", Math.max(0, curSemStudentTotal - curSemPaid));
        stats.put("curSemCollection",     curSemCollection);
        stats.put("archivedCollection",   archivedCollection);
        stats.put("archivedSemesters",    allSummaries.stream()
                .map(com.college.feesmanagement.entity.SemesterPaymentSummary::getSemester)
                .distinct().count());
        stats.put("grandTotalCollection", archivedCollection + curSemCollection);
        stats.put("totalPayments",        all.size());
        stats.put("completedPayments",    completed.size());
        stats.put("pendingPayments",      pending.size());
        stats.put("totalAmountCollected", curSemCollection);

        return stats;
    }

    public List<Long> getPaidStudentIdsForSemester(Long deptId, Integer semester) {
        return paymentRepository.findPaidStudentIdsForSemester(deptId, semester);
    }
}