package com.grfriends.payslip.model;

/**
 * Result of attempting to send one employee's payslip via WhatsApp.
 */
public class DispatchResult {

    public enum Status {
        SENT,               // PDF generated + uploaded + message delivered
        SKIPPED_NO_CONTACT, // No matching UAN in contact master
        SKIPPED_NO_PHONE,   // Contact found but phone number missing/invalid
        FAILED              // API call failed (network error, invalid token, etc.)
    }

    private int index;
    private String employeeName;
    private String uan;
    private String phone;
    private Status status;
    private String errorMessage;
    private boolean pdfGenerated;

    public DispatchResult() {}

    public DispatchResult(int index, String employeeName, String uan, String phone,
                          Status status, String errorMessage, boolean pdfGenerated) {
        this.index = index;
        this.employeeName = employeeName;
        this.uan = uan;
        this.phone = phone;
        this.status = status;
        this.errorMessage = errorMessage;
        this.pdfGenerated = pdfGenerated;
    }

    // --- Static factory methods for cleaner controller code ---

    public static DispatchResult sent(int idx, String name, String uan, String phone) {
        return new DispatchResult(idx, name, uan, phone, Status.SENT, null, true);
    }

    public static DispatchResult skippedNoContact(int idx, String name, String uan) {
        return new DispatchResult(idx, name, uan, null, Status.SKIPPED_NO_CONTACT,
                "No matching contact found for this UAN", false);
    }

    public static DispatchResult skippedNoPhone(int idx, String name, String uan) {
        return new DispatchResult(idx, name, uan, null, Status.SKIPPED_NO_PHONE,
                "Contact found but phone number is missing or invalid", false);
    }

    public static DispatchResult failed(int idx, String name, String uan, String phone, String error) {
        return new DispatchResult(idx, name, uan, phone, Status.FAILED, error, true);
    }

    // --- Getters and Setters ---

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getUan() { return uan; }
    public void setUan(String uan) { this.uan = uan; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public boolean isPdfGenerated() { return pdfGenerated; }
    public void setPdfGenerated(boolean pdfGenerated) { this.pdfGenerated = pdfGenerated; }

    /**
     * Helper for Thymeleaf templates — CSS class based on status.
     */
    public String getStatusCssClass() {
        return switch (status) {
            case SENT -> "status-sent";
            case SKIPPED_NO_CONTACT, SKIPPED_NO_PHONE -> "status-skipped";
            case FAILED -> "status-failed";
        };
    }

    /**
     * Human-readable status label for the UI.
     */
    public String getStatusLabel() {
        return switch (status) {
            case SENT -> "✅ Sent";
            case SKIPPED_NO_CONTACT -> "⚠️ Skipped — No Contact";
            case SKIPPED_NO_PHONE -> "⚠️ Skipped — No Phone";
            case FAILED -> "❌ Failed";
        };
    }
}
