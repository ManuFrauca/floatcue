package io.github.manufrauca.floatcue

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlin.math.roundToInt

private const val MAX_SCRIPT_CHARS = 100_000
private const val PREVIEW_CHARS = 400 // the preview only shows the first lines

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private var canDraw by mutableStateOf(false)
    private var running by mutableStateOf(false)
    private var speed by mutableIntStateOf(0) // activity-level: the overlay's buttons change it too

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false) // the script may be private
        prefs = Prefs(this)
        if (Build.VERSION.SDK_INT >= 33 && !prefs.notificationPermissionAsked &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            prefs.notificationPermissionAsked = true
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { FloatCueTheme { Screen() } }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        OverlayService.instance?.hide()
        canDraw = Settings.canDrawOverlays(this)
        running = OverlayService.instance != null
        speed = prefs.speedLpm
    }

    override fun onPause() {
        resumed = false
        OverlayService.instance?.refresh()
        super.onPause()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Screen() {
        var script by remember { mutableStateOf(prefs.script) }
        var size by remember { mutableIntStateOf(prefs.textSize) }
        var opacity by remember { mutableIntStateOf(prefs.opacity) }
        var height by remember { mutableIntStateOf(prefs.heightPercent) }
        val pauseColor = if (isSystemInDarkTheme()) PAUSE_DARK else PAUSE_LIGHT
        val pauseHighlight = remember(pauseColor) { PauseTransformation(pauseColor) }

        val dm = LocalResources.current.displayMetrics
        var lines by remember { mutableIntStateOf(-1) } // -1: not measured yet
        LaunchedEffect(script, size) {
            if (lines >= 0) delay(150) // debounce typing; the first measurement is immediate
            lines = withContext(Dispatchers.Default) { countScriptLines(dm, script, size) }
        }

        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { inner ->
            Column(
                Modifier.fillMaxSize().padding(inner).consumeWindowInsets(inner).imePadding()
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = script,
                    onValueChange = { script = it.take(MAX_SCRIPT_CHARS); prefs.script = script },
                    label = { Text(stringResource(R.string.script_label)) },
                    placeholder = { Text(stringResource(R.string.script_placeholder)) },
                    visualTransformation = pauseHighlight,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 360.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val words = wordCount(script)
                    Text(
                        when {
                            script.isBlank() -> stringResource(R.string.stats_empty)
                            lines < 0 -> pluralStringResource(R.plurals.words, words, words)
                            else -> stringResource(
                                R.string.stats_format, pluralStringResource(R.plurals.words, words, words),
                                durationLabel(durationSeconds(lines, speed)),
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LabeledSlider(
                        stringResource(R.string.speed), stringResource(R.string.speed_value, speed),
                        speed, SPEED_MIN..SPEED_MAX,
                    ) { speed = it; prefs.speedLpm = it }
                }
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StripPreview(script.take(PREVIEW_CHARS), size, opacity)
                        LabeledSlider(
                            stringResource(R.string.text_size), stringResource(R.string.number_value, size),
                            size, SIZE_MIN..SIZE_MAX,
                        ) { size = it; prefs.textSize = it }
                        LabeledSlider(
                            stringResource(R.string.opacity), stringResource(R.string.percent_value, opacity),
                            opacity, OPACITY_MIN..OPACITY_MAX,
                        ) { opacity = it; prefs.opacity = it }
                        LabeledSlider(
                            stringResource(R.string.window_height), stringResource(R.string.percent_value, height),
                            height, HEIGHT_MIN..HEIGHT_MAX,
                        ) { height = it; prefs.heightPercent = it }
                    }
                }
                if (!canDraw) PermissionCard() else Buttons(script.isNotBlank())
            }
        }
    }

    @Composable
    private fun Buttons(hasScript: Boolean) {
        val ctx = LocalContext.current
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!running) {
                Button(
                    onClick = { OverlayService.start(ctx); running = true; moveTaskToBack(true) },
                    enabled = hasScript,
                    modifier = Modifier.fillMaxWidth(),
                ) { IconLabel(R.drawable.ic_play, R.string.start) }
                if (!hasScript) {
                    Text(
                        stringResource(R.string.start_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Button(onClick = { moveTaskToBack(true) }, modifier = Modifier.fillMaxWidth()) {
                    IconLabel(R.drawable.ic_arrow_up, R.string.back_to_teleprompter)
                }
                OutlinedButton(
                    onClick = { OverlayService.stop(ctx); running = false },
                    modifier = Modifier.fillMaxWidth(),
                ) { IconLabel(R.drawable.ic_stop, R.string.stop) }
            }
        }
    }

    @Composable
    private fun PermissionCard() {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.perm_body))
                Text(stringResource(R.string.perm_restricted), style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri()))
                }) { Text(stringResource(R.string.perm_button)) }
            }
        }
    }

    companion object {
        /** True while this screen is in front; the overlay stays hidden meanwhile. */
        @Volatile var resumed = false
            private set
    }
}

// Amber like the overlay's pause words; the darker shade keeps 4.5:1 contrast on a light surface.
private val PAUSE_LIGHT = Color(0xFFB26A00)
private val PAUSE_DARK = Color(0xFFFFC107)

private class PauseTransformation(private val color: Color) : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val styled = buildAnnotatedString {
            append(text.text)
            for (r in pauseRanges(text.text)) {
                addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), r.first, r.last + 1)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

@Composable
private fun IconLabel(icon: Int, label: Int) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(stringResource(label))
}

/** A static frame of the real strip over a stand-in camera scene. */
@Composable
private fun StripPreview(script: String, sizeSp: Int, opacity: Int) {
    val placeholder = stringResource(R.string.preview_placeholder)
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.fillMaxWidth().height(112.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF6B7A8F), Color(0xFFC9B79C)))),
    ) {
        Box(Modifier.matchParentSize().background(Color(INK).copy(alpha = opacity / 100f)))
        AndroidView(
            factory = { PrompterView(it, preview = true) },
            update = { it.setScript(script.ifBlank { placeholder }, sizeSp) },
            modifier = Modifier.matchParentSize(),
        )
        Box(Modifier.matchParentSize().border(1.dp, Color.White.copy(alpha = 0.14f), shape))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabeledSlider(label: String, valueText: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            modifier = Modifier.semantics { contentDescription = label },
            track = { SliderDefaults.Track(it, drawStopIndicator = null, drawTick = { _, _ -> }) },
        )
    }
}

@Composable
private fun FloatCueTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = when {
            Build.VERSION.SDK_INT < 31 -> if (dark) darkColorScheme() else lightColorScheme()
            dark -> dynamicDarkColorScheme(ctx)
            else -> dynamicLightColorScheme(ctx)
        },
        content = content,
    )
}
