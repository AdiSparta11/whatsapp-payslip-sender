package com.grfriends.payslip.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service to bundle multiple generated PDF files into a ZIP archive.
 */
@Service
public class ZipService {

    private static final Logger log = LoggerFactory.getLogger(ZipService.class);

    /**
     * Create a ZIP archive byte array from a map of filename -> PDF byte array.
     */
    public byte[] createZipArchive(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                String filename = entry.getKey();
                byte[] content = entry.getValue();

                if (filename == null || content == null) continue;

                ZipEntry zipEntry = new ZipEntry(filename);
                zos.putNextEntry(zipEntry);
                zos.write(content);
                zos.closeEntry();
            }
            zos.finish();
        }

        byte[] zipBytes = baos.toByteArray();
        log.info("Created ZIP archive containing {} files ({} bytes)", files.size(), zipBytes.length);
        return zipBytes;
    }
}
