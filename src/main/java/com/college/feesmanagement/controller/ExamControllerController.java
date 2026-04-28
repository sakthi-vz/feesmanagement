package com.college.feesmanagement.controller;

import com.college.feesmanagement.entity.*;
import com.college.feesmanagement.repository.*;
import com.college.feesmanagement.service.HallTicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/exam-controller")
public class ExamControllerController {

    private final ExamControllerRepository   controllerRepo;
    private final SubjectRepository          subjectRepo;
    private final ExamScheduleRepository     scheduleRepo;
    private final StudentRepository          studentRepo;
    private final ExamRegistrationRepository registrationRepository;
    private final UserRepository             userRepo;
    private final ExamFeePaymentRepository   paymentRepo;
    private final HallTicketService          hallTicketService;
    private final BCryptPasswordEncoder      encoder;

    @Value("${upload.photos.dir:uploads/photos}")
    private String uploadDir;

    public ExamControllerController(ExamControllerRepository controllerRepo,
                                     SubjectRepository subjectRepo,
                                     ExamScheduleRepository scheduleRepo,
                                     StudentRepository studentRepo,
                                     ExamRegistrationRepository registrationRepository,
                                     UserRepository userRepo,
                                     ExamFeePaymentRepository paymentRepo,
                                     HallTicketService hallTicketService,
                                     BCryptPasswordEncoder encoder) {
        this.controllerRepo         = controllerRepo;
        this.subjectRepo            = subjectRepo;
        this.scheduleRepo           = scheduleRepo;
        this.studentRepo            = studentRepo;
        this.registrationRepository = registrationRepository;
        this.userRepo               = userRepo;
        this.paymentRepo            = paymentRepo;
        this.hallTicketService      = hallTicketService;
        this.encoder                = encoder;
    }

    // ══ REGISTRATION ══════════════════════════════════════════════

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> req) {
        try {
            String name        = (String) req.get("name");
            String lastName    = (String) req.get("lastName");
            String employeeId  = (String) req.get("employeeId");
            String username    = (String) req.get("username");
            String email       = (String) req.get("email");
            String phone       = (String) req.getOrDefault("phone", "");
            String designation = (String) req.getOrDefault("designation", "Controller of Examinations");
            String dobStr      = (String) req.get("dateOfBirth");
            String password    = (String) req.get("password");
            String gender      = (String) req.get("gender");
            String bloodGroup  = (String) req.get("bloodGroup");

            if (name==null||lastName==null||lastName.isBlank()||employeeId==null||username==null||email==null||password==null||gender==null||bloodGroup==null)
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
            ec.setLastName(lastName);
            ec.setEmployeeId(employeeId);
            ec.setDesignation(designation);
            if (dobStr != null && !dobStr.isBlank())
                try { ec.setDateOfBirth(LocalDate.parse(dobStr)); } catch (Exception ignored) {}
            ec.setGender(ExamControllerAdmin.Gender.valueOf(gender));
            ec.setBloodGroup(ExamControllerAdmin.BloodGroup.valueOf(bloodGroup));
            ec.setUser(user);
            controllerRepo.save(ec);

            return ResponseEntity.status(201).body(Map.of(
                "message",      "Exam Controller registered successfully.",
                "controllerId", ec.getControllerId(),
                "name",         java.util.stream.Stream.of(ec.getName(), ec.getMiddleName(), ec.getLastName()).filter(n -> n != null && !n.isBlank()).collect(java.util.stream.Collectors.joining(" ")),
                "lastName",     ec.getLastName() != null ? ec.getLastName() : ""
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error","Registration failed: "+e.getMessage()));
        }
    }

    // ══ SIGNATURE / PHOTO UPLOAD ══════════════════════════════════

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
                    "message", "Signature uploaded successfully.", "signaturePath", path));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Returns the COE signature as a base64 data URL — used by the frontend PDF export.
     * Avoids all MIME-sniffing and popup-window fetch issues by embedding inline.
     */
    @GetMapping("/{id}/signature-base64")
    public ResponseEntity<?> getSignatureBase64(@PathVariable Long id) {
        try {
            ExamControllerAdmin ec = controllerRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Controller not found."));
            String sigPath = ec.getSignaturePath();
            if (sigPath == null || sigPath.isBlank())
                return ResponseEntity.ok(Map.of("dataUrl", ""));

            java.io.File f = resolveFile(sigPath);
            if (f == null || !f.exists())
                return ResponseEntity.ok(Map.of("dataUrl", ""));

            // Clean the signature background before encoding
            byte[] bytes = hallTicketService.cleanSignatureBackground(f);
            // Always PNG after cleaning
            String dataUrl = "data:image/png;base64," +
                             java.util.Base64.getEncoder().encodeToString(bytes);
            return ResponseEntity.ok(Map.of("dataUrl", dataUrl));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/upload-photo")
    public ResponseEntity<?> uploadPhoto(@PathVariable Long id,
                                          @RequestParam("photo") MultipartFile file) {
        try {
            ExamControllerAdmin ec = controllerRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Controller not found."));
            String path = saveImage(file, "controller_photo_" + id);
            ec.setPhotoPath(path);
            controllerRepo.save(ec);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "photoPath", path,
                    "photoUrl", "/photos/view/" + Paths.get(path).getFileName().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ══ EXAM SCHEDULE MANAGEMENT ══════════════════════════════════

    @PutMapping("/subjects/{subjectId}/schedule")
    @Transactional
    public ResponseEntity<?> setSchedule(@PathVariable Long subjectId,
                                          @RequestBody Map<String, String> body) {
        try {
            Subject subject = subjectRepo.findById(subjectId)
                    .orElseThrow(() -> new RuntimeException("Subject not found."));

            String cycle       = buildExamCycle();
            String academicYear = buildAcademicYear();
            String examDateStr = body.get("examDate");
            String session     = body.get("session");
            String setBy       = body.getOrDefault("setBy", "Controller");

            ExamSchedule schedule = scheduleRepo
                    .findBySubjectSubjectIdAndExamCycle(subjectId, cycle)
                    .orElseGet(() -> {
                        ExamSchedule s = new ExamSchedule();
                        s.setSubject(subject);
                        s.setExamCycle(cycle);
                        s.setAcademicYear(academicYear);
                        return s;
                    });

            schedule.setExamDate(examDateStr == null || examDateStr.isBlank()
                    ? null : LocalDate.parse(examDateStr));
            schedule.setSession(session == null || session.isBlank() ? null : session);
            schedule.setUpdatedOn(LocalDateTime.now());
            schedule.setSetBy(setBy);
            scheduleRepo.save(schedule);

            return ResponseEntity.ok(Map.of(
                "success",   true,
                "message",   "Schedule updated for: " + subject.getName(),
                "subjectId", subjectId,
                "examDate",  schedule.getExamDate() != null ? schedule.getExamDate().toString() : "",
                "session",   schedule.getSession() != null ? schedule.getSession() : "",
                "examCycle", cycle,
                "scheduled", schedule.isScheduled()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/subjects/bulk-schedule")
    @Transactional
    public ResponseEntity<?> bulkSchedule(@RequestBody List<Map<String, String>> items) {
        String cycle       = buildExamCycle();
        String academicYear = buildAcademicYear();
        int updated = 0;
        List<String> errors = new ArrayList<>();

        for (Map<String, String> item : items) {
            try {
                Long subjectId = Long.parseLong(item.get("subjectId"));
                Subject subject = subjectRepo.findById(subjectId).orElse(null);
                if (subject == null) { errors.add("Subject " + subjectId + " not found"); continue; }

                ExamSchedule schedule = scheduleRepo
                        .findBySubjectSubjectIdAndExamCycle(subjectId, cycle)
                        .orElseGet(() -> {
                            ExamSchedule s = new ExamSchedule();
                            s.setSubject(subject);
                            s.setExamCycle(cycle);
                            s.setAcademicYear(academicYear);
                            return s;
                        });

                String ed = item.get("examDate"), se = item.get("session");
                schedule.setExamDate(ed == null || ed.isBlank() ? null : LocalDate.parse(ed));
                schedule.setSession(se == null || se.isBlank() ? null : se);
                schedule.setUpdatedOn(LocalDateTime.now());
                scheduleRepo.save(schedule);
                updated++;
            } catch (Exception e) {
                errors.add(e.getMessage());
            }
        }
        return ResponseEntity.ok(Map.of("updated", updated, "errors", errors));
    }

    @PostMapping("/schedule/reset")
    @Transactional
    public ResponseEntity<?> resetSchedule(@RequestParam(required = false) String targetCycle) {
        try {
            String cycle = (targetCycle != null && !targetCycle.isBlank()) ? targetCycle : buildExamCycle();
            int deleted = scheduleRepo.deleteByExamCycle(cycle);
            return ResponseEntity.ok(Map.of(
                "success",   true,
                "message",   "Schedule cleared for cycle: " + cycle,
                "deleted",   deleted,
                "examCycle", cycle
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/subjects")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getSubjects(
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Integer semester) {
        try {
            String cycle = buildExamCycle();
            List<Subject> all = subjectRepo.findAllWithDepartment();
            if (deptId != null)
                all = all.stream().filter(s -> s.getDepartment() != null
                        && s.getDepartment().getDeptId().equals(deptId)).toList();
            if (semester != null)
                all = all.stream().filter(s -> semester.equals(s.getSemester())).toList();

            // Build schedule map: subjectId -> ExamSchedule for O(1) lookup
            Map<Long, ExamSchedule> scheduleMap = new HashMap<>();
            scheduleRepo.findByExamCycle(cycle)
                    .forEach(es -> scheduleMap.put(es.getSubject().getSubjectId(), es));

            return ResponseEntity.ok(all.stream().map(s -> {
                ExamSchedule es = scheduleMap.get(s.getSubjectId());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("subjectId",   s.getSubjectId());
                m.put("name",        s.getName());
                m.put("subjectCode", s.getSubjectCode() != null ? s.getSubjectCode() : "");
                m.put("type",        s.getType() != null ? s.getType().name() : "");
                m.put("semester",    s.getSemester());
                m.put("department",  s.getDepartment() != null ? s.getDepartment().getDeptName() : "");
                m.put("deptId",      s.getDepartment() != null ? s.getDepartment().getDeptId() : null);
                m.put("examDate",    es != null && es.getExamDate() != null ? es.getExamDate().toString() : "");
                m.put("session",     es != null && es.getSession() != null ? es.getSession() : "");
                m.put("scheduled",   es != null && es.isScheduled());
                m.put("examCycle",   cycle);
                return m;
            }).toList());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/schedule/current")
    public ResponseEntity<?> getCurrentScheduleInfo() {
        String cycle = buildExamCycle();
        long total     = scheduleRepo.countByExamCycle(cycle);
        long scheduled = scheduleRepo.countScheduledByExamCycle(cycle);
        return ResponseEntity.ok(Map.of(
            "examCycle",    cycle,
            "academicYear", buildAcademicYear(),
            "totalEntries", total,
            "scheduled",    scheduled,
            "pending",      total - scheduled
        ));
    }

    // ══ HALL TICKET ═══════════════════════════════════════════════

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

    @GetMapping("/students")
    public ResponseEntity<?> getStudents(@RequestParam(required = false) Long deptId) {
        try {
            List<Student> all = studentRepo.findAll();
            if (deptId != null) all = all.stream()
                    .filter(s -> s.getDepartment() != null
                            && s.getDepartment().getDeptId().equals(deptId)).toList();

            int currentYear = LocalDate.now().getYear();

            List<Student> filtered = all.stream().filter(s -> {
                boolean isCompleted = s.getProgrammeStatus() == Student.ProgrammeStatus.COMPLETED;
                if (isCompleted) {
                    List<ExamRegistration> arrears = registrationRepository
                            .findByStudentStudentId(s.getStudentId()).stream()
                            .filter(r -> r.getType() == ExamRegistration.RegistrationType.ARREAR).toList();
                    boolean hasUnpaidArrear = arrears.stream().anyMatch(r -> r.getPayment() == null);
                    boolean hasPaidArrearRecent = arrears.stream()
                            .anyMatch(r -> r.getPayment() != null
                                    && r.getPayment().getStatus() == ExamFeePayment.PaymentStatus.COMPLETED
                                    && r.getPayment().getPaymentDate() != null
                                    && r.getPayment().getPaymentDate().getYear() >= currentYear - 1);
                    return hasUnpaidArrear || hasPaidArrearRecent;
                } else {
                    Integer sem = s.getCurrentSemester();
                    if (sem == null) return false;
                    return registrationRepository.findByStudentStudentId(s.getStudentId()).stream()
                            .anyMatch(r -> r.getPayment() != null
                                    && r.getType() == ExamRegistration.RegistrationType.SEMESTER
                                    && r.getSubject().getSemester() != null
                                    && r.getSubject().getSemester().equals(sem));
                }
            }).toList();

            return ResponseEntity.ok(filtered.stream().map(s -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("studentId",       s.getStudentId());
                String fullName = java.util.stream.Stream.of(s.getName(), s.getMiddleName(), s.getLastName())
                        .filter(n -> n != null && !n.isBlank())
                        .collect(java.util.stream.Collectors.joining(" "));
                m.put("name",            fullName);
                m.put("rollNo",          s.getRollNo());
                m.put("department",      s.getDepartment() != null ? s.getDepartment().getDeptName() : "—");
                m.put("deptId",          s.getDepartment() != null ? s.getDepartment().getDeptId() : null);
                m.put("semester",        s.getCurrentSemester() != null ? s.getCurrentSemester() : 1);
                m.put("attendance",      s.getAttendancePercentage() != null ? s.getAttendancePercentage() : 0.0);
                m.put("eligible",        s.getEligibilityStatus() == Student.EligibilityStatus.ELIGIBLE);
                boolean feePaid = isFeePaid(s.getStudentId());
                m.put("feePaid",         feePaid);
                m.put("canGenerate",     s.getEligibilityStatus() == Student.EligibilityStatus.ELIGIBLE && feePaid);
                m.put("programmeStatus", s.getProgrammeStatus() != null ? s.getProgrammeStatus().name() : "ACTIVE");
                boolean hasArrear = registrationRepository.findByStudentStudentId(s.getStudentId()).stream()
                        .anyMatch(r -> r.getType() == ExamRegistration.RegistrationType.ARREAR);
                m.put("hasArrear", hasArrear);
                return m;
            }).toList());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProfileById(@PathVariable Long id) {
        return controllerRepo.findById(id)
                .map(ec -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("controllerId", ec.getControllerId());
                    m.put("name",         ec.getName());
                    m.put("middleName",   ec.getMiddleName() != null ? ec.getMiddleName() : "");
                    m.put("lastName",     ec.getLastName() != null ? ec.getLastName() : "");
                    m.put("employeeId",   ec.getEmployeeId());
                    m.put("designation",  ec.getDesignation() != null ? ec.getDesignation() : "");
                    m.put("dateOfBirth",  ec.getDateOfBirth() != null ? ec.getDateOfBirth().toString() : "");
                    m.put("phone",        ec.getUser() != null && ec.getUser().getPhone() != null ? ec.getUser().getPhone() : "");
                    m.put("email",        ec.getUser() != null && ec.getUser().getEmail() != null ? ec.getUser().getEmail() : "");
                    m.put("photoUrl",     ec.getPhotoPath() != null && !ec.getPhotoPath().isBlank()
                                ? "/photos/view/" + Paths.get(ec.getPhotoPath()).getFileName().toString()
                                : "");
                    m.put("hasSignature", ec.getSignaturePath() != null && !ec.getSignaturePath().isBlank());
                    m.put("signatureUrl", ec.getSignaturePath() != null && !ec.getSignaturePath().isBlank()
                                ? "/photos/view/" + Paths.get(ec.getSignaturePath()).getFileName().toString()
                                : "");
                    m.put("gender",      ec.getGender() != null ? ec.getGender().name() : "");
                    m.put("bloodGroup",  ec.getBloodGroup() != null ? ec.getBloodGroup().name() : "");
                    return ResponseEntity.ok((Object) m);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/me")
    public ResponseEntity<?> getProfile(@RequestParam String email) {
        return controllerRepo.findByUserEmail(email)
                .map(ec -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("controllerId", ec.getControllerId());
                    m.put("name",         ec.getName());
                    m.put("middleName",   ec.getMiddleName() != null ? ec.getMiddleName() : "");
                    m.put("lastName",     ec.getLastName() != null ? ec.getLastName() : "");
                    m.put("employeeId",   ec.getEmployeeId());
                    m.put("designation",  ec.getDesignation() != null ? ec.getDesignation() : "");
                    m.put("dateOfBirth",  ec.getDateOfBirth() != null ? ec.getDateOfBirth().toString() : "");
                    m.put("phone",        ec.getUser() != null && ec.getUser().getPhone() != null ? ec.getUser().getPhone() : "");
                    m.put("email",        ec.getUser() != null && ec.getUser().getEmail() != null ? ec.getUser().getEmail() : "");
                    m.put("photoUrl",     ec.getPhotoPath() != null && !ec.getPhotoPath().isBlank()
                                ? "/photos/view/" + Paths.get(ec.getPhotoPath()).getFileName().toString()
                                : "");
                    m.put("hasSignature", ec.getSignaturePath() != null && !ec.getSignaturePath().isBlank());
                    m.put("signatureUrl", ec.getSignaturePath() != null && !ec.getSignaturePath().isBlank()
                                ? "/photos/view/" + Paths.get(ec.getSignaturePath()).getFileName().toString()
                                : "");
                    m.put("gender",      ec.getGender() != null ? ec.getGender().name() : "");
                    m.put("bloodGroup",  ec.getBloodGroup() != null ? ec.getBloodGroup().name() : "");
                    return ResponseEntity.ok((Object) m);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Helpers ───────────────────────────────────────────────────

    private boolean isFeePaid(Long studentId) {
        return paymentRepo.findByStudentStudentId(studentId).stream()
                .anyMatch(p -> p.getStatus() == ExamFeePayment.PaymentStatus.COMPLETED);
    }

    private String buildExamCycle() {
        LocalDate today = LocalDate.now();
        int month = today.getMonthValue(), year = today.getYear();
        if (month >= 10 || month <= 1) return "NOV/DEC " + (month >= 10 ? year : year - 1);
        return "APR/MAY " + year;
    }

    private String buildAcademicYear() {
        LocalDate today = LocalDate.now();
        int month = today.getMonthValue(), year = today.getYear();
        int startYear = (month <= 5) ? year - 1 : year;
        return startYear + "-" + (startYear + 1);
    }

    // ── Change password ───────────────────────────────────────────────────────
    @PostMapping("/{id}/change-password")
    public ResponseEntity<?> changePassword(@PathVariable Long id,
                                             @RequestBody Map<String, String> body) {
        try {
            ExamControllerAdmin ec = controllerRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Exam Controller not found."));
            if (ec.getUser() == null)
                return ResponseEntity.badRequest().body(Map.of("error", "No user account linked."));
            User user = ec.getUser();
            String curPw = body.get("currentPassword");
            String newPw = body.get("newPassword");
            if (curPw == null || curPw.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "Current password is required."));
            if (!encoder.matches(curPw, user.getPassword()))
                return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect."));
            if (newPw == null || newPw.trim().length() < 8)
                return ResponseEntity.badRequest().body(Map.of("error", "New password must be at least 8 characters."));
            user.setPassword(encoder.encode(newPw.trim()));
            userRepo.save(user);
            return ResponseEntity.ok(Map.of("success", true, "message", "Password changed successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Path resolveUploadDir() {
        Path p = Paths.get(uploadDir);
        if (p.isAbsolute()) return p;
        return Paths.get(System.getProperty("user.home")).resolve(uploadDir);
    }

    /** Resolves a stored absolute path to an existing File, with fallbacks for relative paths. */
    private java.io.File resolveFile(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return null;
        java.io.File f = new java.io.File(storedPath);
        if (f.exists()) return f;
        f = Paths.get(System.getProperty("user.home"), storedPath).toFile();
        if (f.exists()) return f;
        f = new java.io.File(System.getProperty("user.dir"), storedPath);
        if (f.exists()) return f;
        String filename = Paths.get(storedPath).getFileName().toString();
        f = Paths.get(System.getProperty("user.home"), uploadDir, filename).toFile();
        if (f.exists()) return f;
        f = Paths.get(System.getProperty("user.dir"), uploadDir, filename).toFile();
        if (f.exists()) return f;
        return null;
    }

    private String saveImage(MultipartFile file, String prefix) throws IOException {
        if (file == null || file.isEmpty()) throw new RuntimeException("No file provided.");
        Set<String> allowed = Set.of("image/jpeg","image/jpg","image/png","image/webp");
        if (!allowed.contains(file.getContentType())) throw new RuntimeException("Only JPG/PNG/WEBP allowed.");
        if (file.getSize() > 3 * 1024 * 1024) throw new RuntimeException("File must be under 3 MB.");
        Path dir = resolveUploadDir();
        Files.createDirectories(dir);
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().startsWith(prefix + "_"))
                  .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }
        // Always save signatures as PNG with transparent background
        boolean isSignature = prefix.contains("sig");
        String ext = isSignature ? ".png" :
                (file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")
                ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.')).toLowerCase()
                : ".jpg");
        Path dest = dir.resolve(prefix + "_" + System.currentTimeMillis() + ext);
        if (isSignature) {
            saveSignatureTransparent(file.getBytes(), dest);
        } else {
            Files.write(dest, file.getBytes());
        }
        return dest.toAbsolutePath().toString();
    }

    /** Removes near-white background from signature and saves as transparent PNG */
    private void saveSignatureTransparent(byte[] imageBytes, Path dest) throws IOException {
        java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(imageBytes);
        java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(bis);
        if (src == null) { Files.write(dest, imageBytes); return; }
        int w = src.getWidth(), h = src.getHeight();
        // Use RGB (no alpha) so iText renders correctly without black fill
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = out.createGraphics();
        g2d.setColor(java.awt.Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.dispose();
        int threshold = 240;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgba = src.getRGB(x, y);
                int r = (rgba >> 16) & 0xFF;
                int g = (rgba >> 8)  & 0xFF;
                int b = rgba         & 0xFF;
                if (r >= threshold && g >= threshold && b >= threshold) {
                    out.setRGB(x, y, 0xFFFFFF); // replace yellow/cream with white
                } else {
                    out.setRGB(x, y, (r << 16) | (g << 8) | b); // keep ink pixel
                }
            }
        }
        javax.imageio.ImageIO.write(out, "PNG", dest.toFile());
    }
}