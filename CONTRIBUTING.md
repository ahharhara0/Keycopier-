# Contributing to KeyCopier Pro

Thank you for your interest in contributing to **KeyCopier Pro**!  
شكراً لاهتمامك بالمساهمة في تطوير تطبيق **KeyCopier Pro** مفتوح المصدر.

## Core Principles | المبادئ الأساسية

1. **100% Offline / Zero-Network Guarantee:**
   - Never add `INTERNET`, `ACCESS_NETWORK_STATE`, analytics SDKs, or cloud libraries.
   - `app/src/main/AndroidManifest.xml` explicitly removes network permissions via `tools:node="remove"`.
2. **Privacy & Storage Compliance:**
   - Use zero-permission Android Photo Picker (`PickVisualMedia`) and Storage Access Framework (`SAF`) instead of broad storage permissions.
   - All stored keys, photos, and `.kcb` backups must remain encrypted via SQLCipher / Android Keystore AES-256-GCM.
3. **Bilingual Support:**
   - Add both English (`app/src/main/res/values/strings.xml`) and Arabic (`app/src/main/res/values-ar/strings.xml`) string resources for every user-facing label.

## Submitting Changes | خطوات المساهمة

1. Fork the repository on GitHub (`@ahharhara`).
2. Create a feature branch (`git checkout -b feature/my-improvement`).
3. Ensure unit & Robolectric tests pass:
   ```bash
   gradle :app:testDebugUnitTest
   gradle assembleDebug
   ```
4. Open a Pull Request describing your changes and test coverage.

---

**Maintainer:** AHMED AMEEN BAN HARHARA (أحمد أمين بن هرهرة) — GitHub: [@ahharhara](https://github.com/ahharhara)
