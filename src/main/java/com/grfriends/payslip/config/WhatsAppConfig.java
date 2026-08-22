package com.grfriends.payslip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Reads WhatsApp Cloud API credentials from application.properties.
 */
@Configuration
@ConfigurationProperties(prefix = "whatsapp")
public class WhatsAppConfig {

    private String phoneNumberId;
    private String accessToken;
    private String apiVersion = "v21.0";
    private String templateName = "monthly_payslip";

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    /**
     * Base URL for Media upload endpoint.
     * POST https://graph.facebook.com/{api-version}/{phone-number-id}/media
     */
    public String getMediaUploadUrl() {
        return String.format("https://graph.facebook.com/%s/%s/media", apiVersion, phoneNumberId);
    }

    /**
     * Base URL for Messages endpoint.
     * POST https://graph.facebook.com/{api-version}/{phone-number-id}/messages
     */
    public String getMessagesUrl() {
        return String.format("https://graph.facebook.com/%s/%s/messages", apiVersion, phoneNumberId);
    }
}
