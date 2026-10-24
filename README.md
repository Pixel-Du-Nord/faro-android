# Faro for Android

Let the people who use your Android app tell you what is wrong, suggest an idea or ask a question,
from inside the app. Each request reaches your team in [Faro](https://faro.pixeldunord.com)
(Pixel du Nord's support desk) with the app version, the device and what the person was doing, and
your team replies by email.

Two libraries:

| Module | What it is | Depends on |
|---|---|---|
| `faro` | The core: the form definition, the anti-spam proof of work, device facts, breadcrumbs, attachments and sending. Use it with your own UI. | kotlinx-coroutines only (`HttpURLConnection`, `org.json`) |
| `faro-ui` | A ready-made, themable "Help & feedback" screen in Jetpack Compose (Material 3), with screenshot redaction. | `faro`, Compose |

Requirements: Android 7.0 (API 24) or newer, Kotlin 2.0+. Your organization needs Faro's
**Requests** module; the key and the app are in the Faro panel.

## Installation

The libraries are published with [JitPack](https://jitpack.io/#Pixel-Du-Nord/faro-android).

`settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

`app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.Pixel-Du-Nord.faro-android:faro-ui:1.0.2") // the screen (includes the core)
    // or only the core, for your own UI:
    // implementation("com.github.Pixel-Du-Nord.faro-android:faro:1.0.2")
}
```

The core declares the `INTERNET` permission; nothing else is needed (the photo picker requires no
permission).

## Configuration

Once, in `Application.onCreate` (or before the first use):

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Faro.configure(
            this,
            FaroConfig(
                key = "wk_…",          // Widget → Installation in the Faro panel (public by design)
                app = "my-app",        // Organization → Applications: groups requests, picks the app's own form
                locale = null,         // default language; null = the device's
            ),
        )
    }
}
```

| `FaroConfig` | |
|---|---|
| `key` | The public widget key `wk_…`. It can only file requests, never read anything. |
| `app` | The application slug. Optional; with it, requests are grouped by app and validated against the app's own form if it has one. |
| `locale` | The default language (`es`, `en`, `pt-BR`…, cut to its base: `pt`), for calls that do not name one. Picks the form texts and the language of the emails. `null` uses the device's. |
| `apiUrl` | Faro's API. Defaults to `https://api-faro.pixeldunord.com`. |

If your app has its own language setting, do not configure again when it changes: pass the
language with each call instead (`client.form(locale)`, `FaroRequest.locale` or `form.request(…)`,
`FaroHelpOptions.locale`). `Faro.configure` is meant to be called once; if it is called again (say,
another key from a debug menu), it updates the same client, so a `FaroClient` already kept by a
`ViewModel` keeps working with the new settings.

## The ready-made screen

As its own activity, in one line:

```kotlin
FaroHelpActivity.start(this) // from an Activity: its screenshot is offered on the help screen
```

Or inside your navigation, as a composable:

```kotlin
composable("help") {
    FaroHelpScreen(
        onClose = { navController.popBackStack() },
        options = FaroHelpOptions(initialKind = FaroKind.QUESTION),
        theme = FaroTheme(accent = MyBrand.primary),
        onSent = { ticket -> analytics.log("support_request", ticket.number) },
    )
}

// Before navigating, capture the screen the person is on:
FaroScreenshot.capture(activity) { navController.navigate("help") }
```

The screen is drawn from your Faro request form (**Widget → Request form**, or the app's own form):
the kinds you offer, every custom field in your order (lists as chips, with their groups; text,
multiline, checkbox, number, phone, URL and date), your texts in the person's language. When your
team edits the form, the app shows the change on the next open, no release needed. It handles
loading, offline (the saved copy, or a retry screen), per-field errors, sending, and success with the
request number.

`FaroHelpOptions`:

| Option | Default | |
|---|---|---|
| `locale` | `FaroConfig.locale` | Language of the form and the screen. |
| `initialKind` | `null` | A kind chosen in advance. |
| `prefill` | empty | Starting values by field key (`email`, `name`, `message`, custom keys). |
| `extraDevice` | empty | Your own facts for the request (see Privacy). |
| `showDeviceInfo` | `true` | Show people what travels with the request. |
| `allowAttachments` | `true` | Up to three pictures. |
| `offerScreenshot` | `true` | Offer the captured screenshot (never attached without the person choosing to). |
| `rememberContact` | `true` | Remember the email and name typed last time, on this device. |
| `contactEmail` | `null` | An address shown at the bottom as a last resort. |
| `strings` | by language | Your own texts (`FaroStrings`). English, Spanish and Portuguese are built in. |

The names of the kinds ("Something's wrong", "An idea", "A question"), hints, notices and fallbacks
are the SDK's (`FaroStrings`); everything your team wrote in Faro wins. To change a text:

```kotlin
FaroHelpOptions(strings = FaroStrings.English.copy(kindProblem = "Report a bug"))
```

### Theme

By default the screen wears Faro's colors (amber light over sand by day, over deep sea by night) and
follows the system's light or dark mode.

```kotlin
FaroTheme(
    accent = Color(0xFF6D4AFF),        // your brand color; text on it is black or white automatically
    darkMode = FaroDarkMode.SYSTEM,    // LIGHT, DARK
    cornerRadius = 8.dp,               // 0.dp for square
    fontFamily = myFontFamily,         // keeps Material's sizes
    // typography = myTypography,      // or a full Material 3 typography
    // lightColors = …, darkColors = …,// or full palettes
    // useHostTheme = true,            // or inherit your MaterialTheme (composable only)
)
```

### Screenshot and covering details

`FaroScreenshot.capture(activity)` takes a picture of the current window right before the help screen
opens (`FaroHelpActivity.start` and shake-to-report do it for you). On the help screen it is offered,
not attached: the person can attach it, **cover details first** (draw over names, numbers or faces
with a thick marker or boxes, or point something out with a thin pen) or skip it. What is covered is
burnt into the image on the device; the original never leaves it. Windows marked `FLAG_SECURE` are
never captured. `FaroScreenshot.set(bitmap)` offers your own picture instead.

### Shake to report

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Faro.enableShakeToReport(this) // listens only while this activity is resumed
}
```

The screen opens with the screenshot and "Something's wrong" already chosen; pass your own
`FaroHelpOptions` to change that.

## Your own UI with the core

```kotlin
// appLanguage: your app's own language setting ("es", "pt-BR"…), passed with each call.
class FeedbackViewModel(private val appLanguage: String) : ViewModel() {
    private val faro = Faro.client // safe to keep: Faro.configure never replaces it

    init {
        faro.prepare() // starts the anti-spam proof now: by the time the person sends, it is ready
    }

    // Draw instantly from the disk copy (or the SDK's minimal form), then refresh.
    val first: FaroForm = faro.cachedForm(appLanguage) ?: FaroForm.fallback(appLanguage)
    suspend fun load(): FaroForm = faro.form(appLanguage)

    suspend fun send(form: FaroForm, kind: FaroKind, values: Map<String, String>, pictures: List<FaroAttachment>): FaroTicket {
        val issues = form.validate(kind, values) // same checks as Faro, no round trip
        require(issues.isEmpty()) { "show $issues next to the fields" }
        return faro.send(form.request(kind, values, pictures)) // in the form's language
    }

    override fun onCleared() {
        faro.cancelPending() // the screen is gone: stop a proof still being solved
    }
}
```

- `form(locale)` returns the form with every text in one language: `title`, `intro`, `submit`,
  `success`, `kinds` and `fields` in order (the built-in `name` unless it is off, `email`, `message`,
  and your custom fields with `type`, `label`, `placeholder`, `help`, `required`, `options`,
  `groups`, `halfWidth`). Offline, it returns the disk copy with `isCached = true`.
- `form.inLocale(locale)` gives the same form in another language without the network (for an app
  that switches language on the spot).
- `form.request(kind, values, attachments)` builds the `FaroRequest`: message, email and name to their
  own properties, the custom answers through `customValues`, the form's language for the emails.
- `send` waits whatever is left of Faro's 3-second minimum, retries once with a new proof if Faro
  rejects it, keeps the proof after a validation error so the corrected request goes out at once,
  and starts the next proof right after a success. `cancelPending()` stops a proof still being
  solved (keeping a solved one); a `send` waiting for it simply makes another.
- Pictures: `FaroAttachment.fromUri(context, uri)` (photo picker), `fromBitmap(bitmap)` or
  `fromBytes(bytes)`. They are scaled to 2048 px and re-encoded as JPEG under 3 MB.

### A form of your own, or none at all

When Faro cannot be reached and nothing is cached, `FaroForm.fallback(locale)` gives a minimal form
(the three kinds, the message, the email and an optional name, without texts: use your own labels).
You can also build one by hand; `validate`, `customValues` and `request` work on it the same way:

```kotlin
val form = FaroForm(
    locale = "en",
    fields = listOf(
        FaroField(FaroForm.MESSAGE, FaroFieldType.TEXTAREA, label = "What happened?", required = true),
        FaroField(FaroForm.EMAIL, FaroFieldType.EMAIL, label = "Your email", required = true),
        FaroField("where", FaroFieldType.SELECT, label = "Where?", options = listOf(FaroOption("online", "Online game"))),
    ),
)
```

### Sending without a name

`FaroRequest.name = null` uses the identified person's name; `""` sends no name at all, even for an
identified person. `form.request` follows what the person saw: with the name turned off in the form,
or emptied by the person, the request has no name.

## Identifying the person

```kotlin
Faro.identify(FaroUser(id = user.id, email = user.email, name = user.name)) // every part is optional
// on sign-out
Faro.identify(null)
```

The email and name prefill the form and fill a request that leaves them out. The `id` alone travels
with the device facts (`userId`) so your team can find the person. With a `signature` (HMAC-SHA256 of
the id with your organization's identity secret, **made by your server**, see Faro's
[signed identity](https://faro.pixeldunord.com/en/docs/identity) guide) Faro trusts the person: the
email counts as confirmed, they get an acknowledgement and the request is linked to their contact.
Never put the secret in the app. `identify` is kept in memory: call it on every launch after sign-in.

## Breadcrumbs

```kotlin
Faro.breadcrumb("Opened checkout")
Faro.breadcrumb("Tapped Pay")
```

The last 20 actions (60 characters each) travel with the next request: the newest ones that fit in
120 characters as the device `trail` (shown in the panel today) and all of them, with how many seconds
before sending, in `context.breadcrumbs`. Use screen and button names, never what people typed.
`Faro.clearBreadcrumbs()` forgets them.

## Errors

Every failure is a `FaroException` (a sealed class); tell them apart by type or by `code` (Faro's
`errorCode`), never by the message.

| Type | `code` | When |
|---|---|---|
| `Network` | `NETWORK` | Faro was not reached (offline, DNS, timeout). Retry. |
| `InvalidForm(field)` | `INVALID_FORM` | A field is missing or invalid: `kind`, `name` or a custom key. |
| `InvalidEmail` | `INVALID_EMAIL_FORMAT` | The email is missing or not an address. |
| `InvalidMessage` | `INVALID_TEXT` | The message is not 3 to 4,000 characters. |
| `RateLimited(retryAfterSeconds)` | `RATE_LIMITED` | Faro takes 6 requests per hour per IP and 3 per hour per email. |
| `SpamCheck(reason)` | `SPAM_CHECK_FAILED` | The proof was rejected twice (`send` retries once by itself). |
| `AttachmentTooLarge` | `FILE_TOO_LARGE` | More than 3 images, or one over 3 MB. |
| `AttachmentType` | `FILE_TYPE_NOT_ALLOWED` | Not PNG, JPEG, GIF or WebP. |
| `ModuleRequired` | `MODULE_REQUIRED` | The organization does not have Requests. |
| `InvalidKey` | `WIDGET_NOT_FOUND` | The key does not exist or its organization is not active. |
| `SubscriptionInactive` | `SUBSCRIPTION_INACTIVE` | The organization's subscription is not active. |
| `Server(code, status, message)` | anything else | Server errors and unexpected answers. |

`send` checks the message, the email and the attachments before any network call and throws the same
errors, so a typo does not count against the hourly limit.

## Privacy: what is sent and what is not

Sent with a request, and only when the person sends it:

- What they typed in the form and the pictures they chose to attach (covered parts burnt in).
- The kind, the app slug and `platform: android`.
- Device facts: app version and build, `Android` and its version, model, manufacturer, device
  language, time zone, screen size and the SDK version (`sdk`). The help screen shows this list
  before sending.
- The breadcrumbs your app recorded, the identified user (email, name, id, signature) and any
  `extraDevice` entries you add.
- The language for the reply emails (its base: `pt`, not `pt-BR`).

Never sent by the SDK: advertising or device identifiers, location, contacts, files, other apps,
what was typed elsewhere in your app, or anything when the screen is merely opened (opening only
fetches the form and an anti-spam puzzle, which carry the public key and app slug). Keep
`extraDevice` and breadcrumbs free of secrets, tokens, account numbers and personal data.

On the device the SDK stores the last form definition (in `noBackupFilesDir/faro`) and, in
`faro-ui` with `rememberContact`, the last email and name typed (SharedPreferences `faro_ui`).

## API

| Call | |
|---|---|
| `Faro.configure(context, FaroConfig)` | Sets up the SDK; returns the client. Again: updates the same client. |
| `Faro.client` | The `FaroClient` (throws if not configured). |
| `Faro.identify(FaroUser?)` / `Faro.user` | Who is using the app. |
| `Faro.breadcrumb(text)` / `clearBreadcrumbs()` / `breadcrumbs` | Recent actions. |
| `Faro.VERSION` | The SDK version. |
| `FaroClient.form(locale)` | The form from Faro (disk copy when offline). `suspend` |
| `FaroClient.cachedForm(locale)` | The disk copy, or `null`. |
| `FaroClient.prepare()` / `cancelPending()` | Starts the proof of work / stops one being solved. |
| `FaroClient.defaultLocale` | The language used when a call names none. |
| `FaroClient.send(FaroRequest)` | Sends; returns `FaroTicket(number, email, confirmed)`. `suspend` |
| `FaroClient.deviceFacts(extra)` | What travels as device facts right now. |
| `FaroForm.validate(kind, values)` | Faro's checks, locally: issues by field key. |
| `FaroForm.request(kind, values, attachments)` | The `FaroRequest` for what was typed. |
| `FaroForm.customValues(values)` | The answers ready for `FaroRequest.fields`. |
| `FaroForm.inLocale(locale)` | The same form in another language. |
| `FaroForm(locale, fields, …)` / `FaroForm.fallback(locale)` | A form built by hand / a minimal one for offline. |
| `FaroLocale.language(tag)` | The base language of a tag (`pt-BR` → `pt`). |
| `FaroAttachment.fromUri / fromBitmap / fromBytes` | Pictures ready to send. |
| `FaroHelpScreen(onClose, modifier, options, theme, onSent)` | The ready-made screen (Compose). |
| `FaroHelpActivity.start(context, options, theme)` / `intent(…)` | The screen as an activity; result extra `EXTRA_TICKET_NUMBER`. |
| `FaroScreenshot.capture(activity, then)` / `captureNow` / `set` / `clear` | The screenshot offered on the next screen. |
| `Faro.enableShakeToReport(activity, options, theme)` | Shake to open the screen; returns `FaroShake`. |
| `FaroTheme`, `FaroDarkMode`, `FaroHelpOptions`, `FaroStrings` | Look, behavior and texts. |

## Sample app

`sample/` uses both libraries against production with Pixel du Nord's public key and the `parques`
app (change them in `gradle.properties`, `faro.sample.key` and `faro.sample.app`, or in the app's
Settings). It shows the activity, the screen inside your own navigation with a brand theme, and a
form of your own on the core.

```bash
./gradlew :sample:installDebug
```

## Building

```bash
./gradlew build                     # libraries, sample, unit tests and lint
./gradlew :faro:testDebugUnitTest   # core unit tests
./gradlew publishToMavenLocal       # faro and faro-ui into ~/.m2
```

## License

MIT, © Pixel du Nord LLC. See [LICENSE](LICENSE). Icons from [Tabler Icons](https://tabler.io/icons) (MIT).
