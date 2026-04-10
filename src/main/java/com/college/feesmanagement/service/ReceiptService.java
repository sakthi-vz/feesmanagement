package com.college.feesmanagement.service;

import com.college.feesmanagement.entity.ExamFeePayment;
import com.college.feesmanagement.entity.ExamRegistration;
import com.college.feesmanagement.repository.ExamFeePaymentRepository;
import com.college.feesmanagement.repository.ExamRegistrationRepository;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.draw.SolidLine;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.LineSeparator;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.itextpdf.io.image.ImageDataFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Service
public class ReceiptService {

    private final ExamFeePaymentRepository    paymentRepository;
    private final ExamRegistrationRepository  registrationRepository;

    // ── College details ───────────────────────────────────────────
    private static final String COLLEGE_NAME    = "Mohamed Sathak A.J. College of Engineering";
    private static final String COLLEGE_ADDRESS = "34, Rajiv Gandhi Salai (OMR), Inside SIPCOT IT Park";
    private static final String COLLEGE_CITY    = "Siruseri, Egattur, Chennai, Tamil Nadu - 603103";
    private static final String RECEIPT_TITLE   = "EXAMINATION FEE RECEIPT";

    // ── Premium colour palette ────────────────────────────────────
    // Deep navy header
    private static final DeviceRgb C_NAVY        = new DeviceRgb(15,  37,  82);
    // Gold accent
    private static final DeviceRgb C_GOLD        = new DeviceRgb(193, 154, 50);
    // Light gold tint for alternating rows
    private static final DeviceRgb C_GOLD_LIGHT  = new DeviceRgb(253, 249, 235);
    // Table header navy
    private static final DeviceRgb C_TH_BG       = new DeviceRgb(22,  48,  100);
    // Subtle stripe
    private static final DeviceRgb C_STRIPE      = new DeviceRgb(245, 247, 252);
    // Success green for total
    private static final DeviceRgb C_GREEN       = new DeviceRgb(21,  128, 61);
    private static final DeviceRgb C_GREEN_LIGHT = new DeviceRgb(240, 253, 244);
    // Label gray
    private static final DeviceRgb C_LABEL       = new DeviceRgb(90,  100, 120);
    // Border
    private static final DeviceRgb C_BORDER      = new DeviceRgb(210, 218, 235);

    // Embedded logo (base64)
    private static final String LOGO_B64 = "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";

    public ReceiptService(ExamFeePaymentRepository paymentRepository,
                          ExamRegistrationRepository registrationRepository) {
        this.paymentRepository      = paymentRepository;
        this.registrationRepository = registrationRepository;
    }

    public byte[] generateReceipt(String receiptNo) {
        ExamFeePayment payment = paymentRepository.findByReceiptNo(receiptNo)
                .orElseThrow(() -> new RuntimeException("Receipt not found: " + receiptNo));

        List<ExamRegistration> all = registrationRepository
                .findByStudentStudentId(payment.getStudent().getStudentId());

        List<ExamRegistration> paidRegs = all.stream()
                .filter(r -> r.getPayment() != null &&
                             r.getPayment().getPaymentId().equals(payment.getPaymentId()))
                .toList();

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter   writer = new PdfWriter(baos);
            PdfDocument pdf    = new PdfDocument(writer);
            Document    doc    = new Document(pdf, PageSize.A4);
            doc.setMargins(36, 40, 36, 40);

            // ── HEADER ────────────────────────────────────────────
            Table headerTable = new Table(UnitValue.createPercentArray(new float[]{1, 3.5f}))
                    .useAllAvailableWidth()
                    .setMarginBottom(0);

            // Logo cell
            try {
                byte[] logoBytes = Base64.getDecoder().decode(LOGO_B64);
                Image logo = new Image(ImageDataFactory.create(logoBytes))
                        .setWidth(70).setHeight(72);
                headerTable.addCell(new Cell()
                        .add(logo)
                        .setBorder(Border.NO_BORDER)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE)
                        .setPaddingRight(10));
            } catch (Exception e) {
                headerTable.addCell(new Cell().setBorder(Border.NO_BORDER));
            }

            // College name + address cell
            Cell nameCell = new Cell().setBorder(Border.NO_BORDER)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE);
            nameCell.add(new Paragraph(COLLEGE_NAME)
                    .setFontSize(15).setBold()
                    .setFontColor(C_NAVY)
                    .setMarginBottom(3));
            nameCell.add(new Paragraph(COLLEGE_ADDRESS)
                    .setFontSize(8.5f)
                    .setFontColor(C_LABEL)
                    .setMarginBottom(1));
            nameCell.add(new Paragraph(COLLEGE_CITY)
                    .setFontSize(8.5f)
                    .setFontColor(C_LABEL));
            headerTable.addCell(nameCell);
            doc.add(headerTable);

            // Gold divider line
            doc.add(new LineSeparator(new SolidLine(1.5f))
                    .setStrokeColor(C_GOLD)
                    .setMarginTop(8).setMarginBottom(4));

            // Navy title bar
            doc.add(new Paragraph(RECEIPT_TITLE)
                    .setFontSize(11).setBold()
                    .setFontColor(ColorConstants.WHITE)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setBackgroundColor(C_NAVY)
                    .setPaddingTop(7).setPaddingBottom(7)
                    .setMarginBottom(16));

            // ── RECEIPT META + STUDENT INFO (side by side) ────────
            Table infoOuter = new Table(UnitValue.createPercentArray(new float[]{1, 0.06f, 1}))
                    .useAllAvailableWidth().setMarginBottom(16);

            // Left: Receipt details
            Cell leftCell = new Cell().setBorder(Border.NO_BORDER).setPaddingRight(6);
            leftCell.add(sectionTitle("Receipt Details"));
            leftCell.add(infoRow("Receipt No",    payment.getReceiptNo()));
            leftCell.add(infoRow("Date",          payment.getPaymentDate() != null
                    ? payment.getPaymentDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
                    : "N/A"));
            leftCell.add(infoRow("Status",        payment.getStatus() != null
                    ? payment.getStatus().toString() : "N/A"));
            leftCell.add(infoRow("Transaction ID", payment.getTransactionId() != null
                    ? payment.getTransactionId() : "—"));
            infoOuter.addCell(leftCell);

            // Divider column
            infoOuter.addCell(new Cell().setBorder(Border.NO_BORDER)
                    .setBorderRight(new SolidBorder(C_BORDER, 0.5f)));

            // Right: Student details
            Cell rightCell = new Cell().setBorder(Border.NO_BORDER).setPaddingLeft(12);
            rightCell.add(sectionTitle("Student Details"));
            rightCell.add(infoRow("Name",       payment.getStudent().getName()));
            rightCell.add(infoRow("Roll No",    payment.getStudent().getRollNo()));
            rightCell.add(infoRow("Department", payment.getStudent().getDepartment() != null
                    ? payment.getStudent().getDepartment().getDeptName() : "N/A"));
            rightCell.add(infoRow("Semester",   payment.getSemester() != null
                    ? "Semester " + payment.getSemester() : "N/A"));
            infoOuter.addCell(rightCell);
            doc.add(infoOuter);

            // ── SUBJECTS TABLE ────────────────────────────────────
            doc.add(sectionTitle("Subjects Registered").setMarginBottom(6));

            // Columns: #  |  Code  |  Subject Name  |  Type  |  Fee
            Table subTable = new Table(UnitValue.createPercentArray(
                    new float[]{0.4f, 1.4f, 3f, 1.2f, 1f}))
                    .useAllAvailableWidth()
                    .setMarginBottom(16);

            // Table header
            for (String h : new String[]{"#", "Code", "Subject Name", "Type", "Fee (₹)"}) {
                subTable.addHeaderCell(new Cell()
                        .add(new Paragraph(h).setBold().setFontSize(9)
                                .setFontColor(ColorConstants.WHITE))
                        .setBackgroundColor(C_TH_BG)
                        .setBorder(Border.NO_BORDER)
                        .setPaddingTop(7).setPaddingBottom(7)
                        .setPaddingLeft(6).setPaddingRight(6));
            }

            int i = 1;
            for (ExamRegistration reg : paidRegs) {
                DeviceRgb bg = (i % 2 == 0) ? C_STRIPE : null;
                String code    = reg.getSubject() != null && reg.getSubject().getSubjectCode() != null
                        ? reg.getSubject().getSubjectCode() : "—";
                String name    = reg.getSubject() != null ? reg.getSubject().getName() : "N/A";
                String type    = reg.getType() != null ? reg.getType().toString() : "N/A";
                String fee     = reg.getSubject() != null
                        ? formatAmount(reg.getSubject().getFee()) : "N/A";

                subTable.addCell(tableCell(String.valueOf(i++), bg, TextAlignment.CENTER));
                subTable.addCell(tableCell(code, bg, TextAlignment.LEFT)
                        .setFontColor(C_NAVY));
                subTable.addCell(tableCell(name, bg, TextAlignment.LEFT));
                subTable.addCell(tableCell(type, bg, TextAlignment.CENTER));
                subTable.addCell(tableCell(fee,  bg, TextAlignment.RIGHT));
            }
            doc.add(subTable);

            // ── FEE SUMMARY ───────────────────────────────────────
            double base    = payment.getTotalAmount() != null ? payment.getTotalAmount() : 0;
            double total   = payment.getTotalAmount() != null ? payment.getTotalAmount() : 0;

            // Right-aligned summary block (50% width)
            Table feeWrap = new Table(UnitValue.createPercentArray(new float[]{1, 1}))
                    .useAllAvailableWidth().setMarginBottom(24);
            feeWrap.addCell(new Cell().setBorder(Border.NO_BORDER)); // spacer

            Table feeTable = new Table(UnitValue.createPercentArray(new float[]{2, 1}))
                    .useAllAvailableWidth();

            addFeeRow(feeTable, "Base Exam Fee", formatAmount(base), C_STRIPE);

            // Gold separator
            feeTable.addCell(new Cell(1, 2)
                    .setHeight(1.5f).setBackgroundColor(C_GOLD)
                    .setBorder(Border.NO_BORDER));

            // Total row
            feeTable.addCell(new Cell()
                    .add(new Paragraph("TOTAL AMOUNT PAID").setBold().setFontSize(10)
                            .setFontColor(C_GREEN))
                    .setBackgroundColor(C_GREEN_LIGHT)
                    .setBorder(new SolidBorder(C_GREEN, 0.5f))
                    .setPadding(8));
            feeTable.addCell(new Cell()
                    .add(new Paragraph("₹ " + formatAmount(total)).setBold().setFontSize(10)
                            .setFontColor(C_GREEN))
                    .setBackgroundColor(C_GREEN_LIGHT)
                    .setBorder(new SolidBorder(C_GREEN, 0.5f))
                    .setPadding(8).setTextAlignment(TextAlignment.RIGHT));

            feeWrap.addCell(new Cell().add(feeTable).setBorder(Border.NO_BORDER));
            doc.add(feeWrap);

            // ── FOOTER ────────────────────────────────────────────
            doc.add(new LineSeparator(new SolidLine(0.5f))
                    .setStrokeColor(C_BORDER).setMarginBottom(8));

            doc.add(new Paragraph(
                    "This is a computer-generated receipt and does not require a signature. "
                    + "For queries contact the examination section.")
                    .setFontSize(8).setItalic()
                    .setFontColor(C_LABEL)
                    .setTextAlignment(TextAlignment.CENTER));

            doc.close();
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate receipt PDF: " + e.getMessage(), e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private Paragraph sectionTitle(String text) {
        return new Paragraph(text)
                .setFontSize(9).setBold()
                .setFontColor(C_NAVY)
                .setTextAlignment(TextAlignment.LEFT)
                .setMarginBottom(5)
                .setBorderBottom(new SolidBorder(C_GOLD, 1.5f))
                .setPaddingBottom(3);
    }

    private Paragraph infoRow(String label, String value) {
        Paragraph p = new Paragraph().setMarginBottom(4).setFontSize(9);
        p.add(new Text(label + ":  ").setBold().setFontColor(C_LABEL));
        p.add(new Text(value != null ? value : "—").setFontColor(C_NAVY));
        return p;
    }

    private Cell tableCell(String text, DeviceRgb bg, TextAlignment align) {
        Cell cell = new Cell()
                .add(new Paragraph(text != null ? text : "—").setFontSize(9))
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(C_BORDER, 0.4f))
                .setPaddingTop(6).setPaddingBottom(6)
                .setPaddingLeft(6).setPaddingRight(6)
                .setTextAlignment(align);
        if (bg != null) cell.setBackgroundColor(bg);
        return cell;
    }

    private void addFeeRow(Table t, String label, String value, DeviceRgb bg) {
        Cell lc = new Cell()
                .add(new Paragraph(label).setFontSize(9).setFontColor(C_LABEL))
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(C_BORDER, 0.3f))
                .setPadding(6);
        Cell vc = new Cell()
                .add(new Paragraph(value).setFontSize(9).setFontColor(C_NAVY))
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(C_BORDER, 0.3f))
                .setPadding(6).setTextAlignment(TextAlignment.RIGHT);
        if (bg != null) { lc.setBackgroundColor(bg); vc.setBackgroundColor(bg); }
        t.addCell(lc);
        t.addCell(vc);
    }

    private String formatAmount(double amount) {
        if (amount == (long) amount)
            return String.format("%,d", (long) amount);
        return String.format("%,.2f", amount);
    }
}