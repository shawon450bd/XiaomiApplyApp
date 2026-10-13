# XIAOMIxAPPLYxFAHIM

Combined app: Token Viewer (main) + APPLY (BL auth).

## How to use
1. Open Page 1 → login → Get Token 1
2. Open Page 2 → login → Get Token 2
3. Tap **APPLY** (top right) → tokens auto-paste → RUN

## Build APK with GitHub Actions
1. Create a new GitHub repo
2. Upload all files from this ZIP
3. Go to Actions → Build APK → Run workflow
4. Download the APK artifact

## Local build
```bash
./gradlew assembleDebug
```
