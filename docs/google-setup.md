# Turning on Google Sheets sync

The app code is done. Google only lets it talk to Sheets once a Cloud project knows about the app. This takes about ten minutes and costs nothing.

## 1. Create the project

1. Go to https://console.cloud.google.com/ and sign in with the Google account you want the spreadsheet in.
2. Top bar, project picker, **New project**. Name it `Budgeter`. Create, then select it.

## 2. Turn on the Sheets API

**APIs & Services → Library**, search **Google Sheets API**, enable it.

## 3. Consent screen

**APIs & Services → OAuth consent screen** (may be called **Google Auth Platform → Branding**).

1. User type: **External**.
2. App name `Budgeter`, your email for support and developer contact. Save.
3. **Data access / Scopes**: add `https://www.googleapis.com/auth/drive.file`. It is listed as non-sensitive, so no verification is needed.
4. **Audience**: click **Publish app** so it is "In production". With only non-sensitive scopes there is no review and sign-ins don't expire weekly. (Leaving it in Testing also works if you add your email as a test user.)

## 4. Android OAuth client

**APIs & Services → Credentials → Create credentials → OAuth client ID**

| Field | Value |
|---|---|
| Application type | Android |
| Package name | `com.nyxulrix.budgeter` |
| SHA-1 certificate fingerprint | `13:AD:2E:3F:3A:3B:2B:37:79:6F:AC:8B:C9:34:0F:C1:E4:6E:29:8D` |

That fingerprint is the Budgeter release key. It signs every build: plugged-in installs from this PC and GitHub releases alike.

No client ID needs to be pasted into the app. Google matches the package name and signature.

## 5. Connect in the app

Profile → Google Sheets sync → **Connect Google**. Pick your account and allow access. A spreadsheet called **Budgeter** appears in your Drive with one tab per budget month.

## If it fails

| Message | Fix |
|---|---|
| "Google sign-in isn't available" / `DEVELOPER_ERROR` / code 10 | Package name or SHA-1 in step 4 doesn't match. Re-check both. |
| "Sync paused: access revoked" | Profile → Google Sheets sync → Sign in again. |
| Sheets API "has not been used in project" | Step 2 was skipped or the wrong project is selected. |
