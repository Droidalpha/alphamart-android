# ALPHAMART Android apps (v6)

Two apps, built for you by GitHub for free. No Android Studio needed.

- **ALPHAMART** (customers) – `customer/`, id `com.alphamart.customer`
- **ALPHAMART Delivery** (delivery boys) – `delivery/`, id `com.alphamart.delivery`, shares live location in the background

Each app is a thin shell that opens your server's web app. When you update the website (web zip), phones get the change at once. A new APK is needed only to change the icon, name, permissions or native features.

Not tested on a phone yet. I could not compile Android here. Do the test list at the end on the first APK.

## Before you start
You need your server running on a real https address (see the VPS guide), e.g. `https://shop.yourdomain.com`.

## 1. Put this folder on GitHub
1. Create a free account at github.com, then **New repository**. Name it `alphamart-android`. Choose **Private**.
2. Upload everything in this folder (drag and drop on the repo page works). Make sure the hidden `.github` folder goes up too.
   Do NOT upload `SECRETS.txt`, `alphamart.keystore` or `keystore.b64`. Those are in the separate keys zip.

## 2. Firebase (for push notifications)
1. Go to console.firebase.google.com, **Add project**, name it ALPHAMART. Turn Google Analytics off.
2. Click the Android icon to add an app. Package name `com.alphamart.customer`. Register. Skip the rest.
3. In Project settings, **Add app** again: Android, package name `com.alphamart.delivery`.
4. Project settings > Your apps: download `google-services.json`. It must list both apps. If it lists only one, download it again after adding the second.
5. Make it one line of text: on Windows PowerShell run `[Convert]::ToBase64String([IO.File]::ReadAllBytes("google-services.json"))`. On Linux/Mac run `base64 -w0 google-services.json`. Copy the output.
6. For the server: Project settings > Service accounts > **Generate new private key**. Save that file on the VPS and set `FCM_SERVICE_ACCOUNT=/path/to/file.json` in the server `.env`, then restart.

## 3. Add secrets and the server address in GitHub
Repo > Settings > Secrets and variables > Actions.

**Secrets** tab, New repository secret, one for each:
| Name | Value |
|---|---|
| `KEYSTORE_BASE64` | from `keystore.b64` in the keys zip |
| `KEYSTORE_PASSWORD` | from `SECRETS.txt` |
| `KEY_ALIAS` | `alphamart` |
| `KEY_PASSWORD` | from `SECRETS.txt` |
| `GOOGLE_SERVICES_JSON` | the one-line text from Firebase step 5 |

**Variables** tab, New repository variable: `APP_URL` = your server address with https, no slash at the end.

## 4. Build
Repo > **Actions** > "Build Android apps" > Run workflow. Takes about 6 to 10 minutes.
When it turns green, open the run and download `alphamart-customer-apk` and `alphamart-delivery-apk` from the Artifacts list at the bottom. Unzip to get the `.apk`.

If it turns red, open the failed step and send me the last 30 lines of its log.

## 5. Install
Send the `.apk` to the phone (WhatsApp, Drive, USB). Open it. Android asks to allow installs from this source: allow. Updates later install over the old app as long as you use the same keystore.

## Test list for the first APK
Customer app:
1. Opens the shop, not a blank page. Turn on airplane mode and reopen: you should see "No internet connection".
2. Sign in with OTP, add to cart, place a COD order.
3. Online payment: the UPI apps should open from the payment sheet. If a UPI app does not open, tell me which one.
4. Allow notifications, then change the order status from the admin. A notification should arrive, with the app closed.
5. Rotate, back button, keyboard does not hide the buttons at the bottom.

Delivery app:
1. Sign in, allow location. Choose "Allow all the time" when asked (Android shows it in Settings > Permissions > Location).
2. Go on duty and accept a delivery. Lock the phone and walk around for 5 minutes. The customer's tracking should keep moving. You should see a "Sharing your location" notification while on duty.
3. New order notification arrives when the app is closed.

## Play Store (optional, later)
For the pilot, installing the APK directly is enough. For the Play Store you need an `.aab`, a $25 developer account, a privacy policy page, and a background-location declaration for the delivery app (Google reviews that one). Tell me when you want it and I will add the AAB build.

## Changing things later
- Server address changed: update the `APP_URL` variable and run the workflow again.
- App name or icon: edit then rerun `python3 tools/customize.py customer ALPHAMART customer` (needs Python with Pillow), commit, run the workflow.
- Each run gives the APK a higher version number automatically.

## Keep safe
`alphamart.keystore` and its passwords. Without them you cannot update the apps you have already handed out.

## SMS sender app (own SIM) — `smsgateway/`
Sends login codes from a spare phone's SIM. Built by `.github/workflows/build-smsgateway.yml` (Actions tab → "Build SMS sender app" → Run workflow). Server needs `SMS_GATEWAY_TOKEN` in `.env`. Android 8+.
