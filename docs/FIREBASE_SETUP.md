# Firebase account backend setup

## What this milestone implements

- Google sign-in through Android Credential Manager and Firebase Authentication.
- One profile document per Firebase Auth UID at `users/{uid}`.
- Unique, normalized usernames reserved transactionally in `usernames/{username}`.
- Owner-only access to a user's profile and future private subcollections.
- Existing Room storage is preserved. This milestone does not upload or delete existing chats or memories; their sync is a later migration slice.
- Firestore Android offline persistence is enabled by the SDK by default. This first slice syncs account/profile identity only.

## 1. Create the Firebase project

1. Create a Firebase project named **Theo**. Analytics can stay disabled.
2. Keep the project on the **Spark (no-cost) plan**. Do not upgrade or add billing for this milestone.
3. Add an Android app with this exact application ID: `com.aistudio.theoai.qxntp`.
   - Do not use `com.example`; that is the Kotlin source namespace, not the installed app ID.
4. Download `google-services.json`.
5. In **Build → Firestore Database**, create a **Native mode** database. Choose **Mumbai (`asia-south1`)** for this project's initial location. The location cannot be changed later.
6. In **Build → Authentication → Sign-in method**, enable **Google**.

## 2. Publish the Firestore rules

Copy the complete contents of the repository's `firestore.rules` into **Firestore Database → Rules**, then publish.

The rules:
- Let a signed-in user read only their own profile.
- Deny listing all user profiles.
- Limit profile creation/update to the fields used by the app.
- Restrict each user's subcollections to that user.
- Let signed-in users check a username document by exact ID but never list the username index.
- Allow only the authenticated owner to reserve, update, or release their username.

Do not use test mode or an allow-all rule.

## 3. Add the Android config to GitHub Actions

The Firebase Android config contains project/app identifiers and a Firebase API key. It is client configuration, not a service-account private key. Because this repository is public, the config file is ignored by Git and injected during CI.

1. Open the repository **Kentaakakashi/Theo-AI-mobile**.
2. Go to **Settings → Secrets and variables → Actions → New repository secret**.
3. Name the secret `GOOGLE_SERVICES_JSON`.
4. Paste the entire contents of `google-services.json` as plain text (do not base64 encode it).
5. The Android workflow writes it to `app/google-services.json` for that build only.

For a local build, place the downloaded file at `app/google-services.json` on your own device. Do not commit it.

## 4. Make Google sign-in use a stable debug signing key

Google sign-in checks the Android package and signing-certificate fingerprints. Earlier CI builds used a newly generated debug key each time. The workflow now supports a persistent key through the GitHub Actions secret `THEO_DEBUG_KEYSTORE_BASE64`; without it, CI still builds but Google sign-in fingerprints will change each run.

In Termux, install Java tools if needed, then run:

```bash
pkg install openjdk-17
keytool -genkeypair -v \
  -keystore debug.keystore \
  -storepass android \
  -alias androiddebugkey \
  -keypass android \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Theo Debug,O=Theo,C=IN"

keytool -list -v -keystore debug.keystore \
  -alias androiddebugkey -storepass android

base64 -w 0 debug.keystore
```

1. Copy the SHA-1 fingerprint (and SHA-256 if shown/requested) from the listing into **Firebase Console → Project settings → Your apps → Theo Android app → SHA certificate fingerprints**.
2. Add the one-line Base64 output as GitHub Actions secret `THEO_DEBUG_KEYSTORE_BASE64`.
3. After Google sign-in is enabled and the SHA fingerprint is registered, download the refreshed `google-services.json` and update `GOOGLE_SERVICES_JSON`.

Keep the keystore file and Base64 value private. This is a debug key, not the production release key.

**Install warning:** the older CI workflow generated random signing keys, so a currently installed APK may have a different signature. The next APK may require one uninstall/reinstall. Uninstalling can delete Theo's local Room data; export anything important before doing that. Do not uninstall merely to test the backend until a stable-key APK is ready.

## 5. Credentials that must never go into the APK

- Do not create/download a Firebase service-account JSON key for Android.
- Never put a Firebase Admin SDK private key or other privileged server credential in the repository, GitHub Actions client build variables, or APK.
- Firebase client API keys and OAuth client IDs identify the project/app; they do not authorize database access. Authentication and Firestore Security Rules protect account data.
- Do not place Gemini/Groq model-provider keys in `google-services.json`. Keys embedded in a client app can be extracted; a later secure server-side proxy is required for genuinely private provider credentials.

## Free-plan boundaries

Firestore's current no-cost quota includes 1 GiB stored data, 50,000 document reads/day, 20,000 writes/day, and 20,000 deletes/day for one database. Quotas and plan terms can change. This milestone does not use Cloud Functions, paid extensions, or billing-required backups. Keep the Firebase project on Spark and monitor usage.

## References

- Firebase Android setup: https://firebase.google.com/docs/android/setup
- Firebase API keys: https://firebase.google.com/docs/projects/api-keys
- Google sign-in with Firebase Authentication: https://firebase.google.com/docs/auth/android/google-signin
- Firestore offline access: https://firebase.google.com/docs/firestore/manage-data/enable-offline
- Firestore pricing: https://cloud.google.com/firestore/pricing
