# Alpha Blocker

A private, offline self-control blocker for Android, built for a Google Pixel on Android 16.

- **No internet permission.** The app cannot send anything anywhere; Android enforces this.
- **Stronger is instant, weaker waits.** Tightening applies now. Loosening waits for your delay and is frozen during a fixed lock period.
- **Hard to remove.** Device Owner mode blocks uninstall, factory reset, safe mode, new users, Private Space and Private DNS changes.
- **Catholic encouragement.** The block screen shows a Douay-Rheims verse: purity verses for adult content, diligence verses for time-wasting apps.

---

## 1. Build it

You need **Android Studio** (Ladybug 2024.2 or newer) on your computer.

1. Unzip the project and choose **File › Open** › the `AlphaBlocker` folder.
2. Let Android Studio sync. If it asks to create/use the Gradle wrapper, accept (the project pins Gradle 8.9 in `gradle/wrapper/gradle-wrapper.properties`).
3. **Build › Generate Signed App Bundle / APK › APK**, create a keystore (keep it safe: you need the same key to install updates), build `release`.
4. Copy the APK to your phone and install it (allow "Install unknown apps" for your file manager once).

> Not compiled yet: this code was written without an Android SDK available, so expect the first build to possibly surface a few small compile errors. Fix them in Android Studio (or paste them back to Claude).

### Verify the privacy claim yourself
After building, check the final merged permissions (libraries can add permissions, so check the *built* APK, not just the source):

```
<android-sdk>/build-tools/<version>/aapt dump permissions app-release.apk
```
You should see only `RECEIVE_BOOT_COMPLETED`, `POST_NOTIFICATIONS`,  the optional `WRITE_SECURE_SETTINGS` (only works if you grant it by ADB, see below; used for auto re-enable and the banking pause) and Android's internal `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. **No `INTERNET`.**

---

## 2. Set it up

Open Alpha Blocker and follow the wizard.

### Device Owner (maximum, recommended)
Device Owner can only be set when the phone has **no accounts**.

1. **Settings › Passwords & accounts**: remove every account (Google, etc.). Your data stays in the cloud; you'll sign back in after.
2. **Settings › About phone**: tap *Build number* 7 times. Then **System › Developer options › USB debugging** on.
3. Install [platform-tools](https://developer.android.com/tools/releases/platform-tools) on your computer, connect the phone, and run:
   ```
   adb shell dpm set-device-owner com.alphablocker/.enforcement.AdminReceiver
   ```
   Expected output starts with `Success: Device owner set`.
4. Add your accounts back, return to the wizard, tap **Check**.

Once protection is on, "Block developer options / USB debugging" switches ADB off, so ADB can't be used to undo this.

### Device Admin (medium)
Tap **Activate Device Admin** in the wizard. No computer needed, but factory reset, safe mode, installs and Private DNS changes can't be blocked.

### Blocker service (accessibility)
Android 13+ hides the switch for sideloaded apps until you allow it:
first tap the switch in **Settings › Accessibility › Alpha Blocker** once and accept the "Restricted setting" message (the unlock option only appears after this), then **Settings › Apps › Alpha Blocker › ⋮ (top right) › Allow restricted settings**, then **Settings › Accessibility › Alpha Blocker › Use Alpha Blocker**. The setup wizard's **Help: switch greyed out?** button walks through this with shortcuts.

### Auto re-enable the blocker service (recommended)
Android lets anyone switch an accessibility service off: in Accessibility settings, in **Security & privacy › apps with full access**, or from the "has full access" notification. Alpha Blocker closes those screens while protection is on, and with one extra ADB command it can also **turn itself straight back on** if it's switched off any other way:
```
adb shell pm grant com.alphablocker android.permission.WRITE_SECURE_SETTINGS
```
If Alpha Blocker runs in a secondary user (not the phone's main user), add `--user <number>`: `adb shell pm grant --user 10 com.alphablocker android.permission.WRITE_SECURE_SETTINGS`. Find the number with `adb shell pm list users`; the Status tab shows the exact command for your user. The Status tab shows ✔ **Auto re-enable blocker service** once it's granted. It only acts while protection is on. In Device Owner mode, browsers and rule apps are also paused while the service is off ("fail closed").

### DNS filter (recommended)
A family DNS service blocks adult sites in **every** app and browser, including links opened inside apps, which the word blocker can't read. Android sends every lookup to it through the **Private DNS** setting; Alpha Blocker itself still never goes online.

| Host (Private DNS) | Service | Filters |
|---|---|---|
| `family-filter-dns.cleanbrowsing.org` | CleanBrowsing Family | Adult sites, SafeSearch, YouTube Restricted Mode; also VPN/proxy sites and some mixed sites like Reddit |
| `adult-filter-dns.cleanbrowsing.org` | CleanBrowsing Adult | Adult sites, SafeSearch |
| `family.adguard-dns.com` | AdGuard Family | Adult sites, ads and trackers, SafeSearch |
| `family.cloudflare-dns.com` | Cloudflare for Families | Adult sites, malware |

- **Device Owner:** enter the host in the setup wizard or **Protect › DNS filter**; Alpha Blocker sets it and locks the setting.
- **Device Admin:** Settings › Network & internet › Private DNS › *Private DNS provider hostname*. While protection is on, that screen is closed automatically.
- A VPN app replaces Private DNS while it's connected. If you use one, set a family DNS inside the VPN app too.

Test: visit a known adult site in each allowed browser. It should fail to load (DNS) or be closed (word/page blocker).

### Banking pause (optional)
Many bank apps refuse to run while an app installed outside the Play Store has accessibility access, because banking malware works that way. They show a warning and close until you uninstall Alpha Blocker. The banking pause lets you use them anyway:

1. **Protect › Banking pause**: switch on **Allow banking pause** and **Add a bank app** for each bank. Both wait for your delay once protection is on (do it before starting protection to make it instant). Browsers and the apps the phone needs can't be picked.
2. Requirement: the auto re-enable ADB grant above (with `--user N` if Alpha Blocker runs in a secondary user). Works in Device Owner and Device Admin mode.
3. To bank: **Status › Banking › Open <your bank>**. No daily limit. Alpha Blocker switches its blocker service off and opens the bank, then:
   - **After your pause length** (2, 3 or 4 minutes; set it under **Pause length**, default 4; shorter is instant, longer waits the delay): a popup offers **Turn protection back on** or **Give me 2 more minutes**. In Device Admin mode it appears as a heads-up notification with the same two buttons.
   - **2 minutes later:** protection turns back on by itself and a popup says **Protection is back on**. A restart also ends the pause.

What stays protected during the pause:

| Mode | Still blocked during the pause |
|---|---|
| Device Owner | Browsers and apps with rules (suspended before the bank opens), uninstall, force stop, clear storage, Private DNS, developer options, factory reset |
| Device Admin | Nothing is blocked for those minutes. The admin-deactivation guard and protected screens are off too, so Alpha Blocker could be removed during a pause |

If the bank re-checks while it's open, it may close when protection comes back; open it again from Alpha Blocker.

### Before you start: set the Protect tab first
Setup leaves protection **off**, and while it's off every change is instant. Once it's on, anything that loosens protection waits your delay. The **Status** tab (and the first page of **Start protection**) shows a checklist. Do these first, in this order:

1. **ADB permission** for auto re-enable and the banking pause (needs USB debugging, so do it before step 2).
2. **Block USB debugging** (Device Owner).
3. **Banking pause** + your bank apps, if a bank app complains about Alpha Blocker.
4. **Install every app you need** before blocking installs (Device Owner); later installs and updates need a 15-minute window that waits your delay.
5. **DNS filter**.
6. Review the starter word/page lists (removals wait later; additions are always instant).

### Review, then start
Setup leaves protection **off** so you can review the starter word and page lists while every edit is still instant (Filters tab). Remove anything that would cause false positives now; once protection is on, removals wait for the delay.

Then **Status › Start protection**. You'll get a **recovery code** shown once. Write it on paper and store it somewhere inconvenient (sealed envelope, or with a trusted friend).

---

### Language
The app is in English and Spanish. It follows the phone's language, or choose it on the first setup screen or **Protect › Language**. On Android 13+ you can also set it in **Settings › Apps › Alpha Blocker › Language**. The block-screen verses follow the language: Douay-Rheims in English, Torres Amat (1825) in Spanish (34 verses checked against the English set). Your own verses always show as you typed them.

The protected-screens list matches Pixel settings in both English and Spanish (phrases like `Deactivate|Desactivar`). The Spanish wording is best-effort: with your phone in Spanish, after starting protection open each protected screen once to confirm it closes. If one doesn't, add your own entry on the Filters tab (adding is instant).

## 3. How it behaves

| Action | Protection off | Tightening | Loosening | Loosening during lock |
|---|---|---|---|---|
| Result | applied now | applied now | waits *delay* hours | refused |

**Tightening:** adding words/pages/rules, stricter rule edits, longer delay, longer lock, fewer extensions, turning a feature on, blocking a browser, cancelling a pending change.
**Loosening:** removing anything, weaker rule edits, shorter delay, more extensions, turning a feature off, allowing a browser, changing the Private DNS host, install windows, turning protection off.

**Recovery code:** single-use. Immediately turns protection off, ends the lock, clears pending changes. A new code is created when you start protection again. Wrong attempts are throttled. Forgot your PIN? The PIN screen has **Forgot PIN? Use recovery code** (while protection is on); after the code you choose a new PIN.

**Blocked apps reopen fresh:** when an app is blocked, Alpha Blocker force-stops it first: you'll see its App info page for about a second while the blocker presses **Force stop › OK** for you, then the block screen. The app's process ends and it disappears from Recents (no preview), so opening it again starts at its first screen; closing and reopening it quickly can't be used to get further in. Its data (logins, settings) isn't touched. (Android 14+ no longer lets apps stop other apps directly, so this is the reliable way.)

**Daily limits:** when used up, you get a limited number of short extensions per day (shared across apps), then a hard block until midnight. While a time-limited app is open, an optional quiet notification counts down the time left today (**Apps › Time-left notification**, on by default).

**If the blocker service is switched off** (Device Owner): every browser and every app with a rule is suspended until it's back on ("fail closed"), and a notification tells you.

**Uninstalling later:** request "Turn protection off", wait the delay (or use the recovery code), then **Protect › Release device**, then uninstall normally.

---

## 4. What it can't stop (threat model)

Honest limits, so you know where the gaps are:

- **Device Admin mode:** factory reset, safe mode, and deactivating the admin are possible. Changing the date while the phone is off can shorten delays.
- **In-app browsers** (Instagram, Reddit, etc.) aren't read by the word blocker. DNS filtering still covers them.
- **Address-bar reading depends on each browser's layout.** A browser update can break detection until the view IDs in `core/Browsers.kt` are updated. DNS is the backstop.
- **SafeSearch rewriting is best-effort** (some browsers show only the search words). Option B DNS enforces SafeSearch reliably.
- **Bootloader unlocking / flashing** is outside what any app can fully control. On Pixel it needs the *OEM unlocking* switch in Developer options, which are blocked while "Block developer options / USB debugging" is on.
- **Another device** (laptop, tablet) isn't covered.
- **Banking pause** (if you turn it on): for up to 6 minutes (your pause length + 2) the blocker service is off. In Device Owner mode browsers and rule apps stay suspended; in Device Admin mode nothing is blocked during those minutes.

---

## 5. Auditing the code

Every file starts with a header explaining what it does and why. Start here:

| File | What to check |
|---|---|
| `AndroidManifest.xml` | Permissions: no INTERNET. Components and who may call them. |
| `app/build.gradle.kts` | Dependencies: AndroidX + Compose + coroutines only. No network/analytics libraries. |
| `data/Models.kt`, `data/Store.kt` | Exactly what is saved, where, and that backup is off. |
| `security/Crypto.kt` | PIN/recovery hashing (PBKDF2-SHA256, salted, constant-time compare). |
| `security/DevUnlock.kt`, `tools/dev_unlock.py` | Developer unlock codes: per-phone, single-use, ECDSA P-256 signatures; no master codes. |
| `core/Changes.kt` | The tightening/loosening rule, lock, recovery code, attempt throttling. |
| `core/TrustedClock.kt` | Why changing the date doesn't skip delays. |
| `service/BlockerService.kt` | What screen content is read, and that it's never stored. |
| `enforcement/PolicyManager.kt` | Every Device Owner restriction and the loophole each closes. |
| `enforcement/BankPause.kt` | The optional banking pause: requirements, timeline, what stays locked. |
| `core/Verses.kt` | Verse texts; please check them against your own Douay-Rheims copy. |

### Security & privacy measures (summary)
- **No network:** no `INTERNET` permission; no network, analytics, ads or crash-report libraries.
- **No logs:** the app writes nothing to Android's log; screen text is checked in memory and dropped.
- **Stored data:** settings (`files/state.json`), today's per-app minutes, and the trusted-clock anchor, all in private storage.
- **Never leaves the phone:** excluded from cloud backup *and* phone-to-phone transfer (`data_extraction_rules.xml`).
- **Secrets:** PIN and recovery code stored only as salted PBKDF2-SHA256 hashes; wrong attempts are throttled; the recovery code can't be copied to the clipboard.
- **No screenshots:** settings screens use `FLAG_SECURE` (no screenshots, screen recording or Recents preview).
- **Components:** only the launcher screen is exported; the admin receiver and accessibility service can only be called by Android itself.
- **Tamper paths covered:** uninstall, force stop, clear storage, accessibility switch, device-admin deactivation, full-access review, Private DNS, USB debugging; plus optional auto re-enable and Device Owner "fail closed".

### Project layout
```
tools/dev_unlock.py              developer unlock codes (run on your computer)
app/src/main/java/com/alphablocker/
  AlphaApp.kt, Graph.kt          startup + shared objects
  data/        Models, Store, StateJson
  core/        Changes (gate), RuleEngine, ContentMatcher, SafeSearch,
               TrustedClock, UsageTracker, Browsers, AppCatalog, Defaults, Verses
  security/    Crypto, DevUnlock + DevKey (developer unlock codes)
  enforcement/ AdminReceiver, PolicyManager, Enforcer, WatchdogReceiver,
               BankPause (banking pause timers + popup trigger)
  service/     BlockerService (accessibility), LimitCountdown (time-left notification)
  ui/          MainActivity, SetupScreens, StatusScreen, AppsScreen,
               FiltersScreen, VersesScreen, ProtectionScreen, BlockActivity,
               ResumeActivity (banking-pause popup), DnsFilterNote,
               BeforeYouStart (pre-start checklist), DevUnlockDialog,
               Components, Theme
```

Colors: brand blue `#273DCC` (white text) and orange `#FF792E` (dark text only, for contrast). Follows system light/dark mode.

---

## Support

Alpha Blocker is free and always will be. If it helps you and you'd like to support its development, you can leave a tip on [Ko-fi](https://ko-fi.com/catstormrider). The link is also at the bottom of the app's Status tab; it opens in your browser, and the app itself still has no internet access.

## License

Copyright (C) 2026 the Alpha Blocker authors.

Alpha Blocker is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License, version 3 or (at your option) any later version** (`GPL-3.0-or-later`). See [`LICENSE`](LICENSE). Every source file carries an `SPDX-License-Identifier: GPL-3.0-or-later` line.

The bundled Bible verses are from the Douay-Rheims (Challoner) translation, which is in the public domain. The app icon is original artwork under the same license as the code.

## Developer unlock codes (for people who lost their recovery code)

Optional, for whoever publishes a build. If a user loses or miswrote their paper recovery code, the recovery dialog offers **Lost or miswrote the code? Ask the developer**. It shows a 16-character **request code** unique to that phone; the developer turns it into an **unlock code** that works only on that phone, only once, and does what the recovery code does.

There are no fixed master codes: the app is open source, so a fixed code would unlock every copy as soon as it leaked. Instead the app holds only a **public key** (`security/DevKey.kt`), which can check codes but not make them.

Set up once, on your own computer (needs Python 3):
```
pip install cryptography
python tools/dev_unlock.py keygen
```
This writes `alphablocker_dev_private.pem` (**secret**: back it up offline, never share or commit it; `.gitignore` excludes it) and prints the `PUBLIC_KEY` line to paste into `security/DevKey.kt`. Rebuild. With `PUBLIC_KEY` empty, the option is hidden.

When someone sends a request code:
```
python tools/dev_unlock.py sign ABCD-EFGH-IJKL-MNOP
```
(add `--app <your applicationId>` if you changed it). Send back the printed unlock code. Before you do, make sure the request really comes from the phone's owner, and consider asking them to wait a day: the code removes their protection. Losing the private key only means you can't make new codes; anyone who gets it can unlock any phone running your build, so then publish a build with a new key.

## App ID and F-Droid

- The app ID is set in **one place**: `applicationId` in `app/build.gradle.kts`. The ADB commands the app shows (Device Owner, auto re-enable) are built from it automatically. If you change it, use the commands the app shows rather than the `com.alphablocker` examples in this README.
- Store listing for F-Droid: `fastlane/metadata/android/en-US/` and `es-ES/` (title, short and full description, changelog per `versionCode`, 512 px icon). Put phone screenshots in `images/phoneScreenshots/`.
- `dependenciesInfo` is turned off in the build so the APK has no Google dependency-info block (an F-Droid requirement).
