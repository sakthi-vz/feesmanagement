package com.college.feesmanagement.controller;

import com.college.feesmanagement.entity.*;
import com.college.feesmanagement.repository.*;
import com.college.feesmanagement.service.HallTicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/exam-controller")
public class ExamControllerController {

    private final ExamControllerRepository   controllerRepo;
    private final SubjectRepository          subjectRepo;
    private final StudentRepository          studentRepo;
    private final ExamRegistrationRepository registrationRepository;
    private final UserRepository           userRepo;
    private final ExamFeePaymentRepository paymentRepo;
    private final HallTicketService        hallTicketService;
    private final BCryptPasswordEncoder    encoder;

    @Value("${upload.photos.dir:uploads/photos}")
    private String uploadDir;

    public ExamControllerController(ExamControllerRepository controllerRepo,
                                     SubjectRepository subjectRepo,
                                     StudentRepository studentRepo,
                                     ExamRegistrationRepository registrationRepository,
                                     UserRepository userRepo,
                                     ExamFeePaymentRepository paymentRepo,
                                     HallTicketService hallTicketService,
                                     BCryptPasswordEncoder encoder) {
        this.controllerRepo  = controllerRepo;
        this.subjectRepo     = subjectRepo;
        this.studentRepo            = studentRepo;
        this.registrationRepository = registrationRepository;
        this.userRepo               = userRepo;
        this.paymentRepo     = paymentRepo;
        this.hallTicketService = hallTicketService;
        this.encoder         = encoder;
    }

    // ══ REGISTRATION ══════════════════════════════════════════

    /** POST /exam-controller/register — Admin registers an Exam Controller */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> req) {
        try {
            String name       = (String) req.get("name");
            String employeeId = (String) req.get("employeeId");
            String username   = (String) req.get("username");
            String email      = (String) req.get("email");
            String phone      = (String) req.getOrDefault("phone", "");
            String designation= (String) req.getOrDefault("designation", "Controller of Examinations");
            String dobStr     = (String) req.get("dateOfBirth");
            String password   = (String) req.get("password");

            if (name==null||employeeId==null||username==null||email==null||password==null)
                return ResponseEntity.badRequest().body(Map.of("error","All required fields must be filled."));
            if (password.length() < 8)
                return ResponseEntity.badRequest().body(Map.of("error","Password must be at least 8 characters."));
            if (userRepo.findByUsername(username).isPresent())
                return ResponseEntity.status(409).body(Map.of("error","Username already exists."));
            if (userRepo.findByEmail(email).isPresent())
                return ResponseEntity.status(409).body(Map.of("error","Email already registered."));
            if (controllerRepo.findByEmployeeId(employeeId).isPresent())
                return ResponseEntity.status(409).body(Map.of("error","Employee ID already registered."));

            User user = new User();
            user.setUsername(username);
            user.setPassword(encoder.encode(password));
            user.setRole(User.Role.EXAM_CONTROLLER);
            user.setEmail(email);
            user.setPhone(phone);
            userRepo.save(user);

            ExamControllerAdmin ec = new ExamControllerAdmin();
            ec.setName(name);
            ec.setEmployeeId(employeeId);
            ec.setDesignation(designation);
            if (dobStr != null && !dobStr.isBlank())
                try { ec.setDateOfBirth(LocalDate.parse(dobStr)); } catch (Exception ignored) {}
            ec.setUser(user);
            controllerRepo.save(ec);

            return ResponseEntity.status(201).body(Map.of(
                "message",      "Exam Controller registered successfully.",
                "controllerId", ec.getControllerId(),
                "name",         ec.getName()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error","Registration failed: "+e.getMessage()));
        }
    }

    // ══ SIGNATURE UPLOAD ══════════════════════════════════════

    /** POST /exam-controller/{id}/upload-signature — Upload controller's signature image */
    @PostMapping("/{id}/upload-signature")
    public ResponseEntity<?> uploadSignature(@PathVariable Long id,
                                              @RequestParam("signature") MultipartFile file) {
        try {
            ExamControllerAdmin ec = controllerRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Controller not found."));

            String path = saveImage(file, "controller_sig_" + id);
            ec.setSignaturePath(path);
            controllerRepo.save(ec);

            return ResponseEntity.ok(Map.of("success", true,
                    "message", "Signature uploaded successfully.",
                    "signaturePath", path));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** POST /exam-controller/{id}/upload-photo — Upload controller's profile photo */
    @PostMapping("/{id}/upload-photo")
    public ResponseEntity<?> uploadPhoto(@PathVariable Long id,
                                          @RequestParam("photo") MultipartFile file) {
        try {
            ExamControllerAdmin ec = controllerRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Controller not found."));
            String path = saveImage(file, "controller_photo_" + id);
            ec.setPhotoPath(path);
            controllerRepo.save(ec);
            return ResponseEntity.ok(Map.of("success", true, "photoPath", path));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ══ SUBJECT SCHEDULE MANAGEMENT ═══════════════════════════

    /** PUT /exam-controller/subjects/{subjectId}/schedule — Set exam date + session */
    @PutMapping("/subjects/{subjectId}/schedule")
    public ResponseEntity<?> setSchedule(@PathVariable Long subjectId,
                                          @RequestBody Map<String, String> body) {
        try {
            Subject subject = subjectRepo.findById(subjectId)
                    .orElseThrow(() -> new RuntimeException("Subject not found."));

            String examDate = body.get("examDate");
            String session  = body.get("session");

            if (examDate == null || examDate.isBlank()) {
                subject.setExamDate(null);
            } else {
                subject.setExamDate(LocalDate.parse(examDate));
            }
            if (session == null || session.isBlank()) {
                subject.setSession(null);
            } else {
                subject.setSession(session);
            }

            subjectRepo.save(subject);

            return ResponseEntity.ok(Map.of(
                "success",      true,
                "message",      "Schedule updated for: " + subject.getName(),
                "subjectId",    subjectId,
                "examDate",     subject.getExamDate() != null ? subject.getExamDate().toString() : "",
                "session",      subject.getSession() != null ? subject.getSession() : ""
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** POST /exam-controller/subjects/bulk-schedule — Set schedule for many subjects at once */
    @PostMapping("/subjects/bulk-schedule")
    public ResponseEntity<?> bulkSchedule(@RequestBody List<Map<String, String>> items) {
        int updated = 0;
        List<String> errors = new ArrayList<>();
        for (Map<String, String> item : items) {
            try {
                Long id = Long.parseLong(item.get("subjectId"));
                Subject sub = subjectRepo.findById(id).orElse(null);
                if (sub == null) { errors.add("Subject " + id + " not found"); continue; }
                String ed = item.get("examDate"), se = item.get("session");
                // Always set — empty string means CLEAR (set to null)
                if (ed == null || ed.isBlank()) {
                    sub.setExamDate(null);
                } else {
                    sub.setExamDate(LocalDate.parse(ed));
                }
                if (se == null || se.isBlank()) {
                    sub.setSession(null);
                } else {
                    sub.setSession(se);
                }
                subjectRepo.save(sub);
                updated++;
            } catch (Exception e) { errors.add(e.getMessage()); }
        }
        return ResponseEntity.ok(Map.of("updated", updated, "errors", errors));
    }

    /** GET /exam-controller/subjects — All subjects with their schedule */
    @GetMapping("/subjects")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> getSubjects(
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Integer semester) {
        try {
            List<Subject> all = subjectRepo.findAllWithDepartment();
            if (deptId != null)   all = all.stream().filter(s -> s.getDepartment() != null && s.getDepartment().getDeptId().equals(deptId)).toList();
            if (semester != null) all = all.stream().filter(s -> semester.equals(s.getSemester())).toList();
            return ResponseEntity.ok(all.stream().map(s -> {
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("subjectId",   s.getSubjectId());
                m.put("name",        s.getName());
                m.put("subjectCode", s.getSubjectCode() != null ? s.getSubjectCode() : "");
                m.put("type",        s.getType() != null ? s.getType().name() : "");
                m.put("semester",    s.getSemester());
                m.put("department",  s.getDepartment() != null ? s.getDepartment().getDeptName() : "");
                m.put("deptId",      s.getDepartment() != null ? s.getDepartment().getDeptId() : null);
                m.put("examDate",    s.getExamDate() != null ? s.getExamDate().toString() : "");
                m.put("session",     s.getSession() != null ? s.getSession() : "");
                m.put("scheduled",   s.getExamDate() != null && s.getSession() != null && !s.getSession().isBlank());
                return m;
            }).toList());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ══ HALL TICKET GENERATION ════════════════════════════════

    /** GET /exam-controller/hall-ticket/check/{studentId} */
    @GetMapping("/hall-ticket/check/{studentId}")
    public ResponseEntity<?> checkEligibility(@PathVariable Long studentId) {
        try {
            hallTicketService.checkEligibility(studentId);
            Student s = studentRepo.findById(studentId).orElseThrow();
            return ResponseEntity.ok(Map.of(
                "eligible",   true,
                "message",    "Student is eligible for hall ticket.",
                "name",       s.getName(),
                "rollNo",     s.getRollNo(),
                "semester",   s.getCurrentSemester() != null ? s.getCurrentSemester() : 1,
                "attendance", s.getAttendancePercentage() != null ? s.getAttendancePercentage() : 0.0,
                "department", s.getDepartment() != null ? s.getDepartment().getDeptName() : "—"
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("eligible", false, "message", e.getMessage()));
        }
    }

    /** GET /exam-controller/hall-ticket/download/{studentId} */
    @GetMapping("/hall-ticket/download/{studentId}")
    public ResponseEntity<byte[]> downloadHallTicket(@PathVariable Long studentId) {
        try {
            byte[]  pdf = hallTicketService.generateHallTicket(studentId);
            Student s   = studentRepo.findById(studentId).orElseThrow();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=HallTicket-" + s.getRollNo() + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .header("X-Error-Message", e.getMessage())
                    .build();
        }
    }

    /** GET /exam-controller/students — All students with eligibility info */
    @GetMapping("/students")
    public ResponseEntity<?> getStudents(@RequestParam(required = false) Long deptId) {
        try {
            List<Student> all = studentRepo.findAll();
            if (deptId != null) all = all.stream()
                    .filter(s -> s.getDepartment() != null && s.getDepartment().getDeptId().equals(deptId)).toList();

            // FILTER RULES:
            // COMPLETED students:
            //   Show if they have UNPAID arrear (registered, needs payment → hall ticket after paying)
            //   OR paid arrear where payment.semester matches current academic semester (just paid this cycle)
            // ACTIVE students:
            //   Show only if they have paid SEMESTER regs for CURRENT semester

            // Determine current exam cycle year for comparison
            int currentYear = java.time.LocalDate.now().getYear();

            List<Student> filtered = all.stream().filter(s -> {
                boolean isCompleted = s.getProgrammeStatus() == Student.ProgrammeStatus.COMPLETED;
                if (isCompleted) {
                    List<ExamRegistration> arrears = registrationRepository
                            .findByStudentStudentId(s.getStudentId()).stream()
                            .filter(r -> r.getType() == ExamRegistration.RegistrationType.ARREAR)
                            .toList();

                    // Case 1: Has unpaid arrear registration (registered but fee not paid yet)
                    boolean hasUnpaidArrear = arrears.stream()
                            .anyMatch(r -> r.getPayment() == null);

                    // Case 2: Has paid arrear where payment was made in current or previous year
                    // (i.e. recently paid arrear — needs hall ticket)
                    boolean hasPaidArrearRecent = arrears.stream()
                            .anyMatch(r -> r.getPayment() != null
                                    && r.getPayment().getStatus() == ExamFeePayment.PaymentStatus.COMPLETED
                                    && r.getPayment().getPaymentDate() != null
                                    && r.getPayment().getPaymentDate().getYear() >= currentYear - 1);

                    return hasUnpaidArrear || hasPaidArrearRecent;
                } else {
                    // ACTIVE: must have paid SEMESTER registrations for CURRENT semester
                    Integer sem = s.getCurrentSemester();
                    if (sem == null) return false;
                    return registrationRepository
                            .findByStudentStudentId(s.getStudentId()).stream()
                            .anyMatch(r -> r.getPayment() != null
                                    && r.getType() == ExamRegistration.RegistrationType.SEMESTER
                                    && r.getSubject().getSemester() != null
                                    && r.getSubject().getSemester().equals(sem));
                }
            }).toList();

            return ResponseEntity.ok(filtered.stream().map(s -> {
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("studentId",    s.getStudentId());
                m.put("name",         s.getName());
                m.put("rollNo",       s.getRollNo());
                m.put("department",   s.getDepartment() != null ? s.getDepartment().getDeptName() : "—");
                m.put("deptId",       s.getDepartment() != null ? s.getDepartment().getDeptId() : null);
                m.put("semester",     s.getCurrentSemester() != null ? s.getCurrentSemester() : 1);
                m.put("attendance",   s.getAttendancePercentage() != null ? s.getAttendancePercentage() : 0.0);
                m.put("eligible",     s.getEligibilityStatus() == Student.EligibilityStatus.ELIGIBLE);
                boolean feePaid = isFeePaid(s.getStudentId());
                m.put("feePaid",      feePaid);
                boolean eligible = s.getEligibilityStatus() == Student.EligibilityStatus.ELIGIBLE;
                m.put("canGenerate",  eligible && feePaid);
                m.put("programmeStatus", s.getProgrammeStatus() != null ? s.getProgrammeStatus().name() : "ACTIVE");
                // hasArrear: true if student has any ARREAR registration
                boolean hasArrear = registrationRepository.findByStudentStudentId(s.getStudentId()).stream()
                        .anyMatch(r -> r.getType() == ExamRegistration.RegistrationType.ARREAR);
                m.put("hasArrear", hasArrear);
                return m;
            }).toList());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** GET /exam-controller/me?email={email} — Get controller profile */
    @GetMapping("/me")
    public ResponseEntity<?> getProfile(@RequestParam String email) {
        return controllerRepo.findByUserEmail(email)
                .map(ec -> ResponseEntity.ok((Object) Map.of(
                        "controllerId", ec.getControllerId(),
                        "name",        ec.getName(),
                        "employeeId",  ec.getEmployeeId(),
                        "designation", ec.getDesignation() != null ? ec.getDesignation() : "",
                        "hasSignature",ec.getSignaturePath() != null && !ec.getSignaturePath().isBlank(),
                        "signatureUrl", ec.getSignaturePath() != null && !ec.getSignaturePath().isBlank()
                                ? "/photos/view/" + java.nio.file.Paths.get(ec.getSignaturePath()).getFileName().toString()
                                : ""
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Helpers ───────────────────────────────────────────────

    private boolean isFeePaid(Long studentId) {
        return paymentRepo.findByStudentStudentId(studentId).stream()
                .anyMatch(p -> p.getStatus() == ExamFeePayment.PaymentStatus.COMPLETED);
    }

    private String saveImage(MultipartFile file, String prefix) throws IOException {
        if (file == null || file.isEmpty()) throw new RuntimeException("No file provided.");
        Set<String> allowed = Set.of("image/jpeg","image/jpg","image/png","image/webp");
        if (!allowed.contains(file.getContentType())) throw new RuntimeException("Only JPG/PNG/WEBP allowed.");
        if (file.getSize() > 3 * 1024 * 1024) throw new RuntimeException("File must be under 3 MB.");
        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        // Delete old file with same prefix
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().startsWith(prefix + "_"))
                  .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }
        String ext = file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")
                ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.')).toLowerCase() : ".jpg";
        Path dest = dir.resolve(prefix + "_" + System.currentTimeMillis() + ext);
        Files.write(dest, file.getBytes());
        return dest.toString();
    }
}