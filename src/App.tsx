import React, { useEffect, useRef, useState, useCallback } from 'react';
import {
  Camera,
  RefreshCw,
  Bookmark,
  MapPin,
  Sparkles,
  Grid,
  FlipHorizontal,
  Upload,
  Download,
  Key,
  Trash2,
  X,
  Check,
  Compass,
  Eye,
  Layers
} from 'lucide-react';
import { PoseResponse, SavedPose, WireframeStyle } from './types/pose';
import { analyzeSceneWithGemini, reverseGeocodeCoords } from './services/geminiWebService';
import { PoseWireframeSvg } from './components/PoseWireframeSvg';
import { MapsSearchModal } from './components/MapsSearchModal';

const STORAGE_KEY_SAVED_POSES = 'smart_pose_web_saved_v1';
const STORAGE_KEY_API_KEY = 'smart_pose_web_gemini_key';

const STYLE_LABELS: { id: WireframeStyle; label: string }[] = [
  { id: 'classic', label: 'Classic Stand' },
  { id: 'walking', label: 'Walking Stride' },
  { id: 'seated', label: 'Seated Cafe' },
  { id: 'over_shoulder', label: 'Over Shoulder' },
  { id: 'crossed_arms', label: 'Crossed Arms' }
];

export const App: React.FC = () => {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  // Camera & Media states
  const [facingMode, setFacingMode] = useState<'user' | 'environment'>('user');
  const [cameraReady, setCameraReady] = useState(false);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [uploadedImage, setUploadedImage] = useState<string | null>(null);
  const [isMirrored, setIsMirrored] = useState(true);
  const [showGrid, setShowGrid] = useState(true);
  const [screenFlash, setScreenFlash] = useState(false);

  // AI & Pose states
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [activePose, setActivePose] = useState<PoseResponse | null>(null);
  const [manualWireframe, setManualWireframe] = useState<WireframeStyle | null>(null);

  // Optional Google Maps & Search Grounding states (disabled by default per user instructions)
  const [isMapsEnabled, setIsMapsEnabled] = useState(false);
  const [isSearchEnabled, setIsSearchEnabled] = useState(false);
  const [detectedLocation, setDetectedLocation] = useState('');
  const [customPlaceQuery, setCustomPlaceQuery] = useState('');
  const [isFetchingGps, setIsFetchingGps] = useState(false);
  const [gpsError, setGpsError] = useState<string | null>(null);
  const [showMapsModal, setShowMapsModal] = useState(false);

  // Saved poses & settings drawers
  const [savedPoses, setSavedPoses] = useState<SavedPose[]>(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY_SAVED_POSES);
      return raw ? JSON.parse(raw) : [];
    } catch {
      return [];
    }
  });
  const [showSavedDrawer, setShowSavedDrawer] = useState(false);
  const [showApiModal, setShowApiModal] = useState(false);
  const [userApiKey, setUserApiKey] = useState<string>(() => {
    return localStorage.getItem(STORAGE_KEY_API_KEY) || '';
  });
  const [justSavedToast, setJustSavedToast] = useState(false);

  // Start or switch WebRTC camera stream
  const startCamera = useCallback(async (mode: 'user' | 'environment') => {
    setCameraError(null);
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      setCameraError('Camera API not supported in this browser. You can still upload a photo or use interactive pose guides!');
      return;
    }

    try {
      if (videoRef.current?.srcObject) {
        const prevStream = videoRef.current.srcObject as MediaStream;
        prevStream.getTracks().forEach((t) => t.stop());
      }

      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: { ideal: mode },
          width: { ideal: 1280 },
          height: { ideal: 720 }
        },
        audio: false
      });

      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
        setCameraReady(true);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Camera permission declined';
      setCameraError(`Live camera unavailable (${msg}). Use "Upload Photo" or explore interactive pose wireframes below.`);
      setCameraReady(false);
    }
  }, []);

  useEffect(() => {
    if (!uploadedImage) {
      startCamera(facingMode);
    }
    return () => {
      if (videoRef.current?.srcObject) {
        const stream = videoRef.current.srcObject as MediaStream;
        stream.getTracks().forEach((t) => t.stop());
      }
    };
  }, [facingMode, uploadedImage, startCamera]);

  // Handle Optional Live GPS Toggle (only requests browser permission when user explicitly enables it)
  const handleToggleMapsGps = useCallback((enable: boolean) => {
    setIsMapsEnabled(enable);
    setGpsError(null);

    if (!enable) {
      setDetectedLocation('');
      setIsFetchingGps(false);
      return;
    }

    setIsSearchEnabled(true);
    if (!navigator.geolocation) {
      setGpsError('Browser Geolocation is not supported on this device');
      return;
    }

    setIsFetchingGps(true);
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const { latitude, longitude } = pos.coords;
        const name = await reverseGeocodeCoords(latitude, longitude);
        setDetectedLocation(name);
        setIsFetchingGps(false);
      },
      (err) => {
        setIsFetchingGps(false);
        setGpsError(err.message || 'Location permission denied');
      },
      { enableHighAccuracy: true, timeout: 10000 }
    );
  }, []);

  const handleDisableAllLocation = useCallback(() => {
    setIsMapsEnabled(false);
    setIsSearchEnabled(false);
    setDetectedLocation('');
    setCustomPlaceQuery('');
    setGpsError(null);
  }, []);

  // Capture current video frame or uploaded photo as base64 JPEG
  const captureFrameBase64 = useCallback((): string | undefined => {
    if (uploadedImage) return uploadedImage;
    const video = videoRef.current;
    if (!video || !cameraReady || video.videoWidth === 0) return undefined;

    const canvas = document.createElement('canvas');
    const scale = Math.min(1, 640 / Math.max(video.videoWidth, video.videoHeight));
    canvas.width = Math.round(video.videoWidth * scale);
    canvas.height = Math.round(video.videoHeight * scale);
    const ctx = canvas.getContext('2d');
    if (!ctx) return undefined;

    if (isMirrored) {
      ctx.translate(canvas.width, 0);
      ctx.scale(-1, 1);
    }
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    return canvas.toDataURL('image/jpeg', 0.8);
  }, [uploadedImage, cameraReady, isMirrored]);

  // Trigger AI Pose Analysis
  const handleAnalyzeScene = useCallback(async () => {
    setIsAnalyzing(true);
    setScreenFlash(true);
    setTimeout(() => setScreenFlash(false), 180);

    try {
      const frameBase64 = captureFrameBase64();
      const result = await analyzeSceneWithGemini({
        imageBase64: frameBase64,
        enableMaps: isMapsEnabled,
        enableSearch: isSearchEnabled,
        detectedLocation,
        customPlaceQuery,
        userApiKey
      });
      setActivePose(result);
      setManualWireframe(null);
    } finally {
      setIsAnalyzing(false);
    }
  }, [
    captureFrameBase64,
    isMapsEnabled,
    isSearchEnabled,
    detectedLocation,
    customPlaceQuery,
    userApiKey
  ]);

  // Save current pose to browser localStorage
  const handleSavePose = () => {
    if (!activePose) return;
    const locLabel =
      activePose.place_info?.place_name ||
      customPlaceQuery.trim() ||
      detectedLocation.trim() ||
      undefined;

    const newEntry: SavedPose = {
      id: `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      poseName: activePose.recommended_pose.pose_name,
      detectedEnvironment: activePose.analysis.detected_environment,
      detectedOutfit: activePose.analysis.detected_outfit,
      photographerTips: activePose.recommended_pose.photographer_tips,
      wireframeStyle: manualWireframe || activePose.recommended_pose.wireframe_style,
      locationName: locLabel,
      bestPhotoSpot: activePose.place_info?.best_photo_spot,
      timestamp: Date.now()
    };

    const updated = [newEntry, ...savedPoses];
    setSavedPoses(updated);
    localStorage.setItem(STORAGE_KEY_SAVED_POSES, JSON.stringify(updated));
    setJustSavedToast(true);
    setTimeout(() => setJustSavedToast(false), 2000);
  };

  const handleDeleteSavedPose = (id: string) => {
    const updated = savedPoses.filter((p) => p.id !== id);
    setSavedPoses(updated);
    localStorage.setItem(STORAGE_KEY_SAVED_POSES, JSON.stringify(updated));
  };

  // Download high-res snapshot
  const handleDownloadSnapshot = () => {
    const dataUrl = captureFrameBase64();
    if (!dataUrl) return;
    const link = document.createElement('a');
    link.href = dataUrl;
    link.download = `smart-pose-${Date.now()}.jpg`;
    link.click();
  };

  // Handle photo upload for desktop/gallery testing
  const handlePhotoUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        setUploadedImage(reader.result);
      }
    };
    reader.readAsDataURL(file);
  };

  const currentWireframeStyle: WireframeStyle | null =
    manualWireframe || activePose?.recommended_pose.wireframe_style || null;

  const activeLocationLabel = customPlaceQuery.trim() || detectedLocation.trim();
  const isGroundingActive = Boolean((isMapsEnabled || isSearchEnabled) && activeLocationLabel);

  return (
    <div className="relative flex min-h-screen w-full flex-col bg-[#0B0C10] text-white select-none overflow-hidden">
      {/* Screen Flash Effect on Shutter */}
      {screenFlash && (
        <div className="pointer-events-none fixed inset-0 z-50 bg-white/60 transition-opacity duration-200" />
      )}

      {/* Top Navigation & Controls Bar */}
      <header className="relative z-30 flex flex-wrap items-center justify-between gap-2 bg-gradient-to-b from-black/85 via-black/50 to-transparent px-4 py-3.5 sm:px-6">
        <div className="flex items-center gap-2.5">
          <div className="flex h-10 w-10 items-center justify-center rounded-2xl bg-cyan-400/15 border border-cyan-400/40 text-cyan-300 shadow-lg shadow-cyan-500/10">
            <Camera className="h-5 w-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-base sm:text-lg font-extrabold tracking-tight text-white">
                SMART POSE <span className="text-cyan-400 font-semibold text-xs">WEB</span>
              </h1>
              {isGroundingActive && (
                <span className="hidden sm:inline-flex items-center gap-1 rounded-full bg-cyan-500/20 border border-cyan-400/40 px-2.5 py-0.5 text-[11px] font-semibold text-cyan-300">
                  <MapPin className="h-3 w-3" />
                  {activeLocationLabel}
                </span>
              )}
            </div>
            <p className="text-[11px] text-gray-400">
              AI Pose Director & Optional Live Maps Grounding
            </p>
          </div>
        </div>

        {/* Right Toolbar Actions */}
        <div className="flex items-center gap-1.5 sm:gap-2">
          {/* Optional Google Maps & Search Grounding Button */}
          <button
            type="button"
            onClick={() => setShowMapsModal(true)}
            className={`inline-flex items-center gap-1.5 rounded-full px-3 py-2 text-xs font-semibold transition border ${
              isGroundingActive
                ? 'bg-cyan-400/20 border-cyan-400 text-cyan-200 shadow-lg shadow-cyan-500/20'
                : 'bg-white/10 border-white/15 text-gray-200 hover:bg-white/15'
            }`}
            title="Optional Google Maps & Place Search"
          >
            <Compass className="h-4 w-4 text-cyan-300" />
            <span className="hidden md:inline">
              {isGroundingActive ? 'Maps Active' : 'Maps / Search (Optional)'}
            </span>
          </button>

          {/* Grid Toggle */}
          <button
            type="button"
            onClick={() => setShowGrid((g) => !g)}
            className={`rounded-full p-2.5 border transition ${
              showGrid
                ? 'bg-cyan-400/20 border-cyan-400/40 text-cyan-300'
                : 'bg-white/10 border-white/10 text-gray-400 hover:text-white'
            }`}
            title="Toggle Rule-of-Thirds Grid"
          >
            <Grid className="h-4 w-4" />
          </button>

          {/* Mirror Toggle */}
          <button
            type="button"
            onClick={() => setIsMirrored((m) => !m)}
            className={`rounded-full p-2.5 border transition ${
              isMirrored
                ? 'bg-cyan-400/20 border-cyan-400/40 text-cyan-300'
                : 'bg-white/10 border-white/10 text-gray-400 hover:text-white'
            }`}
            title="Mirror Viewfinder"
          >
            <FlipHorizontal className="h-4 w-4" />
          </button>

          {/* Switch Front/Back Camera */}
          <button
            type="button"
            onClick={() => {
              setUploadedImage(null);
              setFacingMode((prev) => (prev === 'user' ? 'environment' : 'user'));
            }}
            className="rounded-full bg-white/10 border border-white/10 p-2.5 text-gray-200 hover:bg-white/20 transition"
            title="Switch Front / Back Camera"
          >
            <RefreshCw className="h-4 w-4" />
          </button>

          {/* Upload Photo Fallback */}
          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            className="rounded-full bg-white/10 border border-white/10 p-2.5 text-gray-200 hover:bg-white/20 transition"
            title="Upload Photo from Device"
          >
            <Upload className="h-4 w-4" />
          </button>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/*"
            onChange={handlePhotoUpload}
            className="hidden"
          />

          {/* Saved Poses Library */}
          <button
            type="button"
            onClick={() => setShowSavedDrawer(true)}
            className="relative rounded-full bg-white/10 border border-white/10 p-2.5 text-gray-200 hover:bg-white/20 transition"
            title="Saved Poses Library"
          >
            <Bookmark className="h-4 w-4" />
            {savedPoses.length > 0 && (
              <span className="absolute -top-1 -right-1 flex h-4 w-4 items-center justify-center rounded-full bg-amber-400 text-[10px] font-bold text-black">
                {savedPoses.length}
              </span>
            )}
          </button>

          {/* Gemini API Key Settings */}
          <button
            type="button"
            onClick={() => setShowApiModal(true)}
            className={`rounded-full p-2.5 border transition ${
              userApiKey
                ? 'bg-emerald-500/20 border-emerald-400/40 text-emerald-300'
                : 'bg-white/10 border-white/10 text-gray-300 hover:bg-white/20'
            }`}
            title="Configure Optional Gemini API Key"
          >
            <Key className="h-4 w-4" />
          </button>
        </div>
      </header>

      {/* Main Viewfinder Stage */}
      <main className="relative flex-1 flex flex-col items-center justify-center overflow-hidden">
        {/* Live Video or Uploaded Photo */}
        <div className="relative h-[calc(100vh-140px)] w-full max-w-5xl overflow-hidden sm:rounded-3xl border border-white/10 bg-[#11131C] shadow-2xl">
          {uploadedImage ? (
            <img
              src={uploadedImage}
              alt="Uploaded scene"
              className="h-full w-full object-cover"
              style={{ transform: isMirrored ? 'scaleX(-1)' : undefined }}
            />
          ) : (
            <video
              ref={videoRef}
              playsInline
              muted
              autoPlay
              className="h-full w-full object-cover"
              style={{ transform: isMirrored ? 'scaleX(-1)' : undefined }}
            />
          )}

          {/* Subtle Studio Vignette */}
          <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-black/35" />

          {/* Camera Fallback Message Banner if No Webcam */}
          {cameraError && !uploadedImage && (
            <div className="absolute top-4 left-4 right-4 z-20 mx-auto max-w-md rounded-2xl border border-white/15 bg-black/75 backdrop-blur-md p-3.5 text-center">
              <p className="text-xs text-gray-300">{cameraError}</p>
              <div className="mt-2.5 flex justify-center gap-2">
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  className="inline-flex items-center gap-1.5 rounded-xl bg-cyan-400 px-3.5 py-1.5 text-xs font-bold text-black"
                >
                  <Upload className="h-3.5 w-3.5" />
                  Upload Scene Photo
                </button>
                <button
                  type="button"
                  onClick={() => startCamera(facingMode)}
                  className="inline-flex items-center gap-1.5 rounded-xl bg-white/10 px-3.5 py-1.5 text-xs font-semibold text-white"
                >
                  Retry Camera
                </button>
              </div>
            </div>
          )}

          {/* Uploaded Image Clear Chip */}
          {uploadedImage && (
            <div className="absolute top-4 left-4 z-20">
              <button
                type="button"
                onClick={() => setUploadedImage(null)}
                className="inline-flex items-center gap-1.5 rounded-full bg-black/75 border border-white/20 px-3 py-1.5 text-xs font-semibold text-cyan-300 hover:bg-black"
              >
                <X className="h-3.5 w-3.5" />
                Return to Live Camera
              </button>
            </div>
          )}

          {/* Active Location Grounding Floating Pill */}
          {isGroundingActive && (
            <div className="absolute top-4 right-4 z-20 flex items-center gap-2 rounded-full bg-black/80 border border-cyan-400/50 px-3.5 py-1.5 text-xs text-cyan-200 backdrop-blur-md">
              <MapPin className="h-3.5 w-3.5 text-cyan-400 shrink-0" />
              <span className="max-w-[180px] truncate font-medium">{activeLocationLabel}</span>
              <button
                type="button"
                onClick={handleDisableAllLocation}
                className="ml-1 text-gray-400 hover:text-white"
                title="Clear Location & Use Normal Mode"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            </div>
          )}

          {/* SVG Pose Wireframe Overlay */}
          {currentWireframeStyle ? (
            <PoseWireframeSvg
              style={currentWireframeStyle}
              showGrid={showGrid}
              mirrored={isMirrored}
            />
          ) : (
            showGrid && (
              <PoseWireframeSvg
                style="classic"
                showGrid={true}
                mirrored={isMirrored}
                opacity={0.32}
              />
            )
          )}

          {/* Quick Wireframe Style Switcher Pill Bar */}
          <div className="absolute top-16 left-1/2 -translate-x-1/2 z-20 flex items-center gap-1 rounded-full bg-black/65 border border-white/15 p-1 backdrop-blur-md max-w-[95%] overflow-x-auto">
            <span className="hidden sm:inline-flex items-center gap-1 px-2.5 text-[11px] font-semibold text-gray-400">
              <Layers className="h-3 w-3 text-cyan-400" />
              Wireframe:
            </span>
            {STYLE_LABELS.map((item) => {
              const active = currentWireframeStyle === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setManualWireframe(item.id)}
                  className={`whitespace-nowrap rounded-full px-2.5 py-1 text-[11px] font-semibold transition ${
                    active
                      ? 'bg-cyan-400 text-black shadow'
                      : 'text-gray-300 hover:bg-white/10'
                  }`}
                >
                  {item.label}
                </button>
              );
            })}
          </div>

          {/* Active AI Pose Director Card */}
          {activePose && (
            <div className="absolute bottom-24 left-4 right-4 z-20 mx-auto max-w-xl rounded-2xl border border-cyan-400/35 bg-[#10131E]/90 p-4 backdrop-blur-xl shadow-2xl">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="rounded-md bg-cyan-400/20 px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider text-cyan-300 border border-cyan-400/30">
                      {manualWireframe || activePose.recommended_pose.wireframe_style}
                    </span>
                    {activePose.place_info?.place_name && (
                      <span className="inline-flex items-center gap-1 rounded-md bg-amber-400/20 px-2 py-0.5 text-[10px] font-bold text-amber-300 border border-amber-400/30">
                        <MapPin className="h-3 w-3" />
                        {activePose.place_info.place_name}
                      </span>
                    )}
                  </div>
                  <h3 className="mt-1 text-base font-extrabold text-white">
                    {activePose.recommended_pose.pose_name}
                  </h3>
                </div>

                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    onClick={handleSavePose}
                    className="inline-flex items-center gap-1 rounded-xl bg-white/10 hover:bg-white/20 px-3 py-1.5 text-xs font-semibold text-amber-300 transition"
                  >
                    {justSavedToast ? <Check className="h-3.5 w-3.5" /> : <Bookmark className="h-3.5 w-3.5" />}
                    <span>{justSavedToast ? 'Saved' : 'Save'}</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setActivePose(null)}
                    className="rounded-xl p-1.5 text-gray-400 hover:bg-white/10 hover:text-white"
                    title="Dismiss pose card"
                  >
                    <X className="h-4 w-4" />
                  </button>
                </div>
              </div>

              <p className="mt-2 text-xs leading-relaxed text-gray-200">
                {activePose.recommended_pose.photographer_tips}
              </p>

              <div className="mt-2.5 flex flex-wrap gap-2 text-[11px] text-gray-400">
                <span className="rounded-lg bg-white/5 px-2.5 py-1">
                  Scene: <strong className="text-gray-200">{activePose.analysis.detected_environment}</strong>
                </span>
                <span className="rounded-lg bg-white/5 px-2.5 py-1">
                  Outfit: <strong className="text-gray-200">{activePose.analysis.detected_outfit}</strong>
                </span>
              </div>

              {activePose.place_info && (activePose.place_info.best_photo_spot || activePose.place_info.location_tips) && (
                <div className="mt-2.5 rounded-xl border border-amber-400/25 bg-amber-500/10 p-2.5 text-xs text-amber-200">
                  {activePose.place_info.best_photo_spot && (
                    <div>
                      <strong>Best Spot:</strong> {activePose.place_info.best_photo_spot}
                    </div>
                  )}
                  {activePose.place_info.location_tips && (
                    <div className="mt-0.5 text-amber-100/90">
                      <strong>Maps Tip:</strong> {activePose.place_info.location_tips}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {/* Bottom Shutter & Action Bar */}
          <div className="absolute bottom-4 left-0 right-0 z-20 flex items-center justify-center gap-4 px-4">
            {/* Optional Maps/Search Quick Button */}
            <button
              type="button"
              onClick={() => setShowMapsModal(true)}
              className={`inline-flex items-center gap-2 rounded-2xl px-4 py-3 text-xs font-semibold backdrop-blur-md border transition ${
                isGroundingActive
                  ? 'bg-cyan-500/25 border-cyan-400 text-cyan-200'
                  : 'bg-black/65 border-white/15 text-gray-200 hover:bg-black/80'
              }`}
            >
              <MapPin className="h-4 w-4 text-cyan-400" />
              <span>{isGroundingActive ? 'Edit Place' : 'Add Place / Maps'}</span>
            </button>

            {/* Primary AI Analyze & Pose Shutter Button */}
            <button
              type="button"
              disabled={isAnalyzing}
              onClick={handleAnalyzeScene}
              className="group relative flex h-16 w-16 items-center justify-center rounded-full bg-gradient-to-tr from-cyan-400 to-teal-300 p-1 shadow-[0_0_25px_rgba(0,229,255,0.5)] hover:scale-105 active:scale-95 transition disabled:opacity-60"
              title="Analyze Scene & Recommend Best Pose"
            >
              <div className="flex h-full w-full items-center justify-center rounded-full border-2 border-black/40 bg-transparent text-black">
                {isAnalyzing ? (
                  <RefreshCw className="h-6 w-6 animate-spin" />
                ) : (
                  <Sparkles className="h-6 w-6" />
                )}
              </div>
            </button>

            {/* Download Snapshot Button */}
            <button
              type="button"
              onClick={handleDownloadSnapshot}
              className="inline-flex items-center gap-2 rounded-2xl bg-black/65 border border-white/15 px-4 py-3 text-xs font-semibold text-gray-200 hover:bg-black/80 backdrop-blur-md transition"
              title="Save Camera Frame"
            >
              <Download className="h-4 w-4 text-amber-300" />
              <span className="hidden sm:inline">Save Photo</span>
            </button>
          </div>
        </div>
      </main>

      {/* Optional Google Maps & Search Grounding Modal */}
      <MapsSearchModal
        isOpen={showMapsModal}
        onClose={() => setShowMapsModal(false)}
        isMapsEnabled={isMapsEnabled}
        isSearchEnabled={isSearchEnabled}
        detectedLocation={detectedLocation}
        customPlaceQuery={customPlaceQuery}
        isFetchingGps={isFetchingGps}
        gpsError={gpsError}
        onToggleMapsGps={handleToggleMapsGps}
        onToggleSearchGrounding={setIsSearchEnabled}
        onUpdatePlaceQuery={setCustomPlaceQuery}
        onApplyAndAnalyze={handleAnalyzeScene}
        onDisableAllAndReset={handleDisableAllLocation}
      />

      {/* Saved Poses Drawer */}
      {showSavedDrawer && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/75 backdrop-blur-sm">
          <div className="flex h-full w-full max-w-md flex-col bg-[#12141F] border-l border-white/15 p-5 text-white">
            <div className="flex items-center justify-between border-b border-white/10 pb-4">
              <div className="flex items-center gap-2.5">
                <Bookmark className="h-5 w-5 text-amber-400" />
                <h2 className="text-lg font-bold">Saved Poses ({savedPoses.length})</h2>
              </div>
              <button
                type="button"
                onClick={() => setShowSavedDrawer(false)}
                className="rounded-full p-2 text-gray-400 hover:bg-white/10 hover:text-white"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="mt-4 flex-1 space-y-3 overflow-y-auto pr-1">
              {savedPoses.length === 0 ? (
                <div className="flex h-64 flex-col items-center justify-center text-center text-gray-400">
                  <Bookmark className="h-10 w-10 mb-2 opacity-40" />
                  <p className="text-sm font-medium">No saved poses yet</p>
                  <p className="text-xs mt-1 max-w-xs">
                    Tap the sparkle shutter button to generate a pose, then click "Save" to bookmark it here.
                  </p>
                </div>
              ) : (
                savedPoses.map((pose) => (
                  <div
                    key={pose.id}
                    className="rounded-2xl border border-white/10 bg-white/[0.04] p-4 hover:border-cyan-400/40 transition"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <span className="rounded bg-cyan-400/15 px-2 py-0.5 text-[10px] font-bold uppercase text-cyan-300">
                          {pose.wireframeStyle}
                        </span>
                        {pose.locationName && (
                          <span className="ml-1.5 inline-flex items-center gap-1 rounded bg-amber-400/15 px-2 py-0.5 text-[10px] font-semibold text-amber-300">
                            <MapPin className="h-2.5 w-2.5" />
                            {pose.locationName}
                          </span>
                        )}
                        <h3 className="mt-1.5 font-bold text-sm">{pose.poseName}</h3>
                      </div>
                      <button
                        type="button"
                        onClick={() => handleDeleteSavedPose(pose.id)}
                        className="text-gray-500 hover:text-red-400 p-1"
                        title="Delete saved pose"
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                    <p className="mt-1.5 text-xs text-gray-300">{pose.photographerTips}</p>
                    <div className="mt-3 flex justify-end">
                      <button
                        type="button"
                        onClick={() => {
                          setActivePose({
                            status: 'success',
                            analysis: {
                              detected_environment: pose.detectedEnvironment,
                              detected_outfit: pose.detectedOutfit
                            },
                            recommended_pose: {
                              pose_name: pose.poseName,
                              wireframe_url: 'local_wireframe',
                              photographer_tips: pose.photographerTips,
                              wireframe_style: pose.wireframeStyle
                            },
                            place_info: pose.locationName
                              ? {
                                  place_name: pose.locationName,
                                  best_photo_spot: pose.bestPhotoSpot
                                }
                              : undefined
                          });
                          setManualWireframe(pose.wireframeStyle);
                          setShowSavedDrawer(false);
                        }}
                        className="inline-flex items-center gap-1.5 rounded-xl bg-cyan-400/20 border border-cyan-400/40 px-3 py-1.5 text-xs font-semibold text-cyan-200 hover:bg-cyan-400 hover:text-black transition"
                      >
                        <Eye className="h-3.5 w-3.5" />
                        Load onto Viewfinder
                      </button>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      )}

      {/* Optional Gemini API Key Modal */}
      {showApiModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/75 backdrop-blur-sm p-4">
          <div className="w-full max-w-md rounded-3xl border border-white/15 bg-[#12141F] p-6 text-white shadow-2xl">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <Key className="h-5 w-5 text-cyan-400" />
                <h2 className="text-base font-bold">Gemini API Key (Vercel / Browser)</h2>
              </div>
              <button
                type="button"
                onClick={() => setShowApiModal(false)}
                className="rounded-full p-1.5 text-gray-400 hover:bg-white/10"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <p className="mt-2 text-xs text-gray-400 leading-relaxed">
              When deployed on <strong>Vercel</strong>, you can set <code className="text-cyan-300">GEMINI_API_KEY</code> or{' '}
              <code className="text-cyan-300">VITE_GEMINI_API_KEY</code> in your Vercel Project Environment Variables.
              Optionally, you can also paste a key below to save it in your browser:
            </p>
            <input
              type="password"
              value={userApiKey}
              onChange={(e) => setUserApiKey(e.target.value)}
              placeholder="AIzaSy..."
              className="mt-3 w-full rounded-xl border border-white/15 bg-black/50 px-3.5 py-2.5 text-sm text-white placeholder-gray-500 focus:border-cyan-400 focus:outline-none"
            />
            <div className="mt-4 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => {
                  setUserApiKey('');
                  localStorage.removeItem(STORAGE_KEY_API_KEY);
                  setShowApiModal(false);
                }}
                className="rounded-xl border border-white/15 px-4 py-2 text-xs font-semibold text-gray-300 hover:bg-white/10"
              >
                Clear
              </button>
              <button
                type="button"
                onClick={() => {
                  localStorage.setItem(STORAGE_KEY_API_KEY, userApiKey.trim());
                  setShowApiModal(false);
                }}
                className="rounded-xl bg-cyan-400 px-4 py-2 text-xs font-bold text-black hover:brightness-110"
              >
                Save Key
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default App;
