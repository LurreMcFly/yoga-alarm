package com.yogaalarm.prototype

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yogaalarm.prototype.data.AlarmStore
import com.yogaalarm.prototype.model.AlarmConfig
import com.yogaalarm.prototype.ui.AlarmEditorScreen
import com.yogaalarm.prototype.ui.AlarmHomeScreen
import com.yogaalarm.prototype.ui.CameraSpikeScreen
import com.yogaalarm.prototype.ui.PoseDebugViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            YogaAlarmTheme {
                YogaAlarmRoot()
            }
        }
    }
}

@Composable
private fun YogaAlarmRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { AlarmStore(context.applicationContext) }
    var alarms by remember { mutableStateOf(store.load()) }
    var route by rememberSaveable { mutableStateOf("home") }
    var editingAlarmId by rememberSaveable { mutableStateOf(-1L) }
    var pendingEditorAlarm by remember { mutableStateOf<AlarmConfig?>(null) }

    when (route) {
        "editor" -> {
            val initial = pendingEditorAlarm
                ?: alarms.firstOrNull { it.id == editingAlarmId }
                ?: AlarmConfig.create(editingAlarmId)
            AlarmEditorScreen(
                initialAlarm = initial,
                onCancel = {
                    pendingEditorAlarm = null
                    route = "home"
                },
                onSave = { saved ->
                    alarms = if (alarms.any { it.id == saved.id }) {
                        alarms.map { if (it.id == saved.id) saved else it }
                    } else {
                        alarms + saved
                    }
                    store.save(alarms)
                    pendingEditorAlarm = null
                    route = "home"
                },
                onTestRoutine = { draft ->
                    pendingEditorAlarm = draft
                    route = "camera"
                },
            )
        }
        "camera" -> {
            val viewModel: PoseDebugViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            CameraPermissionRoute(
                uiState = uiState,
                onPoseFrame = viewModel::onPoseFrame,
                onCameraError = viewModel::onCameraError,
                onBack = { route = "editor" },
            )
        }
        else -> AlarmHomeScreen(
            alarms = alarms,
            onAddAlarm = {
                editingAlarmId = System.currentTimeMillis()
                pendingEditorAlarm = null
                route = "editor"
            },
            onEditAlarm = {
                editingAlarmId = it
                pendingEditorAlarm = null
                route = "editor"
            },
            onToggleAlarm = { id, enabled ->
                alarms = alarms.map { if (it.id == id) it.copy(enabled = enabled) else it }
                store.save(alarms)
            },
        )
    }
}

@Composable
private fun CameraPermissionRoute(
    uiState: com.yogaalarm.prototype.ui.PoseDebugUiState,
    onPoseFrame: (com.yogaalarm.prototype.model.PoseFrame) -> Unit,
    onCameraError: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
    }

    if (hasPermission) {
        CameraSpikeScreen(
            uiState = uiState,
            onPoseFrame = onPoseFrame,
            onCameraError = onCameraError,
            onBack = onBack,
        )
    } else {
        CameraPermissionScreen(
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onBack = onBack,
        )
    }
}

@Composable
private fun CameraPermissionScreen(
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101511))
            .padding(24.dp),
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding(),
        ) {
            Text("‹ Back", color = Color.White)
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Let’s make sure we can see you.",
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Video is analyzed on this phone and is never recorded or uploaded.",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = onRequestPermission) {
                Text("Allow camera")
            }
        }
    }
}

@Composable
private fun YogaAlarmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF274D34),
            onPrimary = Color.White,
            secondary = Color(0xFFBDEC68),
            surface = Color(0xFFF8FBF7),
            onSurface = Color(0xFF142018),
        ),
        content = content,
    )
}
