package com.college.feesmanagement.service;

import com.college.feesmanagement.entity.*;
import com.college.feesmanagement.repository.*;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.AreaBreak;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class HallTicketService {

    private final StudentRepository          studentRepository;
    private final ExamRegistrationRepository registrationRepository;
    private final ExamFeePaymentRepository   paymentRepository;
    private final ExamControllerRepository   controllerRepository;
    private final ExamScheduleRepository     scheduleRepository;
    private final PrincipalRepository        principalRepository;

    @Value("${upload.photos.dir:uploads/photos}")
    private String uploadDir;

    private static final float B = 0.5f; // border width

    public HallTicketService(StudentRepository studentRepository,
                              ExamRegistrationRepository registrationRepository,
                              ExamFeePaymentRepository paymentRepository,
                              ExamControllerRepository controllerRepository,
                              ExamScheduleRepository scheduleRepository,
                              PrincipalRepository principalRepository) {
        this.studentRepository      = studentRepository;
        this.registrationRepository = registrationRepository;
        this.paymentRepository      = paymentRepository;
        this.controllerRepository   = controllerRepository;
        this.scheduleRepository     = scheduleRepository;
        this.principalRepository    = principalRepository;
    }

    /**
     * Resolves a stored signature/photo path to an existing File.
     * New uploads store absolute paths, so case 1 will always hit.
     * Cases 2-5 are fallbacks for any paths saved before this fix.
     *
     * Resolution order:
     *   1. As-is (works for absolute paths — all new uploads)
     *   2. Relative to user.home  (old save location on most systems)
     *   3. Relative to user.dir
     *   4. Filename only inside uploadDir under user.home
     *   5. Filename only inside uploadDir under user.dir
     */
    private File resolveFile(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return null;

        // 1. absolute path (all new uploads land here)
        File f = new File(storedPath);
        if (f.exists()) return f;

        // 2. relative to user.home
        f = Paths.get(System.getProperty("user.home"), storedPath).toFile();
        if (f.exists()) return f;

        // 3. relative to user.dir
        f = new File(System.getProperty("user.dir"), storedPath);
        if (f.exists()) return f;

        // 4. filename only, inside uploadDir under user.home
        String filename = Paths.get(storedPath).getFileName().toString();
        f = Paths.get(System.getProperty("user.home"), uploadDir, filename).toFile();
        if (f.exists()) return f;

        // 5. filename only, inside uploadDir under user.dir
        f = Paths.get(System.getProperty("user.dir"), uploadDir, filename).toFile();
        if (f.exists()) return f;

        System.err.println("[HallTicket] Cannot resolve: " + storedPath
                + " | user.home=" + System.getProperty("user.home")
                + " | user.dir="  + System.getProperty("user.dir")
                + " | uploadDir=" + uploadDir);
        return null;
    }

    // ── Eligibility ───────────────────────────────────────────
    public void checkEligibility(Long studentId) {
        Student s = studentRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found."));
        // Passed-out students sit only for arrears — skip attendance gate,
        // they already graduated. Just verify they have a paid exam fee.
        if (!s.isCompleted()) {
            if (s.getEligibilityStatus() == Student.EligibilityStatus.NOT_ELIGIBLE)
                throw new RuntimeException("Not eligible. Attendance "
                        + s.getAttendancePercentage() + "% is below 75%.");
        }
        boolean paid = paymentRepository.findByStudentStudentId(studentId).stream()
                .anyMatch(p -> p.getStatus() == ExamFeePayment.PaymentStatus.COMPLETED);
        if (!paid) throw new RuntimeException("Exam fee not paid.");
    }

    // ── Generate ──────────────────────────────────────────────
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public byte[] generateHallTicket(Long studentId) {
        checkEligibility(studentId);
        Student student = studentRepository.findById(studentId).orElseThrow();
        Integer currentSem = student.getCurrentSemester();

        boolean isPassedOut = student.isCompleted(); // ProgrammeStatus.COMPLETED
        // For passed-out students, the "final semester" they completed
        // (currentSemester still holds 8 after programme completion).
        // We must ONLY show genuine ARREAR registrations and must NEVER
        // include subjects that belong to the semester they just finished.
        Integer finalSem = isPassedOut ? student.getCurrentSemester() : null;

        List<ExamRegistration> regs = registrationRepository
                .findByStudentStudentId(studentId).stream()
                .filter(r -> r.getPayment() != null
                          && r.getPayment().getStatus() == ExamFeePayment.PaymentStatus.COMPLETED)
                .filter(r -> {
                    if (isPassedOut) {
                        // Passed-out: ONLY genuine arrears paid in this exam cycle,
                        // NOT subjects from the semester they already completed
                        return r.getType() == ExamRegistration.RegistrationType.ARREAR
                                && currentSem.equals(r.getPayment().getSemester())
                                && (r.getSubject().getSemester() == null
                                    || !r.getSubject().getSemester().equals(finalSem));
                    }
                    // Active student: only registrations paid in the current exam cycle
                    if (!currentSem.equals(r.getPayment().getSemester())) return false;
                    if (r.getType() == ExamRegistration.RegistrationType.ARREAR) return true;
                    // SEMESTER type: show current-semester subjects AND HOD-added extra subjects
                    // (extra subjects may belong to a different semester in the subject table)
                    return r.getType() == ExamRegistration.RegistrationType.SEMESTER;
                })
                .sorted(Comparator
                        .comparing((ExamRegistration r) -> r.getType().name())
                        .thenComparing(r -> r.getSubject().getSemester() != null
                                ? r.getSubject().getSemester() : 0))
                .toList();

        if (regs.isEmpty())
            throw new RuntimeException(isPassedOut
                ? "No paid arrear registrations found. Passed-out students can only appear for arrear subjects."
                : "No paid exam registrations found for current semester.");

        // Fetch controller fresh — pick one with signature if available
        List<ExamControllerAdmin> allControllers = controllerRepository.findAll();
        ExamControllerAdmin controller = allControllers.stream()
                .filter(e -> e.getSignaturePath() != null && !e.getSignaturePath().isBlank())
                .findFirst()
                .orElse(allControllers.isEmpty() ? null : allControllers.get(0));
        System.out.println("[HallTicket] Controller sigPath: "
                + (controller != null ? controller.getSignaturePath() : "none"));

        // Fetch principal with signature if available
        List<Principal> allPrincipals = principalRepository.findAll();
        Principal principal = allPrincipals.stream()
                .filter(p -> p.getSignaturePath() != null && !p.getSignaturePath().isBlank())
                .findFirst()
                .orElse(allPrincipals.isEmpty() ? null : allPrincipals.get(0));

        return buildPdf(student, regs, controller, principal, currentSem);
    }

    // ── PDF Builder ───────────────────────────────────────────
    private byte[] buildPdf(Student student, List<ExamRegistration> regs,
                             ExamControllerAdmin ec, Principal principal, Integer currentSem) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            PdfWriter   writer = new PdfWriter(baos);
            PdfDocument pdf    = new PdfDocument(writer);
            // Narrow margins so everything fits on one page
            Document doc = new Document(pdf, PageSize.A4);
            doc.setMargins(12, 18, 12, 18);

            // ═════════════════════════════════════════════════
            //  PAGE 1  —  HALL TICKET
            //  Target: header + details + 30-row table + footer
            //  + signatures — all on one A4 page
            // ═════════════════════════════════════════════════

            // ── HEADER — inside bordered box ──────────────────
            Table hdrBox = new Table(UnitValue.createPercentArray(new float[]{1}))
                    .useAllAvailableWidth().setMarginBottom(0);
            Cell hdrCell = new Cell()
                    .setBorder(new SolidBorder(ColorConstants.BLACK, B))
                    .setPaddingTop(4).setPaddingBottom(4)
                    .setPaddingLeft(8).setPaddingRight(8);
            hdrCell.add(new Paragraph("MOHAMED SATHAK")
                    .setFontSize(13).setBold().setTextAlignment(TextAlignment.CENTER).setMarginBottom(0));
            hdrCell.add(new Paragraph("A.J. COLLEGE OF ENGINEERING")
                    .setFontSize(13).setBold().setTextAlignment(TextAlignment.CENTER).setMarginBottom(1));
            hdrCell.add(new Paragraph("(An Autonomous Institution)")
                    .setFontSize(8.5f).setTextAlignment(TextAlignment.CENTER).setMarginBottom(0));
            hdrCell.add(new Paragraph("Approved by AICTE, New Delhi & Affiliated to Anna University, Chennai")
                    .setFontSize(7.5f).setTextAlignment(TextAlignment.CENTER).setMarginBottom(2));
            hdrCell.add(new Paragraph("END SEMESTER EXAMINATIONS \u2013 " + getExamPeriod())
                    .setFontSize(9.5f).setBold().setTextAlignment(TextAlignment.CENTER).setMarginBottom(0));
            hdrCell.add(new Paragraph("HALL TICKET (Page 1/1)")
                    .setFontSize(9.5f).setBold().setTextAlignment(TextAlignment.CENTER).setMarginBottom(0));
            hdrBox.addCell(hdrCell);
            doc.add(hdrBox);
            doc.add(new Paragraph("").setMarginBottom(2).setMarginTop(0));

            // ── DETAILS TABLE ──────────────────────────────────
            // Columns: label | value | label | value | photo(rowspan3)
            Table det = new Table(UnitValue.createPercentArray(
                    new float[]{1.5f, 2.2f, 1.3f, 1.4f, 1.4f}))
                    .useAllAvailableWidth();

            // Row 1
            det.addCell(dc("Register Number :", true));
            det.addCell(dc(student.getRollNo(), false));
            det.addCell(dc("Current Semester :", true));
            det.addCell(dc(String.valueOf(
                    student.getCurrentSemester() != null ? student.getCurrentSemester() : "—"), false));
            // Photo rowspan=3
            Cell photo = new Cell(3, 1)
                    .setBorder(new SolidBorder(ColorConstants.BLACK, B))
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setTextAlignment(TextAlignment.CENTER).setPadding(2);
            if (student.getPhotoPath() != null && !student.getPhotoPath().isBlank()) {
                try {
                    File f = resolveFile(student.getPhotoPath());
                    if (f != null && f.exists())
                        photo.add(new Image(ImageDataFactory.create(f.getAbsolutePath()))
                                .setWidth(52).setHeight(64)
                                .setHorizontalAlignment(HorizontalAlignment.CENTER));
                    else photo.add(new Paragraph("Photo").setFontSize(8).setFontColor(ColorConstants.GRAY));
                } catch (Exception ignored) {
                    photo.add(new Paragraph("Photo").setFontSize(8).setFontColor(ColorConstants.GRAY));
                }
            } else {
                photo.add(new Paragraph("Photo").setFontSize(8).setFontColor(ColorConstants.GRAY));
            }
            det.addCell(photo);

            // Row 2
            det.addCell(dc("Name :", true));
            String fullName = java.util.stream.Stream.of(
                    student.getName(),
                    student.getMiddleName(),
                    student.getLastName())
                .filter(n -> n != null && !n.isBlank())
                .collect(java.util.stream.Collectors.joining(" "))
                .toUpperCase();
            det.addCell(dc(fullName, false));
            det.addCell(dc("D.O.B :", true));
            String dob = student.getDateOfBirth() != null
                    ? student.getDateOfBirth().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) : "—";
            det.addCell(dc(dob, false));

            // Row 3: Degree & Branch colspan=3
            det.addCell(dc("Degree & Branch :", true));
            det.addCell(new Cell(1, 3)
                    .add(new Paragraph("B.E - " + (student.getDepartment() != null
                            ? student.getDepartment().getDeptName() : "—")).setFontSize(9))
                    .setBorder(new SolidBorder(ColorConstants.BLACK, B)).setPadding(4));
            doc.add(det);

            // ── SUBJECT TABLE ──────────────────────────────────
            // ROW HEIGHT = 14pt → 30 rows = 420pt
            // A4 usable height ≈ 842 - 36 margin = 806pt
            // Header≈65 + details≈75 + footer≈20 + sigs≈40 = 200pt
            // 806 - 200 = 606pt available for table → 606/30 = 20.2pt per row ✓
            Table sub = new Table(UnitValue.createPercentArray(
                    new float[]{0.5f, 1.1f, 4.2f, 1.3f, 0.7f}))
                    .useAllAvailableWidth()
                    .setMarginTop(1);

            // Header
            for (String h : new String[]{"Sem", "Sub. Code", "Subject Name", "Date", "Session"})
                sub.addHeaderCell(new Cell()
                        .add(new Paragraph(h).setFontSize(9).setBold()
                                .setTextAlignment(TextAlignment.CENTER))
                        .setBorder(new SolidBorder(ColorConstants.BLACK, B))
                        .setPaddingTop(3).setPaddingBottom(3));

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yy");

            // Data rows
            for (ExamRegistration reg : regs) {
                Subject s2 = reg.getSubject();
                // If subject has no semester number, use the student's current semester
                // (e.g. Internship or HOD-added extras not tied to a specific semester)
                String sem  = s2.getSemester() != null ? String.valueOf(s2.getSemester()) : String.valueOf(currentSem);
                // Look up exam date + session from ExamSchedule table (not subject)
                String cycle = getExamCycle();
                java.util.Optional<ExamSchedule> es = scheduleRepository
                        .findBySubjectSubjectIdAndExamCycle(s2.getSubjectId(), cycle);
                String date = es.map(e -> e.getExamDate() != null ? e.getExamDate().format(fmt) : "").orElse("");
                String sess = es.map(e -> e.getSession() != null ? e.getSession() : "").orElse("");

                sub.addCell(sc(sem,  true));
                sub.addCell(sc(s2.getSubjectCode() != null ? s2.getSubjectCode() : "—", true));
                sub.addCell(sc(s2.getName(), false));
                sub.addCell(sc(date, true));
                sub.addCell(sc(sess, true));
            }

            // Empty rows up to 30
            int empty = Math.max(0, 30 - regs.size());
            for (int i = 0; i < empty; i++)
                for (int j = 0; j < 5; j++)
                    sub.addCell(new Cell()
                            .add(new Paragraph("\u00A0").setFontSize(8))
                            .setBorderTop(Border.NO_BORDER).setBorderBottom(Border.NO_BORDER)
                            .setBorderLeft(new SolidBorder(ColorConstants.BLACK, B))
                            .setBorderRight(new SolidBorder(ColorConstants.BLACK, B))
                            .setHeight(13));

            doc.add(sub);

            // ── FOOTER + SIGNATURE — single keepTogether block ──
            String ecDes  = ec != null && ec.getDesignation() != null
                    ? ec.getDesignation() : "Controller of Examinations";
            String sigPath = ec != null ? ec.getSignaturePath() : null;

            // Footer row
            Table foot = new Table(UnitValue.createPercentArray(new float[]{10f, 1f}))
                    .useAllAvailableWidth().setMarginTop(0).setKeepTogether(true);
            foot.addCell(new Cell()
                    .add(new Paragraph("No of Subjects Registered:  "
                            + "(Page 1/1 Hall Ticket contain per Page Maximum 30 Subjects only)")
                            .setFontSize(7.5f))
                    .setBorder(new SolidBorder(ColorConstants.BLACK, B)).setPadding(2));
            foot.addCell(new Cell()
                    .add(new Paragraph(String.valueOf(regs.size())).setFontSize(8.5f).setBold()
                            .setTextAlignment(TextAlignment.CENTER))
                    .setBorderTop(new SolidBorder(ColorConstants.BLACK, B))
                    .setBorderBottom(new SolidBorder(ColorConstants.BLACK, B))
                    .setBorderRight(new SolidBorder(ColorConstants.BLACK, B))
                    .setBorderLeft(Border.NO_BORDER).setPadding(2));
            doc.add(foot);

            // Signature row
            String principalDes = principal != null && principal.getDesignation() != null
                    ? principal.getDesignation() : "Principal";
            String principalSigPath = principal != null ? principal.getSignaturePath() : null;

            Table sig = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                    .useAllAvailableWidth().setMarginTop(0).setKeepTogether(true);
            sig.addCell(sigC("Signature of the Candidate", null, false));
            sig.addCell(sigC(ecDes, sigPath, sigPath != null && !sigPath.isBlank()));
            sig.addCell(sigC(principalDes + " with seal", principalSigPath, principalSigPath != null && !principalSigPath.isBlank()));
            doc.add(sig);
            // ═════════════════════════════════════════════════
            //  PAGE 2  —  INSTRUCTIONS
            // ═════════════════════════════════════════════════
            doc.add(new AreaBreak());

            // ── Page 2: wrap ALL content in a bordered table cell ──
            // This is the only reliable way to get a margin box in iText.
            // The outer table fills the page (using page 1 margins 12,18,12,18),
            // and the single cell has padding=28 and a solid border — so content
            // is always inside the box regardless of margins.
            Table page2Box = new Table(UnitValue.createPercentArray(new float[]{1}))
                    .useAllAvailableWidth()
                    .setHeight(UnitValue.createPercentValue(100));

            // Build all content as a single cell
            Cell boxCell = new Cell()
                    .setBorder(new SolidBorder(ColorConstants.BLACK, 1f))
                    .setPadding(24f);

            // Header
            boxCell.add(new Paragraph("Mohamed Sathak A J College of Engineering, Chennai 603103")
                    .setFontSize(12).setBold().setTextAlignment(TextAlignment.CENTER).setMarginBottom(2).setMarginTop(0));
            boxCell.add(new Paragraph("(An Autonomous Institution)")
                    .setFontSize(10).setTextAlignment(TextAlignment.CENTER).setMarginBottom(6).setMarginTop(0));
            boxCell.add(new Paragraph("Instructions to the Candidates")
                    .setFontSize(11).setBold().setTextAlignment(TextAlignment.CENTER).setMarginBottom(10).setMarginTop(0));

            String[] ins = {
                "Dress code: Only formal dress is permitted for both Boys and Girls (Multi packeted pants, Track Pant, Shorts, Round Neck T shirts, Printed Shirt / T shirt, Full hand shirts and Shoes are strictly prohibited)",
                "Candidate should ensure that they receive their hall tickets at least two days prior to the examinations, provided they have adequate attendance and paid required examination fees.",
                "Bring their hall tickets on all days of the examination failing which he / she will not be admitted to the examination hall.",
                "If the hall ticket is lost, duplicate hall ticket may be issued by obtaining a declaration from the candidate duly recommended by the head of the department after levying the prescribed fee.",
                "The candidates should enter the examination hall at least 15 minutes before the commencement of examination.",
                "Candidates are permitted to use only blue or black pens for writing examinations. No other colored pens are allowed for writing the exam.",
                "Logarithm tables, calculators and other drawing equipment may be allowed only if they are required for answering questions and the question paper contains a note to this effect.",
                "Candidate shall not carry any written / printed matter, any paper material, cell phone, pen drive, ipad, programmable calculator, any electronic gadgets, any unauthorized data sheet / table into the examination hall and if anything is found, he / she shall be liable for disciplinary action.",
                "Instances of malpractice such as copying using manuscripts, copying from other candidates, smuggling of answer books, indecent behaviour in the examination room, use of unfair means etc., are liable to be punished as per college rules.",
                "Instructions given in the answer books and question paper should be strictly followed. Before proceeding to answer the paper, the candidate should fill the necessary details required for the answer booklet and question paper.",
                "Students are not permitted to write any identification mark anywhere inside the answer paper. If a candidate writes his / her register number on any part of the answer book / sheets other than the one provided for or puts any special mark or writes anything which may disclose, in any way, the identity of the candidate / college, he / she will render himself / herself liable for disciplinary action.",
                "Student should not write anything on the Hall ticket and question paper other than his/her register number. Any other writings in the Question Paper / Hall ticket is prohibited and punishable.",
                "Strict silence should be maintained in the examination hall. Candidates are not allowed to get clarification from other students. In case of any doubt, they can seek the help of the invigilator.",
                "No Candidate shall pass any part or whole of answer papers or question papers to any other candidate. No candidate shall allow another candidate to copy from his / her answer paper or copy from the answer paper of another candidate. If found committing such malpractice, the involved candidates shall be liable for disciplinary action.",
                "They should return all answer books before leaving the room. Candidates are not permitted to leave the examination hall without the permission of the invigilator during the course of the examination.",
                "Candidates are forbidden to ask questions of any kind during the examination related to the question papers, whether as explanation of meaning or correction of typographical errors.",
                "The rule that candidates should not be allowed to leave the hall before 2.30 hours from the commencement of the examination should be followed.",
                "Candidates shall be permitted to leave the examination hall only after his / her answer book is taken care of by the invigilator on duty in."
            };

            for (int n = 0; n < ins.length; n++) {
                boxCell.add(new Paragraph((n+1) + ".  " + ins[n])
                        .setFontSize(9.5f).setMarginBottom(4).setMarginTop(0)
                        .setMarginLeft(16).setFirstLineIndent(-16));
            }

            // Controller of Examination footer
            Table stamp = new Table(UnitValue.createPercentArray(new float[]{1,1})).useAllAvailableWidth()
                    .setMarginTop(6);
            stamp.addCell(new Cell().setBorder(Border.NO_BORDER));
            stamp.addCell(new Cell()
                    .add(new Paragraph("Controller of Examination").setFontSize(9).setBold()
                            .setTextAlignment(TextAlignment.RIGHT))
                    .setBorder(Border.NO_BORDER));
            boxCell.add(stamp);

            page2Box.addCell(boxCell);
            doc.add(page2Box);

            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate hall ticket: " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private void p(Document doc, String text, float size, boolean bold,
                   TextAlignment align, float marginBottom) {
        Paragraph para = new Paragraph(text).setFontSize(size).setTextAlignment(align)
                .setMarginBottom(marginBottom).setMarginTop(0);
        if (bold) para.setBold();
        doc.add(para);
    }

    /** Details table cell */
    private Cell dc(String text, boolean label) {
        Paragraph para = new Paragraph(text).setFontSize(9);
        if (label) para.setBold();
        return new Cell().add(para)
                .setBorder(new SolidBorder(ColorConstants.BLACK, B))
                .setPadding(3).setMinHeight(18);
    }

    /** Subject table data cell */
    private Cell sc(String text, boolean center) {
        return new Cell()
                .add(new Paragraph(text != null ? text : "").setFontSize(9)
                        .setTextAlignment(center ? TextAlignment.CENTER : TextAlignment.LEFT))
                .setBorderTop(Border.NO_BORDER).setBorderBottom(Border.NO_BORDER)
                .setBorderLeft(new SolidBorder(ColorConstants.BLACK, B))
                .setBorderRight(new SolidBorder(ColorConstants.BLACK, B))
                .setPaddingTop(1).setPaddingBottom(1).setPaddingLeft(3).setPaddingRight(3)
                .setMinHeight(14);
    }

    /** Signature cell — works for both controller and principal */
    private Cell sigC(String label, String sigImagePath, boolean hasSignature) {
        Cell cell = new Cell()
                .setBorder(new SolidBorder(ColorConstants.BLACK, B))
                .setHeight(55f)   // Fixed height — all 3 cells equal
                .setVerticalAlignment(VerticalAlignment.BOTTOM)
                .setTextAlignment(TextAlignment.CENTER)
                .setPaddingTop(2).setPaddingBottom(3)
                .setPaddingLeft(3).setPaddingRight(3);

        if (hasSignature && sigImagePath != null && !sigImagePath.isBlank()) {
            try {
                File f = resolveFile(sigImagePath);
                if (f != null && f.exists()) {
                    byte[] sigBytes = cleanSignatureBackground(f);
                    Image sig = new Image(ImageDataFactory.create(sigBytes));
                    sig.scaleToFit(148f, 30f);
                    sig.setHorizontalAlignment(HorizontalAlignment.CENTER);
                    cell.add(sig);
                    System.out.println("[HallTicket] Signature loaded OK: " + f.getAbsolutePath());
                } else {
                    cell.add(new Paragraph("\u00A0").setFontSize(8));
                }
            } catch (Exception e) {
                System.err.println("[HallTicket] Signature load error: " + e.getMessage());
                cell.add(new Paragraph("\u00A0").setFontSize(8));
            }
        } else {
            cell.add(new Paragraph("\u00A0").setFontSize(8));
        }

        for (String line : label.split("\n"))
            cell.add(new Paragraph(line).setFontSize(8)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(0).setMarginTop(1));
        return cell;
    }

    private String getExamPeriod() {
        java.time.LocalDate now = java.time.LocalDate.now();
        int m = now.getMonthValue(), y = now.getYear();
        if (m >= 4 && m <= 6)  return "MAY / JUN " + y;
        if (m >= 10 && m <= 12) return "NOV / DEC " + y;
        return "APR / MAY " + y;
    }

    private String getExamCycle() {
        java.time.LocalDate today = java.time.LocalDate.now();
        int month = today.getMonthValue(), year = today.getYear();
        if (month >= 10 || month <= 1) return "NOV/DEC " + (month >= 10 ? year : year - 1);
        return "APR/MAY " + year;
    }

    /**
     * Removes the background from a signature image.
     *
     * Since signatures are written only in BLUE, GREEN, or BLACK ink,
     * we simply keep pixels whose hue and darkness match one of those
     * three ink colours and whiten everything else — no flood-fill needed.
     *
     * Ink detection rules (applied per pixel):
     *   Black : brightness < 110  AND  low saturation (all channels similar)
     *   Blue  : blue channel dominates AND pixel is dark (brightness < 190)
     *   Green : green channel dominates AND pixel is dark (brightness < 190)
     *
     * After colour filtering, an iterative neighbour check removes any
     * remaining isolated speckles that slipped through.
     */
    public byte[] cleanSignatureBackground(File imageFile) throws Exception {
        java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(imageFile);
        if (src == null) return java.nio.file.Files.readAllBytes(imageFile.toPath());

        int w = src.getWidth(), h = src.getHeight();
        java.awt.image.BufferedImage out =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);

        // ── Pass 1: Keep only blue / green / black ink; whiten everything else ──
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgba = src.getRGB(x, y);
                int a = (rgba >> 24) & 0xFF;
                int r = (rgba >> 16) & 0xFF;
                int g = (rgba >> 8)  & 0xFF;
                int b = rgba         & 0xFF;

                // Flatten semi-transparent pixels onto white
                if (a < 255) {
                    r = 255 - (255 - r) * a / 255;
                    g = 255 - (255 - g) * a / 255;
                    b = 255 - (255 - b) * a / 255;
                }

                int brightness = Math.max(r, Math.max(g, b));
                int darkness   = Math.min(r, Math.min(g, b));
                int saturation = brightness - darkness; // 0 = grey/black, high = colourful

                boolean isInk = false;

                if (brightness < 190) {  // must be dark enough to be ink at all

                    // BLACK ink: very dark, low saturation (R≈G≈B, all small)
                    boolean isBlack = brightness < 110 && saturation < 60;

                    // BLUE ink: blue channel is clearly dominant
                    //   b > r  AND  b >= g - 20  (handles navy / indigo / royal blue)
                    boolean isBlue  = b > r + 10 && b >= g - 20 && brightness < 190;

                    // GREEN ink: green channel clearly dominates both R and B
                    boolean isGreen = g > r + 20 && g > b + 10 && brightness < 190;

                    isInk = isBlack || isBlue || isGreen;
                }

                out.setRGB(x, y, isInk ? ((r << 16) | (g << 8) | b) : 0xFFFFFF);
            }
        }

        // ── Pass 2: Iterative speckle removal ──────────────────────────────────
        // Any dark pixel with < MIN_INK_NEIGHBOURS dark 8-neighbours is noise.
        // Real strokes are thick; lone specks have almost no dark neighbours.
        final int INK_BRIGHT_LIMIT   = 190; // same as above
        final int MIN_INK_NEIGHBOURS = 3;   // speck survives only if ≥3 dark neighbours
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int pix = out.getRGB(x, y);
                    int pr = (pix >> 16) & 0xFF, pg = (pix >> 8) & 0xFF, pb = pix & 0xFF;
                    if (Math.max(pr, Math.max(pg, pb)) >= INK_BRIGHT_LIMIT) continue; // white
                    int darkN = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = x + dx, ny = y + dy;
                            if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                            int np = out.getRGB(nx, ny);
                            if (Math.max((np>>16)&0xFF, Math.max((np>>8)&0xFF, np&0xFF))
                                    < INK_BRIGHT_LIMIT) darkN++;
                        }
                    }
                    if (darkN < MIN_INK_NEIGHBOURS) {
                        out.setRGB(x, y, 0xFFFFFF);
                        changed = true;
                    }
                }
            }
        }

        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(out, "PNG", bos);
        return bos.toByteArray();
    }
}