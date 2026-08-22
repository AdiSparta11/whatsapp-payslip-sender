package com.grfriends.payslip.service;

import com.grfriends.payslip.model.Employee;
import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates an in-memory PDF payslip for an Employee using OpenPDF.
 * Creates a bilingual (English + Bengali / ইংরেজি ও বাংলা) payslip where standard Helvetica font
 * renders numbers and values, while NotoSansBengali renders Bengali text.
 */
@Service
public class PdfGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);

    public byte[] generatePayslipPdf(Employee emp) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);

        document.open();

        // 1. English Fonts (Helvetica for reliable ASCII/Latin numbers and values)
        Font engTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(15, 23, 42));
        Font engSubTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(71, 85, 105));
        Font engHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(30, 41, 59));
        Font engBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(15, 23, 42));
        Font engNormalFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(30, 41, 59));
        Font engWhiteBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font engFootFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(148, 163, 184));

        // 2. Bengali Fonts (NotoSansBengali for Bengali script)
        Font benNormalFont = null;
        Font benBoldFont = null;
        Font benTitleFont = null;
        Font benSubTitleFont = null;
        Font benHeaderFont = null;
        Font benWhiteBoldFont = null;
        Font benFootFont = null;

        try {
            BaseFont bfBengali = BaseFont.createFont("fonts/NotoSansBengali-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            benNormalFont = new Font(bfBengali, 9, Font.NORMAL, new Color(30, 41, 59));
            benBoldFont = new Font(bfBengali, 9, Font.BOLD, new Color(15, 23, 42));
            benTitleFont = new Font(bfBengali, 14, Font.BOLD, new Color(15, 23, 42));
            benSubTitleFont = new Font(bfBengali, 9, Font.NORMAL, new Color(71, 85, 105));
            benHeaderFont = new Font(bfBengali, 10, Font.BOLD, new Color(30, 41, 59));
            benWhiteBoldFont = new Font(bfBengali, 9, Font.BOLD, Color.WHITE);
            benFootFont = new Font(bfBengali, 8, Font.NORMAL, new Color(148, 163, 184));
        } catch (Exception e) {
            log.warn("Could not load NotoSansBengali font, falling back to English Helvetica: {}", e.getMessage());
        }

        // --- Header Section (Bilingual Title) ---
        Paragraph title = new Paragraph();
        title.setAlignment(Element.ALIGN_CENTER);
        title.add(createBilingualPhrase("M/S. FRIENDS ENTERPRISE", "ফ্রেন্ডস এন্টারপ্রাইজ", engTitleFont, benTitleFont));
        document.add(title);

        Paragraph site = new Paragraph();
        site.setAlignment(Element.ALIGN_CENTER);
        site.add(createBilingualPhrase("GRAPHITE INDIA LIMITED, DURGAPUR", "গ্রাফাইট ইন্ডিয়া লিমিটেড, দুর্গাপুর", engSubTitleFont, benSubTitleFont));
        document.add(site);

        String monthYear = (emp.getMonth() != null ? emp.getMonth() : "JULY") + " " +
                (emp.getYear() != null ? emp.getYear() : "2026");
        Paragraph period = new Paragraph();
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(12);
        period.add(createBilingualPhrase("PAY SLIP FOR " + monthYear, "পে স্লিপ - " + monthYear, engHeaderFont, benHeaderFont));
        document.add(period);

        // --- Employee Info Table (Bilingual Labels + English Values) ---
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1, 1});

        addInfoCell(infoTable, "Pay Slip No", "পে স্লিপ নং", String.valueOf(emp.getSlNo()), engBoldFont, benBoldFont, engNormalFont);
        addInfoCell(infoTable, "Employee Name", "শ্রমিকের নাম", emp.getName(), engBoldFont, benBoldFont, engNormalFont);
        addInfoCell(infoTable, "UAN No", "ইউ.এ.এন নং", emp.getUan(), engBoldFont, benBoldFont, engNormalFont);
        addInfoCell(infoTable, "ESI No", "ই.এস.আই নং", defaultVal(emp.getEsiNo(), "-"), engBoldFont, benBoldFont, engNormalFont);
        addInfoCell(infoTable, "Days Worked", "কাজের দিন", defaultVal(emp.getDaysWorked(), "0"), engBoldFont, benBoldFont, engNormalFont);
        addInfoCell(infoTable, "Basic Rate", "দৈনিক মজুরি হার", "Rs. " + defaultVal(emp.getBasicRate(), "0.00"), engBoldFont, benBoldFont, engNormalFont);

        document.add(infoTable);

        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(8);
        document.add(spacer);

        // --- Earnings & Deductions Table ---
        PdfPTable payTable = new PdfPTable(4);
        payTable.setWidthPercentage(100);
        payTable.setWidths(new float[]{2.5f, 1.2f, 2.5f, 1.2f});

        // Header Row
        addHeaderCell(payTable, "Earnings Description", "উপার্জনের বিবরণ", engWhiteBoldFont, benWhiteBoldFont);
        addHeaderCell(payTable, "Amount (Rs.)", "টাকা", engWhiteBoldFont, benWhiteBoldFont);
        addHeaderCell(payTable, "Deductions Description", "কর্তনের বিবরণ", engWhiteBoldFont, benWhiteBoldFont);
        addHeaderCell(payTable, "Amount (Rs.)", "টাকা", engWhiteBoldFont, benWhiteBoldFont);

        // Build Itemized Earnings List: [engLabel, benLabel, amount]
        List<String[]> earnings = new ArrayList<>();
        addIfPresentBilingual(earnings, "Basic Wages", "মূল বেতন", emp.getBasicAmount());
        addIfPresentBilingual(earnings, "Dearness Allowance (DA)", "মহার্ঘ ভাতা", emp.getDa());
        addIfPresentBilingual(earnings, "House Rent Allowance (HRA)", "বাড়ি ভাড়া ভাতা", emp.getHra());
        addIfPresentBilingual(earnings, "Washing Allowance", "ধোলাই ভাতা", emp.getWashingAllowance());
        addIfPresentBilingual(earnings, "Fuel Allowance", "জ্বালানি ভাতা", emp.getFuelAllowance());
        addIfPresentBilingual(earnings, "Attendance Allowance", "উপস্থিতি ভাতা", emp.getAttendanceAllowance());
        addIfPresentBilingual(earnings, "Food Allowance", "খাদ্য ভাতা", emp.getFoodAllowance());
        addIfPresentBilingual(earnings, "Gratuity", "গ্র্যাচুইটি", emp.getGratuity());
        addIfPresentBilingual(earnings, "Overtime Amount", "ওভারটাইম মজুরি", emp.getOvertimeAmount());
        addIfPresentBilingual(earnings, "Performance Incentive", "পারফরম্যান্স ইনসেনটিভ", emp.getPerformanceIncentive());
        addIfPresentBilingual(earnings, "Other Allowances", "অন্যান্য ভাতা", emp.getOtherAllowances());

        if (earnings.isEmpty()) {
            addIfPresentBilingual(earnings, "Basic Wages", "মূল বেতন", "0.00");
        }

        // Build Itemized Deductions List: [engLabel, benLabel, amount]
        List<String[]> deductions = new ArrayList<>();
        addIfPresentBilingual(deductions, "E.P.F. Contribution", "পি.এফ. জমা", emp.getEpfDeduction());
        addIfPresentBilingual(deductions, "E.S.I.C. Contribution", "ই.এস.আই. জমা", emp.getEsiDeduction());
        addIfPresentBilingual(deductions, "Advance Deduction", "অগ্রিম কাটা", emp.getAdvanceDeduction());
        addIfPresentBilingual(deductions, "Other Deductions", "অন্যান্য কর্তন", emp.getOtherDeductions());

        if (deductions.isEmpty()) {
            addIfPresentBilingual(deductions, "E.P.F. Contribution", "পি.এফ. জমা", "0.00");
        }

        // Render Balanced Table Rows
        int maxRows = Math.max(earnings.size(), deductions.size());
        for (int i = 0; i < maxRows; i++) {
            String[] eTuple = i < earnings.size() ? earnings.get(i) : new String[]{"", "", ""};
            String[] dTuple = i < deductions.size() ? deductions.get(i) : new String[]{"", "", ""};

            addPayRow(payTable,
                    eTuple[0], eTuple[1], eTuple[2],
                    dTuple[0], dTuple[1], dTuple[2],
                    engNormalFont, benNormalFont, engNormalFont);
        }

        // Totals Row
        addTotalRow(payTable,
                "GROSS EARNINGS", "মোট উপার্জন", defaultVal(emp.getGrossEarnings(), "0.00"),
                "TOTAL DEDUCTIONS", "মোট কর্তন", defaultVal(emp.getTotalDeductions(), "0.00"),
                engBoldFont, benBoldFont, engBoldFont);

        // Net Payable Row
        Phrase netLabelPhrase = createBilingualPhrase("NET PAYABLE", "নিট প্রদেয় টাকা", engBoldFont, benBoldFont);
        PdfPCell netLabelCell = new PdfPCell(netLabelPhrase);
        netLabelCell.setBackgroundColor(new Color(226, 232, 240));
        netLabelCell.setPadding(6);
        payTable.addCell(netLabelCell);

        Phrase netValPhrase = new Phrase("Rs. " + defaultVal(emp.getNetPayable(), "0.00"), engBoldFont);
        PdfPCell netValCell = new PdfPCell(netValPhrase);
        netValCell.setBackgroundColor(new Color(226, 232, 240));
        netValCell.setPadding(6);
        payTable.addCell(netValCell);

        PdfPCell emptyCell1 = new PdfPCell(new Phrase("", engNormalFont));
        emptyCell1.setBackgroundColor(new Color(226, 232, 240));
        payTable.addCell(emptyCell1);

        PdfPCell emptyCell2 = new PdfPCell(new Phrase("", engNormalFont));
        emptyCell2.setBackgroundColor(new Color(226, 232, 240));
        payTable.addCell(emptyCell2);

        document.add(payTable);

        // Footer Note
        Paragraph footer = new Paragraph();
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(20);
        footer.add(createBilingualPhrase("This is an official computer-generated payslip", "এটি একটি কম্পিউটার চালিত পে স্লিপ", engFootFont, benFootFont));
        document.add(footer);

        document.close();
        log.info("Generated Bilingual PDF for UAN {}", emp.getUan());
        return out.toByteArray();
    }

    // --- Helper Methods for Bilingual Formatting ---

    private Phrase createBilingualPhrase(String englishText, String bengaliText, Font engFont, Font benFont) {
        Phrase p = new Phrase();
        if (englishText != null && !englishText.isEmpty()) {
            p.add(new Chunk(englishText, engFont));
        }
        if (bengaliText != null && !bengaliText.isEmpty()) {
            if (englishText != null && !englishText.isEmpty()) {
                p.add(new Chunk(" / ", engFont));
            }
            p.add(new Chunk(bengaliText, benFont != null ? benFont : engFont));
        }
        return p;
    }

    private void addInfoCell(PdfPTable table, String engLabel, String benLabel, String value,
                             Font engLabelFont, Font benLabelFont, Font valFont) {
        Phrase phrase = createBilingualPhrase(engLabel, benLabel, engLabelFont, benLabelFont);
        phrase.add(new Chunk(": ", engLabelFont));
        phrase.add(new Chunk(value != null ? value : "", valFont));

        PdfPCell cell = new PdfPCell(phrase);
        cell.setBackgroundColor(new Color(248, 250, 252));
        cell.setBorderColor(new Color(203, 213, 225));
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void addHeaderCell(PdfPTable table, String engText, String benText, Font engFont, Font benFont) {
        Phrase phrase = createBilingualPhrase(engText, benText, engFont, benFont);
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBackgroundColor(new Color(30, 41, 59));
        cell.setPadding(6);
        cell.setBorderColor(new Color(203, 213, 225));
        table.addCell(cell);
    }

    private void addPayRow(PdfPTable table,
                           String engEarning, String benEarning, String eAmt,
                           String engDeduction, String benDeduction, String dAmt,
                           Font engFont, Font benFont, Font valFont) {

        Phrase ePhrase = createBilingualPhrase(engEarning, benEarning, engFont, benFont);
        Phrase eAmtPhrase = new Phrase(eAmt != null ? eAmt : "", valFont);

        Phrase dPhrase = createBilingualPhrase(engDeduction, benDeduction, engFont, benFont);
        Phrase dAmtPhrase = new Phrase(dAmt != null ? dAmt : "", valFont);

        PdfPCell c1 = new PdfPCell(ePhrase);
        PdfPCell c2 = new PdfPCell(eAmtPhrase);
        PdfPCell c3 = new PdfPCell(dPhrase);
        PdfPCell c4 = new PdfPCell(dAmtPhrase);

        for (PdfPCell c : new PdfPCell[]{c1, c2, c3, c4}) {
            c.setPadding(6);
            c.setBorderColor(new Color(203, 213, 225));
            table.addCell(c);
        }
    }

    private void addTotalRow(PdfPTable table,
                            String engEarning, String benEarning, String eAmt,
                            String engDeduction, String benDeduction, String dAmt,
                            Font engFont, Font benFont, Font valFont) {

        Phrase ePhrase = createBilingualPhrase(engEarning, benEarning, engFont, benFont);
        Phrase eAmtPhrase = new Phrase(eAmt != null ? eAmt : "", valFont);

        Phrase dPhrase = createBilingualPhrase(engDeduction, benDeduction, engFont, benFont);
        Phrase dAmtPhrase = new Phrase(dAmt != null ? dAmt : "", valFont);

        PdfPCell c1 = new PdfPCell(ePhrase);
        PdfPCell c2 = new PdfPCell(eAmtPhrase);
        PdfPCell c3 = new PdfPCell(dPhrase);
        PdfPCell c4 = new PdfPCell(dAmtPhrase);

        for (PdfPCell c : new PdfPCell[]{c1, c2, c3, c4}) {
            c.setBackgroundColor(new Color(241, 245, 249));
            c.setPadding(6);
            c.setBorderColor(new Color(203, 213, 225));
            table.addCell(c);
        }
    }

    private void addIfPresentBilingual(List<String[]> list, String engLabel, String benLabel, String val) {
        if (val != null && !val.isBlank() && !val.equals("0.00") && !val.equals("0")) {
            list.add(new String[]{engLabel, benLabel, val});
        }
    }

    private String defaultVal(String val, String fallback) {
        return (val != null && !val.isBlank()) ? val : fallback;
    }
}

