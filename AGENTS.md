# Project Context & AI Memory — WhatsApp Payslip Sender

## 📌 Project Overview
**M/S. FRIENDS ENTERPRISE — WhatsApp Payslip Dispatcher**
A Spring Boot web application that parses statutory Form XVII Excel wage registers, generates PDF payslips using OpenPDF, matches employee records with contact numbers, and dispatches payslips via Meta WhatsApp Cloud API.

- **GitHub Repository**: [AdiSparta11/whatsapp-payslip-sender](https://github.com/AdiSparta11/whatsapp-payslip-sender)
- **Local Directory**: `C:\Users\podda\.gemini\antigravity-ide\scratch\whatsapp-payslip-sender`

---

## 🛠️ Architecture & Key Components
1. **`ExcelParserService.java`**:
   - Form XVII wage sheet parser handling two-row merged headers (Row 9 group headers, Row 10 sub-headers).
   - Fixed POI 0-indexed column mapping (`COL_DAILY_RATE = 10` through `COL_NET_PAID = 30`).
   - Parses identity fields (`UAN`, `Name`, `ESI`, `Designation`, `Days Worked`) and itemized earnings/deductions (`Basic`, `DA`, `HRA`, `Washing`, `Fuel`, `Attendance`, `Food`, `Gratuity`, `Overtime`, `Performance Incentive`, `EPF`, `ESIC`, `Advance Deduction`, `Net Paid`).
   - Includes runtime sanity checking (`Gross - Deductions == Net Paid`).
2. **`PdfGeneratorService.java`**:
   - Generates OpenPDF payslips itemizing earnings and deductions line items into balanced table rows.
3. **`WhatsAppService.java` & `WhatsAppConfig.java`**:
   - Handles HTTP integration with Meta Cloud API (`v21.0`).
   - Uses Meta-approved Template messages (`type: "template"`, default `monthly_payslip`) with document header (`media_id`) and body parameters (`{{1}}` name, `{{2}}` month, `{{3}}` year) for outbound business-initiated messaging outside 24h window.
4. **Deployment Setup**:
   - Multi-stage `Dockerfile` (Maven build + Alpine JRE runtime).
   - `render.yaml` for free one-click hosting on Render.com.

---

## 🔑 Environment & Credentials Needed
- `WHATSAPP_PHONE_NUMBER_ID`: Meta WhatsApp Phone Number ID
- `WHATSAPP_ACCESS_TOKEN`: Meta Access Token / System User Permanent Token
- `WHATSAPP_TEMPLATE_NAME`: Approved Meta Message Template name (default: `monthly_payslip`)

---

## 🚀 Recent Accomplishments
- Fixed two-row header bug in `ExcelParserService`.
- Expanded `Employee` model with itemized allowance fields.
- Added synthetic 2-row header POI unit test (`ExcelParserServiceTest.java`).
- Switched outbound WhatsApp dispatch to Meta approved Template messages (`sendPayslipTemplate`).
- Committed & pushed all changes to `main` branch on GitHub.

---

## 🎯 Current Status / Next Steps
1. **Meta Template Approval (Blocking)**: Create and submit `monthly_payslip` in Meta WhatsApp Manager (Category: `Utility`, Header: `Document`). Wait for **APPROVED** status.
2. **Full File Dry Run (`dryRun=true`)**: Process all 114 rows in dry-run mode and inspect `verifySalaryMathSanity` logs.
3. **Phone Number Coverage Audit**: Audit matched phone count vs skipped ("No Contact") count.
4. **Small Batch Live Test**: Send live test to 2-3 numbers (`dryRun=false`) to verify PDF appearance on mobile screens before triggering the full 114 batch.
5. **Render.com Deployment**: Launch web app on Render.com free tier.

