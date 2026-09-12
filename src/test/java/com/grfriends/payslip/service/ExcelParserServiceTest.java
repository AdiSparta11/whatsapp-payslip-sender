package com.grfriends.payslip.service;

import com.grfriends.payslip.model.Employee;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelParserServiceTest {

    private ExcelParserService excelParserService;

    @BeforeEach
    void setUp() {
        excelParserService = new ExcelParserService();
    }

    @Test
    void testParseWageSheetTwoRowHeader() throws Exception {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("FRIENDS ENTERPRISE PAY SHEET JULY'26");

        // Row 8 (Row 9 in 1-indexed Excel): Group Headers
        Row row9 = sheet.createRow(8);
        row9.createCell(0).setCellValue("Sl. No.");
        row9.createCell(1).setCellValue("Name of Workman");
        row9.createCell(2).setCellValue("UAN No.");
        row9.createCell(3).setCellValue("E.S.I No.");
        row9.createCell(4).setCellValue("Designation of Workman");
        row9.createCell(5).setCellValue("No of days Worked");
        row9.createCell(9).setCellValue("TOTAL DAYS");
        row9.createCell(10).setCellValue("AMOUNT OF WAGES EARNED");
        row9.createCell(24).setCellValue("ESIC & EPFO SALARY");
        row9.createCell(26).setCellValue("DEDUCTIONS");
        row9.createCell(30).setCellValue("Net Amount Paid Rs.");

        // Row 9 (Row 10 in 1-indexed Excel): Sub-Headers
        Row row10 = sheet.createRow(9);
        row10.createCell(10).setCellValue("Daily Rate of wages");
        row10.createCell(11).setCellValue("Basic Wages @Rs.525/day");
        row10.createCell(12).setCellValue("DA @Rs.10/day");
        row10.createCell(13).setCellValue("Washing Allowance");
        row10.createCell(14).setCellValue("Fuel Allowance");
        row10.createCell(15).setCellValue("Attn. Allowance");
        row10.createCell(16).setCellValue("Food Allowance");
        row10.createCell(17).setCellValue("Gratuity");
        row10.createCell(18).setCellValue("House Rent Allowance");
        row10.createCell(19).setCellValue("Overtime Days");
        row10.createCell(20).setCellValue("Overtime Rs.");
        row10.createCell(21).setCellValue("Extra Production");
        row10.createCell(22).setCellValue("Performance Insentive Rs.");
        row10.createCell(23).setCellValue("GROSS TOTAL (RS)");
        row10.createCell(24).setCellValue("ESIC SALARY");
        row10.createCell(25).setCellValue("EPFO SALARY");
        row10.createCell(26).setCellValue("Provident Fund (Rs)");
        row10.createCell(27).setCellValue("Emply. Share (E.S.I)");
        row10.createCell(28).setCellValue("Deduction for ADVANCE");
        row10.createCell(29).setCellValue("Total Deduction (Rs)");
        row10.createCell(30).setCellValue("Net Amount Paid Rs.");

        // Row 10 (Row 11 in 1-indexed Excel): Data Row for MANOJ YADAV
        Row row11 = sheet.createRow(10);
        row11.createCell(0).setCellValue(1);
        row11.createCell(1).setCellValue("MANOJ YADAV");
        row11.createCell(2).setCellValue("100123456789");
        row11.createCell(3).setCellValue("4100123456");
        row11.createCell(4).setCellValue("OPERATOR");
        row11.createCell(5).setCellValue(26);
        row11.createCell(10).setCellValue(525.0);
        row11.createCell(11).setCellValue(13650.0);
        row11.createCell(12).setCellValue(260.0);
        row11.createCell(13).setCellValue(100.0);
        row11.createCell(14).setCellValue(0.0);
        row11.createCell(15).setCellValue(200.0);
        row11.createCell(16).setCellValue(0.0);
        row11.createCell(17).setCellValue(0.0);
        row11.createCell(18).setCellValue(1365.0);
        row11.createCell(19).setCellValue(0);
        row11.createCell(20).setCellValue(0.0);
        row11.createCell(21).setCellValue(0.0);
        row11.createCell(22).setCellValue(500.0);
        row11.createCell(23).setCellValue(16075.0);
        row11.createCell(24).setCellValue(16075.0);
        row11.createCell(25).setCellValue(15000.0);
        row11.createCell(26).setCellValue(1301.0);
        row11.createCell(27).setCellValue(83.0);
        row11.createCell(28).setCellValue(500.0);
        row11.createCell(29).setCellValue(1884.0);
        row11.createCell(30).setCellValue(14191.0);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        List<Employee> employees = excelParserService.parseWageSheet(new ByteArrayInputStream(out.toByteArray()));

        assertEquals(1, employees.size(), "Should parse exactly 1 employee");
        Employee emp = employees.get(0);

        assertEquals("MANOJ YADAV", emp.getName());
        assertEquals("100123456789", emp.getUan());
        assertEquals("4100123456", emp.getEsiNo());
        assertEquals("OPERATOR", emp.getDesignation());
        assertEquals("26", emp.getDaysWorked());
        assertEquals("525.00", emp.getBasicRate());
        assertEquals("13650.00", emp.getBasicAmount());
        assertEquals("260.00", emp.getDa());
        assertEquals("100.00", emp.getWashingAllowance());
        assertEquals("200.00", emp.getAttendanceAllowance());
        assertEquals("1365.00", emp.getHra());
        assertEquals("500.00", emp.getPerformanceIncentive());
        assertEquals("16075.00", emp.getGrossEarnings());
        assertEquals("16075.00", emp.getEsicSalary());
        assertEquals("15000.00", emp.getEpfoSalary());
        assertEquals("1301.00", emp.getEpfDeduction());
        assertEquals("83.00", emp.getEsiDeduction());
        assertEquals("500.00", emp.getAdvanceDeduction());
        assertEquals("1884.00", emp.getTotalDeductions());
        assertEquals("14191.00", emp.getNetPayable());
        assertEquals("JULY", emp.getMonth());
        assertEquals("2026", emp.getYear());
    }

    @Test
    void testParseSingleWorkbookWithWhatsAppTab() throws Exception {
        Workbook workbook = new XSSFWorkbook();

        // 1. Wage Sheet Tab
        Sheet wageSheet = workbook.createSheet("FRIENDS ENTERPRISE PAY SHEET");
        Row h9 = wageSheet.createRow(8);
        h9.createCell(0).setCellValue("Sl. No.");
        h9.createCell(1).setCellValue("Name of Workman");
        h9.createCell(2).setCellValue("UAN No.");
        h9.createCell(3).setCellValue("E.S.I No.");
        h9.createCell(4).setCellValue("Designation");
        h9.createCell(5).setCellValue("No of days Worked");
        h9.createCell(9).setCellValue("TOTAL DAYS");
        h9.createCell(10).setCellValue("AMOUNT OF WAGES EARNED");
        h9.createCell(30).setCellValue("Net Amount Paid Rs.");

        Row h10 = wageSheet.createRow(9);
        h10.createCell(10).setCellValue("Daily Rate of wages");
        h10.createCell(11).setCellValue("Basic Wages @Rs. 525.00 PER DAY");
        h10.createCell(12).setCellValue("DA @Rs.10.00 PER DAY");
        h10.createCell(13).setCellValue("Washing Allowance");
        h10.createCell(14).setCellValue("Fuel Allowance");
        h10.createCell(15).setCellValue("Attn. Allowance");
        h10.createCell(16).setCellValue("Food Allowance");
        h10.createCell(17).setCellValue("Gratuity");
        h10.createCell(18).setCellValue("House Rent Allowance");
        h10.createCell(23).setCellValue("GROSS TOTAL");
        h10.createCell(26).setCellValue("EPF");
        h10.createCell(27).setCellValue("ESIC");
        h10.createCell(28).setCellValue("Advance");
        h10.createCell(29).setCellValue("Total Deduction");
        h10.createCell(30).setCellValue("Net Amount Paid Rs.");

        // Employee 1: MANOJ YADAV
        Row r1 = wageSheet.createRow(10);
        r1.createCell(0).setCellValue(1);
        r1.createCell(1).setCellValue("MANOJ YADAV");
        r1.createCell(2).setCellValue("100222327291");
        r1.createCell(3).setCellValue("4108643725");
        r1.createCell(4).setCellValue("WORKMAN");
        r1.createCell(5).setCellValue(16);
        r1.createCell(10).setCellValue(525.0);
        r1.createCell(11).setCellValue(8925.0);
        r1.createCell(12).setCellValue(170.0);
        r1.createCell(13).setCellValue(344.0);
        r1.createCell(14).setCellValue(320.0);
        r1.createCell(15).setCellValue(176.0);
        r1.createCell(16).setCellValue(240.0);
        r1.createCell(17).setCellValue(64.0);
        r1.createCell(18).setCellValue(454.75);
        r1.createCell(23).setCellValue(10694.0);
        r1.createCell(26).setCellValue(1221.0);
        r1.createCell(27).setCellValue(78.0);
        r1.createCell(28).setCellValue(0.0);
        r1.createCell(29).setCellValue(1299.0);
        r1.createCell(30).setCellValue(9395.0);

        // Employee 2: LAKHI NARAYAN LOHAR (Has NA in WhatsApp tab)
        Row r2 = wageSheet.createRow(11);
        r2.createCell(0).setCellValue(2);
        r2.createCell(1).setCellValue("LAKHI NARAYAN LOHAR");
        r2.createCell(2).setCellValue("100202820575");
        r2.createCell(3).setCellValue("4108661222");
        r2.createCell(4).setCellValue("WORKMAN");
        r2.createCell(5).setCellValue(26);
        r2.createCell(10).setCellValue(525.0);
        r2.createCell(11).setCellValue(14175.0);
        r2.createCell(23).setCellValue(17026.0);
        r2.createCell(29).setCellValue(1924.0);
        r2.createCell(30).setCellValue(15102.0);

        // Employee 3: SANJAY PANDIT (Matches by ESI No. fallback)
        Row r3 = wageSheet.createRow(12);
        r3.createCell(0).setCellValue(3);
        r3.createCell(1).setCellValue("SANJAY PANDIT");
        r3.createCell(2).setCellValue("100333774416");
        r3.createCell(3).setCellValue("4108663553");
        r3.createCell(4).setCellValue("WORKMAN");
        r3.createCell(5).setCellValue(20);
        r3.createCell(10).setCellValue(525.0);
        r3.createCell(11).setCellValue(11025.0);
        r3.createCell(23).setCellValue(13227.0);
        r3.createCell(29).setCellValue(1606.0);
        r3.createCell(30).setCellValue(11621.0);

        // Employee 4: RAJU SHARMA (Has NA in WhatsApp tab)
        Row r4 = wageSheet.createRow(13);
        r4.createCell(0).setCellValue(4);
        r4.createCell(1).setCellValue("RAJU SHARMA");
        r4.createCell(2).setCellValue("100444555666");
        r4.createCell(3).setCellValue("4109998887");
        r4.createCell(4).setCellValue("WORKMAN");
        r4.createCell(5).setCellValue(20);
        r4.createCell(10).setCellValue(525.0);
        r4.createCell(11).setCellValue(11025.0);
        r4.createCell(23).setCellValue(13227.0);
        r4.createCell(29).setCellValue(1606.0);
        r4.createCell(30).setCellValue(11621.0);

        // 2. PAYSLIP Tab (should be ignored by parser)
        Sheet payslipSheet = workbook.createSheet("PAYSLIP");
        payslipSheet.createRow(0).createCell(0).setCellValue("PAY SLIP VIEW");

        // 3. WHATS APP NO. Tab (with space in header "WHATS APP No.")
        Sheet contactSheet = workbook.createSheet("WHATS APP NO.");
        Row cRow1 = contactSheet.createRow(0);
        cRow1.createCell(0).setCellValue("Name & address of contractor : FRIENDS ENTERPRISE");

        Row cRow5 = contactSheet.createRow(4);
        cRow5.createCell(0).setCellValue("Sl. No.");
        cRow5.createCell(1).setCellValue("Name of Workman");
        cRow5.createCell(2).setCellValue("UAN No.");
        cRow5.createCell(3).setCellValue("E.S.I No.");
        cRow5.createCell(4).setCellValue("WHATS APP No.");

        // Contact 1: MANOJ YADAV -> Match on UAN
        Row cr1 = contactSheet.createRow(5);
        cr1.createCell(0).setCellValue(1);
        cr1.createCell(1).setCellValue("MANOJ YADAV");
        cr1.createCell(2).setCellValue("100222327291");
        cr1.createCell(3).setCellValue("4108643725");
        cr1.createCell(4).setCellValue("8967840595");

        // Contact 2: LAKHI NARAYAN LOHAR -> numeric 8101617475.0
        Row cr2 = contactSheet.createRow(6);
        cr2.createCell(0).setCellValue(2);
        cr2.createCell(1).setCellValue("LAKHI NARAYAN LOHAR");
        cr2.createCell(2).setCellValue("100202820575");
        cr2.createCell(3).setCellValue("4108661222");
        cr2.createCell(4).setCellValue(8101617475.0);

        // Contact 3: SANJAY PANDIT -> Blank UAN, matched via ESI No. 4108663553
        Row cr3 = contactSheet.createRow(7);
        cr3.createCell(0).setCellValue(3);
        cr3.createCell(1).setCellValue("SANJAY PANDIT");
        cr3.createCell(2).setCellValue("");
        cr3.createCell(3).setCellValue("4108663553");
        cr3.createCell(4).setCellValue("8617469384");

        // Contact 4: RAJU SHARMA -> NA
        Row cr4 = contactSheet.createRow(8);
        cr4.createCell(0).setCellValue(4);
        cr4.createCell(1).setCellValue("RAJU SHARMA");
        cr4.createCell(2).setCellValue("100444555666");
        cr4.createCell(3).setCellValue("4109998887");
        cr4.createCell(4).setCellValue("NA");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        workbook.close();

        // Execute single workbook parse
        ExcelParserService.ParsedWorkbookResult result =
                excelParserService.parseSingleWorkbook(new ByteArrayInputStream(out.toByteArray()));

        List<Employee> employees = result.employees();
        assertEquals(4, employees.size(), "Should parse 4 employees from wage sheet");

        // Validate Employee 1: MANOJ YADAV matched by UAN
        Employee e1 = employees.get(0);
        assertEquals("MANOJ YADAV", e1.getName());
        assertEquals("100222327291", e1.getUan());
        assertEquals("+918967840595", e1.getPhoneNumber());
        assertTrue(e1.hasPhone());

        // Validate Employee 2: LAKHI NARAYAN LOHAR has valid phone parsed from numeric cell
        Employee e2 = employees.get(1);
        assertEquals("LAKHI NARAYAN LOHAR", e2.getName());
        assertEquals("100202820575", e2.getUan());
        assertEquals("+918101617475", e2.getPhoneNumber(), "Should match 8101617475 to +918101617475");
        assertTrue(e2.hasPhone());

        // Validate Employee 3: SANJAY PANDIT matched by ESI No. fallback
        Employee e3 = employees.get(2);
        assertEquals("SANJAY PANDIT", e3.getName());
        assertEquals("+918617469384", e3.getPhoneNumber(), "Should match phone using ESI fallback");
        assertTrue(e3.hasPhone());

        // Validate Employee 4: RAJU SHARMA has NA
        Employee e4 = employees.get(3);
        assertEquals("RAJU SHARMA", e4.getName());
        assertNull(e4.getPhoneNumber(), "Should have null phone because value was NA");
        assertFalse(e4.hasPhone());
        assertTrue(result.contactStore().isKnown(e4.getUan(), e4.getEsiNo(), e4.getName()),
                "Should recognize employee exists in contact sheet despite NA phone");
    }

    @Test
    void testSanitizePhoneNumberVariations() {
        assertEquals("+918101617475", excelParserService.sanitizePhoneNumber("8101617475"));
        assertEquals("+918101617475", excelParserService.sanitizePhoneNumber("08101617475"));
        assertEquals("+918101617475", excelParserService.sanitizePhoneNumber("918101617475"));
        assertEquals("+918101617475", excelParserService.sanitizePhoneNumber("+918101617475"));
        assertEquals("+918101617475", excelParserService.sanitizePhoneNumber("+91 81016 17475"));
        assertEquals("+918101617475", excelParserService.sanitizePhoneNumber("8101617475.0"));
        assertEquals("+918101617475", excelParserService.sanitizePhoneNumber("8101617475.00"));
        assertEquals("+918101617475", excelParserService.sanitizePhoneNumber("\u00A08101617475\u00A0"));

        assertNull(excelParserService.sanitizePhoneNumber("NA"));
        assertNull(excelParserService.sanitizePhoneNumber("N/A"));
        assertNull(excelParserService.sanitizePhoneNumber("NIL"));
        assertNull(excelParserService.sanitizePhoneNumber("NONE"));
        assertNull(excelParserService.sanitizePhoneNumber("NULL"));
        assertNull(excelParserService.sanitizePhoneNumber("-"));
        assertNull(excelParserService.sanitizePhoneNumber(null));
        assertNull(excelParserService.sanitizePhoneNumber(""));
    }
}
