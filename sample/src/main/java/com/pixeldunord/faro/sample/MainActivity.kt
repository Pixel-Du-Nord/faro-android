package com.pixeldunord.faro.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixeldunord.faro.Faro
import com.pixeldunord.faro.FaroException
import com.pixeldunord.faro.FaroForm
import com.pixeldunord.faro.FaroKind
import com.pixeldunord.faro.FaroRequest
import com.pixeldunord.faro.ui.FaroDarkMode
import com.pixeldunord.faro.ui.FaroHelpActivity
import com.pixeldunord.faro.ui.FaroHelpOptions
import com.pixeldunord.faro.ui.FaroHelpScreen
import com.pixeldunord.faro.ui.FaroScreenshot
import com.pixeldunord.faro.ui.FaroTheme
import com.pixeldunord.faro.ui.enableShakeToReport
import kotlinx.coroutines.launch

/**
 * The sample: a pretend app screen (with details worth covering in a screenshot) and three ways to
 * use Faro: the ready-made activity, the ready-made screen inside your own navigation with your
 * brand's theme, and your own form on the core API.
 *
 * Extras for scripted runs (`adb shell am start -n com.pixeldunord.faro.sample/.MainActivity …`):
 * `--es open activity|embedded|core`, `--es kind problem`, `--es locale es`, `--es dark true|false`,
 * and `--es prefill.<key> value` for any form field (`prefill.email`, `prefill.message`, `prefill.where`…).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        when (darkMode()) {
            FaroDarkMode.DARK -> enableEdgeToEdge(
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            )
            FaroDarkMode.LIGHT -> enableEdgeToEdge(
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            )
            FaroDarkMode.SYSTEM -> enableEdgeToEdge()
        }
        super.onCreate(savedInstanceState)
        Faro.enableShakeToReport(this, helpOptions())
        val open = intent.getStringExtra("open")
        if (savedInstanceState == null && open == "activity") {
            window.decorView.post { FaroHelpActivity.start(this, helpOptions(), activityTheme()) }
        }
        setContent {
            SampleApp(
                initial = if (savedInstanceState == null) open else null,
                options = helpOptions(),
                activityTheme = activityTheme(),
            )
        }
    }

    private fun darkMode(): FaroDarkMode = when (intent.getStringExtra("dark")) {
        "true" -> FaroDarkMode.DARK
        "false" -> FaroDarkMode.LIGHT
        else -> FaroDarkMode.SYSTEM
    }

    private fun activityTheme() = FaroTheme(darkMode = darkMode())

    /** The help screen's options, from the launch extras (prefill, kind, locale). */
    private fun helpOptions(): FaroHelpOptions {
        val extras = intent.extras
        val prefill = extras?.keySet().orEmpty()
            .filter { it.startsWith("prefill.") }
            .associate { it.removePrefix("prefill.") to extras?.getString(it).orEmpty() }
        return FaroHelpOptions(
            locale = intent.getStringExtra("locale"),
            initialKind = FaroKind.fromWire(intent.getStringExtra("kind")),
            prefill = prefill,
            extraDevice = mapOf("sample" to "yes"),
            contactEmail = "contact@pixeldunord.com",
        )
    }
}

private enum class Screen { HOME, EMBEDDED, CORE }

/** A brand theme for the embedded screen: violet, squarer corners. */
private val BrandTheme = FaroTheme(accent = Color(0xFF6D4AFF), cornerRadius = 8.dp)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SampleApp(initial: String?, options: FaroHelpOptions, activityTheme: FaroTheme) {
    var screen by rememberSaveable {
        mutableStateOf(
            when (initial) {
                "embedded" -> Screen.EMBEDDED
                "core" -> Screen.CORE
                else -> Screen.HOME
            },
        )
    }
    val dark = when (activityTheme.darkMode) {
        FaroDarkMode.DARK -> true
        FaroDarkMode.LIGHT -> false
        FaroDarkMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
            BackHandler(enabled = screen != Screen.HOME) { screen = Screen.HOME }
            when (screen) {
                Screen.HOME -> Home(
                    options = options,
                    activityTheme = activityTheme,
                    onEmbedded = { screen = Screen.EMBEDDED },
                    onCore = { screen = Screen.CORE },
                )
                Screen.EMBEDDED -> FaroHelpScreen(
                    onClose = { screen = Screen.HOME },
                    options = options,
                    theme = BrandTheme.copy(darkMode = activityTheme.darkMode),
                    onSent = { Faro.breadcrumb("Sent request #${it.number}") },
                )
                Screen.CORE -> CoreForm(onBack = { screen = Screen.HOME })
            }
        }
    }
}

@Composable
private fun Home(options: FaroHelpOptions, activityTheme: FaroTheme, onEmbedded: () -> Unit, onCore: () -> Unit) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    var settings by remember { mutableStateOf(SampleSettings.load(context)) }
    var applied by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Faro Sample", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "A pretend app. Open help from here: the screenshot of this screen is offered so you can cover " +
                "the private details below before sending.",
            style = MaterialTheme.typography.bodyMedium,
        )

        // Details a person would want to cover in a screenshot.
        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF17213A), contentColor = Color.White) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Ana Pérez", fontWeight = FontWeight.SemiBold)
                Text("ana.perez@example.com", style = MaterialTheme.typography.bodySmall)
                Text("Card •••• 4242", fontFamily = FontFamily.Monospace)
                Text("Balance  $1,284.50", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }

        Button(
            onClick = {
                Faro.breadcrumb("Tapped Help (activity)")
                FaroHelpActivity.start(activity, options, activityTheme)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Help & feedback") }
        FilledTonalButton(
            onClick = {
                Faro.breadcrumb("Tapped Help (embedded)")
                FaroScreenshot.capture(activity) { onEmbedded() }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Help inside my navigation (brand theme)") }
        OutlinedButton(
            onClick = {
                Faro.breadcrumb("Tapped My own form")
                onCore()
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("My own form (core API only)") }
        Text(
            "Tip: shake the phone on this screen to report.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))
        Text("Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(settings.key, { settings = settings.copy(key = it) }, label = { Text("Widget key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(settings.app, { settings = settings.copy(app = it) }, label = { Text("App slug") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(settings.locale, { settings = settings.copy(locale = it) }, label = { Text("Language (empty: device)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Identify a signed-in user", modifier = Modifier.weight(1f))
            Switch(checked = settings.identify, onCheckedChange = { settings = settings.copy(identify = it) })
        }
        if (settings.identify) {
            OutlinedTextField(settings.email, { settings = settings.copy(email = it) }, label = { Text("User email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(settings.name, { settings = settings.copy(name = it) }, label = { Text("User name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = {
            settings.save(context)
            settings.apply(context)
            applied = true
            Faro.breadcrumb("Changed settings")
        }) { Text(if (applied) "Applied" else "Apply") }
        Text(
            "Faro SDK ${Faro.VERSION}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Your own form on the core API: three fields, the proof of work, the request number. */
@Composable
private fun CoreForm(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(FaroKind.QUESTION) }
    var email by remember { mutableStateOf(Faro.user?.email.orEmpty()) }
    var message by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var form by remember { mutableStateOf<FaroForm?>(Faro.client.cachedForm()) }

    // Start the 3-second anti-spam clock and refresh the form as soon as the screen opens.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        Faro.client.prepare()
        form = runCatching { Faro.client.form() }.getOrNull() ?: form
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OutlinedButton(onClick = onBack) { Text("Back") }
        Text(form?.title?.ifEmpty { null } ?: "My own form", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Kinds offered by the form: ${form?.kinds?.joinToString { it.wire } ?: "…"}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (form?.kinds ?: FaroKind.entries).forEach { k ->
                FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.wire) })
            }
        }
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(message, { message = it }, label = { Text("Message") }, minLines = 4, modifier = Modifier.fillMaxWidth())
        Button(
            enabled = !busy,
            onClick = {
                busy = true
                status = "Sending…"
                scope.launch {
                    status = try {
                        val ticket = Faro.client.send(FaroRequest(kind = kind, message = message, email = email, locale = form?.locale))
                        "Sent: request #${ticket.number} (replies to ${ticket.email})"
                    } catch (e: FaroException.InvalidForm) {
                        "Fix the field «${e.field}»"
                    } catch (e: FaroException.RateLimited) {
                        "Too many requests; retry in ${e.retryAfterSeconds ?: "a while"} s"
                    } catch (e: FaroException.Network) {
                        "No connection"
                    } catch (e: FaroException) {
                        "Faro said ${e.code}"
                    }
                    busy = false
                }
            },
        ) { Text("Send with the core API") }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium)
    }
}
