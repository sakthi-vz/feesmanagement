package com.college.feesmanagement.controller;

import com.college.feesmanagement.entity.*;
import com.college.feesmanagement.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    // ── Rate-limiting store: IP → [attempts, windowStart] ────────
    private static final int    MAX_LOGIN_ATTEMPTS = 10;
    private static final long   WINDOW_MS          = 60_000L; // 1 minute
    private final ConcurrentHashMap<String, long[]> loginAttempts = new ConcurrentHashMap<>();

    @Autowired private UserRepository       userRepository;
    @Autowired private StudentRepository    studentRepository;
    @Autowired private HodRepository        hodRepository;
    @Autowired private AdminRepository          adminRepository;
    @Autowired private ExamControllerRepository examControllerRepository;
    @Autowired private DepartmentRepository departmentRepository;

    // Use the Spring-managed bean — NOT a raw new BCryptPasswordEncoder()
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthController(BCryptPasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    // ═══════════════════════════════════════════════════════════════
    //  REGISTRATION
    // ═══════════════════════════════════════════════════════════════

    /** Public — students may self-register */
    @PostMapping("/register/student")
    public ResponseEntity<?> registerStudent(@RequestBody Map<String, Object> request) {
        try {
            String  rollNo   = (String) request.get("rollNo");
            String  name     = (String) request.get("name");
            String  email    = (String) request.get("email");
            String  phone       = (String) request.get("phone");
            String  dobStr      = (String) request.get("dateOfBirth");
            String  password    = (String) request.get("password");
            Long    deptId   = Long.valueOf(request.get("deptId").toString());

            if (rollNo == null || name == null || email == null || password == null || deptId == null)
                return ResponseEntity.badRequest().body(Map.of("error", "All fields are required"));

            if (password.length() < 8)
                return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 8 characters"));

            if (studentRepository.findByRollNo(rollNo).isPresent())
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Roll number already registered"));

            if (userRepository.findByEmail(email).isPresent())
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email already registered"));

            Department department = departmentRepository.findById(deptId)
                .orElseThrow(() -> new RuntimeException("Department not found"));

            User user = new User();
            user.setUsername(rollNo);
            user.setRollNo(rollNo);
            user.setPassword(passwordEncoder.encode(password));
            user.setRole(User.Role.STUDENT);
            user.setEmail(email);
            user.setPhone(phone);
            user = userRepository.save(user);

            Student student = new Student();
            student.setRollNo(rollNo);
            student.setName(name);
            student.setDepartment(department);
            student.setCurrentSemester(1);           // FIXED: always start at semester 1
            student.setAttendancePercentage(0.0);
            if (dobStr != null && !dobStr.isBlank()) {
                try { student.setDateOfBirth(LocalDate.parse(dobStr)); }
                catch (Exception ignored) {}
            }
            student.setUser(user);
            student = studentRepository.save(student);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message",   "Student registered successfully",
                "studentId", student.getStudentId(),
                "rollNo",    student.getRollNo(),
                "name",      student.getName(),
                "photoUploadUrl", "/photos/upload/student/" + student.getStudentId()
            ));

        } catch (Exception e) {
            log.error("Student registration failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Registration failed. Please try again."));
        }
    }

    /**
     * HOD registration — requires an existing admin's credentials in the
     * X-Admin-Username / X-Admin-Password headers to prevent self-service abuse.
     */
    @PostMapping("/register/hod")
    public ResponseEntity<?> registerHod(@RequestBody Map<String, Object> request,
                                          @RequestHeader(value = "X-Admin-Username", required = false) String adminUsername,
                                          @RequestHeader(value = "X-Admin-Password", required = false) String adminPassword) {
        // Verify admin credentials
        ResponseEntity<?> authErr = requireAdminAuth(adminUsername, adminPassword);
        if (authErr != null) return authErr;

        try {
            String username   = (String) request.get("username");
            String name       = (String) request.get("name");
            String employeeId = (String) request.get("employeeId");
            String email      = (String) request.get("email");
            String phone      = (String) request.get("phone");
            String dobStr     = (String) request.get("dateOfBirth");
            String password   = (String) request.get("password");
            Long   deptId     = Long.valueOf(request.get("deptId").toString());

            if (username == null || name == null || employeeId == null || email == null
                    || password == null || deptId == null)
                return ResponseEntity.badRequest().body(Map.of("error", "All fields are required"));

            if (password.length() < 8)
                return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 8 characters"));

            if (userRepository.findByUsername(username).isPresent())
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username already exists"));

            if (userRepository.findByEmail(email).isPresent())
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email already registered"));

            if (hodRepository.findByEmployeeId(employeeId).isPresent())
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Employee ID already registered"));

            Department department = departmentRepository.findById(deptId)
                .orElseThrow(() -> new RuntimeException("Department not found"));

            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password));
            user.setRole(User.Role.HOD);
            user.setEmail(email);
            user.setPhone(phone);
            user = userRepository.save(user);

            Hod hod = new Hod();
            hod.setEmployeeId(employeeId);
            hod.setName(name);
            hod.setDepartment(department);
            if (dobStr != null && !dobStr.isBlank()) {
                try { hod.setDateOfBirth(java.time.LocalDate.parse(dobStr)); } catch (Exception ignored) {}
            }
            hod.setUser(user);
            hod = hodRepository.save(hod);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message",    "HOD registered successfully",
                "hodId",      hod.getHodId(),
                "employeeId", hod.getEmployeeId(),
                "name",       hod.getName(),
                "photoUploadUrl", "/photos/upload/hod/" + hod.getHodId()
            ));

        } catch (Exception e) {
            log.error("HOD registration failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Registration failed. Please try again."));
        }
    }

    /**
     * Admin registration — also requires existing admin credentials.
     * On a fresh install with no admins yet, seed one via DB migration.
     */
    @PostMapping("/register/admin")
    public ResponseEntity<?> registerAdmin(@RequestBody Map<String, Object> request,
                                            @RequestHeader(value = "X-Admin-Username", required = false) String adminUsername,
                                            @RequestHeader(value = "X-Admin-Password", required = false) String adminPassword) {
        ResponseEntity<?> authErr = requireAdminAuth(adminUsername, adminPassword);
        if (authErr != null) return authErr;

        try {
            String username   = (String) request.get("username");
            String name       = (String) request.get("name");
            String employeeId = (String) request.get("employeeId");
            String designation = (String) request.get("designation");
            String email      = (String) request.get("email");
            String phone      = (String) request.get("phone");
            String dobStr     = (String) request.get("dateOfBirth");
            String password   = (String) request.get("password");

            if (username == null || name == null || employeeId == null || email == null || password == null)
                return ResponseEntity.badRequest().body(Map.of("error", "All fields are required"));

            if (password.length() < 8)
                return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 8 characters"));

            if (userRepository.findByUsername(username).isPresent())
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username already exists"));

            if (userRepository.findByEmail(email).isPresent())
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email already registered"));

            if (adminRepository.findByEmployeeId(employeeId).isPresent())
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Employee ID already registered"));

            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password));
            user.setRole(User.Role.ADMIN);
            user.setEmail(email);
            user.setPhone(phone);
            user = userRepository.save(user);

            Admin admin = new Admin();
            admin.setEmployeeId(employeeId);
            admin.setName(name);
            admin.setDesignation(designation);
            if (dobStr != null && !dobStr.isBlank()) {
                try { admin.setDateOfBirth(java.time.LocalDate.parse(dobStr)); } catch (Exception ignored) {}
            }
            admin.setUser(user);
            admin = adminRepository.save(admin);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message",    "Admin registered successfully",
                "adminId",    admin.getAdminId(),
                "employeeId", admin.getEmployeeId(),
                "name",       admin.getName()
            ));

        } catch (Exception e) {
            log.error("Admin registration failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Registration failed. Please try again."));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  LOGIN
    // ═══════════════════════════════════════════════════════════════

    @PostMapping("/login/student")
    public ResponseEntity<?> loginStudent(@RequestBody Map<String, String> credentials,
                                           HttpServletRequest httpReq) {
        if (isRateLimited(httpReq.getRemoteAddr()))
            return ResponseEntity.status(429).body(Map.of("error", "Too many login attempts. Please wait a minute."));

        try {
            String rollNo   = credentials.get("rollNo");
            String password = credentials.get("password");

            if (rollNo == null || password == null)
                return ResponseEntity.badRequest().body(Map.of("error", "Roll number and password required"));

            Optional<Student> studentOpt = studentRepository.findByRollNo(rollNo);
            if (studentOpt.isEmpty() || !passwordEncoder.matches(password, studentOpt.get().getUser().getPassword())) {
                recordAttempt(httpReq.getRemoteAddr());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid roll number or password"));
            }

            Student student = studentOpt.get();
            User    user    = student.getUser();

            // Return only what the UI actually needs — no phone, no raw IDs beyond what's required
            Map<String, Object> resp = new HashMap<>();
            resp.put("userId",             user.getId());
            resp.put("role",               user.getRole().toString());
            resp.put("studentId",          student.getStudentId());
            resp.put("rollNo",             student.getRollNo());
            resp.put("name",               student.getName());
            resp.put("deptId",             student.getDepartment().getDeptId());
            resp.put("deptName",           student.getDepartment().getDeptName());
            resp.put("currentSemester",    student.getCurrentSemester());
            resp.put("attendancePercentage", student.getAttendancePercentage());
            resp.put("eligibilityStatus",  student.getEligibilityStatus() != null
                                               ? student.getEligibilityStatus().toString() : "NOT_ELIGIBLE");
            resp.put("programmeStatus",    student.getProgrammeStatus() != null
                                               ? student.getProgrammeStatus().toString() : "ACTIVE");
            resp.put("dateOfBirth",        student.getDateOfBirth() != null
                                               ? student.getDateOfBirth().toString() : "");
            resp.put("photoPath",          student.getPhotoPath() != null
                                               ? student.getPhotoPath() : "");
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            log.error("Student login failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Login failed. Please try again."));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials,
                                    HttpServletRequest httpReq) {
        if (isRateLimited(httpReq.getRemoteAddr()))
            return ResponseEntity.status(429).body(Map.of("error", "Too many login attempts. Please wait a minute."));

        try {
            String loginId  = credentials.get("loginId");
            String password = credentials.get("password");

            if (loginId == null || password == null)
                return ResponseEntity.badRequest().body(Map.of("error", "Username and password required"));

            Optional<User> userOpt = userRepository.findByUsername(loginId);
            if (userOpt.isEmpty() || !passwordEncoder.matches(password, userOpt.get().getPassword())) {
                recordAttempt(httpReq.getRemoteAddr());
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid username or password"));
            }

            User user = userOpt.get();

            Map<String, Object> resp = new HashMap<>();
            resp.put("userId",   user.getId());
            resp.put("username", user.getUsername());
            resp.put("role",     user.getRole().toString());

            if (user.getRole() == User.Role.HOD) {
                Hod hod = hodRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("HOD profile not found"));
                resp.put("hodId",      hod.getHodId());
                resp.put("employeeId", hod.getEmployeeId());
                resp.put("name",       hod.getName());
                resp.put("deptId",     hod.getDepartment().getDeptId());
                resp.put("deptName",   hod.getDepartment().getDeptName());
                resp.put("dateOfBirth", hod.getDateOfBirth() != null ? hod.getDateOfBirth().toString() : "");
                resp.put("photoPath",   hod.getPhotoPath() != null ? hod.getPhotoPath() : "");
            } else if (user.getRole() == User.Role.ADMIN) {
                Admin admin = adminRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Admin profile not found"));
                resp.put("adminId",     admin.getAdminId());
                resp.put("employeeId",  admin.getEmployeeId());
                resp.put("name",        admin.getName());
                resp.put("designation", admin.getDesignation());
                resp.put("dateOfBirth", admin.getDateOfBirth() != null ? admin.getDateOfBirth().toString() : "");
                resp.put("photoPath",   admin.getPhotoPath() != null ? admin.getPhotoPath() : "");
            } else if (user.getRole() == User.Role.EXAM_CONTROLLER) {
                ExamControllerAdmin ec = examControllerRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new RuntimeException("Exam Controller profile not found"));
                resp.put("controllerId", ec.getControllerId());
                resp.put("employeeId",   ec.getEmployeeId());
                resp.put("name",         ec.getName());
                resp.put("designation",  ec.getDesignation() != null ? ec.getDesignation() : "Controller of Examinations");
                resp.put("dateOfBirth",  ec.getDateOfBirth() != null ? ec.getDateOfBirth().toString() : "");
                resp.put("photoPath",    ec.getPhotoPath() != null ? ec.getPhotoPath() : "");
                resp.put("hasSignature", ec.getSignaturePath() != null && !ec.getSignaturePath().isBlank());
            }

            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            log.error("Login failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Login failed. Please try again."));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  HELPERS — private, not exposed as endpoints
    // ═══════════════════════════════════════════════════════════════

    /** Verify caller is an existing admin — used to gate HOD/admin registration */
    private ResponseEntity<?> requireAdminAuth(String username, String password) {
        if (username == null || password == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Admin credentials required in X-Admin-Username / X-Admin-Password headers"));

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty() || userOpt.get().getRole() != User.Role.ADMIN
                || !passwordEncoder.matches(password, userOpt.get().getPassword()))
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Invalid admin credentials"));

        return null; // null = authorised
    }

    private boolean isRateLimited(String ip) {
        long now = Instant.now().toEpochMilli();
        long[] state = loginAttempts.getOrDefault(ip, new long[]{0, now});
        if (now - state[1] > WINDOW_MS) {
            loginAttempts.put(ip, new long[]{0, now});
            return false;
        }
        return state[0] >= MAX_LOGIN_ATTEMPTS;
    }

    private void recordAttempt(String ip) {
        long now = Instant.now().toEpochMilli();
        loginAttempts.compute(ip, (k, state) -> {
            if (state == null || now - state[1] > WINDOW_MS) return new long[]{1, now};
            state[0]++;
            return state;
        });
    }
}