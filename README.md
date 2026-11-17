# Kerala Calendar 2027 — Android Studio project

An offline-first Android app using Kotlin and XML layouts. It uses the exact calendar PDF supplied for this project as its source, and adds native date-level calendar records so the printed details can be viewed and searched without opening the PDF.

## Features

### Native Kerala calendar data
- Gregorian calendar for January–December 2027.
- Saka, Hijri and Malayalam day numbers on every date, extracted from the PDF's printed date cells.
- The nakshatra and tithi text shown in each date cell, including end-time values exactly as extracted.
- English and Malayalam observances, vrathams, temple festivals and other date notes from the daily calendar entries.
- Muhurtham details and time windows where printed in the source.
- Monthly notes and timing tables, including Rahu Kalam and Gulika/Yamakandakam information where provided on the page.
- Dates printed in red in the PDF are marked in red in the app and identified as public-holiday markings, following the PDF's own legend.
- Search across date details, almanac terms, observances and monthly text.
- A month-details view that gathers that month's timing notes and day-by-day entries.

### Personal planning
- Add personal, vacation/leave, travel, family, work and other events.
- Select a start date and an end date for one event. Both endpoints are included.
- Events spanning several days are marked on every covered date.
- Events are stored locally on the device; no account or server is used.
- Events can be viewed and deleted from **My Events**.

### Original source
- The original supplied 12-page Kerala Calendar 2027 PDF is bundled unchanged and can be viewed offline page by page.
- The JSON asset also retains extracted page text for offline search.

## Source-data notes

The native data was extracted from the supplied PDF's text layer and the printed date-cell text. The red-date flag is derived from the colour of the large Gregorian date numeral in the source page. No dates, nakshatras, tithis or muhurtham timings have been generated from an external almanac or calculated independently. The source itself says nakshatra timings are end times (IST) from Kerala sunrise.

The untouched PDF remains included because its visual layout is the authoritative reference for checking a printed item, especially where Malayalam font layers overlap or text extraction may not preserve the original typography perfectly.

## Build a debug APK on Windows

1. Install a current stable Android Studio.
2. In **Tools → SDK Manager → SDK Platforms**, install **Android 15 / API 35**. In **SDK Tools**, install **Android SDK Build-Tools 35.x**.
3. Android Studio includes a bundled JDK. The build helper checks `JAVA_HOME`, common Android Studio JDK locations, and then `PATH` for a JDK 17 or newer.
4. Extract this project ZIP. Double-click `build-apk.bat`. The script checks the JDK and SDK, downloads the official Gradle 8.9 distribution if it is not already cached, and runs the debug build. An internet connection is required for the first Gradle/dependency download.
5. The installable debug APK will be created at `app/build/outputs/apk/debug/app-debug.apk`. Copy that APK to your Android phone and open it to install. You may need to allow installation from that file manager.

You can also open the extracted `KeralaCalendar2027` folder in Android Studio, allow Gradle sync, then choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**. For macOS or Linux, use this Android Studio build menu rather than the Windows `.bat` helper. The project uses Android Gradle Plugin 8.7.3, Gradle 8.9 and JDK 17 or newer.

Minimum Android version: Android 8.0 (API 26). No Internet permission, account, cloud service or third-party library is required by the app.

## Main project files

- `app/src/main/java/com/keralacalendar2027/MainActivity.kt` — app screens and logic.
- `app/src/main/assets/calendar_2027.json` — native 2027 month/day almanac data.
- `app/src/main/assets/kerala_calendar_2027.pdf` — original supplied PDF.
- `app/src/main/res/layout/activity_main.xml` — XML layout.

## Build the APK using GitHub Actions (no local Android SDK needed)

This project includes `.github/workflows/build-apk.yml`. To build in GitHub's hosted Android/Java environment:

1. Create a GitHub repository and upload the extracted project folder contents, including the hidden `.github` folder.
2. Open the repository's **Actions** tab and select **Build Kerala Calendar 2027 APK**.
3. Choose **Run workflow**. The workflow installs JDK 17, Android API 35 and Gradle 8.9, then runs `assembleDebug`.
4. When the run completes successfully, open the run's **Artifacts** section and download `kerala-calendar-2027-debug-apk`.
5. Extract the artifact. The installable file is `app-debug.apk`.

This creates a debug-signed APK suitable for direct installation/testing. It is not a Play Store release-signed package.
