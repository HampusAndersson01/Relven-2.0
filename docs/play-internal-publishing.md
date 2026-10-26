# Google Play Internal Publishing

The manual workflow at **Actions → Publish Relven to Play Internal Testing → Run workflow** supports two modes on `master`:

- `VALIDATE_SIGNING_ONLY` builds and validates signed phone and Wear bundles, makes both verified AAB files downloadable in the workflow's **Artifacts** section, prints both public SHA-256 signing-certificate fingerprints and the expected existing Play upload certificate in the Actions job summary before comparing them, and does not restore the Play service-account secret or run either upload action. If the comparison fails, the summary still shows the observed fingerprints needed to identify the keystore mismatch.
- `PUBLISH_INTERNAL` builds, validates, first saves both signed bundles as downloadable artifacts, and then uploads the phone and Wear bundles to their internal testing tracks. This is the only mode that restores the Play service-account secret or can publish.

Both modes require an even phone version code above `24000374`. **For actual Play upload**, the code must also exceed every version code already used by the app across tracks (and must not already be consumed by a staged upload). Wear uses the next code. The current local phone build is `24000374`, so the default suggested first code is `24000376`; check Play Console before publishing and raise it if any track already contains a higher code.

The action signs with the currently configured Relven upload keystore. Google Play uses the package ID `se.hampusandersson.relven`. The expected current **upload** certificate SHA-256 is `acbe3aa08ff36fea31581156b14b2f69f1735161eae3e34f7153126c3dc62f99` (the Play Console Upload key after the October 2026 reset; not the app signing key). The workflow checks that both built AABs have this fingerprint and refuses to export or publish them if they do not. Do not store private keys or keystore passwords in artifacts.

## Add the five repository secrets

In `HampusAndersson01/Relven-2.0`, open **Settings → Secrets and variables → Actions** to check the existing names. As of 24 October 2026, the repository has `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, and `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64`. Do not create duplicate entries. If a value needs replacing, use the edit control for that exact secret and enter the value from your secure local file or password manager.

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 encoding of the existing Relven upload `.jks` or `.keystore` file |
| `ANDROID_KEYSTORE_PASSWORD` | The keystore password |
| `ANDROID_KEY_ALIAS` | The alias of the upload key inside that keystore |
| `ANDROID_KEY_PASSWORD` | The password for that alias/key |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64` | Base64 encoding of the Google Play service-account JSON key file |

GitHub cannot show or copy secret values from either repository; `gh secret list` and the Actions settings page expose names only. All five names are present in `Relven-2.0`; signing-only validation has run and identified a keystore certificate mismatch. Validation-only mode does not check the Play service-account secret. Do not paste credentials into a chat, commit them, or store the generated `release.properties` file. If a secret value is missing or invalid, replace it from your secure credential files/password manager; do not assume GitHub can recover the old value.

For the binary keystore and JSON key, PowerShell can pipe base64 directly into `gh secret set` without printing the encoded data:

```powershell
$repo = "HampusAndersson01/Relven-2.0"
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\secure\relven-upload.jks")) | gh secret set ANDROID_KEYSTORE_BASE64 --repo $repo
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\secure\google-play-service-account.json")) | gh secret set GOOGLE_PLAY_SERVICE_ACCOUNT_JSON_BASE64 --repo $repo
```

Replace the example paths with your actual local files. Enter the passwords and alias through the GitHub secret form so they are not written into shell history. Do not pass a secret using a literal command-line argument.

## Find or create the Play service account key

If the old JSON key file is available locally, keep using it; there is no need to create another account. If it is missing, open [Google Cloud Console → Service Accounts](https://console.cloud.google.com/iam-admin/serviceaccounts), select or create a Cloud project, then go to **APIs & Services → Library**, enable **Google Play Android Developer API**, and create a service account. Open its **Keys → Add key → Create new key → JSON**. Google only offers the downloaded private key once, so store it securely.

In Google Play Console, open **Users and permissions**, invite the service account's `client_email`, and grant app-level **Release apps to testing tracks** access for Relven. The Google Play API describes this as `CAN_MANAGE_TRACK_APKS`; it permits testing-track releases without granting production-release permission. The API setup steps are in Google's [Getting Started guide](https://developers.google.com/android-publisher/getting_started), and the JSON-key creation steps are in [Google Cloud's service-account key guide](https://docs.cloud.google.com/iam/docs/keys-create-delete).

## Download signed AABs for a manual Play Console release

1. Open **Actions → Publish Relven to Play Internal Testing → Run workflow** on `master`. Select `VALIDATE_SIGNING_ONLY` and an even phone version code (for example `24000376`, only if it is still unused in Play Console). This mode never calls the Play Developer API.
2. Open the finished workflow run's **Summary** and scroll to **Artifacts**. Download both archives named `relven-phone-signed-<code>-<run>-<attempt>` and `relven-wear-signed-<code>-<run>-<attempt>`. The archives are retained for **14 days**.
3. Extract the archives. The phone archive contains `app-latest-release.aab`; the Wear OS archive contains `wear-release.aab`. These files were signed by the GitHub Actions keystore and verified against the currently registered Play upload certificate. **Do not upload the ZIP archives themselves.**
4. In Play Console, open the desired Internal testing track for **Phones, tablets and desktop** and upload `app-latest-release.aab`. Complete any required **Health apps** declaration; the newly uploaded version can cause new permissions to appear.
5. Switch the form factor to **Wear OS** and upload `wear-release.aab` in its Wear-specific Internal testing release. Verify the health declaration and required review/publishing steps before rolling out. Do not create a separate Play app.

**Version codes:** The workflow sets phone to the supplied even code and Wear to phone + 1. Manual uploads can consume a version code; when you later run `PUBLISH_INTERNAL`, use the next unused *even* code greater than every code already present. A failed Play API edit may not have committed a release, but verify in Play Console before reusing a code.

Artifacts contain only the signed AAB files, not keystore files, signing passwords, Google Play service-account JSON, or `release.properties`. The repo is public, so treat exported AABs as distributable build outputs. GitHub Actions artifacts are separate from Play publishing and are still saved when a later Play upload/commit fails.

## Run the workflow

For a credentials check, choose a valid even phone version code greater than `24000374`, leave room for Wear's `+1`, type `VALIDATE_SIGNING_ONLY`, and start the workflow on `master`. Compare the SHA-256 fingerprint in the run summary with the upload certificate in Play Console. This mode builds bundles but performs no Play API upload.

Before publishing, check the highest uploaded phone/Wear version code in Play Console. Choose an even phone code greater than every code already uploaded, leave room for Wear's `+1`, type `PUBLISH_INTERNAL`, and start the workflow on `master`. GitHub Actions secrets are managed as described in [GitHub's Actions secrets guide](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).

The action validates both bundle signatures, package IDs, version codes, watch form-factor metadata, and matching upload certificates before export or upload. On a successful certificate check, the summary also confirms that both bundles match the current Play upload certificate. It uploads Wear to `wear:internal` and phone to `internal`. Google Play processes the two bundles in separate API calls, so if one upload succeeds and the other fails, check the two tracks in Play Console before retrying.
