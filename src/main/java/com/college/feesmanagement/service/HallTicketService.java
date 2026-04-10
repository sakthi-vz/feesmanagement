package com.college.feesmanagement.service;

import com.college.feesmanagement.entity.*;
import com.college.feesmanagement.repository.*;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
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
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
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

    private static final float B = 0.5f; // border width

    public HallTicketService(StudentRepository studentRepository,
                              ExamRegistrationRepository registrationRepository,
                              ExamFeePaymentRepository paymentRepository,
                              ExamControllerRepository controllerRepository) {
        this.studentRepository      = studentRepository;
        this.registrationRepository = registrationRepository;
        this.paymentRepository      = paymentRepository;
        this.controllerRepository   = controllerRepository;
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
                    return r.getSubject().getSemester() != null
                            && r.getSubject().getSemester().equals(currentSem);
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
        return buildPdf(student, regs, controller);
    }

    // ── PDF Builder ───────────────────────────────────────────
    private byte[] buildPdf(Student student, List<ExamRegistration> regs,
                             ExamControllerAdmin ec) {
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
                    File f = new File(student.getPhotoPath());
                    if (!f.exists()) f = new File(System.getProperty("user.dir"), student.getPhotoPath());
                    if (!f.exists()) f = new File("uploads/photos/" + new File(student.getPhotoPath()).getName());
                    if (f.exists())
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
            det.addCell(dc(student.getName().toUpperCase(), false));
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
                String sem  = s2.getSemester() != null ? String.valueOf(s2.getSemester()) : "—";
                String date = s2.getExamDate() != null ? s2.getExamDate().format(fmt) : "";
                String sess = s2.getSession() != null ? s2.getSession() : "";

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
            Table sig = new Table(UnitValue.createPercentArray(new float[]{1, 1, 1}))
                    .useAllAvailableWidth().setMarginTop(0).setKeepTogether(true);
            sig.addCell(sigC("Signature of the Candidate", null, false));
            sig.addCell(sigC(ecDes, sigPath, true));
            sig.addCell(sigC("Signature of the Principal with seal", null, false));
            doc.add(sig);
            // ═════════════════════════════════════════════════
            //  PAGE 2  —  INSTRUCTIONS
            // ═════════════════════════════════════════════════
            doc.add(new AreaBreak());

            p(doc,"Mohamed Sathak A J College of Engineering, Chennai 603103",
                    12, true, TextAlignment.CENTER, 2);
            p(doc,"(An Autonomous Institution)",
                    10, false, TextAlignment.CENTER, 6);
            p(doc,"Instructions to the Candidates",
                    11, true, TextAlignment.CENTER, 10);

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
                Paragraph ip = new Paragraph((n+1) + ".  " + ins[n])
                        .setFontSize(9.5f).setMarginBottom(4)
                        .setMarginLeft(16).setFirstLineIndent(-16);
                // No bold on any instruction point
                doc.add(ip);
            }

            doc.add(new Paragraph("\n"));
            Table stamp = new Table(UnitValue.createPercentArray(new float[]{1,1})).useAllAvailableWidth();
            stamp.addCell(new Cell().setBorder(Border.NO_BORDER));
            stamp.addCell(new Cell()
                    .add(new Paragraph("Controller of Examination").setFontSize(9).setBold()
                            .setTextAlignment(TextAlignment.RIGHT))
                    .setBorder(Border.NO_BORDER));
            doc.add(stamp);

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

    /** Signature cell */
    private Cell sigC(String label, String sigImagePath, boolean isController) {
        Cell cell = new Cell()
                .setBorder(new SolidBorder(ColorConstants.BLACK, B))
                .setHeight(55f)   // Fixed height — all 3 cells equal
                .setVerticalAlignment(VerticalAlignment.BOTTOM)
                .setTextAlignment(TextAlignment.CENTER)
                .setPaddingTop(2).setPaddingBottom(3)
                .setPaddingLeft(3).setPaddingRight(3);

        if (isController && sigImagePath != null && !sigImagePath.isBlank()) {
            try {
                File f = new File(sigImagePath);
                if (!f.exists()) f = new File(System.getProperty("user.dir"), sigImagePath);
                if (!f.exists() && !sigImagePath.startsWith("/")) {
                    f = new File("uploads/photos/" + new File(sigImagePath).getName());
                }
                System.out.println("[HallTicket] Trying sig path: " + f.getAbsolutePath() + " exists=" + f.exists());
                if (f.exists()) {
                    Image sig = new Image(ImageDataFactory.create(f.getAbsolutePath()));
                    // Scale to fill cell width (3-col layout, each ~183pt)
                    // Don't clamp height — let it scale proportionally
                    // Cell fixed at 44pt, usable ~38pt after padding
                    // scaleToFit ensures both width and height constraints respected
                    sig.scaleToFit(148f, 30f);
                    sig.setHorizontalAlignment(HorizontalAlignment.CENTER);
                    cell.add(sig);
                    System.out.println("[HallTicket] Signature loaded OK: " + f.getAbsolutePath());
                } else {
                    System.err.println("[HallTicket] Signature NOT found at: " + f.getAbsolutePath());
                    System.err.println("[HallTicket] Original path stored: " + sigImagePath);
                    System.err.println("[HallTicket] user.dir: " + System.getProperty("user.dir"));
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
}