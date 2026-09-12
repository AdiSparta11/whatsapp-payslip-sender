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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates an in-memory PDF payslip for an Employee using OpenPDF.
 * Creates a trilingual (English / Hindi / Bengali — ইংরেজি / हिंदी / বাংলা) payslip
 * with accurate statutory terminology.
 */
@Service
public class PdfGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);

    public byte[] generatePayslipPdf(Employee emp) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 32, 32, 32, 32);
        PdfWriter.getInstance(document, out);

        document.open();

        // 1. English Fonts (Helvetica for reliable ASCII/Latin numbers and values)
        Font engTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, new Color(15, 23, 42));
        Font engSubTitleFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, new Color(71, 85, 105));
        Font engHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, new Color(30, 41, 59));
        Font engBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, new Color(15, 23, 42));
        Font engNormalFont = FontFactory.getFont(FontFactory.HELVETICA, 8f, new Color(30, 41, 59));
        Font engWhiteBoldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.WHITE);
        Font engFootFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, new Color(148, 163, 184));

        // 2. Hindi (Devanagari) Fonts
        BaseFont bfHindi = loadFontSafe("/fonts/NotoSansDevanagari-Regular.ttf", "src/main/resources/fonts/NotoSansDevanagari-Regular.ttf");
        Font hinNormalFont = (bfHindi != null) ? new Font(bfHindi, 8f, Font.NORMAL, new Color(30, 41, 59)) : engNormalFont;
        Font hinBoldFont = (bfHindi != null) ? new Font(bfHindi, 8.5f, Font.BOLD, new Color(15, 23, 42)) : engBoldFont;
        Font hinTitleFont = (bfHindi != null) ? new Font(bfHindi, 12.5f, Font.BOLD, new Color(15, 23, 42)) : engTitleFont;
        Font hinSubTitleFont = (bfHindi != null) ? new Font(bfHindi, 8.5f, Font.NORMAL, new Color(71, 85, 105)) : engSubTitleFont;
        Font hinHeaderFont = (bfHindi != null) ? new Font(bfHindi, 9.5f, Font.BOLD, new Color(30, 41, 59)) : engHeaderFont;
        Font hinWhiteBoldFont = (bfHindi != null) ? new Font(bfHindi, 8.5f, Font.BOLD, Color.WHITE) : engWhiteBoldFont;
        Font hinFootFont = (bfHindi != null) ? new Font(bfHindi, 7.5f, Font.NORMAL, new Color(148, 163, 184)) : engFootFont;

        // 3. Bengali Fonts
        BaseFont bfBengali = loadFontSafe("/fonts/NotoSansBengali-Regular.ttf", "src/main/resources/fonts/NotoSansBengali-Regular.ttf");
        Font benNormalFont = (bfBengali != null) ? new Font(bfBengali, 8f, Font.NORMAL, new Color(30, 41, 59)) : engNormalFont;
        Font benBoldFont = (bfBengali != null) ? new Font(bfBengali, 8.5f, Font.BOLD, new Color(15, 23, 42)) : engBoldFont;
        Font benTitleFont = (bfBengali != null) ? new Font(bfBengali, 12.5f, Font.BOLD, new Color(15, 23, 42)) : engTitleFont;
        Font benSubTitleFont = (bfBengali != null) ? new Font(bfBengali, 8.5f, Font.NORMAL, new Color(71, 85, 105)) : engSubTitleFont;
        Font benHeaderFont = (bfBengali != null) ? new Font(bfBengali, 9.5f, Font.BOLD, new Color(30, 41, 59)) : engHeaderFont;
        Font benWhiteBoldFont = (bfBengali != null) ? new Font(bfBengali, 8.5f, Font.BOLD, Color.WHITE) : engWhiteBoldFont;
        Font benFootFont = (bfBengali != null) ? new Font(bfBengali, 7.5f, Font.NORMAL, new Color(148, 163, 184)) : engFootFont;

        // --- Header Section (Trilingual Title) ---
        Paragraph title = new Paragraph();
        title.setAlignment(Element.ALIGN_CENTER);
        title.add(createTrilingualPhrase("M/S. FRIENDS ENTERPRISE", "मैसर्स फ्रेंड्स एंटरप्राइज", "মেসার্স ফ্রেন্ডস এন্টারপ্রাইজ",
                engTitleFont, hinTitleFont, benTitleFont));
        document.add(title);

        Paragraph site = new Paragraph();
        site.setAlignment(Element.ALIGN_CENTER);
        site.add(createTrilingualPhrase("GRAPHITE INDIA LIMITED, DURGAPUR", "ग्रेफाइट इंडिया लिमिटेड, दुर्गापुर", "গ্রাফাইট ইন্ডিয়া লিমিটেড, দুর্গাপুর",
                engSubTitleFont, hinSubTitleFont, benSubTitleFont));
        document.add(site);

        String monthYear = (emp.getMonth() != null ? emp.getMonth() : "JULY") + " " +
                (emp.getYear() != null ? emp.getYear() : "2026");
        Paragraph period = new Paragraph();
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(10);
        period.add(createTrilingualPhrase("PAY SLIP FOR " + monthYear, "वेतन पर्ची - " + monthYear, "পে স্লিপ - " + monthYear,
                engHeaderFont, hinHeaderFont, benHeaderFont));
        document.add(period);

        // --- Employee Info Table (Trilingual Labels + English Values) ---
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1, 1});

        addInfoCell(infoTable, "Pay Slip No", "पे स्लिप नं.", "পে স্লিপ নং", String.valueOf(emp.getSlNo()),
                engBoldFont, hinBoldFont, benBoldFont, engNormalFont);
        addInfoCell(infoTable, "Employee Name", "कर्मचारी का नाम", "শ্রমিকের নাম", emp.getName(),
                engBoldFont, hinBoldFont, benBoldFont, engNormalFont);
        addInfoCell(infoTable, "UAN No", "यू.ए.एन. नं.", "ইউ.এ.এন নং", emp.getUan(),
                engBoldFont, hinBoldFont, benBoldFont, engNormalFont);
        addInfoCell(infoTable, "ESI No", "ई.एस.आई. नं.", "ই.এস.আই নং", defaultVal(emp.getEsiNo(), "-"),
                engBoldFont, hinBoldFont, benBoldFont, engNormalFont);
        addInfoCell(infoTable, "Designation", "पद", "পদবী", defaultVal(emp.getDesignation(), "WORKMAN"),
                engBoldFont, hinBoldFont, benBoldFont, engNormalFont);
        addInfoCell(infoTable, "Days Worked", "कार्य दिवस", "কাজের দিন", defaultVal(emp.getDaysWorked(), "0"),
                engBoldFont, hinBoldFont, benBoldFont, engNormalFont);
        addInfoCell(infoTable, "Basic Rate", "दैनिक मूल दर", "দৈনিক মজুরি হার", "Rs. " + defaultVal(emp.getBasicRate(), "0.00"),
                engBoldFont, hinBoldFont, benBoldFont, engNormalFont);
        addInfoCell(infoTable, "Gross Salary", "सकल वेतन", "মোট বেতন", "Rs. " + defaultVal(emp.getGrossEarnings(), "0.00"),
                engBoldFont, hinBoldFont, benBoldFont, engNormalFont);

        document.add(infoTable);

        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(6);
        document.add(spacer);

        // --- Earnings & Deductions Table ---
        PdfPTable payTable = new PdfPTable(4);
        payTable.setWidthPercentage(100);
        payTable.setWidths(new float[]{3.2f, 1.2f, 3.2f, 1.2f});

        // Header Row
        addHeaderCell(payTable, "Earnings", "उपार্জন", "উপার্জনের বিবরণ", engWhiteBoldFont, hinWhiteBoldFont, benWhiteBoldFont);
        addHeaderCell(payTable, "Amount (Rs.)", "राशि", "টাকা", engWhiteBoldFont, hinWhiteBoldFont, benWhiteBoldFont);
        addHeaderCell(payTable, "Deductions", "कटौती", "কর্তনের বিবরণ", engWhiteBoldFont, hinWhiteBoldFont, benWhiteBoldFont);
        addHeaderCell(payTable, "Amount (Rs.)", "राशि", "টাকা", engWhiteBoldFont, hinWhiteBoldFont, benWhiteBoldFont);

        // Build Itemized Earnings List: [engLabel, hinLabel, benLabel, amount]
        List<String[]> earnings = new ArrayList<>();
        // Accurate spellings per User's reference screenshots:
        addIfPresentTrilingual(earnings, "Basic Wages", "मूल वेतन", "মূল মজুরি", emp.getBasicAmount());
        addIfPresentTrilingual(earnings, "Dearness Allowance (DA)", "महंगाई भत्ता", "মহার্ঘ ভাতা (ডি.এ)", emp.getDa());
        addIfPresentTrilingual(earnings, "House Rent Allowance (HRA)", "मकान किराया भत्ता", "বাড়ি ভাড়া ভাতা", emp.getHra());
        addIfPresentTrilingual(earnings, "Washing Allowance", "धुलाई भत्ता", "ধোলাই ভাতা", emp.getWashingAllowance());
        addIfPresentTrilingual(earnings, "Fuel Allowance", "ईंधन भत्ता", "জ্বালানি ভাতা", emp.getFuelAllowance());
        addIfPresentTrilingual(earnings, "Attendance Allowance", "उपस्थिति भत्ता", "উপস্থিতি ভাতা", emp.getAttendanceAllowance());
        addIfPresentTrilingual(earnings, "Food Allowance", "भोजन भत्ता", "খাদ্য ভাতা", emp.getFoodAllowance());
        addIfPresentTrilingual(earnings, "Gratuity", "ग्रेच्युटी", "গ্র্যাচুইটি", emp.getGratuity());
        addIfPresentTrilingual(earnings, "Overtime Amount", "ओवरटाइम राशि", "ওভারটাইম মজুরি", emp.getOvertimeAmount());
        addIfPresentTrilingual(earnings, "Extra Production", "अतिरिक्त उत्पादन", "অতিরিক্ত উৎপাদন", emp.getExtraProduction());
        addIfPresentTrilingual(earnings, "Performance Incentive", "प्रोत्साहन राशि", "ইনসেনটিভ", emp.getPerformanceIncentive());
        addIfPresentTrilingual(earnings, "Other Allowances", "अन्य भत्ते", "অন্যান্য ভাতা", emp.getOtherAllowances());

        if (earnings.isEmpty()) {
            addIfPresentTrilingual(earnings, "Basic Wages", "मूल वेतन", "মূল মজুরি", "0.00");
        }

        // Build Itemized Deductions List: [engLabel, hinLabel, benLabel, amount]
        List<String[]> deductions = new ArrayList<>();
        addIfPresentTrilingual(deductions, "E.P.F. Contribution", "भविष्य निधि (PF)", "প্রভিডেন্ট ফান্ড (PF)", emp.getEpfDeduction());
        addIfPresentTrilingual(deductions, "E.S.I.C. Contribution", "ई.एस.आई.सी (ESI)", "ই.এস.আই.সি (ESI)", emp.getEsiDeduction());
        addIfPresentTrilingual(deductions, "Advance Deduction", "अग्रिम कटौती", "অগ্রিম কর্তন", emp.getAdvanceDeduction());
        addIfPresentTrilingual(deductions, "Other Deductions", "अन्य कटौती", "অন্যান্য কর্তন", emp.getOtherDeductions());

        if (deductions.isEmpty()) {
            addIfPresentTrilingual(deductions, "E.P.F. Contribution", "भविष्य निधि (PF)", "প্রভিডেন্ট ফান্ড (PF)", "0.00");
        }

        // Render Balanced Table Rows
        int maxRows = Math.max(earnings.size(), deductions.size());
        for (int i = 0; i < maxRows; i++) {
            String[] eTuple = i < earnings.size() ? earnings.get(i) : new String[]{"", "", "", ""};
            String[] dTuple = i < deductions.size() ? deductions.get(i) : new String[]{"", "", "", ""};

            addPayRow(payTable,
                    eTuple[0], eTuple[1], eTuple[2], eTuple[3],
                    dTuple[0], dTuple[1], dTuple[2], dTuple[3],
                    engNormalFont, hinNormalFont, benNormalFont, engNormalFont);
        }

        // Totals Row
        addTotalRow(payTable,
                "GROSS EARNINGS", "सकल वेतन", "মোট উপার্জন", defaultVal(emp.getGrossEarnings(), "0.00"),
                "TOTAL DEDUCTIONS", "कुल कटौती", "মোট কর্তন", defaultVal(emp.getTotalDeductions(), "0.00"),
                engBoldFont, hinBoldFont, benBoldFont, engBoldFont);

        // Net Payable Row
        Phrase netLabelPhrase = createTrilingualPhrase("NET PAYABLE", "शुद्ध देय राशि", "নিট প্রদেয় টাকা",
                engBoldFont, hinBoldFont, benBoldFont);
        PdfPCell netLabelCell = new PdfPCell(netLabelPhrase);
        netLabelCell.setBackgroundColor(new Color(226, 232, 240));
        netLabelCell.setPadding(5);
        payTable.addCell(netLabelCell);

        Phrase netValPhrase = new Phrase("Rs. " + defaultVal(emp.getNetPayable(), "0.00"), engBoldFont);
        PdfPCell netValCell = new PdfPCell(netValPhrase);
        netValCell.setBackgroundColor(new Color(226, 232, 240));
        netValCell.setPadding(5);
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
        footer.setSpacingBefore(16);
        footer.add(createTrilingualPhrase("This is an official computer-generated payslip", "यह एक आधिकारिक कंप्यूटर जनित वेतन पर्ची है", "এটি একটি কম্পিউটার চালিত সরকারি পে স্লিপ",
                engFootFont, hinFootFont, benFootFont));
        document.add(footer);

        document.close();
        log.info("Generated Trilingual (English/Hindi/Bengali) PDF for UAN {}", emp.getUan());
        return out.toByteArray();
    }

    // --- Helper Methods for Trilingual Formatting ---

    private Phrase createTrilingualPhrase(String englishText, String hindiText, String bengaliText,
                                          Font engFont, Font hinFont, Font benFont) {
        Phrase p = new Phrase();
        if (englishText != null && !englishText.isEmpty()) {
            p.add(new Chunk(englishText, engFont));
        }
        if (hindiText != null && !hindiText.isEmpty()) {
            if (!p.isEmpty()) {
                p.add(new Chunk(" / ", engFont));
            }
            p.add(new Chunk(hindiText, hinFont != null ? hinFont : engFont));
        }
        if (bengaliText != null && !bengaliText.isEmpty()) {
            if (!p.isEmpty()) {
                p.add(new Chunk(" / ", engFont));
            }
            p.add(new Chunk(bengaliText, benFont != null ? benFont : engFont));
        }
        return p;
    }

    private void addInfoCell(PdfPTable table, String engLabel, String hinLabel, String benLabel, String value,
                             Font engLabelFont, Font hinLabelFont, Font benLabelFont, Font valFont) {
        Phrase phrase = createTrilingualPhrase(engLabel, hinLabel, benLabel, engLabelFont, hinLabelFont, benLabelFont);
        phrase.add(new Chunk(": ", engLabelFont));
        phrase.add(new Chunk(value != null ? value : "", valFont));

        PdfPCell cell = new PdfPCell(phrase);
        cell.setBackgroundColor(new Color(248, 250, 252));
        cell.setBorderColor(new Color(203, 213, 225));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addHeaderCell(PdfPTable table, String engText, String hinText, String benText,
                               Font engFont, Font hinFont, Font benFont) {
        Phrase phrase = createTrilingualPhrase(engText, hinText, benText, engFont, hinFont, benFont);
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBackgroundColor(new Color(30, 41, 59));
        cell.setPadding(5);
        cell.setBorderColor(new Color(203, 213, 225));
        table.addCell(cell);
    }

    private void addPayRow(PdfPTable table,
                           String engEarning, String hinEarning, String benEarning, String eAmt,
                           String engDeduction, String hinDeduction, String benDeduction, String dAmt,
                           Font engFont, Font hinFont, Font benFont, Font valFont) {

        Phrase ePhrase = createTrilingualPhrase(engEarning, hinEarning, benEarning, engFont, hinFont, benFont);
        Phrase eAmtPhrase = new Phrase(eAmt != null ? eAmt : "", valFont);

        Phrase dPhrase = createTrilingualPhrase(engDeduction, hinDeduction, benDeduction, engFont, hinFont, benFont);
        Phrase dAmtPhrase = new Phrase(dAmt != null ? dAmt : "", valFont);

        PdfPCell c1 = new PdfPCell(ePhrase);
        PdfPCell c2 = new PdfPCell(eAmtPhrase);
        PdfPCell c3 = new PdfPCell(dPhrase);
        PdfPCell c4 = new PdfPCell(dAmtPhrase);

        for (PdfPCell c : new PdfPCell[]{c1, c2, c3, c4}) {
            c.setPadding(5);
            c.setBorderColor(new Color(203, 213, 225));
            table.addCell(c);
        }
    }

    private void addTotalRow(PdfPTable table,
                             String engEarning, String hinEarning, String benEarning, String eAmt,
                             String engDeduction, String hinDeduction, String benDeduction, String dAmt,
                             Font engFont, Font hinFont, Font benFont, Font valFont) {

        Phrase ePhrase = createTrilingualPhrase(engEarning, hinEarning, benEarning, engFont, hinFont, benFont);
        Phrase eAmtPhrase = new Phrase(eAmt != null ? eAmt : "", valFont);

        Phrase dPhrase = createTrilingualPhrase(engDeduction, hinDeduction, benDeduction, engFont, hinFont, benFont);
        Phrase dAmtPhrase = new Phrase(dAmt != null ? dAmt : "", valFont);

        PdfPCell c1 = new PdfPCell(ePhrase);
        PdfPCell c2 = new PdfPCell(eAmtPhrase);
        PdfPCell c3 = new PdfPCell(dPhrase);
        PdfPCell c4 = new PdfPCell(dAmtPhrase);

        for (PdfPCell c : new PdfPCell[]{c1, c2, c3, c4}) {
            c.setBackgroundColor(new Color(241, 245, 249));
            c.setPadding(5);
            c.setBorderColor(new Color(203, 213, 225));
            table.addCell(c);
        }
    }

    private void addIfPresentTrilingual(List<String[]> list, String engLabel, String hinLabel, String benLabel, String val) {
        if (val != null && !val.isBlank() && !val.equals("0.00") && !val.equals("0")) {
            list.add(new String[]{engLabel, hinLabel, benLabel, val});
        }
    }

    private String defaultVal(String val, String fallback) {
        return (val != null && !val.isBlank()) ? val : fallback;
    }

    private BaseFont loadFontSafe(String classpathResource, String fallbackFilePath) {
        try (InputStream is = getClass().getResourceAsStream(classpathResource)) {
            if (is != null) {
                byte[] bytes = is.readAllBytes();
                String fontName = classpathResource.substring(classpathResource.lastIndexOf('/') + 1);
                return BaseFont.createFont(fontName, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
            }
        } catch (Exception e) {
            log.warn("Could not load font from classpath {}: {}", classpathResource, e.getMessage());
        }

        try {
            return BaseFont.createFont(fallbackFilePath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        } catch (Exception e) {
            log.warn("Could not load font from file path {}: {}", fallbackFilePath, e.getMessage());
        }

        return null;
    }
}
