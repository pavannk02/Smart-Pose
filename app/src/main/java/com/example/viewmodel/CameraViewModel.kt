package com.example.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Geocoder
import android.location.Location
import android.util.Base64
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.*
import com.example.db.AppDatabase
import com.example.db.SavedPose
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.util.Locale

sealed interface CameraUiState {
    object Idle : CameraUiState
    object Analyzing : CameraUiState
    data class Success(val poseResponse: PoseResponse) : CameraUiState
    data class Error(val message: String) : CameraUiState
}

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val savedPoseDao = database.savedPoseDao()
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    // UI States
    private val _uiState = MutableStateFlow<CameraUiState>(CameraUiState.Idle)
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _savedPoses = MutableStateFlow<List<SavedPose>>(emptyList())
    val savedPoses: StateFlow<List<SavedPose>> = _savedPoses.asStateFlow()

    private val _activeOverlayPose = MutableStateFlow<PoseResponse?>(null)
    val activeOverlayPose: StateFlow<PoseResponse?> = _activeOverlayPose.asStateFlow()

    private val _cameraFacing = MutableStateFlow(androidx.camera.core.CameraSelector.LENS_FACING_BACK)
    val cameraFacing: StateFlow<Int> = _cameraFacing.asStateFlow()

    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_OFF)
    val flashMode: StateFlow<Int> = _flashMode.asStateFlow()

    // --- Google Maps & Google Search Grounding States ---
    // Grounding is strictly optional per user instruction:
    // If enabled, queries live location/places with gemini-3.8-flash; else runs normal mode
    private val _isMapsGroundingEnabled = MutableStateFlow(false)
    val isMapsGroundingEnabled: StateFlow<Boolean> = _isMapsGroundingEnabled.asStateFlow()

    private val _isSearchGroundingEnabled = MutableStateFlow(true)
    val isSearchGroundingEnabled: StateFlow<Boolean> = _isSearchGroundingEnabled.asStateFlow()

    private val _customPlaceQuery = MutableStateFlow("")
    val customPlaceQuery: StateFlow<String> = _customPlaceQuery.asStateFlow()

    private val _detectedLocationName = MutableStateFlow<String?>(null)
    val detectedLocationName: StateFlow<String?> = _detectedLocationName.asStateFlow()

    private val _userCoordinates = MutableStateFlow<Pair<Double, Double>?>(null)
    val userCoordinates: StateFlow<Pair<Double, Double>?> = _userCoordinates.asStateFlow()

    private val _isFetchingLocation = MutableStateFlow(false)
    val isFetchingLocation: StateFlow<Boolean> = _isFetchingLocation.asStateFlow()

    init {
        // Load favorite poses automatically
        viewModelScope.launch {
            savedPoseDao.getAllSavedPoses().collect { poses ->
                _savedPoses.value = poses
            }
        }
    }

    // --- Maps & Search Controls ---

    fun toggleMapsGrounding(enable: Boolean) {
        _isMapsGroundingEnabled.value = enable
        if (enable) {
            refreshDeviceLocation()
        } else if (_customPlaceQuery.value.isBlank()) {
            _detectedLocationName.value = null
            _userCoordinates.value = null
        }
    }

    fun toggleSearchGrounding(enable: Boolean) {
        _isSearchGroundingEnabled.value = enable
    }

    fun setCustomPlace(place: String) {
        _customPlaceQuery.value = place
        if (place.isNotBlank()) {
            _isMapsGroundingEnabled.value = true
        }
    }

    fun clearPlaceSearch() {
        _customPlaceQuery.value = ""
        if (!_isMapsGroundingEnabled.value) {
            _detectedLocationName.value = null
            _userCoordinates.value = null
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshDeviceLocation() {
        val context = getApplication<Application>()
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            Log.d("CameraViewModel", "Location permission not granted, keeping as manual place search.")
            return
        }

        viewModelScope.launch {
            _isFetchingLocation.value = true
            try {
                val cts = CancellationTokenSource()
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            _userCoordinates.value = Pair(location.latitude, location.longitude)
                            reverseGeocode(location.latitude, location.longitude)
                        } else {
                            // Try lastLocation as fallback
                            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                                if (lastLoc != null) {
                                    _userCoordinates.value = Pair(lastLoc.latitude, lastLoc.longitude)
                                    reverseGeocode(lastLoc.latitude, lastLoc.longitude)
                                }
                            }
                        }
                        _isFetchingLocation.value = false
                    }
                    .addOnFailureListener {
                        _isFetchingLocation.value = false
                    }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error fetching device location", e)
                _isFetchingLocation.value = false
            }
        }
    }

    private fun reverseGeocode(latitude: Double, longitude: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(getApplication(), Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val feature = addr.featureName
                    val locality = addr.locality ?: addr.subAdminArea ?: addr.adminArea
                    val country = addr.countryName
                    val displayName = listOfNotNull(feature, locality, country).distinct().joinToString(", ")
                    _detectedLocationName.value = displayName
                } else {
                    _detectedLocationName.value = "Lat: ${"%.3f".format(latitude)}, Lng: ${"%.3f".format(longitude)}"
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Geocoding failed", e)
                _detectedLocationName.value = "Lat: ${"%.3f".format(latitude)}, Lng: ${"%.3f".format(longitude)}"
            }
        }
    }

    fun toggleCameraFacing() {
        _cameraFacing.value = if (_cameraFacing.value == androidx.camera.core.CameraSelector.LENS_FACING_BACK) {
            androidx.camera.core.CameraSelector.LENS_FACING_FRONT
        } else {
            androidx.camera.core.CameraSelector.LENS_FACING_BACK
        }
    }

    fun toggleFlashMode() {
        _flashMode.value = when (_flashMode.value) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    fun clearActiveOverlay() {
        _activeOverlayPose.value = null
        _uiState.value = CameraUiState.Idle
    }

    fun selectSavedPose(pose: SavedPose) {
        val response = PoseResponse(
            status = "success",
            analysis = PoseAnalysis(
                detectedEnvironment = pose.detectedEnvironment,
                detectedOutfit = pose.detectedOutfit
            ),
            placeInfo = if (!pose.locationName.isNullOrBlank()) {
                PlaceInfo(placeName = pose.locationName, locationTips = "Saved from previous session", bestPhotoSpot = null)
            } else null,
            recommendedPose = RecommendedPose(
                poseName = pose.poseName,
                wireframeUrl = "",
                photographerTips = pose.photographerTips,
                wireframeStyle = pose.wireframeStyle
            )
        )
        _activeOverlayPose.value = response
        _uiState.value = CameraUiState.Success(response)
    }

    // --- Image Capture & Base64 Compression ---

    fun analyzeCapturedImage(imageProxy: ImageProxy) {
        viewModelScope.launch {
            _uiState.value = CameraUiState.Analyzing
            try {
                // Read and compress image off Main Thread
                val bitmap = imageProxyToBitmap(imageProxy)

                if (bitmap == null) {
                    _uiState.value = CameraUiState.Error("Failed to convert camera feed frame.")
                    return@launch
                }

                // Call Gemini REST API directly on-device with gemini-3.8-flash
                val response = performGeminiVisionAnalysis(bitmap)
                if (response != null) {
                    _activeOverlayPose.value = response
                    _uiState.value = CameraUiState.Success(response)
                } else {
                    _uiState.value = CameraUiState.Error("Server is waking up, please try again!")
                }
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error analyzing image", e)
                _uiState.value = CameraUiState.Error(e.message ?: "An unexpected error occurred.")
            }
        }
    }

    private suspend fun performGeminiVisionAnalysis(bitmap: Bitmap): PoseResponse? = withContext(Dispatchers.IO) {
        val apiKey = com.example.BuildConfig.GEMINI_API_KEY
        val hasCustomPlace = _customPlaceQuery.value.isNotBlank()
        val hasLiveLocation = _isMapsGroundingEnabled.value && _detectedLocationName.value != null
        val isGroundingRequested = _isMapsGroundingEnabled.value || hasCustomPlace

        val locationDescriptor = when {
            hasCustomPlace -> _customPlaceQuery.value
            hasLiveLocation -> _detectedLocationName.value
            _userCoordinates.value != null -> "Coordinates: ${_userCoordinates.value!!.first}, ${_userCoordinates.value!!.second}"
            else -> null
        }

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e("CameraViewModel", "Gemini API key is missing or unset. Returning smart fallback.")
            bitmap.recycle()
            return@withContext getBackupDemoPose(locationDescriptor)
        }

        // Compress image to max 800px width with quality 0.5 to prevent memory spikes
        val resizedBitmap = resizeBitmap(bitmap, 800)
        val base64Image = bitmapToBase64(resizedBitmap, 50)

        if (resizedBitmap != bitmap) {
            resizedBitmap.recycle()
        }
        bitmap.recycle()

        // Build prompt with location awareness when maps/search grounding is enabled
        val locationPromptSection = if (isGroundingRequested && !locationDescriptor.isNullOrBlank()) {
            """
            LOCATION CONTEXT: The user is currently at or interested in this specific location: "$locationDescriptor".
            Use Google Maps and Google Search grounding to discover the unique architectural landmarks, famous vantage points, natural lighting, and photographic ambiance of this location.
            In your response, populate the "place_info" object with:
            - "place_name": The verified place or landmark name.
            - "location_tips": Specific advice on how to pose against this landmark or environment (e.g. angle relative to monuments, sun direction, framing).
            - "best_photo_spot": The most photogenic spot or backdrop here.
            """.trimIndent()
        } else {
            """
            Analyze the environment visible in the camera image directly.
            """.trimIndent()
        }

        val prompt = """
            Analyze this photo of the user's environment, surroundings, and outfit/clothing style.
            $locationPromptSection
            Generate a creative posing guide tailored specifically to their environment, outfit, and location.
            Respond with a valid JSON object matching this structure:
            
            {
              "status": "success",
              "analysis": {
                "detected_environment": "Describe the scene/lighting",
                "detected_outfit": "Describe the clothing/style"
              },
              "place_info": {
                "place_name": "Name of location or landmark if detected/searched, else empty",
                "location_tips": "Photographic tip tailored to this place",
                "best_photo_spot": "Best backdrop recommendation"
              },
              "recommended_pose": {
                "pose_name": "Catchy Pose Name",
                "wireframe_url": "",
                "photographer_tips": "Actionable, encouraging instructions for how the subject should stand/sit and angle their body.",
                "wireframe_style": "seated"
              }
            }

            The "wireframe_style" key MUST be exactly one of these strings:
            - "seated" (sitting down, resting, holding objects)
            - "walking" (moving forward, walking, street active)
            - "over-the-shoulder" (look back, back angled, aesthetic portrait)
            - "crossed arms" (confident stance, stand bold)
            - "classic" (standard standing pose, hands around waist or pockets)
        """.trimIndent()

        // Configure grounding tools: googleMaps and googleSearch per user and metadata specification
        val toolsList: List<Tool>? = if (isGroundingRequested) {
            listOf(
                Tool(
                    googleSearch = if (_isSearchGroundingEnabled.value) emptyMap() else null,
                    googleMaps = emptyMap()
                )
            )
        } else if (_isSearchGroundingEnabled.value && !locationDescriptor.isNullOrBlank()) {
            listOf(Tool(googleSearch = emptyMap()))
        } else {
            null
        }

        val request = GeminiRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            tools = toolsList,
            generationConfig = GenerationConfig(
                responseMimeType = if (toolsList == null) "application/json" else null, // When tools are active, some models return mixed content
                temperature = 0.7f
            )
        )

        try {
            val response = withTimeout(20000) {
                RetrofitClient.service.generateContent(apiKey, request)
            }

            val candidate = response.candidates?.firstOrNull()
            val responseText = candidate?.content?.parts?.firstOrNull()?.text
            if (responseText != null) {
                Log.d("CameraViewModel", "Raw Response: $responseText")
                val parsed = parsePoseResponse(responseText)
                if (parsed != null) {
                    // Extract Grounding metadata if available
                    val queries = candidate.groundingMetadata?.webSearchQueries
                    val sources = candidate.groundingMetadata?.groundingChunks?.mapNotNull { it.web?.title ?: it.web?.uri }
                    return@withContext parsed.copy(
                        groundingQueries = queries,
                        groundingSources = sources,
                        placeInfo = if (parsed.placeInfo?.placeName.isNullOrBlank() && !locationDescriptor.isNullOrBlank()) {
                            PlaceInfo(
                                placeName = locationDescriptor,
                                locationTips = parsed.placeInfo?.locationTips ?: "Tailored for $locationDescriptor",
                                bestPhotoSpot = parsed.placeInfo?.bestPhotoSpot
                            )
                        } else parsed.placeInfo
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Gemini API call failed", e)
        }
        return@withContext null
    }

    /**
     * Safe JSON Parsing: Extracts pure JSON between first '{' and last '}'
     * preventing syntax failures even when search grounding outputs inline web citations.
     */
    private fun parsePoseResponse(rawText: String): PoseResponse? {
        return try {
            var clean = rawText.trim()
            val startIdx = clean.indexOf('{')
            val endIdx = clean.lastIndexOf('}')
            if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
                clean = clean.substring(startIdx, endIdx + 1)
            } else {
                if (clean.startsWith("```")) {
                    clean = clean.removePrefix("```json").removePrefix("```")
                    if (clean.endsWith("```")) {
                        clean = clean.removeSuffix("```")
                    }
                    clean = clean.trim()
                }
            }

            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            val adapter = moshi.adapter(PoseResponse::class.java)
            adapter.fromJson(clean)
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Failed to parse JSON response", e)
            null
        }
    }

    private fun getBackupDemoPose(location: String?): PoseResponse {
        val index = (0..4).random()
        val names = listOf("Sartorial Seated", "Golden Hour Jogger", "Candid Lookback", "Confident Creator", "Classic Streetwise")
        val environments = listOf("Indoor Co-working Space", "Urban Sunny Street", "Outdoor Park Path", "Studio Brick Wall", "Casual Cafe")
        val outfits = listOf("Modern Casual Sweater", "Athleisure Fit", "Minimal Trench coat", "Contemporary Denim Set", "Classic Urban Streetwear")
        val tips = listOf(
            "Sit relaxed on the chair edge. Hold a smart cup/pen in one hand, rest your chin on the other, looking candidly slightly off-camera.",
            "Take slow, real strides. Swing your arms naturally. Set the camera low at waist level, pointing slightly upwards.",
            "Stand at a 45-degree angle from the camera. Gently peek backward over your shoulder with a subtle smile.",
            "Stand tall, cross your arms proudly, and tilt your head slightly to the left. Keeps the framing nicely centered.",
            "Rest your left hand casually inside your pocket. Stand with your feet shoulder-width apart, leaning your hips slightly back."
        )
        val styles = listOf("seated", "walking", "over-the-shoulder", "crossed arms", "classic")

        val effectiveLocation = location ?: "Live Camera Environment"

        return PoseResponse(
            status = "success",
            analysis = PoseAnalysis(
                detectedEnvironment = environments[index] + " [Demo Mode]",
                detectedOutfit = outfits[index] + " [Demo Mode]"
            ),
            placeInfo = PlaceInfo(
                placeName = effectiveLocation,
                locationTips = "Position yourself to capture the unique architectural lines and soft natural daylight.",
                bestPhotoSpot = "Near the prominent corner angle or open backdrop"
            ),
            recommendedPose = RecommendedPose(
                poseName = names[index],
                wireframeUrl = "",
                photographerTips = tips[index],
                wireframeStyle = styles[index]
            ),
            groundingQueries = if (location != null) listOf("best photography spots near $location", "scenic poses for $location") else null
        )
    }

    // --- Database Bookmarking ---

    fun toggleFavoritePose(response: PoseResponse) {
        viewModelScope.launch(Dispatchers.IO) {
            val isAlreadySaved = savedPoseDao.isPoseSaved(response.recommendedPose.poseName) > 0
            if (isAlreadySaved) {
                val match = _savedPoses.value.find { it.poseName == response.recommendedPose.poseName }
                if (match != null) {
                    savedPoseDao.deletePose(match)
                }
            } else {
                val savedPose = SavedPose(
                    poseName = response.recommendedPose.poseName,
                    detectedEnvironment = response.analysis.detectedEnvironment,
                    detectedOutfit = response.analysis.detectedOutfit,
                    photographerTips = response.recommendedPose.photographerTips,
                    wireframeStyle = response.recommendedPose.wireframeStyle ?: "classic",
                    locationName = response.placeInfo?.placeName ?: _detectedLocationName.value ?: _customPlaceQuery.value.ifBlank { null }
                )
                savedPoseDao.insertPose(savedPose)
            }
        }
    }

    fun deleteSavedPose(pose: SavedPose) {
        viewModelScope.launch(Dispatchers.IO) {
            savedPoseDao.deletePose(pose)
        }
    }

    fun isCurrentPoseFavorited(poseName: String): Boolean {
        return _savedPoses.value.any { it.poseName == poseName }
    }

    // --- Low-level Bitmap utilities ---

    private suspend fun imageProxyToBitmap(image: ImageProxy): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val rotationDegrees = image.imageInfo.rotationDegrees
            val plane = image.planes[0]
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            try {
                image.close()
            } catch (e: Exception) {
                Log.e("CameraViewModel", "Error closing ImageProxy", e)
            }

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            val maxDimension = 1024
            var sampleSize = 1
            if (options.outWidth > maxDimension || options.outHeight > maxDimension) {
                val halfWidth = options.outWidth / 2
                val halfHeight = options.outHeight / 2
                while ((halfWidth / sampleSize) >= maxDimension && (halfHeight / sampleSize) >= maxDimension) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)

            if (rotationDegrees != 0 && bitmap != null) {
                val matrix = Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                }
                rotated
            } else {
                bitmap
            }
        } catch (e: Exception) {
            Log.e("CameraViewModel", "Error converting ImageProxy to Bitmap", e)
            null
        } finally {
            try {
                image.close()
            } catch (ex: Exception) { /* ignored */ }
        }
    }

    private fun resizeBitmap(source: Bitmap, maxDim: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDim && height <= maxDim) return source

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (ratio > 1f) {
            newWidth = maxDim
            newHeight = (maxDim / ratio).toInt()
        } else {
            newHeight = maxDim
            newWidth = (maxDim * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap, quality: Int): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
