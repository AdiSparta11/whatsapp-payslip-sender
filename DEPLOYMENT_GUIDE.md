# 🌐 Step-by-Step Free Hosting Guide (Render.com)

Deploying this app to **Render.com** is **100% Free** and requires no credit card. Once deployed, anyone with your link (e.g., your brother) can access the app from any browser on PC, tablet, or phone.

---

## Step 1: Push Code to GitHub

1. Create a free account on [GitHub.com](https://github.com/) if you don't have one.
2. Create a new public or private repository named `whatsapp-payslip-sender`.
3. Push this project folder to your GitHub repo:
   ```bash
   git init
   git add .
   git commit -m "Initial commit for WhatsApp Payslip Sender"
   git branch -M main
   git remote add origin https://github.com/YOUR_GITHUB_USERNAME/whatsapp-payslip-sender.git
   git push -u origin main
   ```

---

## Step 2: Deploy to Render.com (100% Free)

1. Go to [Render.com](https://render.com/) and sign up for a free account (you can sign in with your GitHub account).
2. Click **New +** button in the top right → Select **Web Service**.
3. Connect your GitHub repository (`whatsapp-payslip-sender`).
4. Set the following fields:
   - **Name**: `friends-payslip-sender` (or any name you like)
   - **Region**: Singapore (or nearest to India)
   - **Language / Runtime**: **Docker**
   - **Instance Type**: **Free** ($0/month)
5. Scroll down to **Environment Variables** → Click **Add Environment Variable**:
   - `WHATSAPP_PHONE_NUMBER_ID` = `(Your Meta Phone Number ID)`
   - `WHATSAPP_ACCESS_TOKEN` = `(Your Meta Access Token)`
6. Click **Create Web Service**.

---

## Step 3: Done! 🚀

Render will automatically build the Docker container and launch the web app. 
In 2-3 minutes, you will get a permanent public URL like:

👉 **`https://friends-payslip-sender.onrender.com`**

Share this link with your brother. He can bookmark it and use it every month to upload Excel files and send payslips!

---

## 📌 Features of Free Render Hosting:
- **₹0 Cost**: Permanently free.
- **SSL Encryption**: Automatic HTTPS secure connection.
- **Zero Maintenance**: Auto-deploys whenever you push changes to GitHub.
- **Auto-Sleep**: Sleeps after 15 mins of inactivity. When your brother opens the link each month, it takes ~30 seconds to wake up.

---

## 📋 Pre-Flight Readiness & Go-Live Checklist

Before triggering a live monthly dispatch to all 114 employees:

1. **Meta Message Template Approval (BLOCKING)**:
   - Create template `monthly_payslip` in Meta WhatsApp Manager (Category: `Utility`, Header: `Document`, Body: `Hello {{1}}, your payslip for {{2}} {{3}} from M/S. FRIENDS ENTERPRISE is attached.`).
   - Status MUST show **APPROVED** in Meta Dashboard before sending real messages.

2. **Full File Dry Run (`dryRun=true`)**:
   - Upload full 114-row Wage Sheet and Contact Master with `dryRun` enabled.
   - Review results table in UI and inspect logs for any `verifySalaryMathSanity` pay discrepancy warnings.

3. **Phone Number Coverage Audit**:
   - Check how many of the 114 employees have matched WhatsApp phone numbers vs how many are flagged as skipped ("No Contact Master Match").

4. **Small Batch Live Test (2–3 Employees)**:
   - Send live (`dryRun=false`) to 2–3 test numbers (e.g. your own number + colleagues).
   - Inspect the received PDF document on a mobile device to confirm formatting and figures before running the full 114-employee batch.

