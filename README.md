# Vendas

Android app for a small shop to track customers, their purchases and the installments they still owe. A WhatsApp bot that registers purchases from a message is planned as phase 2.

The interface is in Portuguese (pt-BR).

## What the app does

| Screen | What it does |
|---|---|
| Clientes (customers) | List with what each customer owes; overdue amounts in red. Search by name or number, sort by name or amount owed, pull down to refresh. |
| Cliente (customer page) | WhatsApp number, total owed, overdue amount, next due date and purchase history. "Cobrar no WhatsApp" opens WhatsApp with a reminder of the open installments, ready for you to review and send. |
| Novo / Editar cliente | Name and WhatsApp number are required; the number must be unique. Removing a customer hides them but keeps their purchases. |
| Nova compra (new purchase) | Products and quantities, purchase date, cash or installments (2 to 24), first due date and a preview of the installments. |
| Compra (purchase) | Items, total and installments. Mark an installment as paid, undo a payment, cancel the purchase. |
| Produtos (products) | Name and price. Changing a price does not change past purchases. Products are deactivated, never deleted. |
| Resumo (summary) | Total to receive, total overdue, overdue installments and those due in the next 7 days. |

Business rules (installment rounding, due dates, balances) are described in [the specification](https://claude.ai/artifact/EjMCuTX9UYZSmvSHbUgr2b).

## Technology

| Part | Choice |
|---|---|
| Language and UI | Kotlin, Jetpack Compose, Material 3 |
| Navigation | Navigation Compose with type-safe (`@Serializable`) routes |
| Dependency injection | Hilt |
| Offline copy | Room |
| Backend | Supabase (PostgreSQL, Auth, row level security) via supabase-kt |
| Tests | JUnit for business rules, Compose UI tests with Hilt for end-to-end flows |

## Project layout

```
android/                      Android app
  app/src/main/java/br/vendas/app/
    domain/                   Money, phone numbers, installment plan, models
    data/                     SalesRepository, SalesSnapshot (shared rules), OfflineFirstRepository
      local/                  Room database (offline copy)
      supabase/               Supabase remote data source and auth
      demo/                   In-memory sample data (demo mode)
    di/                       Hilt module: demo or Supabase
    ui/                       Screens, one package per area; navigation/ holds the routes
  app/src/test/               Unit tests for the business rules
  app/src/androidTest/        UI tests (always run on sample data)
  app/schemas/                Room schema history
supabase/migrations/          Database schema, balance view, RLS and functions
```

## How data flows

- **Reads** come from the copy saved on the phone (Room). It is refreshed from Supabase when older than 15 seconds, after every change, and on pull to refresh.
- **Without internet** the saved copy is shown and a red banner says when it was last updated. Changes need internet and fail with a clear message.
- **Writes** go to Supabase first; the local copy is then refreshed. Purchases are created by the `public.create_purchase` database function, which saves the purchase, its items and its installments in one transaction and is the source of truth for installment amounts and due dates (`InstallmentPlan.kt` mirrors it for the on-screen preview and demo mode).
- **Signing out** deletes the local copy.

## Demo mode

Without Supabase settings the app runs in **demo mode**: sample customers and purchases kept in memory, no login, everything lost when the app closes. A banner at the top says so.

## Connecting to Supabase

1. Create a project at [supabase.com](https://supabase.com) (region South America, São Paulo).
2. Open **SQL Editor** and run each file in `supabase/migrations/` in name order, one at a time: first `20261125000000_initial_schema.sql`, then `20261126000000_flexible_payments.sql`.
3. In **Authentication → Users**, add the user (e-mail and password) who will use the app, with **Auto Confirm User** checked. There is no sign-up screen in the app.
4. In **Authentication → Sign In / Providers**, turn off **Allow new users to sign up**. Every signed-in user can read and write all data, so nobody else may be able to create an account with the public key.
5. In **Project Settings → API Keys**, copy the project URL and the `anon` public key (tab **Legacy API keys**) into `android/local.properties`:

   ```properties
   supabase.url=https://<project>.supabase.co
   supabase.anonKey=<anon public key>
   ```

6. Build again. The app now asks for the e-mail and password.

`local.properties` is not committed. The anon key is safe in the app because every table is protected by row level security: only signed-in users can read or write. The bot tables (`authorized_senders`, `whatsapp_messages`, `bot_conversations`) have RLS on and no policies, so only the service role used by the future webhook can reach them.

## Building

Requirements: JDK 17+ (Android Studio's bundled JBR works) and the Android SDK with platform 37.2.

```powershell
cd android
.\gradlew.bat testDebugUnitTest            # business-rule tests
.\gradlew.bat assembleDebug                # app/build/outputs/apk/debug/app-debug.apk
.\gradlew.bat connectedDebugAndroidTest    # UI tests, with a phone or emulator connected
.\gradlew.bat assembleRelease              # shrunk with R8: app/build/outputs/apk/release/app-release.apk
```

Install on a phone with USB debugging on:

```powershell
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```

## Release key

Until a release key is configured, the release build is signed with the debug key (the build prints a warning). That is fine for your own phone, but create a release key before sharing the app, and keep it and its passwords safe: updates must be signed with the same key.

1. Create the key (you choose and type the passwords):

   ```powershell
   & "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore C:\keys\vendas-release.jks -alias vendas -keyalg RSA -keysize 2048 -validity 10000
   ```

2. Add it to `android/local.properties`:

   ```properties
   release.storeFile=C:/keys/vendas-release.jks
   release.storePassword=<store password>
   release.keyAlias=vendas
   release.keyPassword=<key password>
   ```
