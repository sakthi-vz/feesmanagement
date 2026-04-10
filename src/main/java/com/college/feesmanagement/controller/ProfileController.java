package com.college.feesmanagement.controller;

import com.college.feesmanagement.entity.*;
import com.college.feesmanagement.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/**
 * ProfileController — self-service profile update for all roles.
 * Every user can update their own: name, phone, DOB, photo.
 * Email and username cannot be changed (used for login/identity).
 */
@RestController
@RequestMapping("/profile")
public class ProfileController {

    private final StudentRepository        studentRepo;
    private final HodRepository            hodRepo;
    private final AdminRepository          adminRepo;
    private final ExamControllerRepository ecRepo;
    private final UserRepository           userRepo;

    @Value("${upload.photos.dir:uploads/photos}")
    private String uploadDir;

    private static final Set<String> ALLOWED = Set.of("image/jpeg","image/jpg","image/png","image/webp");
    private static final long MAX_BYTES = 2 * 1024 * 1024;

    public ProfileController(StudentRepository studentRepo, HodRepository hodRepo,
                              AdminRepository adminRepo, ExamControllerRepository ecRepo,
                              UserRepository userRepo) {
        this.studentRepo = studentRepo;
        this.hodRepo     = hodRepo;
        this.adminRepo   = adminRepo;
        this.ecRepo      = ecRepo;
        this.userRepo    = userRepo;
    }

    // ══ GET PROFILE ═══════════════════════════════════════════

    /** GET /profile/student/{studentId} */
    @GetMapping("/student/{id}")
    public ResponseEntity<?> getStudent(@PathVariable Long id) {
        return studentRepo.findById(id).map(s -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("studentId",   s.getStudentId());
            m.put("name",        s.getName());
            m.put("rollNo",      s.getRollNo());
            m.put("email",       s.getUser() != null ? s.getUser().getEmail() : "");
            m.put("phone",       s.getUser() != null ? s.getUser().getPhone() : "");
            m.put("dateOfBirth", s.getDateOfBirth() != null ? s.getDateOfBirth().toString() : "");
            m.put("department",  s.getDepartment() != null ? s.getDepartment().getDeptName() : "");
            m.put("semester",    s.getCurrentSemester());
            m.put("attendance",  s.getAttendancePercentage());
            m.put("photoPath",   s.getPhotoPath() != null ? s.getPhotoPath() : "");
            m.put("photoUrl",    photoUrl(s.getPhotoPath()));
            return ResponseEntity.ok((Object) m);
        }).orElse(ResponseEntity.notFound().build());
    }

    /** GET /profile/hod/{hodId} */
    @GetMapping("/hod/{id}")
    public ResponseEntity<?> getHod(@PathVariable Long id) {
        return hodRepo.findById(id).map(h -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("hodId",       h.getHodId());
            m.put("name",        h.getName());
            m.put("employeeId",  h.getEmployeeId());
            m.put("email",       h.getUser() != null ? h.getUser().getEmail() : "");
            m.put("phone",       h.getUser() != null ? h.getUser().getPhone() : "");
            m.put("dateOfBirth", h.getDateOfBirth() != null ? h.getDateOfBirth().toString() : "");
            m.put("department",  h.getDepartment() != null ? h.getDepartment().getDeptName() : "");
            m.put("photoUrl",    photoUrl(h.getPhotoPath()));
            return ResponseEntity.ok((Object) m);
        }).orElse(ResponseEntity.notFound().build());
    }

    /** GET /profile/admin/{adminId} */
    @GetMapping("/admin/{id}")
    public ResponseEntity<?> getAdmin(@PathVariable Long id) {
        return adminRepo.findById(id).map(a -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("adminId",     a.getAdminId());
            m.put("name",        a.getName());
            m.put("employeeId",  a.getEmployeeId());
            m.put("designation", a.getDesignation());
            m.put("email",       a.getUser() != null ? a.getUser().getEmail() : "");
            m.put("phone",       a.getUser() != null ? a.getUser().getPhone() : "");
            m.put("dateOfBirth", a.getDateOfBirth() != null ? a.getDateOfBirth().toString() : "");
            m.put("photoUrl",    photoUrl(a.getPhotoPath()));
            return ResponseEntity.ok((Object) m);
        }).orElse(ResponseEntity.notFound().build());
    }

    /** GET /profile/exam-controller/{controllerId} */
    @GetMapping("/exam-controller/{id}")
    public ResponseEntity<?> getExamController(@PathVariable Long id) {
        return ecRepo.findById(id).map(ec -> {
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("controllerId", ec.getControllerId());
            m.put("name",         ec.getName());
            m.put("employeeId",   ec.getEmployeeId());
            m.put("designation",  ec.getDesignation());
            m.put("email",        ec.getUser() != null ? ec.getUser().getEmail() : "");
            m.put("phone",        ec.getUser() != null ? ec.getUser().getPhone() : "");
            m.put("dateOfBirth",  ec.getDateOfBirth() != null ? ec.getDateOfBirth().toString() : "");
            m.put("photoUrl",     photoUrl(ec.getPhotoPath()));
            m.put("hasSignature", ec.getSignaturePath() != null && !ec.getSignaturePath().isBlank());
            return ResponseEntity.ok((Object) m);
        }).orElse(ResponseEntity.notFound().build());
    }

    // ══ UPDATE PROFILE ════════════════════════════════════════

    /** PUT /profile/student/{studentId} */
    @PutMapping("/student/{id}")
    public ResponseEntity<?> updateStudent(@PathVariable Long id,
                                            @RequestBody Map<String,String> body) {
        try {
            Student s = studentRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Student not found"));
            if (body.containsKey("name") && !body.get("name").isBlank())
                s.setName(body.get("name").trim());
            if (body.containsKey("dateOfBirth") && !body.get("dateOfBirth").isBlank())
                s.setDateOfBirth(LocalDate.parse(body.get("dateOfBirth")));
            if (body.containsKey("phone") && s.getUser() != null) {
                s.getUser().setPhone(body.get("phone").trim());
                userRepo.save(s.getUser());
            }
            studentRepo.save(s);
            return ResponseEntity.ok(Map.of("success", true, "message", "Profile updated successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** PUT /profile/hod/{hodId} */
    @PutMapping("/hod/{id}")
    public ResponseEntity<?> updateHod(@PathVariable Long id,
                                        @RequestBody Map<String,String> body) {
        try {
            Hod h = hodRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("HOD not found"));
            if (body.containsKey("name") && !body.get("name").isBlank())
                h.setName(body.get("name").trim());
            if (body.containsKey("dateOfBirth") && !body.get("dateOfBirth").isBlank())
                h.setDateOfBirth(LocalDate.parse(body.get("dateOfBirth")));
            if (body.containsKey("phone") && h.getUser() != null) {
                h.getUser().setPhone(body.get("phone").trim());
                userRepo.save(h.getUser());
            }
            hodRepo.save(h);
            return ResponseEntity.ok(Map.of("success", true, "message", "Profile updated successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** PUT /profile/admin/{adminId} */
    @PutMapping("/admin/{id}")
    public ResponseEntity<?> updateAdmin(@PathVariable Long id,
                                          @RequestBody Map<String,String> body) {
        try {
            Admin a = adminRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            if (body.containsKey("name") && !body.get("name").isBlank())
                a.setName(body.get("name").trim());
            if (body.containsKey("designation") && !body.get("designation").isBlank())
                a.setDesignation(body.get("designation").trim());
            if (body.containsKey("dateOfBirth") && !body.get("dateOfBirth").isBlank())
                a.setDateOfBirth(LocalDate.parse(body.get("dateOfBirth")));
            if (body.containsKey("phone") && a.getUser() != null) {
                a.getUser().setPhone(body.get("phone").trim());
                userRepo.save(a.getUser());
            }
            adminRepo.save(a);
            return ResponseEntity.ok(Map.of("success", true, "message", "Profile updated successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** PUT /profile/exam-controller/{controllerId} */
    @PutMapping("/exam-controller/{id}")
    public ResponseEntity<?> updateExamController(@PathVariable Long id,
                                                   @RequestBody Map<String,String> body) {
        try {
            ExamControllerAdmin ec = ecRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Exam Controller not found"));
            if (body.containsKey("name") && !body.get("name").isBlank())
                ec.setName(body.get("name").trim());
            if (body.containsKey("designation") && !body.get("designation").isBlank())
                ec.setDesignation(body.get("designation").trim());
            if (body.containsKey("dateOfBirth") && !body.get("dateOfBirth").isBlank())
                ec.setDateOfBirth(LocalDate.parse(body.get("dateOfBirth")));
            if (body.containsKey("phone") && ec.getUser() != null) {
                ec.getUser().setPhone(body.get("phone").trim());
                userRepo.save(ec.getUser());
            }
            ecRepo.save(ec);
            return ResponseEntity.ok(Map.of("success", true, "message", "Profile updated successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ══ PHOTO UPLOAD ══════════════════════════════════════════

    /** POST /profile/student/{id}/photo */
    @PostMapping("/student/{id}/photo")
    public ResponseEntity<?> uploadStudentPhoto(@PathVariable Long id,
                                                 @RequestParam("photo") MultipartFile file) {
        try {
            Student s = studentRepo.findById(id).orElseThrow();
            String path = savePhoto(file, "student_" + id);
            s.setPhotoPath(path);
            studentRepo.save(s);
            return ResponseEntity.ok(Map.of("success", true, "photoUrl", photoUrl(path)));
        } catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    /** POST /profile/hod/{id}/photo */
    @PostMapping("/hod/{id}/photo")
    public ResponseEntity<?> uploadHodPhoto(@PathVariable Long id,
                                             @RequestParam("photo") MultipartFile file) {
        try {
            Hod h = hodRepo.findById(id).orElseThrow();
            String path = savePhoto(file, "hod_" + id);
            h.setPhotoPath(path);
            hodRepo.save(h);
            return ResponseEntity.ok(Map.of("success", true, "photoUrl", photoUrl(path)));
        } catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    /** POST /profile/admin/{id}/photo */
    @PostMapping("/admin/{id}/photo")
    public ResponseEntity<?> uploadAdminPhoto(@PathVariable Long id,
                                               @RequestParam("photo") MultipartFile file) {
        try {
            Admin a = adminRepo.findById(id).orElseThrow();
            String path = savePhoto(file, "admin_" + id);
            a.setPhotoPath(path);
            adminRepo.save(a);
            return ResponseEntity.ok(Map.of("success", true, "photoUrl", photoUrl(path)));
        } catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    /** POST /profile/exam-controller/{id}/photo */
    @PostMapping("/exam-controller/{id}/photo")
    public ResponseEntity<?> uploadEcPhoto(@PathVariable Long id,
                                            @RequestParam("photo") MultipartFile file) {
        try {
            ExamControllerAdmin ec = ecRepo.findById(id).orElseThrow();
            String path = savePhoto(file, "controller_photo_" + id);
            ec.setPhotoPath(path);
            ecRepo.save(ec);
            return ResponseEntity.ok(Map.of("success", true, "photoUrl", photoUrl(path)));
        } catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    // ── Helpers ───────────────────────────────────────────────
    private String savePhoto(MultipartFile file, String prefix) throws IOException {
        if (file == null || file.isEmpty()) throw new RuntimeException("No file provided.");
        if (!ALLOWED.contains(file.getContentType())) throw new RuntimeException("Only JPG/PNG/WEBP allowed.");
        if (file.getSize() > MAX_BYTES) throw new RuntimeException("File must be under 2 MB.");
        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        try (var s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().startsWith(prefix + "_"))
             .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }
        String ext = Optional.ofNullable(file.getOriginalFilename())
                .filter(f -> f.contains(".")).map(f -> f.substring(f.lastIndexOf('.')).toLowerCase()).orElse(".jpg");
        Path dest = dir.resolve(prefix + "_" + System.currentTimeMillis() + ext);
        Files.write(dest, file.getBytes());
        return dest.toString();
    }

    private String photoUrl(String path) {
        if (path == null || path.isBlank()) return "";
        return "/photos/view/" + Paths.get(path).getFileName().toString();
    }
}