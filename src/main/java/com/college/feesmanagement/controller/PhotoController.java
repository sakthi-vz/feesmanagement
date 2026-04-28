package com.college.feesmanagement.controller;

import com.college.feesmanagement.entity.*;
import com.college.feesmanagement.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/photos")
public class PhotoController {

    private final StudentRepository   studentRepository;
    private final HodRepository       hodRepository;
    private final AdminRepository     adminRepository;
    private final PrincipalRepository principalRepository;

    @Value("${upload.photos.dir:uploads/photos}")
    private String uploadDir;

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");
    private static final long MAX_SIZE = 2 * 1024 * 1024; // 2 MB

    public PhotoController(StudentRepository studentRepository,
                           HodRepository hodRepository,
                           AdminRepository adminRepository,
                           PrincipalRepository principalRepository) {
        this.studentRepository   = studentRepository;
        this.hodRepository       = hodRepository;
        this.adminRepository     = adminRepository;
        this.principalRepository = principalRepository;
    }

    /**
     * Returns an absolute Path for the upload directory.
     * Relative paths are resolved under user.home so the folder
     * is always the same regardless of Tomcat's working directory.
     */
    private Path resolveUploadDir() {
        Path p = Paths.get(uploadDir);
        if (p.isAbsolute()) return p;
        return Paths.get(System.getProperty("user.home")).resolve(uploadDir);
    }

    // ── Upload photo ──────────────────────────────────────────

    @PostMapping("/upload/student/{studentId}")
    public ResponseEntity<?> uploadStudentPhoto(@PathVariable Long studentId,
                                                 @RequestParam("photo") MultipartFile file) {
        try {
            String path = savePhoto(file, "student_" + studentId);
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));
            student.setPhotoPath(path);
            studentRepository.save(student);
            return ResponseEntity.ok(Map.of("success", true, "photoPath", path,
                    "photoUrl", "/photos/view/" + Paths.get(path).getFileName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/upload/hod/{hodId}")
    public ResponseEntity<?> uploadHodPhoto(@PathVariable Long hodId,
                                             @RequestParam("photo") MultipartFile file) {
        try {
            String path = savePhoto(file, "hod_" + hodId);
            Hod hod = hodRepository.findById(hodId)
                    .orElseThrow(() -> new RuntimeException("HOD not found"));
            hod.setPhotoPath(path);
            hodRepository.save(hod);
            return ResponseEntity.ok(Map.of("success", true, "photoPath", path,
                    "photoUrl", "/photos/view/" + Paths.get(path).getFileName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/upload/admin/{adminId}")
    public ResponseEntity<?> uploadAdminPhoto(@PathVariable Long adminId,
                                               @RequestParam("photo") MultipartFile file) {
        try {
            String path = savePhoto(file, "admin_" + adminId);
            Admin admin = adminRepository.findById(adminId)
                    .orElseThrow(() -> new RuntimeException("Admin not found"));
            admin.setPhotoPath(path);
            adminRepository.save(admin);
            return ResponseEntity.ok(Map.of("success", true, "photoPath", path,
                    "photoUrl", "/photos/view/" + Paths.get(path).getFileName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/upload/principal/{principalId}")
    public ResponseEntity<?> uploadPrincipalPhoto(@PathVariable Long principalId,
                                                   @RequestParam("photo") MultipartFile file) {
        try {
            String path = savePhoto(file, "principal_" + principalId);
            com.college.feesmanagement.entity.Principal principal = principalRepository.findById(principalId)
                    .orElseThrow(() -> new RuntimeException("Principal not found"));
            principal.setPhotoPath(path);
            principalRepository.save(principal);
            return ResponseEntity.ok(Map.of("success", true, "photoPath", path,
                    "photoUrl", "/photos/view/" + Paths.get(path).getFileName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Serve photo ───────────────────────────────────────────

    @GetMapping("/view/{filename:.+}")
    public ResponseEntity<Resource> viewPhoto(@PathVariable String filename) {
        try {
            Path filePath = resolveUploadDir().resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists())
                return ResponseEntity.notFound().build();

            String contentType = Files.probeContentType(filePath);
            // probeContentType returns null OR "application/octet-stream" on many Linux systems
            // even for valid image files — always use extension-based fallback for both cases.
            if (contentType == null || contentType.equals("application/octet-stream")) {
                String fname = filePath.getFileName().toString().toLowerCase();
                if      (fname.endsWith(".png"))  contentType = "image/png";
                else if (fname.endsWith(".webp")) contentType = "image/webp";
                else                              contentType = "image/jpeg";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private String savePhoto(MultipartFile file, String prefix) throws IOException {
        if (file == null || file.isEmpty())
            throw new RuntimeException("No file provided.");
        if (!ALLOWED_TYPES.contains(file.getContentType()))
            throw new RuntimeException("Only JPG, PNG, WEBP images are allowed.");
        if (file.getSize() > MAX_SIZE)
            throw new RuntimeException("Photo must be under 2 MB.");

        Path dir = resolveUploadDir();
        Files.createDirectories(dir);

        String ext      = getExtension(Objects.requireNonNull(file.getOriginalFilename()));
        String filename = prefix + "_" + System.currentTimeMillis() + ext;
        Path   dest     = dir.resolve(filename);

        // Delete old photo with same prefix
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().startsWith(prefix + "_"))
                  .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }

        Files.write(dest, file.getBytes());
        System.out.println("[PhotoController] Saved " + prefix + " → " + dest.toAbsolutePath());
        return dest.toAbsolutePath().toString();   // ← always store ABSOLUTE path
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot).toLowerCase() : ".jpg";
    }
}