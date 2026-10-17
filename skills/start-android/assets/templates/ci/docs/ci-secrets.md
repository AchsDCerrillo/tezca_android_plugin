# CI secrets

`ci.yml` runs on every PR and push to `main`/`dev` and needs nothing beyond a green build.
`dev.yml` (push to `dev`) and `release.yml` (published GitHub Release) need the secrets below.
Add them under **GitHub repo → Settings → Secrets and variables → Actions**.

| Name | Kind | Used by | How to get it |
|---|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | secret | dev, release | `keytool -genkeypair -v -keystore release.keystore -alias upload -keyalg RSA -keysize 4096 -validity 10000`, then `base64 -i release.keystore \| pbcopy`. Back the keystore up: losing it means a new Play listing unless you use Play App Signing. |
| `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | secret | dev, release | the values used with keytool |
| `FIREBASE_ANDROID_APP_ID` | secret | dev | Firebase console → Project settings → the Android app registered for `__APP_ID__.dev` |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | secret | dev | Google Cloud → IAM → service account with *Firebase App Distribution Admin* → JSON key |
| `FIREBASE_TESTER_GROUPS` | variable | dev | comma-separated group aliases from App Distribution → Testers & Groups |
| `PLAY_SERVICE_ACCOUNT_JSON` | secret | release | Play Console → Setup → API access → service account with *Release manager* on the app → JSON key |
#@if sdk
| `GPR_API_KEY` | secret | all | GitHub token with `read:packages` on the SDK's package (the built-in `GITHUB_TOKEN` can't read packages of another repository unless that package grants this repo access) |
#@endif

**First Play upload is manual.** Play's API can't create an app: build `./gradlew :app:bundleProdRelease` locally once, upload it to the internal track in Play Console, then let `release.yml` take over. The version code is the workflow run number; if your manual upload used a higher one, pass `-PversionCode` once.
