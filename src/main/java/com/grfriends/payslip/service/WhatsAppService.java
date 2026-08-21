package com.grfriends.payslip.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grfriends.payslip.config.WhatsAppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to interact directly with Meta WhatsApp Cloud API endpoints.
 * Handles PDF binary upload to /media and sending document messages via /messages.
 */
@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);

    private final WhatsAppConfig config;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WhatsAppService(WhatsAppConfig config) {
        this.config = config;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Uploads in-memory PDF bytes to Meta's Media endpoint.
     * Returns media_id on success.
     */
    public String uploadPdfMedia(byte[] pdfBytes, String filename) throws Exception {
        if (config.getAccessToken() == null || config.getAccessToken().startsWith("REPLACE_")) {
            throw(new IllegalStateException("Meta Access Token is not configured in application.properties"));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(config.getAccessToken());

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("messaging_product", "whatsapp");

        ByteArrayResource fileResource = new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        body.add("file", fileResource);
        body.add("type", "application/pdf");

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        String uploadUrl = config.getMediaUploadUrl();
        log.info("Uploading PDF '{}' ({} bytes) to Meta Media endpoint...", filename, pdfBytes.length);

        ResponseEntity<String> response = restTemplate.exchange(uploadUrl, HttpMethod.POST, requestEntity, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("id")) {
                String mediaId = root.get("id").asText();
                log.info("Successfully uploaded PDF to Meta. media_id: {}", mediaId);
                return mediaId;
            }
        }

        throw new RuntimeException("Failed to upload media to Meta API. Response: " + response.getBody());
    }

    /**
     * Sends document message to recipient phone number using uploaded media_id.
     */
    public String sendDocumentMessage(String recipientPhone, String mediaId, String filename, String caption) throws Exception {
        if (config.getAccessToken() == null || config.getAccessToken().startsWith("REPLACE_")) {
            throw new IllegalStateException("Meta Access Token is not configured in application.properties");
        }

        // Standardize phone number (strip + prefix for Meta payload if present)
        String cleanPhone = recipientPhone.replaceAll("\\D", "");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getAccessToken());

        Map<String, Object> documentObj = new HashMap<>();
        documentObj.put("id", mediaId);
        documentObj.put("filename", filename);
        if (caption != null && !caption.isBlank()) {
            documentObj.put("caption", caption);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", cleanPhone);
        payload.put("type", "document");
        payload.put("document", documentObj);

        String jsonBody = objectMapper.writeValueAsString(payload);
        HttpEntity<String> requestEntity = new HttpEntity<>(jsonBody, headers);

        String messagesUrl = config.getMessagesUrl();
        log.info("Sending document message to {} (media_id: {})...", cleanPhone, mediaId);

        ResponseEntity<String> response = restTemplate.exchange(messagesUrl, HttpMethod.POST, requestEntity, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.has("messages") && root.get("messages").isArray() && root.get("messages").size() > 0) {
                String wamid = root.get("messages").get(0).get("id").asText();
                log.info("Document sent successfully to {}. Message ID: {}", cleanPhone, wamid);
                return wamid;
            }
        }

        throw new RuntimeException("Failed to send message via Meta API. Response: " + response.getBody());
    }
}
