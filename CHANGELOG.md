# Changelog

All notable changes to Faro for Android. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [1.0.1] - 2026-10-22

### Fixed

- Calling `Faro.configure` again no longer cancels the client: it updates the same `FaroClient`
  (configuration and form cache), so one already kept by a `ViewModel` keeps working. Before, a
  `send` on the old client failed with a silent cancellation. A proof made for another key or API is
  dropped.
- A `send` waiting for a proof that `cancelPending()` (or a new key) stopped makes another one instead
  of failing with a cancellation.
- `FaroRequest.name = ""` sends no name, even for an identified person (before, an empty name took
  the identified person's). `null` still uses it.
- Publishing: the group is fixed to `com.github.Pixel-Du-Nord.faro-android` (JitPack's `GROUP` is no
  longer used) and only POMs are published, without Gradle Module Metadata, whose component JitPack
  rewrote to `com.github.Pixel-Du-Nord:faro-android`.

### Added

- `FaroForm(locale, fields, kinds, title, intro, submit, success, …)`: a form built by hand, and
  public constructors for `FaroField`, `FaroOption` and `FaroOptionGroup`.
- `FaroForm.fallback(locale)`: a minimal form (the three kinds, message, email, optional name) for
  when Faro cannot be reached and nothing is cached. Its `inLocale` follows the language.
- `FaroForm.request(kind, values, attachments)`: the `FaroRequest` for what was typed, in the form's
  language; with the name turned off in the form, or emptied, the request has no name.
- `FaroLocale.language(tag)`: the base language of a tag (`pt-BR` → `pt`).
- The `sdk` device fact (`faro-android 1.0.1`), as on iOS.

### Changed

- After a request gets in, the next proof of work starts right away (as on iOS), so "send another"
  does not wait.
- `cancelPending()` stops only a proof being solved; a solved one is kept for the next request.
- The request language and `FaroClient.defaultLocale` are cut to the base language (`es-CO` → `es`),
  as on iOS.
- Shake to report opens with "Something's wrong" already chosen (as on iOS), unless other
  `FaroHelpOptions` are passed.
- The README no longer suggests configuring again to change the language: pass it with each call.

## [1.0.0] - 2026-10-22

First public release.

### faro (core)

- `Faro.configure(context, FaroConfig(key, app, locale, apiUrl))` and `Faro.client`.
- `FaroClient.form(locale)` / `cachedForm(locale)`: the request form resolved in a language
  (exact, base language, then the form's main one), with a copy cached on disk for instant and
  offline drawing.
- `FaroClient.prepare()` and `send(FaroRequest)`: Faro's proof of work solved in the background,
  the 3-second minimum honored, the proof reused after validation errors and renewed after a success
  or a rejected proof (one automatic retry).
- Device facts added automatically (app version, build, OS, model, manufacturer, locale, time zone,
  screen) within Faro's limits (12 entries, 120 characters).
- `Faro.identify(FaroUser)` (prefill; signed identity when a server-made signature is given) and
  `Faro.breadcrumb(text)` (last 20 actions, as a device `trail` and in `context.breadcrumbs`).
- `FaroAttachment` from a `Uri`, a `Bitmap` or bytes: up to 3 images, reduced to JPEG of 3 MB at
  most, sent as multipart.
- Typed errors: `FaroException` sealed class by Faro's `errorCode`.
- Local checks before any network call (message length, email, attachments).
- No dependencies besides kotlinx-coroutines (`HttpURLConnection` and `org.json`).

### faro-ui

- `FaroHelpScreen` (Compose, Material 3) and `FaroHelpActivity.start(context, options, theme)`.
- Drawn from the form: kinds, text, multiline, select (chips, with groups), checkbox, number, phone,
  URL and date fields, in the team's order, half-width pairs on wide screens.
- Built-in texts in English, Spanish and Portuguese (`FaroStrings`), replaceable.
- Pictures from the system photo picker (no permission), the screenshot of the previous screen
  (`FaroScreenshot.capture`) and an editor to cover private details before attaching.
- Loading, offline with retry, saved-copy notice, per-field errors, sending and success with the
  request number.
- `FaroTheme`: accent, light/dark/system, corner radius, font or full typography, full palettes,
  or the host's `MaterialTheme`.
- Accessible: TalkBack labels, headings, live regions, 48 dp targets, large-text layouts, reduced
  motion respected.
- Optional `Faro.enableShakeToReport(activity)`.

[1.0.1]: https://github.com/Pixel-Du-Nord/faro-android/releases/tag/1.0.1
[1.0.0]: https://github.com/Pixel-Du-Nord/faro-android/releases/tag/1.0.0
