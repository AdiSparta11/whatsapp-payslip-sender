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
    private String cleanPhone;
    private String pdfFilename;
    private String waLink;
    private String month;
    private String year;
    private Status status;
    private String errorMessage;
    private boolean pdfGenerated;

    public DispatchResult() {}

    public DispatchResult(int index, String employeeName, String uan, String phone, String cleanPhone,
                          String pdfFilename, String waLink, String month, String year,
                          Status status, String errorMessage, boolean pdfGenerated) {
        this.index = index;
        this.employeeName = employeeName;
        this.uan = uan;
        this.phone = phone;
        this.cleanPhone = cleanPhone;
        this.pdfFilename = pdfFilename;
        this.waLink = waLink;
        this.month = month;
        this.year = year;
        this.status = status;
        this.errorMessage = errorMessage;
        this.pdfGenerated = pdfGenerated;
    }

    public static DispatchResult matched(int idx, String name, String uan, String phone, String cleanPhone,
                                         String pdfFilename, String waLink, String month, String year) {
        return new DispatchResult(idx, name, uan, phone, cleanPhone, pdfFilename, waLink, month, year, Status.SENT, null, true);
    }

    public static DispatchResult skippedNoContact(int idx, String name, String uan) {
        return new DispatchResult(idx, name, uan, null, null, null, null, null, null, Status.SKIPPED_NO_CONTACT,
                "No matching contact entry in Contact Master for this UAN", false);
    }

    public static DispatchResult skippedNoPhone(int idx, String name, String uan) {
        return new DispatchResult(idx, name, uan, null, null, null, null, null, null, Status.SKIPPED_NO_PHONE,
                "Contact entry found but phone number is missing or invalid", false);
    }

    public static DispatchResult failed(int idx, String name, String uan, String phone, String error) {
        return new DispatchResult(idx, name, uan, phone, null, null, null, null, null, Status.FAILED, error, false);
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

    public String getCleanPhone() { return cleanPhone; }
    public void setCleanPhone(String cleanPhone) { this.cleanPhone = cleanPhone; }

    public String getPdfFilename() { return pdfFilename; }
    public void setPdfFilename(String pdfFilename) { this.pdfFilename = pdfFilename; }

    public String getWaLink() { return waLink; }
    public void setWaLink(String waLink) { this.waLink = waLink; }

    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }

    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public boolean isPdfGenerated() { return pdfGenerated; }
    public void setPdfGenerated(boolean pdfGenerated) { this.pdfGenerated = pdfGenerated; }

    public String getStatusCssClass() {
        return switch (status) {
            case SENT -> "status-sent";
            case SKIPPED_NO_CONTACT, SKIPPED_NO_PHONE -> "status-skipped";
            case FAILED -> "status-failed";
        };
    }

    public String getStatusLabel() {
        return switch (status) {
            case SENT -> "Ready to Send";
            case SKIPPED_NO_CONTACT -> "No Contact Match";
            case SKIPPED_NO_PHONE -> "Invalid Phone";
            case FAILED -> "Generation Error";
        };
    }
}
