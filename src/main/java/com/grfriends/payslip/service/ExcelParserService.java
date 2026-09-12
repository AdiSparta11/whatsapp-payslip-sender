package com.grfriends.payslip.service;

import com.grfriends.payslip.model.Employee;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the GR Friends Enterprise wage sheet Excel and WhatsApp contact numbers.
 *
 * Supports both:
 * 1. Single Master Workbook containing both:
 *    - "FRIENDS ENTERPRISE PAY SHEET" (Form XVII wage register)
 *    - "WHATS APP NO." (Contact matching tab with UAN, ESI, and WhatsApp Numbers)
 * 2. Separate Wage Sheet & Contact Master files.
 */
@Service
public class ExcelParserService {

    private static final Logger log = LoggerFactory.getLogger(ExcelParserService.class);
    private final DataFormatter dataFormatter = new DataFormatter();

    // Keywords to detect the header row in the wage sheet
    private static final List<String> WAGE_HEADER_KEYWORDS = List.of(
            "uan", "name of workman", "workman", "esi no", "sl. no", "sl.no",
            "designation", "basic wages", "net payable", "days worked", "basic rate"
    );

    // Keywords to detect the header row in the WhatsApp contact sheet
    private static final List<String> CONTACT_HEADER_KEYWORDS = List.of(
            "whatsapp", "whats app", "phone", "mobile", "contact", "uan no", "uan", "esi no", "e.s.i", "name of workman", "workman"
    );

    // Fixed POI 0-indexed column locations for Form XVII statutory wage sheet layout
    private static final int COL_SL_NO = 0;               // A
    private static final int COL_NAME = 1;                // B
    private static final int COL_UAN = 2;                 // C
    private static final int COL_ESI = 3;                 // D
    private static final int COL_DESIGNATION = 4;         // E
    private static final int COL_DAYS_WORKED = 5;         // F
    private static final int COL_TOTAL_DAYS = 9;          // J
    private static final int COL_DAILY_RATE = 10;         // K
    private static final int COL_BASIC = 11;              // L
    private static final int COL_DA = 12;                 // M
    private static final int COL_WASHING = 13;            // N
    private static final int COL_FUEL = 14;               // O
    private static final int COL_ATTENDANCE = 15;         // P
    private static final int COL_FOOD = 16;               // Q
    private static final int COL_GRATUITY = 17;           // R
    private static final int COL_HRA = 18;                // S
    private static final int COL_OT_DAYS = 19;            // T
    private static final int COL_OT_AMOUNT = 20;          // U
    private static final int COL_EXTRA_PRODUCTION = 21;   // V
    private static final int COL_PERFORMANCE_INCENTIVE = 22; // W
    private static final int COL_GROSS_TOTAL = 23;        // X
    private static final int COL_ESIC_SALARY = 24;        // Y
    private static final int COL_EPFO_SALARY = 25;        // Z
    private static final int COL_PF_DEDUCTION = 26;       // AA
    private static final int COL_ESI_EMPLOYEE_SHARE = 27; // AB
    private static final int COL_ADVANCE_DEDUCTION = 28;  // AC
    private static final int COL_TOTAL_DEDUCTION = 29;    // AD
    private static final int COL_NET_PAID = 30;           // AE

    // Month names for extraction from sheet title/header
    private static final List<String> MONTHS = List.of(
            "JANUARY", "FEBRUARY", "MARCH", "APRIL", "MAY", "JUNE",
            "JULY", "AUGUST", "SEPTEMBER", "OCTOBER", "NOVEMBER", "DECEMBER"
    );

    /**
     * Contact Store holding mappings by UAN, ESI, and Workman Name.
     */
    public static class ContactStore {
        private final Map<String, String> phoneByUan = new HashMap<>();
        private final Map<String, String> phoneByEsi = new HashMap<>();
        private final Map<String, String> phoneByName = new HashMap<>();
        private final Set<String> knownUans = new HashSet<>();
        private final Set<String> knownEsis = new HashSet<>();
        private final Set<String> knownNames = new HashSet<>();

        public void addContact(String uan, String esi, String name, String phone) {
            if (uan != null && !uan.isBlank()) {
                knownUans.add(uan);
                if (phone != null && !phone.isBlank()) {
                    phoneByUan.put(uan, phone);
                }
            }
            if (esi != null && !esi.isBlank()) {
                knownEsis.add(esi);
                if (phone != null && !phone.isBlank()) {
                    phoneByEsi.put(esi, phone);
                }
            }
            if (name != null && !name.isBlank()) {
                String norm = normalizeName(name);
                knownNames.add(norm);
                if (phone != null && !phone.isBlank()) {
                    phoneByName.put(norm, phone);
                }
            }
        }

        public String findPhone(String uan, String esi, String name) {
            if (uan != null && phoneByUan.containsKey(uan)) {
                return phoneByUan.get(uan);
            }
            if (esi != null && phoneByEsi.containsKey(esi)) {
                return phoneByEsi.get(esi);
            }
            if (name != null) {
                String norm = normalizeName(name);
                if (phoneByName.containsKey(norm)) {
                    return phoneByName.get(norm);
                }
            }
            return null;
        }

        public boolean isKnown(String uan, String esi, String name) {
            if (uan != null && knownUans.contains(uan)) return true;
            if (esi != null && knownEsis.contains(esi)) return true;
            if (name != null && knownNames.contains(normalizeName(name))) return true;
            return false;
        }

        public Map<String, String> toUanPhoneMap() {
            return new HashMap<>(phoneByUan);
        }

        public int size() {
            return Math.max(knownUans.size(), Math.max(knownEsis.size(), knownNames.size()));
        }

        private static String normalizeName(String raw) {
            if (raw == null) return "";
            return raw.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        }
    }

    public record ParsedWorkbookResult(List<Employee> employees, ContactStore contactStore) {}

    /**
     * Parse a single master workbook containing both Wage Sheet and WhatsApp Contact tabs.
     */
    public ParsedWorkbookResult parseSingleWorkbook(InputStream inputStream) throws Exception {
        Workbook workbook = new XSSFWorkbook(inputStream);

        Sheet wageSheet = findBestWageSheet(workbook);
        if (wageSheet == null) {
            workbook.close();
            throw new IllegalArgumentException("Could not find a wage sheet tab in the uploaded Excel file. " +
                    "Expected a sheet with column headers like 'UAN NO', 'NAME OF WORKMAN', 'BASIC WAGES', etc.");
        }

        log.info("Found wage sheet: '{}'", wageSheet.getSheetName());
        List<Employee> employees = parseWageSheetFromSheet(wageSheet);

        String siteName = detectSiteName(workbook);
        log.info("Detected site name: {}", siteName);

        String contractorAddress = detectContractorAddress(workbook);
        log.info("Detected contractor address: {}", contractorAddress);

        for (Employee emp : employees) {
            if (siteName != null) emp.setSiteName(siteName);
            if (contractorAddress != null) emp.setContractorAddress(contractorAddress);
        }

        Sheet contactSheet = findBestContactSheet(workbook);
        ContactStore contactStore = new ContactStore();
        if (contactSheet != null) {
            log.info("Found WhatsApp contact sheet: '{}'", contactSheet.getSheetName());
            contactStore = parseContactSheet(contactSheet);
            matchEmployeesToContacts(employees, contactStore);
        } else {
            log.warn("No separate WhatsApp / Contact tab detected in the workbook.");
        }

        workbook.close();
        return new ParsedWorkbookResult(employees, contactStore);
    }

    /**
     * Parse the wage sheet Excel and return a list of Employee objects.
     */
    public List<Employee> parseWageSheet(InputStream inputStream) throws Exception {
        Workbook workbook = new XSSFWorkbook(inputStream);
        Sheet wageSheet = findBestWageSheet(workbook);
        if (wageSheet == null) {
            workbook.close();
            throw new IllegalArgumentException("Could not find a wage sheet tab in the uploaded file. " +
                    "Expected a sheet with column headers like 'UAN NO', 'NAME OF WORKMAN', 'BASIC WAGES', etc.");
        }

        List<Employee> employees = parseWageSheetFromSheet(wageSheet);
        workbook.close();
        return employees;
    }

    /**
     * Parse the contact master Excel into ContactStore.
     */
    public ContactStore parseContactMasterStore(InputStream inputStream) throws Exception {
        Workbook workbook = new XSSFWorkbook(inputStream);
        Sheet sheet = findBestContactSheet(workbook);
        if (sheet == null) {
            sheet = workbook.getSheetAt(0);
        }
        ContactStore store = parseContactSheet(sheet);
        workbook.close();
        return store;
    }

    /**
     * Parse the contact master Excel and return a map of UAN → phone number (legacy support).
     */
    public Map<String, String> parseContactMaster(InputStream inputStream) throws Exception {
        ContactStore store = parseContactMasterStore(inputStream);
        return store.toUanPhoneMap();
    }

    /**
     * Parse employee rows from a POI Sheet object.
     */
    public List<Employee> parseWageSheetFromSheet(Sheet wageSheet) {
        // Detect month/year from sheet name or early rows
        String[] monthYear = detectMonthYear(wageSheet);
        String month = monthYear[0];
        String year = monthYear[1];
        log.info("Detected period: {} {}", month, year);

        // Detect contractor name from the "Name & address of contractor :" row
        String contractorName = detectContractorName(wageSheet);
        log.info("Detected contractor name: {}", contractorName);

        // Find the header row
        int headerRowIdx = findWageHeaderRow(wageSheet);
        log.info("Wage Header row detected at index: {}", headerRowIdx);

        Row headerRow = wageSheet.getRow(headerRowIdx);
        Map<String, Integer> columnMap = buildColumnMap(headerRow);
        log.info("Wage Column map: {}", columnMap);

        // Parse employee rows
        List<Employee> employees = new ArrayList<>();
        int slNo = 0;

        for (int r = headerRowIdx + 1; r <= wageSheet.getLastRowNum(); r++) {
            Row row = wageSheet.getRow(r);
            if (row == null) continue;

            // Get the UAN — try fuzzy header lookup first, fallback to fixed index COL_UAN (col 2)
            String uan = getCellString(row, columnMap, "uan no", "uan", "uan no.");
            if (uan == null) {
                uan = getCellByIndex(row, COL_UAN);
            }
            uan = normalizeUan(uan);

            // Skip rows without a valid UAN (totals, empty rows, sub-headers)
            if (uan == null || uan.isBlank() || uan.length() < 5) continue;

            // Also skip if it looks like a header or total row
            String nameVal = getCellString(row, columnMap, "name of workman", "workman", "name");
            if (nameVal == null) {
                nameVal = getCellByIndex(row, COL_NAME);
            }
            if (nameVal != null && (nameVal.equalsIgnoreCase("TOTAL") || nameVal.equalsIgnoreCase("GRAND TOTAL"))) {
                continue;
            }

            slNo++;
            Employee emp = new Employee();
            emp.setSlNo(slNo);
            emp.setUan(uan);
            emp.setName(nameVal != null ? nameVal.trim() : "Unknown");

            // Identity columns (fuzzy lookup, with fixed column fallback)
            String esiVal = getCellString(row, columnMap, "e.s.i no", "esi no", "esic no", "esi", "e.s.i no.");
            if (esiVal == null) esiVal = getCellByIndex(row, COL_ESI);
            emp.setEsiNo(normalizeDigitsOnly(esiVal));

            String desigVal = getCellString(row, columnMap, "designation", "designation /nature of work done");
            if (desigVal == null) desigVal = getCellByIndex(row, COL_DESIGNATION);
            emp.setDesignation(desigVal);

            String daysVal = getCellString(row, columnMap, "no. of days worked", "no of days worked", "days worked", "days");
            if (daysVal == null) daysVal = getCellByIndex(row, COL_DAYS_WORKED);
            emp.setDaysWorked(daysVal);

            // Wage and deduction amount columns (fixed POI indices for Form XVII)
            emp.setBasicRate(getCellByIndex(row, COL_DAILY_RATE));
            emp.setBasicAmount(getCellByIndex(row, COL_BASIC));
            emp.setDa(getCellByIndex(row, COL_DA));
            emp.setWashingAllowance(getCellByIndex(row, COL_WASHING));
            emp.setFuelAllowance(getCellByIndex(row, COL_FUEL));
            emp.setAttendanceAllowance(getCellByIndex(row, COL_ATTENDANCE));
            emp.setFoodAllowance(getCellByIndex(row, COL_FOOD));
            emp.setGratuity(getCellByIndex(row, COL_GRATUITY));
            emp.setHra(getCellByIndex(row, COL_HRA));
            emp.setOvertimeDays(getCellByIndex(row, COL_OT_DAYS));
            emp.setOvertimeAmount(getCellByIndex(row, COL_OT_AMOUNT));
            emp.setExtraProduction(getCellByIndex(row, COL_EXTRA_PRODUCTION));
            emp.setPerformanceIncentive(getCellByIndex(row, COL_PERFORMANCE_INCENTIVE));
            emp.setGrossEarnings(getCellByIndex(row, COL_GROSS_TOTAL));
            emp.setEsicSalary(getCellByIndex(row, COL_ESIC_SALARY));
            emp.setEpfoSalary(getCellByIndex(row, COL_EPFO_SALARY));
            emp.setEpfDeduction(getCellByIndex(row, COL_PF_DEDUCTION));
            emp.setEsiDeduction(getCellByIndex(row, COL_ESI_EMPLOYEE_SHARE));
            emp.setAdvanceDeduction(getCellByIndex(row, COL_ADVANCE_DEDUCTION));
            emp.setTotalDeductions(getCellByIndex(row, COL_TOTAL_DEDUCTION));
            emp.setNetPayable(getCellByIndex(row, COL_NET_PAID));

            // Summary of extra allowances
            emp.setOtherAllowances(computeOtherAllowancesSummary(emp));

            emp.setMonth(month);
            emp.setYear(year);
            emp.setContractorName(contractorName);

            // Runtime sanity check: Gross - Total Deductions = Net Payable
            verifySalaryMathSanity(emp);

            employees.add(emp);
        }

        log.info("Parsed {} employees from wage sheet", employees.size());
        return employees;
    }

    /**
     * Parse contacts from a POI Sheet object (e.g. WHATS APP NO. tab).
     */
    public ContactStore parseContactSheet(Sheet sheet) {
        ContactStore store = new ContactStore();
        if (sheet == null) return store;

        int headerRowIdx = findContactHeaderRow(sheet);
        ContactColumns cols = detectContactColumns(sheet, headerRowIdx);
        log.info("Contact Sheet '{}' detected header row at index {} with columns: Name={}, UAN={}, ESI={}, Phone={}",
                sheet.getSheetName(), headerRowIdx, cols.nameCol(), cols.uanCol(), cols.esiCol(), cols.phoneCol());

        for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String uan = normalizeUan(getCellByIndex(row, cols.uanCol()));
            String esi = normalizeDigitsOnly(getCellByIndex(row, cols.esiCol()));
            String name = getCellByIndex(row, cols.nameCol());

            // Resilient UAN fallback: if null or invalid, scan row for 12-digit UAN starting with "10"
            if (uan == null || uan.length() < 10) {
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String cand = normalizeUan(getCellByIndex(row, c));
                    if (cand != null && cand.length() == 12 && cand.startsWith("10")) {
                        uan = cand;
                        break;
                    }
                }
            }

            // Resilient ESI fallback: if null or invalid, scan row for 10-digit ESI starting with "4"
            if (esi == null || esi.length() < 8) {
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String cand = normalizeDigitsOnly(getCellByIndex(row, c));
                    if (cand != null && cand.length() == 10 && (cand.startsWith("4") || cand.startsWith("3"))) {
                        esi = cand;
                        break;
                    }
                }
            }

            // Primary phone candidate from detected phone column
            String rawPhone = getCellByIndex(row, cols.phoneCol());
            String phone = sanitizePhoneNumber(rawPhone);

            // Row-wide fallback scanner: If phone is null or invalid, scan all other cells in this row
            if (phone == null) {
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    if (c == cols.uanCol() || c == cols.esiCol() || c == cols.nameCol() || c == 0) {
                        continue; // Skip UAN, ESI, Name, and Serial No columns
                    }
                    String candidate = getCellByIndex(row, c);
                    if (isValidMobileNumber(candidate)) {
                        String sanitizedCandidate = sanitizePhoneNumber(candidate);
                        if (sanitizedCandidate != null) {
                            log.info("Contact row {}: Discovered valid mobile phone '{}' in column {}", r, candidate, c);
                            phone = sanitizedCandidate;
                            rawPhone = candidate;
                            break;
                        }
                    }
                }
            }

            // Skip empty rows without identity
            if ((uan == null || uan.isBlank()) && (esi == null || esi.isBlank()) && (name == null || name.isBlank())) {
                continue;
            }

            // Skip headers/totals
            if (name != null && (name.equalsIgnoreCase("NAME OF WORKMAN") || name.equalsIgnoreCase("TOTAL") || name.equalsIgnoreCase("GRAND TOTAL"))) {
                continue;
            }

            log.info("Parsed contact row {}: name='{}', uan='{}', esi='{}', rawPhone='{}' -> phone='{}'",
                    r, name, uan, esi, rawPhone, phone);

            store.addContact(uan, esi, name, phone);
        }

        log.info("Parsed {} contacts from sheet '{}'", store.size(), sheet.getSheetName());
        return store;
    }

    /**
     * Match employees to contacts using ContactStore (UAN -> ESI No -> Name).
     */
    public void matchEmployeesToContacts(List<Employee> employees, ContactStore contactStore) {
        int matched = 0;
        for (Employee emp : employees) {
            String phone = contactStore.findPhone(emp.getUan(), emp.getEsiNo(), emp.getName());
            if (phone != null) {
                emp.setPhoneNumber(phone);
                matched++;
            }
        }
        log.info("Matched {}/{} employees to contacts using UAN, ESI No, and Name fallback", matched, employees.size());
    }

    /**
     * Legacy matcher with Map<String, String>.
     */
    public void matchEmployeesToContacts(List<Employee> employees, Map<String, String> contacts) {
        int matched = 0;
        for (Employee emp : employees) {
            String phone = contacts.get(emp.getUan());
            if (phone != null) {
                emp.setPhoneNumber(phone);
                matched++;
            }
        }
        log.info("Matched {}/{} employees to contacts", matched, employees.size());
    }

    // =================================================================
    // Sheet Finder Helpers
    // =================================================================

    /**
     * Find the best sheet in the workbook that looks like a wage register.
     */
    public Sheet findBestWageSheet(Workbook workbook) {
        Sheet bestSheet = null;
        int bestScore = -1;

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName().toUpperCase().replaceAll("\\s+", " ");

            // Skip PAYSILP formatted view and WhatsApp contact sheets
            if (sheetName.contains("PAYSILP") || sheetName.contains("PAYSLIP")) continue;
            if (sheetName.contains("WHATS APP") || sheetName.contains("WHATSAPP") || sheetName.contains("CONTACT")) continue;

            int score = 0;
            if (sheetName.contains("PAY SHEET") || sheetName.contains("PAYSHEET")) score += 15;
            if (sheetName.contains("WAGE")) score += 10;
            if (sheetName.contains("ENTERPRISE") || sheetName.contains("FRIENDS")) score += 5;

            // Score by checking header keywords
            int headerIdx = findWageHeaderRow(sheet);
            if (headerIdx >= 0) {
                Row headerRow = sheet.getRow(headerIdx);
                if (headerRow != null) {
                    String headerText = rowToString(headerRow).toLowerCase();
                    for (String kw : WAGE_HEADER_KEYWORDS) {
                        if (headerText.contains(kw)) score += 2;
                    }
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestSheet = sheet;
            }
        }

        if (bestSheet == null && workbook.getNumberOfSheets() > 0) {
            bestSheet = workbook.getSheetAt(0);
        }

        return bestSheet;
    }

    /**
     * Find the best sheet in the workbook that looks like a WhatsApp / Contact list.
     */
    public Sheet findBestContactSheet(Workbook workbook) {
        Sheet bestSheet = null;
        int bestScore = -1;

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName().toUpperCase().replaceAll("\\s+", " ");

            // Skip payslip views and wage register sheets
            if (sheetName.contains("PAYSILP") || sheetName.contains("PAYSLIP")) continue;
            if (sheetName.contains("PAY SHEET") || sheetName.contains("PAYSHEET") || sheetName.contains("WAGE")) continue;

            int score = 0;
            if (sheetName.contains("WHATS APP") || sheetName.contains("WHATSAPP")) score += 25;
            if (sheetName.contains("CONTACT")) score += 15;
            if (sheetName.contains("PHONE") || sheetName.contains("MOBILE")) score += 10;

            int headerIdx = findContactHeaderRow(sheet);
            if (headerIdx >= 0) {
                Row headerRow = sheet.getRow(headerIdx);
                if (headerRow != null) {
                    String headerText = rowToString(headerRow).toLowerCase();
                    if (headerText.contains("whatsapp") || headerText.contains("phone") || headerText.contains("mobile")) {
                        score += 10;
                    }
                }
            }

            if (score > bestScore && score > 0) {
                bestScore = score;
                bestSheet = sheet;
            }
        }

        return bestSheet;
    }

    // =================================================================
    // Header Row Detection Helpers
    // =================================================================

    private int findWageHeaderRow(Sheet sheet) {
        return findHeaderRowByKeywords(sheet, WAGE_HEADER_KEYWORDS);
    }

    private int findContactHeaderRow(Sheet sheet) {
        return findHeaderRowByKeywords(sheet, CONTACT_HEADER_KEYWORDS);
    }

    private int findHeaderRowByKeywords(Sheet sheet, List<String> keywords) {
        int bestRow = 0;
        int bestScore = 0;

        for (int r = 0; r <= Math.min(25, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String rowText = rowToString(row).toLowerCase();
            int score = 0;
            for (String kw : keywords) {
                if (rowText.contains(kw)) score += 2;
            }

            if (score > bestScore) {
                bestScore = score;
                bestRow = r;
            }
        }

        return bestRow;
    }

    public record ContactColumns(int nameCol, int uanCol, int esiCol, int phoneCol) {}

    /**
     * Resiliently detect column indices in the contact sheet by inspecting the header row
     * and adjacent rows, matching normalized text against keywords.
     */
    public ContactColumns detectContactColumns(Sheet sheet, int headerRowIdx) {
        int nameCol = -1;
        int uanCol = -1;
        int esiCol = -1;
        int phoneCol = -1;

        int startR = Math.max(0, headerRowIdx - 1);
        int endR = Math.min(sheet.getLastRowNum(), headerRowIdx + 2);

        for (int r = startR; r <= endR; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell == null) continue;
                String text = getCellValueAsString(cell).replaceAll("[^a-zA-Z]", "").toLowerCase();

                if (phoneCol == -1 && (text.contains("whatsapp") || text.contains("phone") || text.contains("mobile") || text.contains("contact") || text.contains("cell"))) {
                    phoneCol = c;
                }
                if (uanCol == -1 && text.contains("uan")) {
                    uanCol = c;
                }
                if (esiCol == -1 && text.contains("esi")) {
                    esiCol = c;
                }
                if (nameCol == -1 && (text.contains("workman") || text.contains("employee") || (text.contains("name") && !text.contains("contractor") && !text.contains("principal")))) {
                    nameCol = c;
                }
            }
        }

        // Form XVII contact tab fallback defaults if header labels were omitted/unmatched
        if (nameCol == -1) nameCol = 1;
        if (uanCol == -1) uanCol = 2;
        if (esiCol == -1) esiCol = 3;
        if (phoneCol == -1) phoneCol = 4;

        log.info("Detected contact sheet columns: Name={}, UAN={}, ESI={}, Phone={}",
                nameCol, uanCol, esiCol, phoneCol);
        return new ContactColumns(nameCol, uanCol, esiCol, phoneCol);
    }

    private boolean isValidMobileNumber(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String cleaned = raw.replace('\u00A0', ' ').replace('\u200B', ' ').trim().toUpperCase();
        if (cleaned.equals("NA") || cleaned.equals("N/A") || cleaned.equals("NIL") || cleaned.equals("NONE") || cleaned.equals("NULL")) {
            return false;
        }

        // Strip trailing decimal artifacts e.g. .00 or .0
        if (cleaned.endsWith(".00")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        } else if (cleaned.endsWith(".0")) {
            cleaned = cleaned.substring(0, cleaned.length() - 2);
        }

        String digits = cleaned.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        // Valid Indian mobile numbers are 10 digits starting with 6, 7, 8, or 9
        return digits.length() == 10 && (digits.startsWith("6") || digits.startsWith("7") || digits.startsWith("8") || digits.startsWith("9"));
    }

    // =================================================================
    // Cell & String Helpers
    // =================================================================

    private Map<String, Integer> buildColumnMap(Row headerRow) {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (headerRow == null) return map;

        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            if (cell != null) {
                String value = getCellValueAsString(cell).trim().toLowerCase();
                if (!value.isEmpty()) {
                    map.put(value, c);
                }
            }
        }
        return map;
    }

    private String getCellByIndex(Row row, int colIdx) {
        if (row == null || colIdx < 0) return null;
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;
        String val = getCellValueAsString(cell).trim();
        if (val.isEmpty() || val.equalsIgnoreCase("nan") || val.equalsIgnoreCase("null")) {
            return null;
        }
        return val;
    }

    private String computeOtherAllowancesSummary(Employee emp) {
        double extra = 0.0;
        extra += parseDecimalSafe(emp.getOvertimeAmount());
        extra += parseDecimalSafe(emp.getExtraProduction());
        extra += parseDecimalSafe(emp.getPerformanceIncentive());
        return extra > 0 ? String.format("%.2f", extra) : null;
    }

    private void verifySalaryMathSanity(Employee emp) {
        double gross = parseDecimalSafe(emp.getGrossEarnings());
        double ded = parseDecimalSafe(emp.getTotalDeductions());
        double net = parseDecimalSafe(emp.getNetPayable());
        if (gross > 0 && Math.abs((gross - ded) - net) > 1.0) {
            log.warn("Pay discrepancy detected for UAN {} ({})! Gross: {}, Deductions: {}, Net Paid: {} (Expected Net: {})",
                    emp.getUan(), emp.getName(), emp.getGrossEarnings(), emp.getTotalDeductions(), emp.getNetPayable(), String.format("%.2f", gross - ded));
        }
    }

    private double parseDecimalSafe(String val) {
        if (val == null || val.isBlank()) return 0.0;
        try {
            return Double.parseDouble(val.replaceAll("[^0-9.]", ""));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String getCellString(Row row, Map<String, Integer> columnMap, String... possibleNames) {
        for (String name : possibleNames) {
            Integer colIdx = columnMap.get(name.toLowerCase());
            if (colIdx != null) {
                Cell cell = row.getCell(colIdx);
                if (cell != null) {
                    String val = getCellValueAsString(cell).trim();
                    if (!val.isEmpty() && !val.equals("nan") && !val.equalsIgnoreCase("null")) {
                        return val;
                    }
                }
            }
        }
        // Partial matching fallback
        for (Map.Entry<String, Integer> entry : columnMap.entrySet()) {
            for (String name : possibleNames) {
                if (entry.getKey().contains(name.toLowerCase())) {
                    Cell cell = row.getCell(entry.getValue());
                    if (cell != null) {
                        String val = getCellValueAsString(cell).trim();
                        if (!val.isEmpty() && !val.equals("nan") && !val.equalsIgnoreCase("null")) {
                            return val;
                        }
                    }
                }
            }
        }
        return null;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        // For formula cells, read the cached result value directly (not the formula text)
        if (cell.getCellType() == CellType.FORMULA) {
            try {
                // Try cached string result first
                String strVal = cell.getStringCellValue();
                if (strVal != null && !strVal.isBlank()) {
                    return strVal.trim();
                }
            } catch (Exception e) {
                try {
                    // Fall back to cached numeric result
                    double d = cell.getNumericCellValue();
                    if (d == Math.floor(d) && !Double.isInfinite(d)) {
                        return String.valueOf((long) d);
                    }
                    return String.format("%.2f", d);
                } catch (Exception e2) {
                    // Last resort: return formula text
                    return cell.getCellFormula();
                }
            }
        }

        // For non-formula cells, use DataFormatter for clean formatting (handles number formats, dates, etc.)
        try {
            String formatted = dataFormatter.formatCellValue(cell);
            if (formatted != null && !formatted.isBlank()) {
                return formatted.trim();
            }
        } catch (Exception ignored) {
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    yield String.valueOf((long) d);
                }
                yield String.format("%.2f", d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private String normalizeUan(String uan) {
        if (uan == null || uan.isBlank()) return null;
        uan = uan.trim();
        if (uan.endsWith(".0")) {
            uan = uan.substring(0, uan.length() - 2);
        }
        uan = uan.replaceAll("[^0-9]", "");
        return uan.isEmpty() ? null : uan;
    }

    private String normalizeDigitsOnly(String str) {
        if (str == null || str.isBlank()) return null;
        str = str.trim();
        if (str.endsWith(".0")) {
            str = str.substring(0, str.length() - 2);
        }
        String digits = str.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }

    public String sanitizePhoneNumber(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) return null;

        // Clean non-breaking spaces and invisible characters
        String phone = rawPhone.replace('\u00A0', ' ')
                               .replace('\u200B', ' ')
                               .trim().toUpperCase();

        // Check common placeholders
        if (phone.equals("NA") || phone.equals("N/A") || phone.equals("NIL") ||
            phone.equals("NONE") || phone.equals("NULL") || phone.equals("-") ||
            phone.equals("--") || phone.equals("NOT AVAILABLE") || phone.equals("NO")) {
            return null;
        }

        // Remove numeric trailing decimals like .00 or .0
        if (phone.endsWith(".00")) {
            phone = phone.substring(0, phone.length() - 3);
        } else if (phone.endsWith(".0")) {
            phone = phone.substring(0, phone.length() - 2);
        }

        boolean hasPlus = phone.startsWith("+");
        String digits = phone.replaceAll("\\D", "");

        if (digits.isEmpty()) return null;

        // If 11 digits starting with 0 (e.g. 08101617475) -> strip leading 0
        if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }

        // Indian 10-digit mobile number -> prepend +91
        if (digits.length() == 10) {
            return "+91" + digits;
        }

        // 12-digit number starting with 91 (e.g. 918101617475)
        if (digits.length() == 12 && digits.startsWith("91")) {
            return "+" + digits;
        }

        // International with plus and valid length (10 to 15 digits)
        if (hasPlus && digits.length() >= 10 && digits.length() <= 15) {
            return "+" + digits;
        }

        if (digits.length() > 10 && digits.length() <= 15) {
            return "+" + digits;
        }

        return null;
    }

    private String rowToString(Row row) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null) {
                sb.append(getCellValueAsString(cell)).append(" ");
            }
        }
        return sb.toString();
    }

    /**
     * Detects the contractor name from the "Name & address of contractor :" label
     * cell near the top of the Form XVII wage sheet, so the generated PDF header
     * reflects whichever contractor's workbook was uploaded (e.g. "FRIENDS ENTERPRISE",
     * "B.P. TRANSPORT") instead of a fixed name.
     */
    private String detectContractorName(Sheet sheet) {
        for (int r = 0; r <= Math.min(10, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                String cellText = getCellByIndex(row, c);
                if (cellText == null) continue;
                String normalized = cellText.toLowerCase();
                if (normalized.contains("contractor")) {
                    for (int c2 = c + 1; c2 < row.getLastCellNum(); c2++) {
                        String val = getCellByIndex(row, c2);
                        if (val != null && !val.isBlank()) {
                            return val.trim();
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Detects the principal employer's site name from the "SITE NAME-..." cell on the
     * PAYSILP sheet (the company's own pre-formatted payslip view), so the generated
     * PDF header reflects whichever plant/site the uploaded workbook is for instead of
     * a fixed value. Falls back to null (caller uses a default) if no PAYSILP-style
     * sheet or SITE NAME cell is found.
     */
    private String detectSiteName(Workbook workbook) {
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName().toUpperCase();
            if (!sheetName.contains("PAYSILP") && !sheetName.contains("PAYSLIP")) continue;

            for (int r = 0; r <= Math.min(5, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String text = getCellByIndex(row, c);
                    if (text != null && text.toUpperCase().contains("SITE NAME")) {
                        String cleaned = text.replaceAll("(?i)site\\s*name\\s*[-:]\\s*", "").trim();
                        cleaned = cleaned.replaceAll(",(?=\\S)", ", ");
                        cleaned = cleaned.replaceAll("-(?=\\d)", " - ");
                        if (!cleaned.isBlank()) {
                            return cleaned;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Detects the contractor's registered address/unit suffix (e.g. "DURGAPUR - 12")
     * from the PAYSILP sheet's "M/S. <contractor>, DURGAPUR - 12 ," company line, so it
     * can be appended after the contractor name on the PDF. Only the location suffix is
     * used (not the contractor name itself, which the PAYSILP sheet sometimes misspells
     * e.g. "FRENDS" instead of "FRIENDS") — the correctly spelled name already comes
     * from the wage sheet via detectContractorName().
     */
    private String detectContractorAddress(Workbook workbook) {
        Pattern addressPattern = Pattern.compile("DURGAPUR\\s*-?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName().toUpperCase();
            if (!sheetName.contains("PAYSILP") && !sheetName.contains("PAYSLIP")) continue;

            for (int r = 0; r <= Math.min(5, sheet.getLastRowNum()); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    String text = getCellByIndex(row, c);
                    if (text == null || !text.toUpperCase().contains("M/S.")) continue;
                    Matcher matcher = addressPattern.matcher(text);
                    if (matcher.find()) {
                        return "DURGAPUR - " + matcher.group(1);
                    }
                }
            }
        }
        return null;
    }

    private String[] detectMonthYear(Sheet sheet) {
        String month = "UNKNOWN";
        String year = String.valueOf(java.time.Year.now().getValue());

        String sheetName = sheet.getSheetName().toUpperCase();
        String[] result = extractMonthYear(sheetName);
        if (result[0] != null) return result;

        for (int r = 0; r <= Math.min(10, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String rowText = rowToString(row).toUpperCase();
            result = extractMonthYear(rowText);
            if (result[0] != null) return result;
        }

        return new String[]{month, year};
    }

    private String[] extractMonthYear(String text) {
        for (String m : MONTHS) {
            if (text.contains(m)) {
                String detectedMonth = m;
                Pattern yearPattern = Pattern.compile("'?(\\d{2,4})");
                Matcher matcher = yearPattern.matcher(text.substring(text.indexOf(m) + m.length()));
                String detectedYear = String.valueOf(java.time.Year.now().getValue());
                if (matcher.find()) {
                    String yrStr = matcher.group(1);
                    if (yrStr.length() == 2) {
                        detectedYear = "20" + yrStr;
                    } else if (yrStr.length() == 4) {
                        detectedYear = yrStr;
                    }
                }
                return new String[]{detectedMonth, detectedYear};
            }
        }
        return new String[]{null, null};
    }
}
