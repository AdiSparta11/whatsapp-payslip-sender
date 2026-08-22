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
   - Handles HTTP integration with Meta Cloud API (`v21.0`) to send payslips via WhatsApp.
4. **Deployment Setup**:
   - Multi-stage `Dockerfile` (Maven build + Alpine JRE runtime).
   - `render.yaml` for free one-click hosting on Render.com.

---

## 🔑 Environment & Credentials Needed
- `WHATSAPP_PHONE_NUMBER_ID`: Meta WhatsApp Phone Number ID
- `WHATSAPP_ACCESS_TOKEN`: Meta Access Token / System User Permanent Token

---

## 🚀 Recent Accomplishments
- Fixed two-row header bug in `ExcelParserService`.
- Expanded `Employee` model with itemized allowance fields.
- Added synthetic 2-row header POI unit test (`ExcelParserServiceTest.java`).
- Committed & pushed all changes to `main` branch on GitHub.

---

## 🎯 Current Status / Next Step
- Meta Business Account & WhatsApp Cloud API Setup.
- Deployment to Render.com.
