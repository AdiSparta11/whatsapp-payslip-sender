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
 * Parses the GR Friends Enterprise wage sheet Excel and the contact master Excel.
 *
 * The wage sheet has two relevant tabs:
 *   - "FRIENDS ENTERPRISE PAY SHEET" — the raw wage register with one row per employee
 *   - "PAYSILP" (sic) — a formatted payslip view (formulas pulling from the wage sheet)
 *
 * We read from the wage sheet tab directly (the raw data), not the PAYSILP tab.
 */
@Service
public class ExcelParserService {

    private static final Logger log = LoggerFactory.getLogger(ExcelParserService.class);

    // Keywords to detect the header row in the wage sheet
    private static final List<String> HEADER_KEYWORDS = List.of(
            "uan", "name of workman", "workman", "esi no", "sl. no", "sl.no",
            "designation", "basic wages", "net payable", "days worked", "basic rate"
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
     * Parse the wage sheet Excel and return a list of Employee objects.
     */
    public List<Employee> parseWageSheet(InputStream inputStream) throws Exception {
        Workbook workbook = new XSSFWorkbook(inputStream);

        // Find the best sheet — prefer one with "PAY SHEET" or "WAGE" in the name
        Sheet wageSheet = findBestWageSheet(workbook);
        if (wageSheet == null) {
            throw new IllegalArgumentException("Could not find a wage sheet tab in the uploaded file. " +
                    "Expected a sheet with column headers like 'UAN NO', 'NAME OF WORKMAN', 'BASIC WAGES', etc.");
        }

        log.info("Using sheet: '{}'", wageSheet.getSheetName());

        // Detect month/year from sheet name or early rows
        String[] monthYear = detectMonthYear(wageSheet);
        String month = monthYear[0];
        String year = monthYear[1];
        log.info("Detected period: {} {}", month, year);

        // Find the header row
        int headerRowIdx = findHeaderRow(wageSheet);
        log.info("Header row detected at index: {}", headerRowIdx);

        Row headerRow = wageSheet.getRow(headerRowIdx);
        Map<String, Integer> columnMap = buildColumnMap(headerRow);
        log.info("Column map: {}", columnMap);

        // Parse employee rows
        List<Employee> employees = new ArrayList<>();
        int slNo = 0;

        for (int r = headerRowIdx + 1; r <= wageSheet.getLastRowNum(); r++) {
            Row row = wageSheet.getRow(r);
            if (row == null) continue;

            // Get the UAN — try fuzzy header lookup first, fallback to fixed index COL_UAN (col 2)
            String uan = getCellString(row, columnMap, "uan no", "uan");
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

            // Identity columns (row 9 fuzzy lookup, with fixed column fallback)
            String esiVal = getCellString(row, columnMap, "e.s.i no", "esi no", "esic no", "esi");
            if (esiVal == null) esiVal = getCellByIndex(row, COL_ESI);
            emp.setEsiNo(esiVal);

            String desigVal = getCellString(row, columnMap, "designation");
            if (desigVal == null) desigVal = getCellByIndex(row, COL_DESIGNATION);
            emp.setDesignation(desigVal);

            String daysVal = getCellString(row, columnMap, "no. of days worked", "days worked", "days");
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

            // Runtime sanity check: Gross - Total Deductions = Net Payable
            verifySalaryMathSanity(emp);

            employees.add(emp);
        }

        workbook.close();
        log.info("Parsed {} employees from wage sheet", employees.size());
        return employees;
    }

    /**
     * Parse the contact master Excel and return a map of UAN → phone number.
     */
    public Map<String, String> parseContactMaster(InputStream inputStream) throws Exception {
        Workbook workbook = new XSSFWorkbook(inputStream);
        Sheet sheet = workbook.getSheetAt(0); // Contact master should have one sheet

        // Find header row
        int headerRowIdx = findHeaderRow(sheet);
        Row headerRow = sheet.getRow(headerRowIdx);
        Map<String, Integer> columnMap = buildColumnMap(headerRow);

        Map<String, String> contacts = new LinkedHashMap<>();

        for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String uan = getCellString(row, columnMap, "uan no", "uan", "uan no.");
            uan = normalizeUan(uan);
            if (uan == null || uan.isBlank()) continue;

            String phone = getCellString(row, columnMap,
                    "whatsapp phone number", "whatsapp number", "whatsapp",
                    "phone number", "phone", "mobile", "contact", "number");

            phone = sanitizePhoneNumber(phone);
            if (phone != null) {
                contacts.put(uan, phone);
            }
        }

        workbook.close();
        log.info("Parsed {} contacts from contact master", contacts.size());
        return contacts;
    }

    /**
     * Match employees to contacts by UAN, setting the phone number on each matched employee.
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
    // Private helpers
    // =================================================================

    /**
     * Find the best sheet in the workbook that looks like a wage register.
     */
    private Sheet findBestWageSheet(Workbook workbook) {
        Sheet bestSheet = null;
        int bestScore = -1;

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            String sheetName = sheet.getSheetName().toUpperCase();

            // Skip the PAYSILP formatted view — we want the raw data
            if (sheetName.contains("PAYSILP") || sheetName.contains("PAYSLIP")) continue;

            int score = 0;
            if (sheetName.contains("PAY SHEET") || sheetName.contains("PAYSHEET")) score += 10;
            if (sheetName.contains("WAGE")) score += 8;
            if (sheetName.contains("ENTERPRISE") || sheetName.contains("FRIENDS")) score += 5;

            // Also score by checking if the sheet has our expected header columns
            int headerIdx = findHeaderRow(sheet);
            if (headerIdx >= 0) {
                Row headerRow = sheet.getRow(headerIdx);
                if (headerRow != null) {
                    String headerText = rowToString(headerRow).toLowerCase();
                    for (String kw : HEADER_KEYWORDS) {
                        if (headerText.contains(kw)) score += 2;
                    }
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestSheet = sheet;
            }
        }

        // Fallback: if no good sheet found, use the first non-PAYSILP sheet
        if (bestSheet == null && workbook.getNumberOfSheets() > 0) {
            bestSheet = workbook.getSheetAt(0);
        }

        return bestSheet;
    }

    /**
     * Find the row index that contains the header (column names).
     * Scans the first 25 rows for the one with the most keyword matches.
     */
    private int findHeaderRow(Sheet sheet) {
        int bestRow = 0;
        int bestScore = 0;

        for (int r = 0; r <= Math.min(25, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String rowText = rowToString(row).toLowerCase();
            int score = 0;
            for (String kw : HEADER_KEYWORDS) {
                if (rowText.contains(kw)) score += 2;
            }

            if (score > bestScore) {
                bestScore = score;
                bestRow = r;
            }
        }

        return bestRow;
    }

    /**
     * Build a map of lowercase column name → column index from the header row.
     */
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

    /**
     * Get a cell value by fixed column index.
     */
    private String getCellByIndex(Row row, int colIdx) {
        if (row == null) return null;
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;
        String val = getCellValueAsString(cell).trim();
        if (val.isEmpty() || val.equalsIgnoreCase("nan") || val.equalsIgnoreCase("null")) {
            return null;
        }
        return val;
    }

    /**
     * Calculate summary of extra/other allowances.
     */
    private String computeOtherAllowancesSummary(Employee emp) {
        double extra = 0.0;
        extra += parseDecimalSafe(emp.getOvertimeAmount());
        extra += parseDecimalSafe(emp.getExtraProduction());
        extra += parseDecimalSafe(emp.getPerformanceIncentive());
        return extra > 0 ? String.format("%.2f", extra) : null;
    }

    /**
     * Perform runtime sanity check on gross, deductions, and net payable.
     */
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

    /**
     * Get a cell value as string from a row, trying multiple possible column name variants.
     * Returns the first match found.
     */
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
        // Also try partial matching — the Excel columns often have multi-line or extra text
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

    /**
     * Convert any cell type to a string value.
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                // If it's a whole number, don't show decimal (e.g. UAN "100123456789" not "1.00123456789E11")
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    yield String.valueOf((long) d);
                }
                yield String.format("%.2f", d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                // For formula cells, try to get the cached value
                try {
                    yield String.valueOf(cell.getStringCellValue());
                } catch (Exception e) {
                    try {
                        double d = cell.getNumericCellValue();
                        if (d == Math.floor(d) && !Double.isInfinite(d)) {
                            yield String.valueOf((long) d);
                        }
                        yield String.format("%.2f", d);
                    } catch (Exception e2) {
                        yield cell.getCellFormula();
                    }
                }
            }
            default -> "";
        };
    }

    /**
     * Normalize a UAN value — strip trailing .0, trim whitespace.
     */
    private String normalizeUan(String uan) {
        if (uan == null || uan.isBlank()) return null;
        uan = uan.trim();
        if (uan.endsWith(".0")) {
            uan = uan.substring(0, uan.length() - 2);
        }
        // Remove any non-digit characters (sometimes UANs have spaces or dashes)
        uan = uan.replaceAll("[^0-9]", "");
        return uan.isEmpty() ? null : uan;
    }

    /**
     * Sanitize a phone number to international format (+91XXXXXXXXXX).
     */
    private String sanitizePhoneNumber(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) return null;

        String phone = rawPhone.trim();
        // Remove .0 suffix from numeric cells
        if (phone.endsWith(".0")) {
            phone = phone.substring(0, phone.length() - 2);
        }

        boolean hasPlus = phone.startsWith("+");
        // Keep only digits
        String digits = phone.replaceAll("\\D", "");

        if (digits.isEmpty()) return null;

        if (hasPlus) {
            return "+" + digits;
        } else if (digits.length() == 10) {
            return "+91" + digits;   // Indian 10-digit → prepend +91
        } else if (digits.length() == 12 && digits.startsWith("91")) {
            return "+" + digits;     // 91XXXXXXXXXX → +91XXXXXXXXXX
        } else if (digits.length() > 10) {
            return "+" + digits;     // Already has country code
        }

        return null; // Too short or invalid
    }

    /**
     * Concatenate all cell values in a row into a single string.
     */
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
     * Try to detect the month and year from the sheet name or early rows.
     * e.g. "FRIENDS ENTERPRISE PAY SHEET JULY'26" → ["JULY", "2026"]
     */
    private String[] detectMonthYear(Sheet sheet) {
        String month = "UNKNOWN";
        String year = String.valueOf(java.time.Year.now().getValue());

        // Check sheet name first
        String sheetName = sheet.getSheetName().toUpperCase();
        String[] result = extractMonthYear(sheetName);
        if (result[0] != null) return result;

        // Check first 10 rows for month/year references
        for (int r = 0; r <= Math.min(10, sheet.getLastRowNum()); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String rowText = rowToString(row).toUpperCase();
            result = extractMonthYear(rowText);
            if (result[0] != null) return result;
        }

        return new String[]{month, year};
    }

    /**
     * Extract month and year from a text string.
     */
    private String[] extractMonthYear(String text) {
        for (String m : MONTHS) {
            if (text.contains(m)) {
                String detectedMonth = m;
                // Try to find year — look for 'YY or 20YY
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
