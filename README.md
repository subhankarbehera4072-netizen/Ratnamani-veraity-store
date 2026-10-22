# Ratnamani Variety Store (रत्नमणि वैरायटी स्टोर)

A production-quality Android application built with Kotlin, Jetpack Compose, Material Design 3, Room Database (SQLite), and Google Drive API. Specifically designed for Ratnamani Variety Store supporting Indian Rupees (₹), grams, kilograms, millilitres, litres, pieces, customer Udhar Khata (उधारी बही खाता), point-of-sale billing, inventory stock management, and cloud backup.

---

## 🌟 Key Features

1. **Premium Modern Indian Grocery Theme**:
   - Rich Grocery Green and Amber Gold Material 3 theme.
   - Light and Dark modes.
   - Bilingual support: **English** and **हिंदी (Hindi)** with instant one-tap toggle.
   - Adaptive responsive layouts for phones and tablets (with NavigationRail).
   - High accessibility with clear large typography, high touch targets, and currency formatting.

2. **Smart Weight & Price Calculator**:
   - Real-time decimal-safe calculation (`BigDecimal`).
   - Grams (g), Kilograms (kg), Millilitres (ml), Litres (L), Pieces (pc).
   - Reverse Budget Mode: calculates exact grams for a given rupee amount (e.g., ₹20 of ₹48/kg sugar = 416.67g).
   - Quick one-tap preset buttons (100g, 250g पाव, 500g आधा किलो, 1kg, 2kg, etc.).
   - One-tap "Add to Active Bill".

3. **Point of Sale (POS) Billing System**:
   - Instant product search and quick-tap favorites row.
   - Integrated Camera Barcode Scanner via CameraX & ML Kit.
   - Multi-item billing with quantity steppers and line-item discounts.
   - Cash Received & Return Change calculator (e.g., Bill ₹177, paid ₹500 note -> Change ₹323).
   - Payment modes: Cash, UPI (QR ready), Card, and Udhar (Customer Credit).
   - Atomic database transactions preventing stock corruption.
   - Built-in PDF receipt generation, printing, and WhatsApp share.

4. **Customer Management & Udhar Khata (उधारी बही खाता)**:
   - Digital customer ledger with outstanding balances.
   - Debit (credit purchase) and Credit (jama payment) entries.
   - One-tap Repayment (जमा दर्ज करें) with Cash / UPI.
   - Instant WhatsApp text payment reminder with 1-tap compose.
   - Printable / Shareable PDF Customer Account Statement.

5. **Inventory & Stock Auditing**:
   - Current stock levels with low-stock alerts.
   - Restock purchases modal with cost-price updates.
   - Full movement audit trail (`SALE`, `PURCHASE_RESTOCK`, `MANUAL_ADJUSTMENT`).
   - CSV export for products and sales history.

6. **Profit, Expenses & Sales Reports**:
   - Filters: Today, This Week, This Month, All Time.
   - Clear distinction: **Gross Total Revenue**, **Cost of Goods Sold (COGS)**, **Gross Profit**, **Operating Expenses**, and **Net Profit**.
   - Shop operating expenses ledger (Rent, Electricity, Tea/Snacks, Transport).
   - Payment method breakdown: Cash vs UPI vs Udhar.
   - Top-selling products summary.

7. **Google Drive Cloud Backup & Restore + Local Backup**:
   - Connects to owner's Google Account (`subhankarbehera4072@gmail.com`).
   - Dedicated folder: `Smart Grocery Shop Manager Backup`.
   - Full transactional restore capability.
   - Offline local backup export to device and file import.

8. **Admin Security**:
   - Secure PBKDF2 with SHA-256 password hashing.
   - Default PIN: `1234` (customizable in Settings).
   - Protects financial settings and reports.

---

## 🛠️ Technology Stack

- **Platform**: Android (minSdk 24, targetSdk 36)
- **Language**: Kotlin 2.2.10
- **UI Toolkit**: Jetpack Compose + Material 3
- **Local Database**: Room 2.7.0 (SQLite with KSP)
- **Networking**: OkHttp 4.10.0 + Moshi
- **Camera & Barcode**: CameraX 1.5.0 + Google ML Kit Barcode Scanning 18.3.1
- **Google Services**: Play Services Auth 21.3.0 + Google Drive REST API v3
- **PDF Engine**: Native `android.graphics.pdf.PdfDocument` + `FileProvider`

---

## 📋 Step-by-Step Setup Instructions

### 1. Opening the Project in Android Studio
1. Launch **Android Studio** (Ladybug / Koala or newer).
2. Select **Open** and choose the root directory of this repository.
3. Wait for Gradle sync to complete automatically.

### 2. Configuring the Google Cloud Project for Google Drive
1. Visit the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project or select an existing project (e.g., `SmartGroceryApp`).
3. Under **APIs & Services** > **Library**, search for **Google Drive API** and click **Enable**.
4. Under **APIs & Services** > **OAuth consent screen**:
   - Select **External** (or Internal for Workspace).
   - Enter App Name: `Smart Grocery Shop Manager`.
   - User support email: `subhankarbehera4072@gmail.com`.
   - Under **Scopes**, add:
     - `https://www.googleapis.com/auth/drive.file` (Per-file access created by app)
     - `https://www.googleapis.com/auth/drive.appdata` (Application data folder)
   - Add `subhankarbehera4072@gmail.com` as a **Test User**.

### 3. Configuring OAuth Android Client Identity
1. In Google Cloud Console, navigate to **Credentials** > **Create Credentials** > **OAuth client ID**.
2. Select **Android** as Application type.
3. Enter package name:
   `com.aistudio.smartgrocery.vbnzkq`
4. Enter your SHA-1 certificate fingerprint:
   - For debug builds, get the SHA-1 by running:
     ```bash
     keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
     ```
   - Copy the `SHA1:` hexadecimal string into the Google Cloud OAuth client configuration.

### 4. Connecting the Owner's Google Account
1. Open the app on your Android device.
2. Tap the **Settings & Backup** icon in the top right (or navigation rail).
3. Under **Google Drive Cloud Backup**, tap **Connect Google Drive Account**.
4. Select `subhankarbehera4072@gmail.com`.
5. Grant Drive permissions when prompted.
6. The app displays `Connected: subhankarbehera4072@gmail.com` with a green checkmark.

### 5. Creating and Verifying the Backup Folder
1. When you tap **Backup Now**, the app queries Google Drive for `Smart Grocery Shop Manager Backup`.
2. If the folder does not exist, the app automatically creates it in Drive via the v3 REST API.
3. It packages the database tables into `grocery_backup_YYYY-MM-DD_HH-mm-ss.json` and uploads it.
4. You can also tap **List Backups** in the app to verify existing remote files in Drive.

### 6. Building and Installing the APK
To assemble the debug APK from terminal:
```bash
gradle assembleDebug
```
The generated APK is located at:
`app/build/outputs/apk/debug/app-debug.apk`

To install on a connected phone via ADB:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 7. Testing Offline Billing
1. Turn off Wi-Fi and Mobile Data on the phone (Airplane mode).
2. Open **Smart Grocery Shop Manager**.
3. Go to **Billing (POS)**, tap items, or use the **Weight Calculator**.
4. Tap **Complete Bill** -> Bill is saved immediately in the local SQLite Room database, inventory is deducted, and Udhar is updated without requiring an internet connection.
5. Re-enable internet connectivity -> Go to **Settings & Backup** -> Tap **Backup Now** to synchronize records to Google Drive.

### 8. Customizing Shop Profile, Products & Prices
- **Shop Name & Profile**: Go to **Settings & Backup** -> Edit Shop Name, Tagline, Phone, Address, and Receipt Footer -> Tap **Save Shop Profile**.
- **Change Products & Prices**: Go to **Products** screen -> Tap any item to edit rate, cost, and stock, or tap the **+** FAB to add new items.
