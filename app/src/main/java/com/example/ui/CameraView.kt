@file:OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)

package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.api.PoseResponse
import com.example.db.SavedPose
import com.example.viewmodel.CameraUiState
import com.example.viewmodel.CameraViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Composable
fun SmartPoseApp(
    viewModel: CameraViewModel,
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var currentTab by remember { mutableStateOf("camera") }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color(0xFF121212)
    ) {
        if (cameraPermissionState.status.isGranted) {
            if (currentTab == "camera") {
                CameraScreen(
                    viewModel = viewModel,
                    onNavigateToFavorites = { currentTab = "favorites" }
                )
            } else {
                FavoritesScreen(
                    viewModel = viewModel,
                    onBack = { currentTab = "camera" }
                )
            }
        } else {
            OnboardingPermissionScreen(
                onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
            )
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onNavigateToFavorites: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsState()
    val activeOverlay by viewModel.activeOverlayPose.collectAsState()
    val cameraFacing by viewModel.cameraFacing.collectAsState()
    val flashMode by viewModel.flashMode.collectAsState()

    // Grounding states
    val isMapsEnabled by viewModel.isMapsGroundingEnabled.collectAsState()
    val isSearchEnabled by viewModel.isSearchGroundingEnabled.collectAsState()
    val customPlaceQuery by viewModel.customPlaceQuery.collectAsState()
    val detectedLocationName by viewModel.detectedLocationName.collectAsState()
    val isFetchingLocation by viewModel.isFetchingLocation.collectAsState()

    // Modal state for Google Maps and Search grounding config
    var showMapsSearchSheet by remember { mutableStateOf(false) }

    // Optional Timer state (0 = Off / Immediate Click by default; options: 0, 3, 5, 10)
    val timerOptions = remember { listOf(0, 3, 5, 10) }
    var selectedTimerSeconds by remember { mutableStateOf(0) }
    var countdownRemaining by remember { mutableStateOf<Int?>(null) }

    // Location permission state - only requested when user explicitly enables GPS
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // Setup ProcessCameraProvider and ImageCapture
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(countdownRemaining) {
        val current = countdownRemaining ?: return@LaunchedEffect
        if (current > 0) {
            kotlinx.coroutines.delay(1000L)
            val next = current - 1
            if (next <= 0) {
                countdownRemaining = null
                captureAndProcess(imageCapture, cameraExecutor, viewModel)
            } else {
                countdownRemaining = next
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                if (cameraProviderFuture.isDone) {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                }
            } catch (e: Exception) {
                Log.e("CameraScreen", "Error unbinding camera on dispose", e)
            }
            try {
                (previewView.parent as? android.view.ViewGroup)?.removeView(previewView)
            } catch (e: Exception) {
                Log.e("CameraScreen", "Error detaching previewView on dispose", e)
            }
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(cameraFacing, flashMode) {
        cameraProviderFuture.addListener({
            try {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Log.w("CameraScreen", "Camera permission is not yet fully available at OS level.")
                    return@addListener
                }

                if (!lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.INITIALIZED)) {
                    Log.w("CameraScreen", "Lifecycle is not initialized, skipping bind")
                    return@addListener
                }

                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                imageCapture.flashMode = flashMode

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(cameraFacing)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.e("CameraScreen", "Failed to bind camera lifecycle", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Live Viewfinder
        AndroidView(
            factory = {
                (previewView.parent as? android.view.ViewGroup)?.removeView(previewView)
                previewView
            },
            modifier = Modifier.fillMaxSize().testTag("camera_viewfinder")
        )

        // 2. Ambient Vignette Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
        )

        // 3. Pose Wireframe Floating Overlay Layer
        if (activeOverlay != null) {
            val style = activeOverlay!!.recommendedPose.wireframeStyle ?: "classic"
            val glowColor = when (style.lowercase().trim()) {
                "seated" -> NeonAmber
                "walking" -> NeonGreen
                "over-the-shoulder" -> NeonPink
                else -> NeonCyan
            }
            PoseWireframe(
                styleName = style,
                glowColor = glowColor,
                modifier = Modifier.fillMaxSize().testTag("wireframe_overlay")
            )
        }

        // 4. Header Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title & Visual branding
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = "Logo icon",
                        tint = NeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Smart Pose",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Toolbar Actions (Maps/Search + Flash + Flip + Bookmarks)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Google Maps & Search Grounding Button (Optional)
                    val isLocationActive = isMapsEnabled || customPlaceQuery.isNotBlank()
                    IconButton(
                        onClick = { showMapsSearchSheet = true },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (isLocationActive) NeonAmber.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.6f),
                                CircleShape
                            )
                            .border(
                                width = if (isLocationActive) 1.5.dp else 0.dp,
                                color = if (isLocationActive) NeonAmber else Color.Transparent,
                                shape = CircleShape
                            )
                            .testTag("maps_search_button")
                    ) {
                        Icon(
                            imageVector = if (isLocationActive) Icons.Default.Place else Icons.Outlined.TravelExplore,
                            contentDescription = "Google Maps and Search posing options",
                            tint = if (isLocationActive) NeonAmber else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Optional Timer Toggle Button (Default: Off / 0s)
                    IconButton(
                        onClick = {
                            val nextIdx = (timerOptions.indexOf(selectedTimerSeconds) + 1) % timerOptions.size
                            selectedTimerSeconds = timerOptions[nextIdx]
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (selectedTimerSeconds > 0) NeonAmber.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.6f),
                                CircleShape
                            )
                            .testTag("timer_toggle_button")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Optional capture timer",
                                tint = if (selectedTimerSeconds > 0) NeonAmber else Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(if (selectedTimerSeconds > 0) 15.dp else 20.dp)
                            )
                            if (selectedTimerSeconds > 0) {
                                Text(
                                    text = "${selectedTimerSeconds}s",
                                    color = NeonAmber,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Flash Control Button
                    IconButton(
                        onClick = { viewModel.toggleFlashMode() },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .testTag("flash_toggle_button")
                    ) {
                        Icon(
                            imageVector = when (flashMode) {
                                ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                                ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                                else -> Icons.Default.FlashOff
                            },
                            contentDescription = "Flash settings",
                            tint = if (flashMode == ImageCapture.FLASH_MODE_OFF) Color.White.copy(alpha = 0.6f) else NeonAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Camera Switching Button
                    IconButton(
                        onClick = { viewModel.toggleCameraFacing() },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .testTag("facing_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Flip camera",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Bookmarks List Shortcut Button
                    IconButton(
                        onClick = onNavigateToFavorites,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .testTag("bookmarks_nav_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmarks,
                            contentDescription = "Saved poses list",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Location/Search Active Pill (If user enabled it)
            val activeLocation = when {
                customPlaceQuery.isNotBlank() -> customPlaceQuery
                detectedLocationName != null -> detectedLocationName
                else -> null
            }

            AnimatedVisibility(
                visible = activeLocation != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .border(1.dp, NeonAmber.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .clickable { showMapsSearchSheet = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = NeonAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = activeLocation ?: "",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• Maps & Search Active",
                        color = NeonGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { viewModel.clearPlaceSearch(); viewModel.toggleMapsGrounding(false) },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear place and return to normal mode",
                            tint = Color.LightGray,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        // 5. Instruction Banner at the Top
        if (activeOverlay != null) {
            ActivePoseBanner(
                pose = activeOverlay!!,
                onClear = { viewModel.clearActiveOverlay() },
                onToggleFavorite = { viewModel.toggleFavoritePose(activeOverlay!!) },
                isFavorited = viewModel.isCurrentPoseFavorited(activeOverlay!!.recommendedPose.poseName),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 90.dp)
            )
        }

        // 6. Analyzer Loading Animation State
        if (uiState is CameraUiState.Analyzing) {
            AnalyzingOverlay(
                hasLocation = isMapsEnabled || customPlaceQuery.isNotBlank(),
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 7. Dynamic Shutter / Action Bar at Bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick Location suggestion hint if not configured yet
                if (!isMapsEnabled && customPlaceQuery.isBlank()) {
                    TextButton(
                        onClick = { showMapsSearchSheet = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.85f)),
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 4.dp, vertical = 0.dp)
                            .testTag("add_location_hint_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.TravelExplore,
                            contentDescription = null,
                            tint = NeonAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Add Google Maps / Place (Optional)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Live Countdown Overlay when Optional Timer is Active
                if (countdownRemaining != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.5.dp, NeonAmber)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "${countdownRemaining}s",
                                color = NeonAmber,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Get into pose...",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = { countdownRemaining = null }) {
                                Text("Cancel", color = NeonPink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Main Capture/Analyse Button (Default: immediate click photo; or starts countdown if timer > 0)
                ShutterButton(
                    isAnalyzing = uiState is CameraUiState.Analyzing,
                    onClick = {
                        if (countdownRemaining != null) {
                            countdownRemaining = null
                        } else if (selectedTimerSeconds > 0) {
                            countdownRemaining = selectedTimerSeconds
                        } else {
                            captureAndProcess(imageCapture, cameraExecutor, viewModel)
                        }
                    },
                    modifier = Modifier.testTag("analyze_trigger_shutter")
                )
            }
        }

        // 8. Polite Floating Error Banner
        if (uiState is CameraUiState.Error) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp)
                    .padding(horizontal = 24.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Error message",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = (uiState as CameraUiState.Error).message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 9. Optional Google Maps & Place Search Bottom Sheet
        if (showMapsSearchSheet) {
            MapsSearchModal(
                isMapsEnabled = isMapsEnabled,
                isSearchEnabled = isSearchEnabled,
                customPlaceQuery = customPlaceQuery,
                detectedLocationName = detectedLocationName,
                isFetchingLocation = isFetchingLocation,
                isLocationPermissionGranted = locationPermissionState.status.isGranted,
                onRequestLocationPermission = {
                    locationPermissionState.launchPermissionRequest()
                },
                onToggleMaps = { enable ->
                    if (enable && !locationPermissionState.status.isGranted) {
                        locationPermissionState.launchPermissionRequest()
                    }
                    viewModel.toggleMapsGrounding(enable)
                },
                onToggleSearch = { enable ->
                    viewModel.toggleSearchGrounding(enable)
                },
                onSetPlace = { place ->
                    viewModel.setCustomPlace(place)
                },
                onClearAll = {
                    viewModel.clearPlaceSearch()
                    viewModel.toggleMapsGrounding(false)
                },
                onDismiss = { showMapsSearchSheet = false }
            )
        }
    }
}

// Google Maps & Search Grounding Bottom Sheet Modal
@Composable
fun MapsSearchModal(
    isMapsEnabled: Boolean,
    isSearchEnabled: Boolean,
    customPlaceQuery: String,
    detectedLocationName: String?,
    isFetchingLocation: Boolean,
    isLocationPermissionGranted: Boolean,
    onRequestLocationPermission: () -> Unit,
    onToggleMaps: (Boolean) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onSetPlace: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf(customPlaceQuery) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val popularSpots = listOf(
        "Eiffel Tower",
        "Central Park, NY",
        "Shibuya Crossing",
        "Sunset Beach",
        "Milan Cathedral",
        "Cozy Cafe Terrace",
        "Art Museum Gallery",
        "Urban Rooftop Skyline"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E24),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color.Gray)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(NeonAmber.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = NeonAmber,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google Maps & Search Posing",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Optional: Ground poses with live maps data & search",
                        color = Color.LightGray,
                        fontSize = 11.5.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notice about optional behavior
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Not compulsory. If you prefer, leave disabled and the camera works normally using vision analysis.",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Option 1: Live GPS Location Access
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = NeonAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Live GPS Location",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isFetchingLocation) {
                        Text(
                            text = "Detecting current coordinates...",
                            color = NeonAmber,
                            fontSize = 11.5.sp
                        )
                    } else if (detectedLocationName != null) {
                        Text(
                            text = "📍 $detectedLocationName",
                            color = NeonGreen,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = if (isLocationPermissionGranted) "Use current GPS spot" else "Requires location permission",
                            color = Color.Gray,
                            fontSize = 11.5.sp
                        )
                    }
                }

                Switch(
                    checked = isMapsEnabled,
                    onCheckedChange = { enable ->
                        if (enable && !isLocationPermissionGranted) {
                            onRequestLocationPermission()
                        }
                        onToggleMaps(enable)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = NeonAmber
                    ),
                    modifier = Modifier.testTag("live_gps_switch")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Option 2: Search Any Place or Landmark
            Text(
                text = "SEARCH A PLACE OR LANDMARK",
                color = NeonCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text("e.g. Louvre Museum, Central Park, Shibuya...", color = Color.Gray, fontSize = 13.sp)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.LightGray)
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = ""; onSetPlace("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search", tint = Color.LightGray)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    keyboardController?.hide()
                    onSetPlace(searchQuery.trim())
                }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = NeonAmber,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color.Black.copy(alpha = 0.4f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.4f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("place_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Popular photo spots quick chips
            Text(
                text = "Popular photo backdrops:",
                color = Color.Gray,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(popularSpots) { spot ->
                    val isSelected = searchQuery.equals(spot, ignoreCase = true)
                    SuggestionChip(
                        onClick = {
                            searchQuery = spot
                            onSetPlace(spot)
                        },
                        label = {
                            Text(
                                text = spot,
                                fontSize = 11.sp,
                                color = if (isSelected) Color.Black else Color.White
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (isSelected) NeonAmber else Color.White.copy(alpha = 0.08f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) NeonAmber else Color.White.copy(alpha = 0.15f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Option 3: Google Search Grounding toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google Search Grounding",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Ground with real-time web photo tips & trending angles",
                        color = Color.Gray,
                        fontSize = 11.5.sp
                    )
                }
                Switch(
                    checked = isSearchEnabled,
                    onCheckedChange = onToggleSearch,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = NeonCyan
                    ),
                    modifier = Modifier.testTag("search_grounding_switch")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Actions: Apply or Clear
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onClearAll()
                        searchQuery = ""
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.weight(1f).height(46.dp).testTag("clear_location_button")
                ) {
                    Text("Normal Mode", fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        if (searchQuery.isNotBlank()) {
                            onSetPlace(searchQuery.trim())
                        }
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonAmber),
                    modifier = Modifier.weight(1f).height(46.dp).testTag("apply_location_button")
                ) {
                    Text("Apply Place", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

// Custom animated shutter core Action button
@Composable
fun ShutterButton(
    isAnalyzing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shutter_border")
    val pulseBorderRadiusFloat by infiniteTransition.animateFloat(
        initialValue = 2f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_shutter"
    )
    val pulseBorderRadius = pulseBorderRadiusFloat.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(80.dp)
            .border(
                pulseBorderRadius,
                if (isAnalyzing) NeonGreen else Color.White,
                CircleShape
            )
            .padding(6.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(enabled = !isAnalyzing) { onClick() }
    ) {
        if (isAnalyzing) {
            CircularProgressIndicator(
                color = NeonGreen,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(NeonCyan, Color(0xFF00B0FF))
                        )
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Analyse AI",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp).align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun AnalyzingOverlay(
    hasLocation: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "terminal_loader")
    val translationY by infiniteTransition.animateFloat(
        initialValue = -16f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radar_scanning"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(20.dp))
            .border(1.dp, NeonCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(horizontal = 28.dp, vertical = 24.dp)
            .widthIn(max = 290.dp)
    ) {
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = NeonCyan, strokeWidth = 3.5.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .offset(y = translationY.dp)
                    .background(NeonCyan)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (hasLocation) "Analyzing Scene & Location..." else "Analyzing Outfit & Scene...",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasLocation) "Gemini 3.8 Flash is grounding with Google Maps & Search..." else "Gemini 3.8 Flash is building pose wireframe...",
            color = Color.LightGray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ActivePoseBanner(
    pose: PoseResponse,
    onClear: () -> Unit,
    onToggleFavorite: () -> Unit,
    isFavorited: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.90f)),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Optional Place info from Google Maps / Search Grounding
            if (pose.placeInfo != null && !pose.placeInfo.placeName.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonAmber.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = NeonAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = pose.placeInfo.placeName,
                        color = NeonAmber,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Maps Grounded",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Environment + Outfit Labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ENVIRONMENT",
                        color = NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = pose.analysis.detectedEnvironment,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "OUTFIT STYLE",
                        color = NeonGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = pose.analysis.detectedOutfit,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row {
                    // Save bookmark Heart button
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Add favorite",
                            tint = if (isFavorited) NeonPink else Color.White
                        )
                    }

                    // Reset button
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear pose",
                            tint = Color.White
                        )
                    }
                }
            }

            // Location Spot Tip if provided
            if (pose.placeInfo?.bestPhotoSpot != null && pose.placeInfo.bestPhotoSpot.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Stars,
                        contentDescription = null,
                        tint = NeonAmber,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Spot: " + pose.placeInfo.bestPhotoSpot,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = Color.White.copy(alpha = 0.12f)
            )

            // Suggested Pose details & instruction
            Row(verticalAlignment = Alignment.CenterVertically) {
                val styleColor = when (pose.recommendedPose.wireframeStyle?.lowercase()) {
                    "seated" -> NeonAmber
                    "walking" -> NeonGreen
                    "over-the-shoulder" -> NeonPink
                    else -> NeonCyan
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(styleColor.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Gesture,
                        contentDescription = "Guide",
                        tint = styleColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = pose.recommendedPose.poseName,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = pose.recommendedPose.photographerTips,
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    if (pose.placeInfo?.locationTips != null && pose.placeInfo.locationTips.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "📍 " + pose.placeInfo.locationTips,
                            color = NeonAmber.copy(alpha = 0.9f),
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

// Low level CameraX Capture logic triggering ViewModel analysis
private fun captureAndProcess(
    imageCapture: ImageCapture,
    executor: Executor,
    viewModel: CameraViewModel
) {
    try {
        if (executor is java.util.concurrent.ExecutorService && executor.isShutdown) {
            Log.w("SmartPose", "Camera executor is shutdown, skipping takePicture")
            return
        }
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                    viewModel.analyzeCapturedImage(image)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("SmartPose", "Camera capture error", exception)
                }
            }
        )
    } catch (e: Exception) {
        Log.e("SmartPose", "Failed to invoke takePicture safely", e)
    }
}

// Onboarding Permissions Layout
@Composable
fun OnboardingPermissionScreen(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF1E1E2F), Color(0xFF0F0F1A))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(32.dp)
                .widthIn(max = 320.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(NeonCyan.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera onboarding symbol",
                    tint = NeonCyan,
                    modifier = Modifier.size(44.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Smart Pose Camera",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Powered by Gemini 3.8 Flash. Analyzes your surroundings, outfit, and optional Google Maps & Search location to overlay customized pose wireframes on screen.",
                color = Color.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("grant_camera_permission_button")
            ) {
                Icon(Icons.Default.VpnKey, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Grant Camera Access",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Google Maps access is strictly optional and only enabled if you request it.",
                color = Color.Gray,
                fontSize = 10.5.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Bookmarks/Saved Poses Library Screen
@Composable
fun FavoritesScreen(
    viewModel: CameraViewModel,
    onBack: () -> Unit
) {
    val savedPoses by viewModel.savedPoses.collectAsState()
    var sortOrder by remember { mutableStateOf("Newest") }
    var sortDropdownExpanded by remember { mutableStateOf(false) }

    val sortedPoses = remember(savedPoses, sortOrder) {
        if (sortOrder == "Newest") {
            savedPoses.sortedByDescending { it.timestamp }
        } else {
            savedPoses.sortedBy { it.timestamp }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // App top header with Sorting Dropdown ('Newest' / 'Oldest')
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to camera view",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Saved Poses",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Sort Dropdown ('Newest' vs 'Oldest')
            Box {
                OutlinedButton(
                    onClick = { sortDropdownExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("saved_poses_sort_dropdown")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort saved poses",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = sortOrder,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = sortDropdownExpanded,
                    onDismissRequest = { sortDropdownExpanded = false },
                    modifier = Modifier.background(Color(0xFF1E1E2A))
                ) {
                    DropdownMenuItem(
                        text = { Text("Newest", color = if (sortOrder == "Newest") NeonCyan else Color.White) },
                        onClick = {
                            sortOrder = "Newest"
                            sortDropdownExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Oldest", color = if (sortOrder == "Oldest") NeonCyan else Color.White) },
                        onClick = {
                            sortOrder = "Oldest"
                            sortDropdownExpanded = false
                        }
                    )
                }
            }
        }

        if (sortedPoses.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Bookmarks,
                        contentDescription = "No bookmarks",
                        tint = Color.DarkGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your Bookmarks Library is Empty",
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Take a photo inside viewfinder mode to find custom poses and save them here!",
                        color = Color.DarkGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 48.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("saved_poses_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(sortedPoses) { savedPose ->
                    FavoritePoseCard(
                        pose = savedPose,
                        onSelect = {
                            viewModel.selectSavedPose(savedPose)
                            onBack()
                        },
                        onDelete = { viewModel.deleteSavedPose(savedPose) }
                    )
                }
            }
        }
    }
}

@Composable
fun FavoritePoseCard(
    pose: SavedPose,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val styleColor = when (pose.wireframeStyle.lowercase()) {
        "seated" -> NeonAmber
        "walking" -> NeonGreen
        "over-the-shoulder" -> NeonPink
        else -> NeonCyan
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(styleColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = pose.poseName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete bookmark",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Metadata info row list
            if (!pose.locationName.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = NeonAmber,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = pose.locationName,
                        color = NeonAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = "Scene: " + pose.detectedEnvironment,
                color = Color.LightGray,
                fontSize = 12.sp
            )
            Text(
                text = "Wear: " + pose.detectedOutfit,
                color = Color.LightGray,
                fontSize = 12.sp
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = Color.White.copy(alpha = 0.05f)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = "Tips icon",
                    tint = styleColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = pose.photographerTips,
                    color = Color.Gray,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
