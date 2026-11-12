package io.github.manufrauca.floatcue

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private var canDraw by mutableStateOf(false)
    private var running by mutableStateOf(false)
    private var speed by mutableFloatStateOf(0f) // activity-level: the overlay's buttons change it too

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
        canDraw = Settings.canDrawOverlays(this)
        running = OverlayService.running
        speed = prefs.speed
    }

    @Composable
    private fun Screen() {
        var script by remember { mutableStateOf(prefs.script) }
        var size by remember { mutableIntStateOf(prefs.textSize) }
        var opacity by remember { mutableIntStateOf(prefs.opacity) }
        var height by remember { mutableIntStateOf(prefs.heightPercent) }

        Scaffold { inner ->
            Column(
                Modifier.fillMaxSize().padding(inner).imePadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = script,
                    onValueChange = { script = it; prefs.script = it },
                    label = { Text(stringResource(R.string.script_label)) },
                    placeholder = { Text(stringResource(R.string.script_placeholder)) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                LabeledSlider(
                    stringResource(R.string.speed, speed.roundToInt()), speed, Prefs.SPEED_MIN..Prefs.SPEED_MAX,
                ) { speed = it; prefs.speed = it }
                LabeledSlider(stringResource(R.string.text_size, size), size.toFloat(), 18f..56f) {
                    size = it.roundToInt(); prefs.textSize = size
                }
                LabeledSlider(stringResource(R.string.opacity, opacity), opacity.toFloat(), 30f..100f) {
                    opacity = it.roundToInt(); prefs.opacity = opacity
                }
                LabeledSlider(stringResource(R.string.window_height, height), height.toFloat(), 20f..60f) {
                    height = it.roundToInt(); prefs.heightPercent = height
                }
                if (!canDraw) PermissionCard() else StartStop()
            }
        }
    }

    @Composable
    private fun StartStop() {
        val ctx = LocalContext.current
        Button(
            onClick = { OverlayService.start(ctx); running = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.start)) }
        if (running) {
            Button(
                onClick = { OverlayService.stop(ctx); running = false },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.stop)) }
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
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                    )
                }) { Text(stringResource(R.string.perm_button)) }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    Slider(value = value, onValueChange = onChange, valueRange = range)
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
