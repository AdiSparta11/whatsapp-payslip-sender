package com.grfriends.payslip.service;

import com.grfriends.payslip.model.Employee;
import com.lowagie.text.*;
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
 * Layout mirrors the official trilingual block structure from the Excel wage register.
 */
@Service
public class PdfGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);

    public byte[] generatePayslipPdf(Employee emp) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        PdfWriter.getInstance(document, out);

        document.open();

        // Fonts
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, new Color(15, 23, 42));
        Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(71, 85, 105));
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, new Color(30, 41, 59));
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(15, 23, 42));
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(30, 41, 59));
        Font whiteBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font footFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(148, 163, 184));

        // Company Title Header
        Paragraph title = new Paragraph("M/S. FRIENDS ENTERPRISE", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph site = new Paragraph("GRAPHITE INDIA LIMITED, DURGAPUR", subTitleFont);
        site.setAlignment(Element.ALIGN_CENTER);
        document.add(site);

        String monthYear = (emp.getMonth() != null ? emp.getMonth() : "JULY") + " " +
                (emp.getYear() != null ? emp.getYear() : "2026");
        Paragraph period = new Paragraph("PAY SLIP FOR " + monthYear, headerFont);
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(12);
        document.add(period);

        // Employee Info Table
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1, 1});

        addInfoCell(infoTable, "Pay Slip No:", String.valueOf(emp.getSlNo()), boldFont, normalFont);
        addInfoCell(infoTable, "Employee Name:", emp.getName(), boldFont, normalFont);
        addInfoCell(infoTable, "UAN No:", emp.getUan(), boldFont, normalFont);
        addInfoCell(infoTable, "ESI No:", defaultVal(emp.getEsiNo(), "-"), boldFont, normalFont);
        addInfoCell(infoTable, "Days Worked:", defaultVal(emp.getDaysWorked(), "0"), boldFont, normalFont);
        addInfoCell(infoTable, "Basic Rate:", "Rs. " + defaultVal(emp.getBasicRate(), "0.00"), boldFont, normalFont);

        document.add(infoTable);

        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(8);
        document.add(spacer);

        // Earnings & Deductions Table
        PdfPTable payTable = new PdfPTable(4);
        payTable.setWidthPercentage(100);
        payTable.setWidths(new float[]{2.5f, 1.5f, 2.5f, 1.5f});

        // Header Row
        addHeaderCell(payTable, "Earnings Description", whiteBoldFont);
        addHeaderCell(payTable, "Amount (Rs.)", whiteBoldFont);
        addHeaderCell(payTable, "Deductions Description", whiteBoldFont);
        addHeaderCell(payTable, "Amount (Rs.)", whiteBoldFont);

        // Build itemized list of earnings
        List<String[]> earnings = new ArrayList<>();
        addIfPresent(earnings, "Basic Wages", emp.getBasicAmount());
        addIfPresent(earnings, "Dearness Allowance (DA)", emp.getDa());
        addIfPresent(earnings, "House Rent Allowance (HRA)", emp.getHra());
        addIfPresent(earnings, "Washing Allowance", emp.getWashingAllowance());
        addIfPresent(earnings, "Fuel Allowance", emp.getFuelAllowance());
        addIfPresent(earnings, "Attendance Allowance", emp.getAttendanceAllowance());
        addIfPresent(earnings, "Food Allowance", emp.getFoodAllowance());
        addIfPresent(earnings, "Gratuity", emp.getGratuity());
        addIfPresent(earnings, "Overtime Amount", emp.getOvertimeAmount());
        addIfPresent(earnings, "Performance Incentive", emp.getPerformanceIncentive());
        addIfPresent(earnings, "Other Allowances", emp.getOtherAllowances());
        if (earnings.isEmpty()) {
            addIfPresent(earnings, "Basic Wages", "0.00");
        }

        // Build itemized list of deductions
        List<String[]> deductions = new ArrayList<>();
        addIfPresent(deductions, "E.P.F. Contribution", emp.getEpfDeduction());
        addIfPresent(deductions, "E.S.I.C. Contribution", emp.getEsiDeduction());
        addIfPresent(deductions, "Advance Deduction", emp.getAdvanceDeduction());
        addIfPresent(deductions, "Other Deductions", emp.getOtherDeductions());
        if (deductions.isEmpty()) {
            addIfPresent(deductions, "E.P.F. Contribution", "0.00");
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

        // Totals Row
        addTotalRow(payTable, "GROSS EARNINGS", defaultVal(emp.getGrossEarnings(), "0.00"),
                "TOTAL DEDUCTIONS", defaultVal(emp.getTotalDeductions(), "0.00"), boldFont);

        // Net Payable Row
        PdfPCell netLabelCell = new PdfPCell(new Phrase("NET PAYABLE", boldFont));
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

        // Footer note
        Paragraph footer = new Paragraph("This is an official computer-generated payslip notification.", footFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(20);
        document.add(footer);

        document.close();
        log.info("Generated PDF for UAN {}", emp.getUan());
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
