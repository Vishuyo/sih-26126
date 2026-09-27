package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.CameraProvider
import com.example.data.ConnectionStatus
import com.example.ui.theme.WaynestBlack
import com.example.ui.theme.WaynestDarkGray
import com.example.ui.theme.WaynestMidGray
import com.example.ui.theme.WaynestStatusBlue
import com.example.ui.theme.WaynestStatusGreen
import com.example.ui.theme.WaynestStatusOrange
import com.example.ui.theme.WaynestStatusRed
import com.example.ui.theme.WaynestWhite
import com.example.viewmodel.StreamViewModel

@Composable
fun CameraPreviewScreen(
    viewModel: StreamViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current

    val uiState by viewModel.uiState.collectAsState()
    val navigationData by viewModel.navigationData.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
        viewModel.startSensors()
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            viewModel.stopSensors()
        }
    }

    // CameraProvider instance
    val cameraProvider = remember {
        CameraProvider(context) { jpegBytes ->
            viewModel.onFrameCaptured(jpegBytes)
        }
    }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var showTelemetryDetails by remember { mutableStateOf(false) }
    var isControlsExpandedInLandscape by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
    }

    LaunchedEffect(configuration.orientation) {
        display?.rotation?.let { rotation ->
            cameraProvider.updateTargetRotation(rotation)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Live Camera Stream or Fallback Background
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        previewViewRef = this
                        cameraProvider.bindCamera(
                            lifecycleOwner = lifecycleOwner,
                            previewView = this,
                            onInitialized = {
                                viewModel.setCameraReady(true)
                            }
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("camera_preview_view")
            )
        } else {
            // Simulated / Camera-Disabled Feed Pattern
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1E242B),
                                Color(0xFF121519),
                                Color(0xFF0B0D10)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Camera Permission Required",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = "Camera Permission Required",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Enable camera access to stream live video to the compute laptop, or switch to Simulation Mode.",
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.widthIn(max = 300.dp)
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WaynestWhite,
                            contentColor = WaynestBlack
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.testTag("request_camera_permission_btn")
                    ) {
                        Text("Grant Camera Access", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. HUD Canvas Overlay (Red Bounding Boxes, Green Trajectory Path, Minimal Black/White Gauges)
        HudOverlay(
            navigationData = navigationData,
            telemetry = telemetry,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Compact Unified Header Bar (Top Safe Area)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = if (isLandscape) 4.dp else 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Unified Brand + Status Pill (merged)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .shadow(6.dp, RoundedCornerShape(16.dp))
                        .background(WaynestBlack.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                color = when (uiState.status) {
                                    ConnectionStatus.STREAMING -> WaynestStatusGreen
                                    ConnectionStatus.CONNECTED -> WaynestStatusBlue
                                    ConnectionStatus.CONNECTING -> WaynestStatusOrange
                                    ConnectionStatus.ERROR -> WaynestStatusRed
                                    ConnectionStatus.DISCONNECTED -> Color.Gray
                                },
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "UGV EDGE NODE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Inline status text
                    Text(
                        text = when {
                            uiState.isSimulationMode -> "SIM"
                            uiState.status == ConnectionStatus.STREAMING -> "${uiState.fps} FPS"
                            uiState.status == ConnectionStatus.CONNECTED -> "CONNECTED"
                            uiState.status == ConnectionStatus.CONNECTING -> "CONNECTING..."
                            uiState.status == ConnectionStatus.ERROR -> "ERROR"
                            else -> "STANDBY"
                        },
                        color = when {
                            uiState.isSimulationMode -> WaynestStatusGreen
                            uiState.status == ConnectionStatus.STREAMING -> WaynestStatusGreen
                            uiState.status == ConnectionStatus.CONNECTED -> WaynestStatusBlue
                            uiState.status == ConnectionStatus.CONNECTING -> WaynestStatusOrange
                            uiState.status == ConnectionStatus.ERROR -> WaynestStatusRed
                            else -> Color.LightGray
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // 4. Compact Floating Action Buttons (Right rail - only essential)
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = if (isLandscape) 10.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Torch + Camera flip combined (toggle)
            FloatingCircleIconButton(
                icon = if (uiState.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDesc = "Toggle Torch",
                isActive = uiState.isTorchOn,
                testTag = "toggle_torch_btn",
                onClick = {
                    val newState = cameraProvider.toggleTorch()
                    viewModel.setTorchState(newState)
                }
            )

            // Simulation Mode Toggle
            FloatingCircleIconButton(
                icon = Icons.Default.Sensors,
                contentDesc = "Simulation Mode",
                isActive = uiState.isSimulationMode,
                testTag = "toggle_simulation_btn",
                onClick = { viewModel.toggleSimulation() }
            )
        }

        // 5. Compact Bottom Control Card
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = if (isLandscape) 6.dp else 10.dp)
        ) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = WaynestWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Compact Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (uiState.isSimulationMode) "SIMULATION ACTIVE" else "NAVIGATION UPLINK",
                            color = if (uiState.isSimulationMode) WaynestStatusGreen else WaynestBlack,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Simulation toggle badge
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (uiState.isSimulationMode) WaynestStatusGreen.copy(alpha = 0.15f) else Color(0xFFF0F2F5),
                            modifier = Modifier
                                .clickable { viewModel.toggleSimulation() }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (uiState.isSimulationMode) "SIM" else "LIVE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (uiState.isSimulationMode) WaynestStatusGreen else WaynestDarkGray
                            )
                        }
                    }

                    // Error Banner (compact)
                    if (uiState.errorMessage != null && uiState.status == ConnectionStatus.ERROR) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFF1F2),
                            border = BorderStroke(1.dp, Color(0xFFFECDD3)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = WaynestStatusRed, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = uiState.errorMessage ?: "Connection failed",
                                        color = Color(0xFF9F1239),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Button(
                                    onClick = { viewModel.toggleSimulation() },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = WaynestBlack, contentColor = WaynestWhite),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Demo", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                    // Main Row: IP Field + Connect Button (compact)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.serverAddress,
                            onValueChange = { viewModel.updateServerAddress(it) },
                            placeholder = { Text("192.168.x.x:8080", fontSize = 11.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            textStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WaynestBlack,
                                unfocusedBorderColor = Color(0xFFE5E7EB),
                                focusedContainerColor = Color(0xFFF9FAFB),
                                unfocusedContainerColor = Color(0xFFF9FAFB),
                                focusedTextColor = WaynestBlack,
                                unfocusedTextColor = WaynestBlack
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); viewModel.connect() }),
                            leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null, tint = WaynestMidGray, modifier = Modifier.size(16.dp)) },
                            modifier = Modifier.weight(1f).height(40.dp)
                        )

                        val isConnectedOrStreaming = uiState.status == ConnectionStatus.STREAMING || uiState.status == ConnectionStatus.CONNECTED

                        Button(
                            onClick = { focusManager.clearFocus(); viewModel.toggleConnect() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isConnectedOrStreaming) WaynestDarkGray else WaynestBlack,
                                contentColor = WaynestWhite
                            ),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isConnectedOrStreaming) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isConnectedOrStreaming) "Disconnect" else "Connect",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Waynest styled Circular Icon Button with soft shadow.
 */
@Composable
private fun FloatingCircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    isActive: Boolean = false,
    testTag: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(44.dp)
            .shadow(6.dp, CircleShape)
            .background(
                if (isActive) WaynestBlack else WaynestWhite,
                CircleShape
            )
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = if (isActive) WaynestWhite else WaynestBlack,
            modifier = Modifier.size(20.dp)
        )
    }
}
