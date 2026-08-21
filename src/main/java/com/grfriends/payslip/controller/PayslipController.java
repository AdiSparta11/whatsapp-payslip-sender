package com.grfriends.payslip.controller;

import com.grfriends.payslip.config.WhatsAppConfig;
import com.grfriends.payslip.model.DispatchResult;
import com.grfriends.payslip.model.Employee;
import com.grfriends.payslip.service.ExcelParserService;
import com.grfriends.payslip.service.PdfGeneratorService;
import com.grfriends.payslip.service.WhatsAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class PayslipController {

    private static final Logger log = LoggerFactory.getLogger(PayslipController.class);

    private final ExcelParserService excelParserService;
    private final PdfGeneratorService pdfGeneratorService;
    private final WhatsAppService whatsAppService;
    private final WhatsAppConfig whatsAppConfig;

    public PayslipController(ExcelParserService excelParserService,
                             PdfGeneratorService pdfGeneratorService,
                             WhatsAppService whatsAppService,
                             WhatsAppConfig whatsAppConfig) {
        this.excelParserService = excelParserService;
        this.pdfGeneratorService = pdfGeneratorService;
        this.whatsAppService = whatsAppService;
        this.whatsAppConfig = whatsAppConfig;
    }

    @GetMapping("/")
    public String index(Model model) {
        boolean configured = whatsAppConfig.getAccessToken() != null &&
                !whatsAppConfig.getAccessToken().startsWith("REPLACE_") &&
                whatsAppConfig.getPhoneNumberId() != null &&
                !whatsAppConfig.getPhoneNumberId().startsWith("REPLACE_");

        model.addAttribute("apiConfigured", configured);
        model.addAttribute("phoneNumberId", whatsAppConfig.getPhoneNumberId());
        return "index";
    }

    @PostMapping("/dispatch")
    public String dispatch(@RequestParam("wageSheet") MultipartFile wageSheetFile,
                           @RequestParam("contactMaster") MultipartFile contactMasterFile,
                           @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun,
                           @RequestParam(value = "customCaption", required = false) String customCaption,
                           Model model) {

        List<DispatchResult> results = new ArrayList<>();
        int sentCount = 0;
        int skippedNoContactCount = 0;
        int skippedNoPhoneCount = 0;
        int failedCount = 0;

        try {
            if (wageSheetFile.isEmpty() || contactMasterFile.isEmpty()) {
                model.addAttribute("errorMessage", "Both Wage Sheet Excel and Contact Master Excel files are required.");
                return "index";
            }

            log.info("Starting dispatch. Wage file: {}, Contact file: {}, dryRun: {}",
                    wageSheetFile.getOriginalFilename(), contactMasterFile.getOriginalFilename(), dryRun);

            // Step 1: Parse Wage Sheet
            List<Employee> employees = excelParserService.parseWageSheet(wageSheetFile.getInputStream());
            log.info("Parsed {} employees from wage sheet.", employees.size());

            // Step 2: Parse Contact Master
            Map<String, String> contacts = excelParserService.parseContactMaster(contactMasterFile.getInputStream());
            log.info("Parsed {} contacts from contact master.", contacts.size());

            // Step 3: Match Employees to Contacts
            excelParserService.matchEmployeesToContacts(employees, contacts);

            boolean isMetaConfigured = whatsAppConfig.getAccessToken() != null &&
                    !whatsAppConfig.getAccessToken().startsWith("REPLACE_");

            // Step 4: Process Each Employee
            for (int i = 0; i < employees.size(); i++) {
                Employee emp = employees.get(i);
                int idx = i + 1;

                if (!emp.hasPhone()) {
                    if (contacts.containsKey(emp.getUan())) {
                        results.add(DispatchResult.skippedNoPhone(idx, emp.getName(), emp.getUan()));
                        skippedNoPhoneCount++;
                    } else {
                        results.add(DispatchResult.skippedNoContact(idx, emp.getName(), emp.getUan()));
                        skippedNoContactCount++;
                    }
                    continue;
                }

                // Generate PDF
                byte[] pdfBytes;
                try {
                    pdfBytes = pdfGeneratorService.generatePayslipPdf(emp);
                } catch (Exception e) {
                    log.error("Failed to generate PDF for employee UAN {}: {}", emp.getUan(), e.getMessage());
                    results.add(DispatchResult.failed(idx, emp.getName(), emp.getUan(), emp.getPhoneNumber(),
                            "PDF Generation Error: " + e.getMessage()));
                    failedCount++;
                    continue;
                }

                String filename = String.format("Payslip_%s_%s_%s.pdf",
                        emp.getName().replaceAll("[^a-zA-Z0-9]", "_"),
                        emp.getMonth(), emp.getYear());

                String caption = (customCaption != null && !customCaption.isBlank())
                        ? customCaption
                        : String.format("Payslip for %s %s - M/S. FRIENDS ENTERPRISE", emp.getMonth(), emp.getYear());

                // If dry run or Meta credentials not set, simulate send
                if (dryRun || !isMetaConfigured) {
                    String statusMsg = dryRun ? "Dry Run (Simulated Success)" : "Meta API Credentials not configured (Simulated Success)";
                    results.add(new DispatchResult(idx, emp.getName(), emp.getUan(), emp.getPhoneNumber(),
                            DispatchResult.Status.SENT, statusMsg, true));
                    sentCount++;
                } else {
                    // Real WhatsApp Dispatch via Meta Cloud API
                    try {
                        String mediaId = whatsAppService.uploadPdfMedia(pdfBytes, filename);
                        whatsAppService.sendDocumentMessage(emp.getPhoneNumber(), mediaId, filename, caption);

                        results.add(DispatchResult.sent(idx, emp.getName(), emp.getUan(), emp.getPhoneNumber()));
                        sentCount++;
                    } catch (Exception e) {
                        log.error("Failed WhatsApp dispatch for employee {}: {}", emp.getUan(), e.getMessage());
                        results.add(DispatchResult.failed(idx, emp.getName(), emp.getUan(), emp.getPhoneNumber(),
                                "WhatsApp Dispatch Error: " + e.getMessage()));
                        failedCount++;
                    }
                }
            }

            model.addAttribute("results", results);
            model.addAttribute("totalEmployees", employees.size());
            model.addAttribute("sentCount", sentCount);
            model.addAttribute("skippedNoContactCount", skippedNoContactCount);
            model.addAttribute("skippedNoPhoneCount", skippedNoPhoneCount);
            model.addAttribute("failedCount", failedCount);
            model.addAttribute("isDryRun", dryRun || !isMetaConfigured);
            model.addAttribute("dispatchCompleted", true);

        } catch (Exception e) {
            log.error("Error processing dispatch: ", e);
            model.addAttribute("errorMessage", "Error processing files: " + e.getMessage());
        }

        return "index";
    }
}
