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
   - Embeds `NotoSansBengali-Regular.ttf` for full Bengali Unicode font rendering (`বাংলা হরফ`).
   - Renders bilingual (English + Bengali) headers, employee info, earnings/deductions labels, and totals.
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
- Supported 1-file master Excel workbook containing both the Form XVII wage sheet and WHATS APP NO tabs.
- Added dual-key matching on UAN No and ESI No with NA/invalid phone handling.
- Embedded NotoSansDevanagari-Regular.ttf alongside NotoSansBengali-Regular.ttf for Trilingual (English / Hindi / Bengali) PDF generation.
- Corrected Hindi and Bengali terminology and spellings (e.g. मूल वेतन / মূল মজুরি, मकान किराया भत्ता / বাড়ি ভাড়া ভাতা, महंगाई भत्ता / মহার্ঘ ভাতা, Gross earnings: संपूर्ण वेतन, Net payable: कुल राशि).
- Committed & pushed all changes to `main` branch on GitHub triggering auto-deploy on Render.com.

---

## 🎯 Current Status / Next Steps
1. **Local Usage / Render Deployment**: App is 100% operational on Render.com (`https://whatsapp-payslip-sender.onrender.com`).
2. **Monthly Workflow**: Upload single Master Excel file $\rightarrow$ Download ZIP of all generated Trilingual PDFs $\rightarrow$ Click `Open Chat` $\rightarrow$ Send to employees.



