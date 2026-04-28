package com.college.feesmanagement.controller;

import com.college.feesmanagement.entity.Principal;
import com.college.feesmanagement.entity.Student;
import com.college.feesmanagement.entity.User;
import com.college.feesmanagement.repository.PrincipalRepository;
import com.college.feesmanagement.repository.StudentRepository;
import com.college.feesmanagement.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/principal")
public class PrincipalController {

    private final PrincipalRepository    principalRepository;
    private final UserRepository         userRepo;
    private final BCryptPasswordEncoder  passwordEncoder;
    private final StudentRepository      studentRepository;

    @Value("${upload.photos.dir:uploads/photos}")
    private String uploadDir;

    public PrincipalController(PrincipalRepository principalRepository,
                                UserRepository userRepo,
                                BCryptPasswordEncoder passwordEncoder,
                                StudentRepository studentRepository) {
        this.principalRepository = principalRepository;
        this.userRepo            = userRepo;
        this.passwordEncoder     = passwordEncoder;
        this.studentRepository   = studentRepository;
    }

    private Path resolveUploadDir() {
        Path p = Paths.get(uploadDir);
        if (p.isAbsolute()) return p;
        return Paths.get(System.getProperty("user.home")).resolve(uploadDir);
    }

    // ── Upload signature ──────────────────────────────────────────────────────
    @PostMapping("/{id}/upload-signature")
    public ResponseEntity<?> uploadSignature(@PathVariable Long id,
                                              @RequestParam("signature") MultipartFile file) {
        try {
            Principal principal = principalRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Principal not found."));
            String path = saveImage(file, "principal_sig_" + id);
            principal.setSignaturePath(path);
            principalRepository.save(principal);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Signature uploaded successfully.",
                "signaturePath", path
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Upload photo ──────────────────────────────────────────────────────────
    @PostMapping("/{id}/upload-photo")
    public ResponseEntity<?> uploadPhoto(@PathVariable Long id,
                                          @RequestParam("photo") MultipartFile file) {
        try {
            Principal principal = principalRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Principal not found."));
            String path = saveImage(file, "principal_photo_" + id);
            principal.setPhotoPath(path);
            principalRepository.save(principal);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Photo uploaded successfully.",
                "photoPath", path,
                "photoUrl", "/photos/view/" + path
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Get profile ───────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<?> getProfile(@PathVariable Long id) {
        return principalRepository.findById(id)
                .map(p -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("principalId",  p.getPrincipalId());
                    m.put("name",         java.util.stream.Stream.of(p.getName(), p.getMiddleName(), p.getLastName()).filter(n -> n != null && !n.isBlank()).collect(java.util.stream.Collectors.joining(" ")));
                    m.put("middleName",   p.getMiddleName() != null ? p.getMiddleName() : "");
                    m.put("lastName",     p.getLastName() != null ? p.getLastName() : "");
                    m.put("employeeId",   p.getEmployeeId());
                    m.put("designation",  p.getDesignation() != null ? p.getDesignation() : "Principal");
                    m.put("dateOfBirth",  p.getDateOfBirth() != null ? p.getDateOfBirth().toString() : "");
                    m.put("phone",        p.getUser() != null && p.getUser().getPhone() != null ? p.getUser().getPhone() : "");
                    m.put("email",        p.getUser() != null && p.getUser().getEmail() != null ? p.getUser().getEmail() : "");
                    m.put("photoPath",    p.getPhotoPath() != null ? p.getPhotoPath() : "");
                    m.put("photoUrl",     p.getPhotoPath() != null && !p.getPhotoPath().isBlank()
                            ? "/photos/view/" + Paths.get(p.getPhotoPath()).getFileName().toString() : "");
                    m.put("hasSignature",  p.getSignaturePath() != null && !p.getSignaturePath().isBlank());
                    m.put("signatureUrl",  p.getSignaturePath() != null && !p.getSignaturePath().isBlank()
                            ? "/photos/view/" + Paths.get(p.getSignaturePath()).getFileName().toString() : "");
                    m.put("gender",       p.getGender() != null ? p.getGender().name() : "");
                    m.put("bloodGroup",   p.getBloodGroup() != null ? p.getBloodGroup().name() : "");
                    return ResponseEntity.ok((Object) m);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Get signature as base64 (used by COE preview) ─────────────────────────
    @GetMapping("/{id}/signature-base64")
    public ResponseEntity<?> getSignatureBase64(@PathVariable Long id) {
        try {
            Principal p = principalRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Principal not found."));
            String sigPath = p.getSignaturePath();
            if (sigPath == null || sigPath.isBlank())
                return ResponseEntity.ok(Map.of("dataUrl", ""));
            java.io.File f = resolveFile(sigPath);
            if (f == null || !f.exists())
                return ResponseEntity.ok(Map.of("dataUrl", ""));
            byte[] bytes = Files.readAllBytes(f.toPath());
            String fname = f.getName().toLowerCase();
            String mime  = fname.endsWith(".png") ? "image/png" : fname.endsWith(".webp") ? "image/webp" : "image/jpeg";
            String dataUrl = "data:" + mime + ";base64," + java.util.Base64.getEncoder().encodeToString(bytes);
            return ResponseEntity.ok(Map.of("dataUrl", dataUrl));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Update profile ────────────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<?> updateProfile(@PathVariable Long id,
                                            @RequestBody Map<String, String> body) {
        try {
            Principal p = principalRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Principal not found."));

            if (body.containsKey("name") && !body.get("name").isBlank())
                p.setName(body.get("name").trim());
            if (body.containsKey("middleName"))
                p.setMiddleName(body.get("middleName") != null ? body.get("middleName").trim() : null);
            if (body.containsKey("lastName"))
                p.setLastName(body.get("lastName") != null ? body.get("lastName").trim() : null);
            if (body.containsKey("designation") && !body.get("designation").isBlank())
                p.setDesignation(body.get("designation").trim());
            if (body.containsKey("dateOfBirth") && !body.get("dateOfBirth").isBlank())
                p.setDateOfBirth(LocalDate.parse(body.get("dateOfBirth")));

            if (p.getUser() != null) {
                User user = p.getUser();
                if (body.containsKey("phone"))
                    user.setPhone(body.get("phone") != null ? body.get("phone").trim() : null);
                // Password change
                String newPw = body.get("newPassword");
                if (newPw != null && !newPw.isBlank()) {
                    String curPw = body.get("currentPassword");
                    if (curPw == null || curPw.isBlank())
                        return ResponseEntity.badRequest().body(Map.of("error", "Current password is required to set a new password."));
                    if (!passwordEncoder.matches(curPw, user.getPassword()))
                        return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect."));
                    if (newPw.trim().length() < 8)
                        return ResponseEntity.badRequest().body(Map.of("error", "New password must be at least 8 characters."));
                    user.setPassword(passwordEncoder.encode(newPw.trim()));
                }
                userRepo.save(user);
            }

            principalRepository.save(p);
            return ResponseEntity.ok(Map.of("success", true, "message", "Profile updated successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Change password (dedicated endpoint) ─────────────────────────────────
    @PostMapping("/{id}/change-password")
    public ResponseEntity<?> changePassword(@PathVariable Long id,
                                             @RequestBody Map<String, String> body) {
        try {
            Principal p = principalRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Principal not found."));
            if (p.getUser() == null)
                return ResponseEntity.badRequest().body(Map.of("error", "No user account linked."));
            User user = p.getUser();
            String curPw = body.get("currentPassword");
            String newPw = body.get("newPassword");
            if (curPw == null || curPw.isBlank())
                return ResponseEntity.badRequest().body(Map.of("error", "Current password is required."));
            if (!passwordEncoder.matches(curPw, user.getPassword()))
                return ResponseEntity.badRequest().body(Map.of("error", "Current password is incorrect."));
            if (newPw == null || newPw.trim().length() < 8)
                return ResponseEntity.badRequest().body(Map.of("error", "New password must be at least 8 characters."));
            user.setPassword(passwordEncoder.encode(newPw.trim()));
            userRepo.save(user);
            return ResponseEntity.ok(Map.of("success", true, "message", "Password changed successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Attendance (all active students, no payment filter) ────────────────
    @GetMapping("/attendance")
    public ResponseEntity<?> getAllAttendance() {
        try {
            List<java.util.Map<String,Object>> result = studentRepository.findAll().stream()
                .filter(s -> s.getProgrammeStatus() == null
                          || s.getProgrammeStatus() == Student.ProgrammeStatus.ACTIVE)
                .map(s -> {
                    java.util.Map<String,Object> m = new java.util.LinkedHashMap<>();
                    m.put("studentId",      s.getStudentId());
                    String _mid  = s.getMiddleName();
                    String _last = s.getLastName();
                    String _raw  = s.getName();
                    String _first = (_raw == null || _raw.isBlank()) ? "" :
                        (!_raw.contains(" ")) ? _raw.trim() :
                        ((_mid != null && !_mid.isBlank() && _raw.contains(_mid.trim())) ||
                         (_last != null && !_last.isBlank() && _raw.contains(_last.trim())))
                        ? _raw.trim().split("\\s+")[0] : _raw.trim();
                    String fullName = java.util.stream.Stream
                        .of(_first, _mid, _last)
                        .filter(n -> n != null && !n.isBlank())
                        .collect(java.util.stream.Collectors.joining(" "));
                    m.put("name",           fullName);
                    m.put("rollNo",         s.getRollNo());
                    m.put("department",     s.getDepartment() != null ? s.getDepartment().getDeptName() : "—");
                    m.put("semester",       s.getCurrentSemester() != null ? s.getCurrentSemester() : 1);
                    m.put("attendance",     s.getAttendancePercentage() != null ? s.getAttendancePercentage() : 0.0);
                    m.put("programmeStatus",s.getProgrammeStatus() != null ? s.getProgrammeStatus().name() : "ACTIVE");
                    return m;
                })
                .collect(java.util.stream.Collectors.toList());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private String saveImage(MultipartFile file, String prefix) throws Exception {
        if (file == null || file.isEmpty()) throw new RuntimeException("No file provided.");
        boolean isSignature = prefix.contains("sig");
        String ext = isSignature ? ".png" : getExt(file.getOriginalFilename());
        String filename = prefix + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Path dir = resolveUploadDir();
        Files.createDirectories(dir);
        try (var stream = Files.list(dir)) {
            stream.filter(f -> f.getFileName().toString().startsWith(prefix + "_"))
                  .forEach(f -> { try { Files.deleteIfExists(f); } catch (Exception ignored) {} });
        }
        Path dest = dir.resolve(filename);
        if (isSignature) {
            saveSignatureTransparent(file.getBytes(), dest);
        } else {
            Files.write(dest, file.getBytes());
        }
        System.out.println("[PrincipalController] Saved " + prefix + " → " + dest.toAbsolutePath());
        return filename;
    }

    /** Removes near-white background from signature and saves as transparent PNG */
    private void saveSignatureTransparent(byte[] imageBytes, Path dest) throws Exception {
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

    private String getExt(String name) {
        if (name == null) return ".png";
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot).toLowerCase() : ".png";
    }

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
}