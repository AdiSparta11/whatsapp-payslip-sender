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
 * Layout mirrors the official trilingual/bilingual (English + Bengali) block structure from the Excel wage register.
 */
@Service
public class PdfGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);

    public byte[] generatePayslipPdf(Employee emp) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);

        document.open();

        // Fonts — Load NotoSansBengali-Regular.ttf for full Bengali Unicode support
        Font boldFont;
        Font normalFont;
        Font titleFont;
        Font subTitleFont;
        Font headerFont;
        Font whiteBoldFont;
        Font footFont;

        try {
            BaseFont bfBengali = BaseFont.createFont("fonts/NotoSansBengali-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            normalFont = new Font(bfBengali, 9, Font.NORMAL, new Color(30, 41, 59));
            boldFont = new Font(bfBengali, 9, Font.BOLD, new Color(15, 23, 42));
            titleFont = new Font(bfBengali, 14, Font.BOLD, new Color(15, 23, 42));
            subTitleFont = new Font(bfBengali, 9, Font.NORMAL, new Color(71, 85, 105));
            headerFont = new Font(bfBengali, 10, Font.BOLD, new Color(30, 41, 59));
            whiteBoldFont = new Font(bfBengali, 9, Font.BOLD, Color.WHITE);
            footFont = new Font(bfBengali, 8, Font.NORMAL, new Color(148, 163, 184));
        } catch (Exception e) {
            log.warn("Could not load NotoSansBengali font, falling back to Helvetica: {}", e.getMessage());
            titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(15, 23, 42));
            subTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(71, 85, 105));
            headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(30, 41, 59));
            boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(15, 23, 42));
            normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(30, 41, 59));
            whiteBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            footFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(148, 163, 184));
        }

        // Company Title Header (Bilingual English + Bengali)
        Paragraph title = new Paragraph("M/S. FRIENDS ENTERPRISE / ফ্রেন্ডস এন্টারপ্রাইজ", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph site = new Paragraph("GRAPHITE INDIA LIMITED, DURGAPUR / গ্রাফাইট ইন্ডিয়া লিমিটেড, দুর্গাপুর", subTitleFont);
        site.setAlignment(Element.ALIGN_CENTER);
        document.add(site);

        String monthYear = (emp.getMonth() != null ? emp.getMonth() : "JULY") + " " +
                (emp.getYear() != null ? emp.getYear() : "2026");
        Paragraph period = new Paragraph("PAY SLIP FOR " + monthYear + " / পে স্লিপ - " + monthYear, headerFont);
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(12);
        document.add(period);

        // Employee Info Table (Bilingual)
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1, 1});

        addInfoCell(infoTable, "Pay Slip No / পে স্লিপ নং:", String.valueOf(emp.getSlNo()), boldFont, normalFont);
        addInfoCell(infoTable, "Employee Name / শ্রমিকের নাম:", emp.getName(), boldFont, normalFont);
        addInfoCell(infoTable, "UAN No / ইউ.এ.এন নং:", emp.getUan(), boldFont, normalFont);
        addInfoCell(infoTable, "ESI No / ই.এস.আই নং:", defaultVal(emp.getEsiNo(), "-"), boldFont, normalFont);
        addInfoCell(infoTable, "Days Worked / কাজের দিন:", defaultVal(emp.getDaysWorked(), "0"), boldFont, normalFont);
        addInfoCell(infoTable, "Basic Rate / দৈনিক মজুরি হার:", "Rs. " + defaultVal(emp.getBasicRate(), "0.00"), boldFont, normalFont);

        document.add(infoTable);

        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(8);
        document.add(spacer);

        // Earnings & Deductions Table
        PdfPTable payTable = new PdfPTable(4);
        payTable.setWidthPercentage(100);
        payTable.setWidths(new float[]{2.5f, 1.2f, 2.5f, 1.2f});

        // Header Row (Bilingual)
        addHeaderCell(payTable, "Earnings / উপার্জনের বিবরণ", whiteBoldFont);
        addHeaderCell(payTable, "Amount (Rs.) / টাকা", whiteBoldFont);
        addHeaderCell(payTable, "Deductions / কর্তনের বিবরণ", whiteBoldFont);
        addHeaderCell(payTable, "Amount (Rs.) / টাকা", whiteBoldFont);

        // Build itemized list of earnings (Bilingual)
        List<String[]> earnings = new ArrayList<>();
        addIfPresent(earnings, "Basic Wages / মূল বেতন", emp.getBasicAmount());
        addIfPresent(earnings, "Dearness Allowance (DA) / মহার্ঘ ভাতা", emp.getDa());
        addIfPresent(earnings, "House Rent Allowance / বাড়ি ভাড়া ভাতা", emp.getHra());
        addIfPresent(earnings, "Washing Allowance / ধোলাই ভাতা", emp.getWashingAllowance());
        addIfPresent(earnings, "Fuel Allowance / জ্বালানি ভাতা", emp.getFuelAllowance());
        addIfPresent(earnings, "Attendance Allowance / উপস্থিতি ভাতা", emp.getAttendanceAllowance());
        addIfPresent(earnings, "Food Allowance / খাদ্য ভাতা", emp.getFoodAllowance());
        addIfPresent(earnings, "Gratuity / গ্র্যাচুইটি", emp.getGratuity());
        addIfPresent(earnings, "Overtime Amount / ওভারটাইম মজুরি", emp.getOvertimeAmount());
        addIfPresent(earnings, "Performance Incentive / পারফরম্যান্স ইনসেনটিভ", emp.getPerformanceIncentive());
        addIfPresent(earnings, "Other Allowances / অন্যান্য ভাতা", emp.getOtherAllowances());
        if (earnings.isEmpty()) {
            addIfPresent(earnings, "Basic Wages / মূল বেতন", "0.00");
        }

        // Build itemized list of deductions (Bilingual)
        List<String[]> deductions = new ArrayList<>();
        addIfPresent(deductions, "E.P.F. Contribution / পি.এফ. জমা", emp.getEpfDeduction());
        addIfPresent(deductions, "E.S.I.C. Contribution / ই.এস.আই. জমা", emp.getEsiDeduction());
        addIfPresent(deductions, "Advance Deduction / অগ্রিম কাটা", emp.getAdvanceDeduction());
        addIfPresent(deductions, "Other Deductions / অন্যান্য কর্তন", emp.getOtherDeductions());
        if (deductions.isEmpty()) {
            addIfPresent(deductions, "E.P.F. Contribution / পি.এফ. জমা", "0.00");
        }

        // Render balanced rows
        int maxRows = Math.max(earnings.size(), deductions.size());
        for (int i = 0; i < maxRows; i++) {
            String eDesc = i < earnings.size() ? earnings.get(i)[0] : "";
            String eAmt = i < earnings.size() ? earnings.get(i)[1] : "";
            String dDesc = i < deductions.size() ? deductions.get(i)[0] : "";
            String dAmt = i < deductions.size() ? deductions.get(i)[1] : "";
            addPayRow(payTable, eDesc, eAmt, dDesc, dAmt, normalFont);
        }

        // Totals Row (Bilingual)
        addTotalRow(payTable, "GROSS EARNINGS / মোট উপার্জন", defaultVal(emp.getGrossEarnings(), "0.00"),
                "TOTAL DEDUCTIONS / মোট কর্তন", defaultVal(emp.getTotalDeductions(), "0.00"), boldFont);

        // Net Payable Row (Bilingual)
        PdfPCell netLabelCell = new PdfPCell(new Phrase("NET PAYABLE / নিট প্রদেয় টাকা", boldFont));
        netLabelCell.setBackgroundColor(new Color(226, 232, 240));
        netLabelCell.setPadding(6);
        payTable.addCell(netLabelCell);

        PdfPCell netValCell = new PdfPCell(new Phrase("Rs. " + defaultVal(emp.getNetPayable(), "0.00"), boldFont));
        netValCell.setBackgroundColor(new Color(226, 232, 240));
        netValCell.setPadding(6);
        payTable.addCell(netValCell);

        PdfPCell emptyCell1 = new PdfPCell(new Phrase("", normalFont));
        emptyCell1.setBackgroundColor(new Color(226, 232, 240));
        payTable.addCell(emptyCell1);

        PdfPCell emptyCell2 = new PdfPCell(new Phrase("", normalFont));
        emptyCell2.setBackgroundColor(new Color(226, 232, 240));
        payTable.addCell(emptyCell2);

        document.add(payTable);

        // Footer note (Bilingual)
        Paragraph footer = new Paragraph("This is an official computer-generated payslip / এটি একটি কম্পিউটার চালিত পে স্লিপ", footFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(20);
        document.add(footer);

        document.close();
        log.info("Generated Bilingual PDF for UAN {}", emp.getUan());
        return out.toByteArray();
    }

    private void addInfoCell(PdfPTable table, String label, String value, Font labelFont, Font valFont) {
        Phrase phrase = new Phrase();
        phrase.add(new Chunk(label + " ", labelFont));
        phrase.add(new Chunk(value != null ? value : "", valFont));

        PdfPCell cell = new PdfPCell(phrase);
        cell.setBackgroundColor(new Color(248, 250, 252));
        cell.setBorderColor(new Color(203, 213, 225));
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new Color(30, 41, 59));
        cell.setPadding(6);
        cell.setBorderColor(new Color(203, 213, 225));
        table.addCell(cell);
    }

    private void addPayRow(PdfPTable table, String eDesc, String eAmt, String dDesc, String dAmt, Font font) {
        PdfPCell c1 = new PdfPCell(new Phrase(eDesc, font));
        PdfPCell c2 = new PdfPCell(new Phrase(eAmt, font));
        PdfPCell c3 = new PdfPCell(new Phrase(dDesc, font));
        PdfPCell c4 = new PdfPCell(new Phrase(dAmt, font));

        for (PdfPCell c : new PdfPCell[]{c1, c2, c3, c4}) {
            c.setPadding(6);
            c.setBorderColor(new Color(203, 213, 225));
            table.addCell(c);
        }
    }

    private void addTotalRow(PdfPTable table, String eDesc, String eAmt, String dDesc, String dAmt, Font font) {
        PdfPCell c1 = new PdfPCell(new Phrase(eDesc, font));
        PdfPCell c2 = new PdfPCell(new Phrase(eAmt, font));
        PdfPCell c3 = new PdfPCell(new Phrase(dDesc, font));
        PdfPCell c4 = new PdfPCell(new Phrase(dAmt, font));

        for (PdfPCell c : new PdfPCell[]{c1, c2, c3, c4}) {
            c.setBackgroundColor(new Color(241, 245, 249));
            c.setPadding(6);
            c.setBorderColor(new Color(203, 213, 225));
            table.addCell(c);
        }
    }

    private void addIfPresent(List<String[]> list, String label, String val) {
        if (val != null && !val.isBlank() && !val.equals("0.00") && !val.equals("0")) {
            list.add(new String[]{label, val});
        }
    }

    private String defaultVal(String val, String fallback) {
        return (val != null && !val.isBlank()) ? val : fallback;
    }
}
