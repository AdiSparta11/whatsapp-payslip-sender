package com.grfriends.payslip.service;

import com.grfriends.payslip.model.Employee;
import com.lowagie.text.*;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Generates an in-memory PDF payslip for an Employee using OpenPDF.
 * Creates a trilingual (English / Hindi / Bengali — ইংরেজি / हिंदी / বাংলা) payslip
 * with accurate statutory terminology.
 */
@Service
public class PdfGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);

    // AWT fonts used to pre-render Hindi/Bengali text as properly shaped images (see
    // appendIndicText). Null if they can't be loaded, in which case Indic text falls back
    // to plain OpenPDF text with the string-level matra fixes.
    private static volatile java.awt.Font hindiAwtFont;
    private static volatile java.awt.Font bengaliAwtFont;
    private static volatile boolean indicRenderingInitialized = false;
    private static volatile String indicRenderingStatus = "not initialized yet (no PDF generated since startup)";
    private static volatile String lastFontLoadError = null;
    private static final long INDIC_FONT_INIT_TIMEOUT_SECONDS = 20;
    private static final Map<String, RenderedText> INDIC_TEXT_IMAGE_CACHE = new ConcurrentHashMap<>();

    private record RenderedText(Image image, float descentPt) {}

    /** Human-readable state of the image-based Indic rendering, for the diagnostics endpoint. */
    public static String getIndicRenderingStatus() {
        return indicRenderingStatus;
    }

    /**
     * Loads the AWT fonts and performs one real render, on a background thread with a hard
     * timeout. This is deliberately NOT done at class-load/Spring-startup time: initializing
     * the JVM's native font subsystem on a minimal container image can block, and the app
     * must still boot and serve its port regardless. If loading times out or fails, image
     * rendering stays disabled and Indic text uses the plain-text fallback.
     */
    public static synchronized void initIndicRenderingIfNeeded() {
        if (indicRenderingInitialized) return;
        indicRenderingInitialized = true;

        if (System.getProperty("java.awt.headless") == null) {
            System.setProperty("java.awt.headless", "true");
        }

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "indic-font-init");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<java.awt.Font[]> future = executor.submit(() -> {
                java.awt.Font hindi = loadAwtFont("/fonts/NotoSansDevanagari-Regular.ttf");
                java.awt.Font bengali = loadAwtFont("/fonts/NotoSansBengali-Regular.ttf");
                // Force the native font pipeline to fully initialize here, under the timeout,
                // rather than on the first real word during a request.
                if (hindi != null) renderTextToImage("क", hindi, 8f, Color.BLACK, false);
                if (bengali != null) renderTextToImage("ক", bengali, 8f, Color.BLACK, false);
                return new java.awt.Font[]{hindi, bengali};
            });
            java.awt.Font[] fonts = future.get(INDIC_FONT_INIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            hindiAwtFont = fonts[0];
            bengaliAwtFont = fonts[1];
            boolean ready = hindiAwtFont != null && bengaliAwtFont != null;
            indicRenderingStatus = (ready ? "READY" : "FALLBACK (font load failed)")
                    + " | hindiFont=" + (hindiAwtFont != null)
                    + " | bengaliFont=" + (bengaliAwtFont != null)
                    + " | java.awt.headless=" + System.getProperty("java.awt.headless")
                    + " | os=" + System.getProperty("os.name") + " " + System.getProperty("os.version")
                    + " | java=" + System.getProperty("java.vendor") + " " + System.getProperty("java.version")
                    + (lastFontLoadError != null ? " | lastError=" + lastFontLoadError : "");
            log.info("Indic image rendering: {}", indicRenderingStatus);
        } catch (TimeoutException e) {
            indicRenderingStatus = "TIMEOUT: AWT font initialization did not complete within "
                    + INDIC_FONT_INIT_TIMEOUT_SECONDS + "s (using plain-text fallback)";
            log.warn(indicRenderingStatus);
        } catch (Throwable t) {
            indicRenderingStatus = "FAILED: " + describe(t) + " (using plain-text fallback)";
            log.warn(indicRenderingStatus);
        } finally {
            executor.shutdownNow();
        }
    }

    private static String describe(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root == t ? t.toString() : t + " (root cause: " + root + ")";
    }

    public byte[] generatePayslipPdf(Employee emp) throws Exception {
        initIndicRenderingIfNeeded();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 32, 32, 32, 32);
        PdfWriter writer = PdfWriter.getInstance(document, out);

        document.open();

        // Draw a card-style border frame around the page content, independent of any
        // text flow (a plain rectangle on the background layer can't disturb table/cell layout).
        PdfContentByte borderCanvas = writer.getDirectContentUnder();
        borderCanvas.saveState();
        borderCanvas.setColorStroke(new Color(191, 205, 224));
        borderCanvas.setLineWidth(1.2f);
        borderCanvas.rectangle(document.left() - 8, document.bottom() - 8,
                (document.right() - document.left()) + 16, (document.top() - document.bottom()) + 16);
        borderCanvas.stroke();
        borderCanvas.restoreState();

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
        Font hinSubTitleFont = (bfHindi != null) ? new Font(bfHindi, 8.5f, Font.NORMAL, new Color(71, 85, 105)) : engSubTitleFont;
        Font hinHeaderFont = (bfHindi != null) ? new Font(bfHindi, 9.5f, Font.BOLD, new Color(30, 41, 59)) : engHeaderFont;
        Font hinWhiteBoldFont = (bfHindi != null) ? new Font(bfHindi, 8.5f, Font.BOLD, Color.WHITE) : engWhiteBoldFont;
        Font hinFootFont = (bfHindi != null) ? new Font(bfHindi, 7.5f, Font.NORMAL, new Color(148, 163, 184)) : engFootFont;
        Font hinSmallFont = (bfHindi != null) ? new Font(bfHindi, 7f, Font.NORMAL, new Color(100, 116, 139)) : engSubTitleFont;

        // 3. Bengali Fonts
        BaseFont bfBengali = loadFontSafe("/fonts/NotoSansBengali-Regular.ttf", "src/main/resources/fonts/NotoSansBengali-Regular.ttf");
        Font benNormalFont = (bfBengali != null) ? new Font(bfBengali, 8f, Font.NORMAL, new Color(30, 41, 59)) : engNormalFont;
        Font benBoldFont = (bfBengali != null) ? new Font(bfBengali, 8.5f, Font.BOLD, new Color(15, 23, 42)) : engBoldFont;
        Font benSubTitleFont = (bfBengali != null) ? new Font(bfBengali, 8.5f, Font.NORMAL, new Color(71, 85, 105)) : engSubTitleFont;
        Font benHeaderFont = (bfBengali != null) ? new Font(bfBengali, 9.5f, Font.BOLD, new Color(30, 41, 59)) : engHeaderFont;
        Font benWhiteBoldFont = (bfBengali != null) ? new Font(bfBengali, 8.5f, Font.BOLD, Color.WHITE) : engWhiteBoldFont;
        Font benFootFont = (bfBengali != null) ? new Font(bfBengali, 7.5f, Font.NORMAL, new Color(148, 163, 184)) : engFootFont;
        Font benSmallFont = (bfBengali != null) ? new Font(bfBengali, 7f, Font.NORMAL, new Color(100, 116, 139)) : engSubTitleFont;

        // --- Header Section (English title, translations as a smaller line below) ---
        String contractorRaw = defaultVal(emp.getContractorName(), "FRIENDS ENTERPRISE");
        String contractorTitleText = "M/S. " + contractorRaw
                + (emp.getContractorAddress() != null ? ", " + emp.getContractorAddress() : "");
        Paragraph title = new Paragraph(contractorTitleText, engTitleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Phrase titleTranslation = createTrilingualPhrase(null,
                getContractorHindi(contractorRaw), getContractorBengali(contractorRaw),
                engSubTitleFont, hinSubTitleFont, benSubTitleFont);
        if (!titleTranslation.isEmpty()) {
            Paragraph titleTranslationPara = new Paragraph();
            titleTranslationPara.setAlignment(Element.ALIGN_CENTER);
            titleTranslationPara.setSpacingAfter(2f);
            titleTranslationPara.add(titleTranslation);
            document.add(titleTranslationPara);
        }

        String siteRaw = "Site: " + defaultVal(emp.getSiteName(), "GRAPHITE INDIA LIMITED, DURGAPUR");
        Paragraph site = new Paragraph();
        site.setAlignment(Element.ALIGN_CENTER);
        site.add(createTrilingualPhrase(siteRaw, "ग्रेफाइट इंडिया लिमिटेड, दुर्गापुर", "গ্রাফাইট ইন্ডিয়া লিমিটেড, দুর্গাপুর",
                engSubTitleFont, hinSubTitleFont, benSubTitleFont));
        document.add(site);

        String rawMonth = (emp.getMonth() != null && !emp.getMonth().isBlank()) ? emp.getMonth() : "AUGUST";
        String rawYear = (emp.getYear() != null && !emp.getYear().isBlank()) ? emp.getYear() : "2026";

        String engPeriod = "PAY SLIP FOR " + rawMonth + " " + rawYear;
        String hinPeriod = "वेतन पर्ची - " + getMonthHindi(rawMonth) + " " + rawYear;
        String benPeriod = "পে স্লিপ - " + getMonthBengali(rawMonth) + " " + toBengaliDigits(rawYear);

        Paragraph period = new Paragraph();
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(10);
        period.add(createTrilingualPhrase(engPeriod, hinPeriod, benPeriod,
                engHeaderFont, hinHeaderFont, benHeaderFont));
        document.add(period);

        // --- Employee Info Table (label+translation on the left of each pair, value to its right) ---
        // Field order follows the PAYSILP sheet: slip no/name, month/ESI/UAN, attendance
        // (days worked, PL, CL, festival, total), basic rate, then the ESIC/EPF wage bases.
        PdfPTable infoTable = new PdfPTable(4);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{2.1f, 1.4f, 2.1f, 1.4f});

        addInfoCell(infoTable, "Pay Slip No", "पे स्लिप नं.", "পে স্লিপ নং", String.valueOf(emp.getSlNo()),
                engBoldFont, hinSmallFont, benSmallFont, engBoldFont);
        addInfoCell(infoTable, "Employee Name", "कर्मचारी का नाम", "শ্রমিকের নাম", emp.getName(),
                engBoldFont, hinSmallFont, benSmallFont, engBoldFont);
        addInfoCell(infoTable, "Pay Period", null, null, rawMonth + " " + rawYear,
                engBoldFont, hinSmallFont, benSmallFont, engBoldFont);
        addInfoCell(infoTable, "ESI No", "ई.एस.आई. नं.", "ই.এস.আই নং", defaultVal(emp.getEsiNo(), "-"),
                engBoldFont, hinSmallFont, benSmallFont, engBoldFont);
        addInfoCell(infoTable, "UAN No", "यू.ए.एन. नं.", "ইউ.এ.এন নং", emp.getUan(),
                engBoldFont, hinSmallFont, benSmallFont, engBoldFont);
        addInfoCell(infoTable, "Designation", "पद", "পদ", defaultVal(emp.getDesignation(), "WORKMAN"),
                engBoldFont, hinSmallFont, benSmallFont, engBoldFont);
        addInfoCell(infoTable, "Days Worked", "कार्य दिवसों की संख्या", "কাজের দিনের সংখ্যা", defaultVal(emp.getDaysWorked(), "0"),
                engBoldFont, hinSmallFont, benSmallFont, engBoldFont);
        addInfoCell(infoTable, "PL (Paid Leave)", "अर्जित अवकाश", "অর্জিত ছুটি", defaultVal(emp.getPl(), "0"),
                engBoldFont, hinSmallFont, benSmallFont, engBoldFont);
        addInfoCell(infoTable, "CL (Casual Leave)", "कैज़ुअल लीव", "ক্যাজুয়াল লিভ", defaultVal(emp.getCl(), "0"),
                engBoldFont, hinSmallFont, benSmallFont, engBoldFont);
        addInfoCell(infoTable, "Festival Holiday", "त्यौहार की छुट्टी", "উৎসবের ছুটি", defaultVal(emp.getFestivalLeave(), "0"),
                engBoldFont, hinSmallFont, benSmallFont, engBoldFont);
        addInfoCell(infoTable, "Total Days", "कुल दिन", "মোট দিন", defaultVal(emp.getTotalDays(), "0"),
                engBoldFont, hinSmallFont, benSmallFont, engBoldFont);
        addInfoCell(infoTable, "Basic Rate", "बेसिक रेट", "বেসিক রেট", "Rs. " + formatAmount(defaultVal(emp.getBasicRate(), "0.00")),
                engBoldFont, hinSmallFont, benSmallFont, engBoldFont);
        addInfoCell(infoTable, "ESIC Salary", "ईएसआईसी वेतन", "ইএসআইসি বেতন", "Rs. " + formatAmount(defaultVal(emp.getEsicSalary(), "0.00")),
                engBoldFont, hinSmallFont, benSmallFont, engBoldFont);
        addInfoCell(infoTable, "EPF Salary", "कुल ईपीएफ वेतन", "মোট ইপিএফ বেতন", "Rs. " + formatAmount(defaultVal(emp.getEpfoSalary(), "0.00")),
                engBoldFont, hinSmallFont, benSmallFont, engBoldFont);

        document.add(infoTable);

        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(6);
        document.add(spacer);

        // --- Earnings & Deductions Table ---
        PdfPTable payTable = new PdfPTable(4);
        payTable.setWidthPercentage(100);
        payTable.setWidths(new float[]{3.2f, 1.2f, 3.2f, 1.2f});

        // Header Row
        addHeaderCell(payTable, "Earnings Description", "उपार्जन", "উপার্জনের বিবরণ", engWhiteBoldFont, hinWhiteBoldFont, benWhiteBoldFont);
        addHeaderCell(payTable, "Amount (Rs.)", "राशि", "টাকা", engWhiteBoldFont, hinWhiteBoldFont, benWhiteBoldFont);
        addHeaderCell(payTable, "Deductions Description", "कटौती", "কর্তনের বিবরণ", engWhiteBoldFont, hinWhiteBoldFont, benWhiteBoldFont);
        addHeaderCell(payTable, "Amount (Rs.)", "राशि", "টাকা", engWhiteBoldFont, hinWhiteBoldFont, benWhiteBoldFont);

        // Every wage head from the Form XVII / PAYSILP sheet is listed, showing 0.00 when not
        // applicable, so the payslip is a complete statutory record rather than only the
        // non-zero lines.
        List<String[]> earnings = new ArrayList<>();
        addRow(earnings, "Basic Wages", "बेसिक वेतन", "বেসিক মজুরি", emp.getBasicAmount());
        addRow(earnings, "Dearness Allowance (DA)", "डीए", "ডিএ", emp.getDa());
        addRow(earnings, "Washing Allowance", "धुलाई भत्ता", "ধোয়ার ভাতা", emp.getWashingAllowance());
        addRow(earnings, "Fuel Allowance", "फ्यूल अलाउंस", "ফুয়েল অ্যালাউন্স", emp.getFuelAllowance());
        addRow(earnings, "Attendance Allowance", "अटेंडेंस अलाउंस", "অ্যাটেনডেন্স অ্যালাউন্স", emp.getAttendanceAllowance());
        addRow(earnings, "Food Allowance", "फूड अलाउंस", "ফুড অ্যালাউন্স", emp.getFoodAllowance());
        addRow(earnings, "Gratuity", "ग्रेच्युटी", "গ্র্যাচুইটি", emp.getGratuity());
        addRow(earnings, "House Rent Allowance (HRA)", "एचआरए", "বাড়িভাড়া ভাতা", emp.getHra());
        addRow(earnings, "Overtime Hours", "ओटी", "ওটি", emp.getOvertimeDays());
        addRow(earnings, "Overtime Amount", "ओटी अमाउंट", "ওটি এমাউন্ট", emp.getOvertimeAmount());
        addRow(earnings, "Extra Production (Tons)", "टननेज", "টনেজ", emp.getExtraProduction());
        addRow(earnings, "Performance Incentive", "इंसेंटिव", "ইনসেনটিভ", emp.getPerformanceIncentive());

        List<String[]> deductions = new ArrayList<>();
        addRow(deductions, "E.P.F. Contribution", "पीएफ", "পিএফ", emp.getEpfDeduction());
        addRow(deductions, "E.S.I.C. Contribution", "ईएसआई", "ইএসআই", emp.getEsiDeduction());
        addRow(deductions, "Advance Deduction", "एडवांस", "অ্যাডভান্স", emp.getAdvanceDeduction());

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
                "GROSS EARNINGS", "कुल भुगतान की गई राशि", "মোট প্রদত্ত অর্থের পরিমাণ", formatAmount(defaultVal(emp.getGrossEarnings(), "0.00")),
                "TOTAL DEDUCTIONS", "कुल कटौती राशि", "মোট কর্তনকৃত অর্থ", formatAmount(defaultVal(emp.getTotalDeductions(), "0.00")),
                engBoldFont, hinBoldFont, benBoldFont, engBoldFont);

        // Net Payable Row
        Phrase netLabelPhrase = createTrilingualPhrase("NET PAYABLE", "नेट पेमेंट", "নেট পেমেন্ট",
                engBoldFont, hinBoldFont, benBoldFont);
        PdfPCell netLabelCell = new PdfPCell(netLabelPhrase);
        netLabelCell.setBackgroundColor(new Color(219, 234, 254));
        netLabelCell.setPadding(5);
        payTable.addCell(netLabelCell);

        Phrase netValPhrase = new Phrase("Rs. " + formatAmount(defaultVal(emp.getNetPayable(), "0.00")), engBoldFont);
        PdfPCell netValCell = new PdfPCell(netValPhrase);
        netValCell.setBackgroundColor(new Color(219, 234, 254));
        netValCell.setPadding(5);
        payTable.addCell(netValCell);

        PdfPCell emptyCell1 = new PdfPCell(new Phrase("", engNormalFont));
        emptyCell1.setBackgroundColor(new Color(219, 234, 254));
        payTable.addCell(emptyCell1);

        PdfPCell emptyCell2 = new PdfPCell(new Phrase("", engNormalFont));
        emptyCell2.setBackgroundColor(new Color(219, 234, 254));
        payTable.addCell(emptyCell2);

        document.add(payTable);

        // Footer Note
        Paragraph footer = new Paragraph();
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(16);
        footer.add(createTrilingualPhrase("This is an official computer-generated payslip", "यह एक आधिकारिक कंप्यूटर जनित वेतन पर्ची है।", "এটি একটি কম্পিউটার দ্বারা তৈরি পে স্লিপ",
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
            appendIndicText(p, hindiText, hindiAwtFont, hinFont != null ? hinFont : engFont);
        }
        if (bengaliText != null && !bengaliText.isEmpty()) {
            if (!p.isEmpty()) {
                p.add(new Chunk(" / ", engFont));
            }
            appendIndicText(p, bengaliText, bengaliAwtFont, benFont != null ? benFont : engFont);
        }
        return p;
    }

    // Devanagari and Bengali vowel sign I (ि / ি) are "pre-base" marks: stored in Unicode
    // after their consonant, but must visually render before it. OpenPDF draws each
    // character in raw storage order with no script-aware reordering, so without this fix
    // words like "राशि" or "दिवस" render with the mark on the wrong side of the consonant.
    // This swaps the mark to precede its consonant (and any conjunct cluster before it) in
    // the string itself, so plain left-to-right character rendering shows it correctly -
    // unlike full complex-script shaping, this doesn't change how any character's width is
    // computed, so it can't cause the table/cell layout issues that approach did.
    private static final Pattern DEVANAGARI_PREBASE_MATRA =
            Pattern.compile("((?:[क-ह]्)*[क-ह])ि");
    private static final Pattern BENGALI_PREBASE_MATRA =
            Pattern.compile("((?:[ক-হ]্)*[ক-হ])ি");

    // Bengali vowel sign O canonically decomposes (per the Unicode Character Database) into
    // E + AA: the E part is pre-base, the AA part is post-base, e.g. "মো" is visually "ে"+"ম"+"া".
    // OpenPDF has no shaping to split it into those two positioned glyphs, so words like "মোট"
    // render with a stray/misplaced mark. Devanagari's equivalent isn't formally decomposable
    // in Unicode, but uses the identical two-part visual convention in standard typography, so
    // the same fix applies there too. AU is a different, less certain decomposition and is left
    // alone.
    private static final Pattern DEVANAGARI_SPLIT_O_MATRA =
            Pattern.compile("((?:[क-ह]्)*[क-ह])ो");
    private static final Pattern BENGALI_SPLIT_O_MATRA =
            Pattern.compile("((?:[ক-হ]্)*[ক-হ])ো");

    private String fixPreBaseMatra(String text) {
        if (text == null || text.isEmpty()) return text;
        text = DEVANAGARI_SPLIT_O_MATRA.matcher(text).replaceAll("े$1ा");
        text = BENGALI_SPLIT_O_MATRA.matcher(text).replaceAll("ে$1া");
        text = DEVANAGARI_PREBASE_MATRA.matcher(text).replaceAll("ि$1");
        text = BENGALI_PREBASE_MATRA.matcher(text).replaceAll("ি$1");
        return text;
    }

    /**
     * Adds one label+translation cell (English label bold, Hindi/Bengali translation
     * smaller/muted below it) followed by a separate value cell to its right, so the
     * value sits beside the label instead of stacked underneath it.
     */
    private void addInfoCell(PdfPTable table, String engLabel, String hinLabel, String benLabel, String value,
                             Font engLabelFont, Font hinLabelFont, Font benLabelFont, Font valFont) {
        PdfPCell labelCell = new PdfPCell();
        labelCell.setBackgroundColor(new Color(248, 250, 252));
        labelCell.setBorderColor(new Color(203, 213, 225));
        labelCell.setPadding(6);
        labelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Paragraph labelPara = new Paragraph(engLabel, engLabelFont);
        labelPara.setSpacingAfter(1f);
        labelCell.addElement(labelPara);

        Phrase translationPhrase = createTrilingualPhrase(null, hinLabel, benLabel, engLabelFont, hinLabelFont, benLabelFont);
        if (!translationPhrase.isEmpty()) {
            labelCell.addElement(new Paragraph(translationPhrase));
        }
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "", valFont));
        valueCell.setBackgroundColor(new Color(248, 250, 252));
        valueCell.setBorderColor(new Color(203, 213, 225));
        valueCell.setPadding(6);
        valueCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(valueCell);
    }

    private void addHeaderCell(PdfPTable table, String engText, String hinText, String benText,
                               Font engFont, Font hinFont, Font benFont) {
        Phrase phrase = createTrilingualPhrase(engText, hinText, benText, engFont, hinFont, benFont);
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBackgroundColor(new Color(51, 91, 158));
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
            c.setBackgroundColor(new Color(239, 246, 255));
            c.setPadding(5);
            c.setBorderColor(new Color(203, 213, 225));
            table.addCell(c);
        }
    }

    private void addRow(List<String[]> list, String engLabel, String hinLabel, String benLabel, String val) {
        list.add(new String[]{engLabel, hinLabel, benLabel, formatAmount(defaultVal(val, "0.00"))});
    }

    private String defaultVal(String val, String fallback) {
        return (val != null && !val.isBlank()) ? val : fallback;
    }

    // OpenPDF draws each character's glyph independently with no OpenType shaping, which
    // garbles Indic scripts (matra reordering, split vowels, conjuncts). Rather than
    // approximate shaping by rewriting strings, Hindi/Bengali text is rendered to a small
    // transparent image via java.awt Graphics2D - whose text pipeline does apply full
    // shaping - and embedded inline as an image Chunk. An image has a fixed width, so this
    // can't disturb PdfPTable/ColumnText layout the way LayoutProcessor did. Each word is
    // its own image, joined by real space Chunks, so narrow cells can still wrap between
    // words. Falls back to plain text (with the string-level matra fixes) if rendering fails.
    private void appendIndicText(Phrase phrase, String text, java.awt.Font awtFont, Font pdfFont) {
        if (awtFont == null) {
            phrase.add(new Chunk(fixPreBaseMatra(text), pdfFont));
            return;
        }
        float sizePt = pdfFont.getSize() > 0 ? pdfFont.getSize() : 8f;
        Color color = pdfFont.getColor() != null ? pdfFont.getColor() : Color.BLACK;
        int style = pdfFont.getStyle();
        boolean bold = style != Font.UNDEFINED && (style & Font.BOLD) != 0;

        Font latinFont = FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, sizePt, color);

        String[] words = text.trim().split("\\s+");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                phrase.add(new Chunk(" ", pdfFont));
            }
            // Only runs of Indic-script characters are rendered as images. ASCII punctuation,
            // hyphens and digits mixed into a word ("ई.एस.आई", "লিমিটেড,", "2026") are emitted
            // as normal Helvetica text: the Indic fonts don't necessarily carry those glyphs
            // (Noto Sans Bengali shows them as boxes), and as text they match the rest of the page.
            for (String run : splitIndicRuns(words[i])) {
                if (!isIndicScript(run.charAt(0))) {
                    phrase.add(new Chunk(run, latinFont));
                    continue;
                }
                try {
                    phrase.add(buildIndicWordChunk(run, awtFont, sizePt, color, bold));
                } catch (Throwable t) {
                    log.warn("Image rendering failed for Indic text '{}', using plain text: {}", run, t.toString());
                    phrase.add(new Chunk(fixPreBaseMatra(run), pdfFont));
                }
            }
        }
    }

    private static boolean isIndicScript(char c) {
        // Devanagari (U+0900-097F) and Bengali (U+0980-09FF) blocks, plus ZWNJ/ZWJ which
        // must stay inside a script run so they keep affecting conjunct formation.
        return (c >= 'ऀ' && c <= '৿') || c == '‌' || c == '‍';
    }

    private static List<String> splitIndicRuns(String word) {
        List<String> runs = new ArrayList<>();
        int start = 0;
        for (int i = 1; i <= word.length(); i++) {
            if (i == word.length() || isIndicScript(word.charAt(i)) != isIndicScript(word.charAt(start))) {
                runs.add(word.substring(start, i));
                start = i;
            }
        }
        return runs;
    }

    private Chunk buildIndicWordChunk(String word, java.awt.Font awtFont, float sizePt, Color color, boolean bold)
            throws Exception {
        String cacheKey = awtFont.getFontName() + "|" + sizePt + "|" + color.getRGB() + "|" + bold + "|" + word;
        RenderedText rendered = INDIC_TEXT_IMAGE_CACHE.get(cacheKey);
        if (rendered == null) {
            rendered = renderTextToImage(word, awtFont, sizePt, color, bold);
            INDIC_TEXT_IMAGE_CACHE.put(cacheKey, rendered);
        }
        // Image bottom lands at baseline + offsetY, so shifting down by the descent puts the
        // rendered glyph baseline exactly on the surrounding text baseline.
        return new Chunk(Image.getInstance(rendered.image()), 0, -rendered.descentPt(), true);
    }

    private static RenderedText renderTextToImage(String text, java.awt.Font awtFont, float sizePt, Color color, boolean bold)
            throws Exception {
        // Render at 4x and scale down in the PDF so the text stays crisp when zoomed/printed.
        final float scale = 4f;
        java.awt.Font font = awtFont.deriveFont(bold ? java.awt.Font.BOLD : java.awt.Font.PLAIN, sizePt * scale);

        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        pg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        pg.setFont(font);
        FontMetrics fm = pg.getFontMetrics();
        Rectangle2D bounds = fm.getStringBounds(text, pg);
        pg.dispose();

        int pad = 2;
        int width = Math.max(1, (int) Math.ceil(bounds.getWidth()) + 2 * pad);
        int height = Math.max(1, fm.getAscent() + fm.getDescent() + 2 * pad);
        int baselineY = pad + fm.getAscent();

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, pad, baselineY);
        g.dispose();

        Image pdfImage = Image.getInstance(img, (Color) null);
        pdfImage.scaleAbsolute(width / scale, height / scale);
        float descentPt = (height - baselineY) / scale;
        return new RenderedText(pdfImage, descentPt);
    }

    private static java.awt.Font loadAwtFont(String classpathResource) {
        try (InputStream is = PdfGeneratorService.class.getResourceAsStream(classpathResource)) {
            if (is == null) {
                lastFontLoadError = classpathResource + ": resource not found on classpath";
                log.warn("AWT font resource {} not found; Indic text will use plain-text fallback", classpathResource);
                return null;
            }
            return java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, is);
        } catch (Throwable t) {
            lastFontLoadError = classpathResource + ": " + describe(t);
            log.warn("Could not load AWT font {}; Indic text will use plain-text fallback: {}", classpathResource, describe(t));
            return null;
        }
    }

    /**
     * Formats a raw numeric string with thousands separators (e.g. "10694.00" -> "10,694.00")
     * to match the statutory payslip format. Falls back to the original value if unparseable.
     */
    private String formatAmount(String val) {
        if (val == null || val.isBlank()) return val;
        try {
            double d = Double.parseDouble(val.replaceAll("[^0-9.\\-]", ""));
            return new DecimalFormat("#,##0.00").format(d);
        } catch (NumberFormatException e) {
            return val;
        }
    }

    /**
     * Known contractor name transliterations. Falls back to null (English-only display
     * via createTrilingualPhrase) for contractors not in this list, since arbitrary
     * company names can't be reliably auto-transliterated into Hindi/Bengali script.
     */
    private String getContractorHindi(String contractorName) {
        if (contractorName == null) return "मैसर्स फ्रेंड्स एंटरप्राइज";
        return switch (contractorName.trim().toUpperCase()) {
            case "FRIENDS ENTERPRISE" -> "मैसर्स फ्रेंड्स एंटरप्राइज";
            case "B.P. TRANSPORT", "BP TRANSPORT", "B.P TRANSPORT" -> "मैसर्स बी.पी. ट्रांसपोर्ट";
            default -> null;
        };
    }

    private String getContractorBengali(String contractorName) {
        if (contractorName == null) return "মেসার্স ফ্রেন্ডস এন্টারপ্রাইজ";
        return switch (contractorName.trim().toUpperCase()) {
            case "FRIENDS ENTERPRISE" -> "মেসার্স ফ্রেন্ডস এন্টারপ্রাইজ";
            case "B.P. TRANSPORT", "BP TRANSPORT", "B.P TRANSPORT" -> "মেসার্স বি.পি. ট্রান্সপোর্ট";
            default -> null;
        };
    }

    private String getMonthHindi(String month) {
        if (month == null) return "";
        return switch (month.toUpperCase().trim()) {
            case "JANUARY" -> "जनवरी";
            case "FEBRUARY" -> "फ़रवरी";
            case "MARCH" -> "मार्च";
            case "APRIL" -> "अप्रैल";
            case "MAY" -> "मई";
            case "JUNE" -> "जून";
            case "JULY" -> "जुलाई";
            case "AUGUST" -> "अगस्त";
            case "SEPTEMBER" -> "सितंबर";
            case "OCTOBER" -> "अक्टूबर";
            case "NOVEMBER" -> "नवंबर";
            case "DECEMBER" -> "दिसंबर";
            default -> month;
        };
    }

    private String getMonthBengali(String month) {
        if (month == null) return "";
        return switch (month.toUpperCase().trim()) {
            case "JANUARY" -> "জানুয়ারি";
            case "FEBRUARY" -> "ফেব্রুয়ারি";
            case "MARCH" -> "মার্চ";
            case "APRIL" -> "এপ্রিল";
            case "MAY" -> "মে";
            case "JUNE" -> "জুন";
            case "JULY" -> "জুলাই";
            case "AUGUST" -> "আগস্ট";
            case "SEPTEMBER" -> "সেপ্টেম্বর";
            case "OCTOBER" -> "অক্টোবর";
            case "NOVEMBER" -> "নভেম্বর";
            case "DECEMBER" -> "ডিসেম্বর";
            default -> month;
        };
    }

    private String toBengaliDigits(String numberStr) {
        if (numberStr == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : numberStr.toCharArray()) {
            if (c >= '0' && c <= '9') {
                sb.append((char) ('\u09E6' + (c - '0'))); // ০ to ৯
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
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
