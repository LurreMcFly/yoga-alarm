package com.lurremcfly.yogaalarm.ui

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lurremcfly.yogaalarm.model.AlarmConfig
import java.util.Date
import kotlinx.coroutines.delay

@Composable
internal fun AlarmRingingScreen(
    alarm: AlarmConfig,
    permissionDenied: Boolean,
    showStop: Boolean,
    showSnooze: Boolean,
    showEmergencyStop: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
    onEmergencyStop: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(60_000L - now % 60_000L)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(SoftSurface).safeDrawingPadding()) {
        // Keep the already-large clock readable without clipping at larger system font scales.
        val clockSize = (minOf(64f, (maxWidth.value - 48f) / 5f) / LocalDensity.current.fontScale).sp
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight).padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "YOGA ALARM",
                color = Forest,
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    DateFormat.getTimeFormat(context).format(Date(now)),
                    color = Forest,
                    fontSize = clockSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    alarm.name,
                    color = Ink,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text("Time to move", color = MutedInk, style = MaterialTheme.typography.titleMedium)
            }
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (permissionDenied) "Allow camera access to validate your poses."
                    else "Place your phone before starting the camera.",
                    color = MutedInk,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AlarmLime, contentColor = Ink),
                ) {
                    Text(
                        if (permissionDenied) "Allow camera" else "Start",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (showStop || showSnooze) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    ) {
                        if (showStop) {
                            OutlinedButton(
                                onClick = onStop,
                                modifier = (if (showSnooze) Modifier.weight(1f) else Modifier)
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Hairline),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
                            ) {
                                Text("Stop alarm", textAlign = TextAlign.Center)
                            }
                        }
                        if (showSnooze) {
                            Button(
                                onClick = onSnooze,
                                modifier = (if (showStop) Modifier.weight(1f) else Modifier)
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFE3F1C8), contentColor = Ink,
                                ),
                            ) {
                                Text("Snooze ${alarm.snoozeMinutes} min", textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
                if (showEmergencyStop) {
                    TextButton(onClick = onEmergencyStop) {
                        Text("Emergency stop", color = MutedInk)
                    }
                }
                Text(
                    "Camera off · Processed on-device · Never recorded",
                    color = MutedInk,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
