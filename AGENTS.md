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
3. **`ZipService.java` & Batch Export**:
   - Packages all or selected generated employee PDF payslips into a single downloadable `.zip` archive (`Payslips_JULY_2026.zip`).
   - Supports full batch zip downloads and subset zip downloads for specific selected employee UANs.
4. **Interactive Manual WhatsApp Dispatch Dashboard**:
   - Builds `wa.me` click-to-chat links (`https://wa.me/<cleanPhone>?text=<encodedMessage>`) pre-filling recipient chat boxes.
   - Configurable message text template (`Hello {name}, please find your payslip for {month} {year} attached.`).
   - Persists sent row state in browser `localStorage` (`payslip_sent_{month}_{year}_{uan}`) with strikethrough styling and dynamic progress counter (`Sent X of Y`).
   - Filter toggles (`All | Pending Only | Sent Only`).
   - Prominently surfaces unmatched/skipped employees with specific reasons.
5. **Deployment Setup**:
   - Multi-stage `Dockerfile` (Maven build + Alpine JRE runtime).
   - `render.yaml` for free one-click hosting on Render.com.

---

## 🚀 Recent Accomplishments
- Fixed two-row header bug in `ExcelParserService`.
- Expanded `Employee` model with itemized allowance fields.
- Added synthetic 2-row header POI unit test (`ExcelParserServiceTest.java`).
- Pivoted to 100% human-driven manual WhatsApp dispatch dashboard & local ZIP export (`ZipService.java` & `ZipServiceTest.java`).
- Added browser `localStorage` sent tracking, filter tabs, subset ZIP download, and pre-filled `wa.me` links.
- Committed & pushed all changes to `main` branch on GitHub.

---

## 🎯 Current Status / Next Steps
1. **Local Usage / Render Deployment**: App is 100% complete and operational locally or on Render.com free tier.
2. **Monthly Workflow**: Upload Excel registers $\rightarrow$ Download ZIP of 114 PDFs $\rightarrow$ Click `Open Chat` $\rightarrow$ Attach PDF & Send.


