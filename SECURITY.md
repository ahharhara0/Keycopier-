# Security Policy | سياسة الأمان

## Offline Isolation Model

KeyCopier Pro is engineered for **100% offline operation**:
- **Zero Network Permissions:** `INTERNET` and `ACCESS_NETWORK_STATE` are explicitly stripped at manifest merge time (`tools:node="remove"`).
- **Encrypted Local Vault:** Key profiles are stored in a **SQLCipher**-encrypted Room database using a key protected in EncryptedSharedPreferences / Android Keystore.
- **Media & Backup Encryption:** Key photographs and `.kcb` backup envelopes are encrypted using **AES-256-GCM** (with PBKDF2-HMAC-SHA256 key derivation for portable `.kcb` passwords and PIN verification).
- **Minimal Permissions:** Only `CAMERA` (optional, requested at runtime) is used for optical card calibration; gallery selection uses the zero-permission Android Photo Picker.

## Reporting a Vulnerability

If you discover a security or cryptography issue in this repository:
1. **Do not** disclose sensitive exploit details in public issues before review.
2. Contact the maintainer **AHMED AMEEN BAN HARHARA** via GitHub: [@ahharhara](https://github.com/ahharhara).
