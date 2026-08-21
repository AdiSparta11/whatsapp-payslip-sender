package com.grfriends.payslip.service;

import com.grfriends.payslip.model.Employee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PdfGeneratorServiceTest {

    private PdfGeneratorService pdfGeneratorService;

    @BeforeEach
    void setUp() {
        pdfGeneratorService = new PdfGeneratorService();
    }

    @Test
    void testGeneratePayslipPdf() throws Exception {
        Employee emp = new Employee();
        emp.setSlNo(1);
        emp.setName("TEST WORKER");
        emp.setUan("100123456789");
        emp.setEsiNo("4100123456");
        emp.setDaysWorked("26");
        emp.setBasicRate("450.00");
        emp.setBasicAmount("11700.00");
        emp.setHra("1170.00");
        emp.setGrossEarnings("12870.00");
        emp.setEpfDeduction("1404.00");
        emp.setEsiDeduction("96.50");
        emp.setTotalDeductions("1500.50");
        emp.setNetPayable("11369.50");
        emp.setMonth("JULY");
        emp.setYear("2026");

        byte[] pdfBytes = pdfGeneratorService.generatePayslipPdf(emp);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500, "PDF should be non-empty");

        // Verify PDF Magic Number header (%PDF-)
        String header = new String(pdfBytes, 0, 5);
        assertEquals("%PDF-", header);
    }
}
