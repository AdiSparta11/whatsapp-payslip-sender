package com.grfriends.payslip.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ZipServiceTest {

    private ZipService zipService;

    @BeforeEach
    void setUp() {
        zipService = new ZipService();
    }

    @Test
    void testCreateZipArchive() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("Payslip_MANOJ_YADAV_JULY_2026.pdf", "%PDF-1.4 Test PDF Content".getBytes());
        files.put("Payslip_RAJU_DAS_JULY_2026.pdf", "%PDF-1.4 Test PDF Content 2".getBytes());

        byte[] zipBytes = zipService.createZipArchive(files);

        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0, "ZIP bytes should be non-empty");

        // Verify ZIP entries
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));
        ZipEntry entry1 = zis.getNextEntry();
        assertNotNull(entry1);
        assertEquals("Payslip_MANOJ_YADAV_JULY_2026.pdf", entry1.getName());

        ZipEntry entry2 = zis.getNextEntry();
        assertNotNull(entry2);
        assertEquals("Payslip_RAJU_DAS_JULY_2026.pdf", entry2.getName());

        assertNull(zis.getNextEntry(), "ZIP should contain exactly 2 files");
    }
}
