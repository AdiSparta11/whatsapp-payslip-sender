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
}
