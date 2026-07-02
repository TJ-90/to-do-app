# Google Drive Sync Setup

1. Create Google Cloud project "Priority Todo".
2. Enable **Google Drive API**.
3. OAuth consent screen: External, add app name + your email, add scope `.../auth/drive.appdata`. Add friends' emails as **Test users** (or publish app). Publishing avoids the 100-test-user cap.
4. Create **OAuth Client ID → Web application**:
   - Authorized JavaScript origins: `https://<your-gh-username>.github.io`
   - Copy the **Web client ID** → paste into `web/config.js` `CLIENT_ID`.
5. Create **OAuth Client ID → Android**:
   - Package name: `com.tj90.prioritytodo`
   - SHA-1: from your signing key. Debug: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`. Release: from your release keystore.
6. Both clients live in the SAME project → they share the SAME per-user `appDataFolder` file. That is what makes cross-platform sync work.

Enable Pages → Source = GitHub Actions in repo settings.
After editing `config.js`, commit and push; GitHub Pages redeploys automatically.
