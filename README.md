This is a Kotlin Multiplatform project targeting Android, iOS.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Local backend

The app and the tests talk to a local Supabase stack (needs Docker running). The first time, create the gitignored
`supabase/.env` with the sign-in hook secret:

```
echo "SEND_SMS_HOOK_SECRET=v1,whsec_$(openssl rand -base64 32)" > supabase/.env
npx supabase start
```

Schema lives in `supabase/migrations`; `npx supabase db reset` rebuilds the database from them.
Sign in on the emulator with `812 3456 7890` and code `123456` (test numbers are in `supabase/config.toml`).

### Hosted backend

To point the app at the hosted project (real-number testing, release), add to the gitignored `local.properties`:

```
kinfolk.supabaseUrl=https://<project>.supabase.co
kinfolk.publishableKey=sb_publishable_…
kinfolk.emergencyUrl=https://<custom domain>/<path that proxies the emergency function>
```

Leave them out to use the local stack. Going live: `docs/whatsapp-templates.md` → Produksi.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest` (the Care Circle tests need the local backend running)
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…