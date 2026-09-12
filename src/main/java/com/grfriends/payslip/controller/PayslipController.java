package com.grfriends.payslip.controller;

import com.grfriends.payslip.model.DispatchResult;
import com.grfriends.payslip.model.Employee;
import com.grfriends.payslip.service.ExcelParserService;
import com.grfriends.payslip.service.PdfGeneratorService;
import com.grfriends.payslip.service.ZipService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
public class PayslipController {

    private static final Logger log = LoggerFactory.getLogger(PayslipController.class);

    private final ExcelParserService excelParserService;
    private final PdfGeneratorService pdfGeneratorService;
    private final ZipService zipService;

    public PayslipController(ExcelParserService excelParserService,
                             PdfGeneratorService pdfGeneratorService,
                             ZipService zipService) {
        this.excelParserService = excelParserService;
        this.pdfGeneratorService = pdfGeneratorService;
        this.zipService = zipService;
    }

    @GetMapping("/")
    public String index(Model model) {
        return "index";
    }

    @PostMapping("/dispatch")
    public String dispatch(@RequestParam("wageSheet") MultipartFile wageSheetFile,
                           @RequestParam(value = "contactMaster", required = false) MultipartFile contactMasterFile,
                           @RequestParam(value = "customCaption", required = false) String customCaption,
                           HttpSession session,
                           Model model) {

        List<DispatchResult> matchedResults = new ArrayList<>();
        List<DispatchResult> unmatchedResults = new ArrayList<>();
        Map<String, byte[]> pdfMap = new LinkedHashMap<>();
        Map<String, String> filenameMap = new LinkedHashMap<>();

        try {
            if (wageSheetFile.isEmpty()) {
                model.addAttribute("errorMessage", "Please select a Monthly Payroll Excel file (.xlsx / .xls).");
                return "index";
            }

            List<Employee> employees;
            ExcelParserService.ContactStore contactStore;

            if (contactMasterFile != null && !contactMasterFile.isEmpty()) {
                log.info("Processing separate files: Wage Sheet ({}) + Contact Master ({})",
                        wageSheetFile.getOriginalFilename(), contactMasterFile.getOriginalFilename());

                employees = excelParserService.parseWageSheet(wageSheetFile.getInputStream());
                contactStore = excelParserService.parseContactMasterStore(contactMasterFile.getInputStream());
                excelParserService.matchEmployeesToContacts(employees, contactStore);
            } else {
                log.info("Processing single Master Excel workbook: {}", wageSheetFile.getOriginalFilename());
                ExcelParserService.ParsedWorkbookResult result =
                        excelParserService.parseSingleWorkbook(wageSheetFile.getInputStream());
                employees = result.employees();
                contactStore = result.contactStore();
            }

            log.info("Parsed {} total employees. Contact store size: {}", employees.size(), contactStore.size());

            String templateMsg = (customCaption != null && !customCaption.isBlank())
                    ? customCaption
                    : "Hello {name}, please find your payslip for {month} {year} attached.";

            String detectedMonth = "JULY";
            String detectedYear = "2026";

            // Step 4: Generate Local PDFs and Build Dispatch Table Rows
            for (int i = 0; i < employees.size(); i++) {
                Employee emp = employees.get(i);
                int idx = i + 1;

                if (emp.getMonth() != null) detectedMonth = emp.getMonth();
                if (emp.getYear() != null) detectedYear = emp.getYear();

                if (!emp.hasPhone()) {
                    if (contactStore.isKnown(emp.getUan(), emp.getEsiNo(), emp.getName())) {
                        unmatchedResults.add(DispatchResult.skippedNoPhone(idx, emp.getName(), emp.getUan()));
                    } else {
                        unmatchedResults.add(DispatchResult.skippedNoContact(idx, emp.getName(), emp.getUan()));
                    }
                    continue;
                }

                // Generate PDF
                byte[] pdfBytes;
                try {
                    pdfBytes = pdfGeneratorService.generatePayslipPdf(emp);
                } catch (Exception e) {
                    log.error("Failed to generate PDF for employee UAN {}: {}", emp.getUan(), e.getMessage());
                    unmatchedResults.add(DispatchResult.failed(idx, emp.getName(), emp.getUan(), emp.getPhoneNumber(),
                            "PDF Generation Error: " + e.getMessage()));
                    continue;
                }

                String sanitizedName = emp.getName().replaceAll("[^a-zA-Z0-9]", "_").replaceAll("_+", "_");
                String filename = String.format("Payslip_%s_%s_%s.pdf",
                        sanitizedName, emp.getMonth(), emp.getYear());

                String cleanPhone = emp.getPhoneNumber().replaceAll("\\D", "");

                // Substitute caption variables
                String messageText = templateMsg
                        .replace("{name}", emp.getName())
                        .replace("{month}", emp.getMonth())
                        .replace("{year}", emp.getYear());

                String encodedMsg = URLEncoder.encode(messageText, StandardCharsets.UTF_8);
                String waLink = "https://wa.me/" + cleanPhone + "?text=" + encodedMsg;

                DispatchResult res = DispatchResult.matched(idx, emp.getName(), emp.getUan(),
                        emp.getPhoneNumber(), cleanPhone, filename, waLink, emp.getMonth(), emp.getYear());

                matchedResults.add(res);
                pdfMap.put(emp.getUan(), pdfBytes);
                filenameMap.put(emp.getUan(), filename);
            }

            // Save batch to session for ZIP & single PDF downloads
            session.setAttribute("pdfMap", pdfMap);
            session.setAttribute("filenameMap", filenameMap);
            session.setAttribute("batchPeriod", detectedMonth + "_" + detectedYear);

            model.addAttribute("matchedResults", matchedResults);
            model.addAttribute("unmatchedResults", unmatchedResults);
            model.addAttribute("totalEmployees", employees.size());
            model.addAttribute("matchedCount", matchedResults.size());
            model.addAttribute("unmatchedCount", unmatchedResults.size());
            model.addAttribute("batchMonth", detectedMonth);
            model.addAttribute("batchYear", detectedYear);
            model.addAttribute("customCaption", templateMsg);
            model.addAttribute("dispatchCompleted", true);

        } catch (Exception e) {
            log.error("Error processing Excel file: ", e);
            model.addAttribute("errorMessage", "Error processing Excel file: " + e.getMessage());
        }

        return "index";
    }

    /**
     * Download all or selected matched employees' PDFs packaged into a ZIP archive.
     */
    @RequestMapping(value = "/download-zip", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<byte[]> downloadZip(@RequestParam(value = "uans", required = false) List<String> uans,
                                             HttpSession session) {

        @SuppressWarnings("unchecked")
        Map<String, byte[]> pdfMap = (Map<String, byte[]>) session.getAttribute("pdfMap");

        @SuppressWarnings("unchecked")
        Map<String, String> filenameMap = (Map<String, String>) session.getAttribute("filenameMap");

        String batchPeriod = (String) session.getAttribute("batchPeriod");
        if (batchPeriod == null) batchPeriod = "MONTHLY";

        if (pdfMap == null || pdfMap.isEmpty()) {
            return ResponseEntity.badRequest().body("No generated payslips found in session. Please process files first.".getBytes());
        }

        Map<String, byte[]> targetFiles = new LinkedHashMap<>();

        if (uans != null && !uans.isEmpty()) {
            for (String uan : uans) {
                if (pdfMap.containsKey(uan)) {
                    String fn = filenameMap.getOrDefault(uan, "Payslip_" + uan + ".pdf");
                    targetFiles.put(fn, pdfMap.get(uan));
                }
            }
        } else {
            for (Map.Entry<String, byte[]> entry : pdfMap.entrySet()) {
                String fn = filenameMap.getOrDefault(entry.getKey(), "Payslip_" + entry.getKey() + ".pdf");
                targetFiles.put(fn, entry.getValue());
            }
        }

        try {
            byte[] zipBytes = zipService.createZipArchive(targetFiles);
            String zipFilename = "Payslips_" + batchPeriod + ".zip";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipFilename + "\"")
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .body(zipBytes);
        } catch (Exception e) {
            log.error("Failed to generate ZIP archive: ", e);
            return ResponseEntity.internalServerError().body(("ZIP creation error: " + e.getMessage()).getBytes());
        }
    }

    /**
     * Download a single employee's generated PDF by UAN.
     */
    @GetMapping("/download-pdf/{uan}")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable("uan") String uan, HttpSession session) {
        @SuppressWarnings("unchecked")
        Map<String, byte[]> pdfMap = (Map<String, byte[]>) session.getAttribute("pdfMap");

        @SuppressWarnings("unchecked")
        Map<String, String> filenameMap = (Map<String, String>) session.getAttribute("filenameMap");

        if (pdfMap == null || !pdfMap.containsKey(uan)) {
            return ResponseEntity.notFound().build();
        }

        byte[] pdfBytes = pdfMap.get(uan);
        String filename = filenameMap.getOrDefault(uan, "Payslip_" + uan + ".pdf");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
