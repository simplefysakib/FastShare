# Fast Share 🚀

Fast Share is a high-speed, offline file-transfer Android application. Built natively with Java, it leverages the **Google Nearby Connections API** to facilitate seamless peer-to-peer (P2P) file sharing without requiring an active internet connection.

Designed with a focus on speed and stability, Fast Share utilizes **NIO (Non-blocking I/O) Zero-Copy** transfer protocols to maximize hardware bandwidth, reaching speeds up to 50 MB/s (hardware dependent). 

---

## ✨ Features

### Core Functionality
*   **100% Offline Sharing:** Transfer files locally via Bluetooth, Wi-Fi Direct, and Hotspot without consuming mobile data.
*   **Ultra-Fast Transfer Engine:** Uses `FileChannel.transferTo()` (Zero-Copy) for instant payload saving and large file (e.g., 10GB+ movies) handling without CPU bottlenecks.
*   **Universal File Support:** Share installed Apps (extracted as APKs), Photos, Videos, and any system Documents.
*   **Smart Device Discovery:** 
    *   Interactive **Radar UI** to discover nearby users.
    *   **QR Code Scanner** for instant, secure device pairing.
*   **Real-Time Dashboard:** Custom liquid wave progress UI, live speed tracking (MB/s), and real-time payload estimations.

### App Highlights
*   **Hardware Stabilization:** Smart micro-delays between metadata and payload transmission to prevent crashes on low-end devices.
*   **Transfer History:** Built-in history tracking to review sent and received files.
*   **Profile Customization:** Choose from distinct avatar icons and customize device display names.
*   **Storage Management:** Real-time internal storage tracking and one-tap access to Android's storage manager.
*   **Monetization Ready:** Seamlessly integrated with **Unity Ads** (Banner & Interstitial) with intelligent ad-loading that preserves the user experience.

---

## 🛠️ Technologies & Libraries

*   **Language:** Java
*   **UI:** XML / Material Design Components
*   **Core API:** [Google Play Services Nearby Connections API](https://developers.google.com/nearby/connections/overview) (`18.5.0`)
*   **Barcode/QR Scanner:** [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded) (`4.3.0`)
*   **Ads SDK:** [Unity Ads](https://unity.com/solutions/unity-ads) (`4.12.2`)
*   **Build System:** Gradle (7.5) / AGP (7.4.2)

---

## 📂 Project Structure

```text
app/src/main/java/com/fastshare/transfer/
 ├── MainActivity.java           # Dashboard, Profile, Storage & Permissions
 ├── FilePickerActivity.java     # Multi-tab file selector (Apps, Photos, Files)
 ├── DeviceDiscoveryActivity.java# Radar UI, Advertising/Discovery & QR Scanning
 ├── TransferActivity.java       # NIO Engine, Payload routing, Wave UI
 ├── HistoryActivity.java        # Transfer logs and details
 ├── PortraitCaptureActivity.java# Custom ZXing orientation lock
 └── WaveProgressView.java       # Custom UI component for transfer progress
```

---

## ⚙️ Installation & Build Steps

### Prerequisites
*   Android Studio (Flamingo/Giraffe or later) OR AndroidIDE (for on-device compilation).
*   Minimum SDK: `24` (Android 7.0 Nougat)
*   Target SDK: `34` (Android 14)

### Build Instructions
1.  **Clone the repository:**
    ```bash
    git clone https://github.com/simplefysakib/FastShare.git
    ```
2.  **Open the Project:**
    Open the cloned directory in Android Studio.
3.  **Sync Gradle:**
    Ensure `gradle-wrapper.properties` is set to Gradle `7.5`. Sync the project to download dependencies.
4.  **Run the App:**
    Connect an Android device (or emulator with Bluetooth/Wi-Fi support) and press Run (`Shift + F10`).

> **Note on App Signing:** To generate a release APK for app stores, configure the `signingConfigs` block in `app/build.gradle` with your JKS keystore details.

---

## 📱 Usage Guide

### To Send Files:
1.  Tap the large **SEND** button on the Home Screen.
2.  Navigate through the `Apps`, `Photos`, or `Files` tabs. Select multiple items.
3.  Tap **Send Selected**.
4.  Wait for the Radar to discover the receiver's avatar, or tap **Scan QR** to scan the receiver's screen.
5.  Watch the blazing fast transfer on the Progress Screen!

### To Receive Files:
1.  Tap the large **RECEIVE** button on the Home Screen.
2.  Your device will instantly generate a QR code and begin advertising its presence.
3.  Accept the incoming connection prompt.
4.  Received files are automatically saved to `Internal Storage > Downloads > FastShare`.

---

## 🛡️ Permissions Required

Fast Share requests permissions strictly necessary for P2P networking and file management:
*   `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` (Android 12+)
*   `NEARBY_WIFI_DEVICES` (Android 13+)
*   `ACCESS_FINE_LOCATION` (Required by Google Nearby API for discovery)
*   `READ/WRITE_EXTERNAL_STORAGE` (Android 10 and below)
*   `MANAGE_EXTERNAL_STORAGE` (Android 11+ for full file access and APK extraction)

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
