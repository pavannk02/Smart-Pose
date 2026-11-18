# Smart Pose

[Live demo](https://smart-pose.vercel.app)

Smart Pose is an AI-assisted camera director that helps users choose natural poses for photos. It provides a live camera viewfinder, pose wireframes, optional location-aware recommendations, and saved poses.

The repository contains a Vite/React web application, a Vercel serverless Gemini API endpoint, an Android application built with Kotlin and Jetpack Compose, and a separate FastAPI backend.

## Features

- Live WebRTC camera preview with front/back-camera switching where supported
- Interactive SVG pose wireframes
- Five built-in pose styles:
  - Classic standing
  - Walking stride
  - Seated café pose
  - Over-the-shoulder
  - Crossed arms
- Camera mirroring and rule-of-thirds guides
- Photo capture and upload fallback
- Saved pose library using browser `localStorage`
- Optional Google Maps and Google Search grounding
- Gemini-powered scene and outfit analysis
- Android app implementation with Jetpack Compose and CameraX
- Python FastAPI pose-analysis service

## Web application

### Requirements

- Node.js 18 or newer
- npm
- A Gemini API key for live AI analysis
- Camera and location permissions are browser/OS dependent

### Run locally

```bash
npm install
npm run dev
```

Open `http://localhost:3000`, or the local URL printed by Vite.

### Build

```bash
npm run build
npm run preview
```

The production output is written to `dist/`.

### Configuration

Copy the environment example and add a key if server-side Gemini analysis is available:

```bash
cp .env.example .env
```

```dotenv
GEMINI_API_KEY=your_gemini_api_key
```

For Vercel, add `GEMINI_API_KEY` in **Project Settings → Environment Variables**. Do not commit real API keys.

The web API is implemented in `api/analyze.js` and is deployed as a Vercel serverless function. The endpoint accepts `POST` requests containing an optional base64 image and location or grounding options.

Optional location-aware features require browser geolocation permission. Google Maps and Google Search grounding are enabled only when the corresponding options and location context are available.

## Android application

The Android project uses Kotlin, Jetpack Compose, CameraX, Retrofit, Moshi, Room, and CameraX permissions support.

### Requirements

- Android Studio with an SDK that includes API 36
- JDK 11 or compatible with the project configuration
- Android device or emulator with API 24 or newer

### Run

1. Open the repository root in Android Studio.
2. Let Gradle synchronize the project.
3. Create a `.env` file in the repository root if local secrets are needed:

```dotenv
GEMINI_API_KEY=your_gemini_api_key
```

4. Select the `app` module and run it on a device or emulator.

A `release` signing configuration also reads `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD` from the environment. The configured keystore defaults to `my-upload-key.jks` in the repository root.

## FastAPI backend

A separate Python backend is available in `backend/main.py`.

### Run locally

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install fastapi uvicorn pydantic google-generativeai
export GEMINI_API_KEY="your_gemini_api_key"
uvicorn main:app --reload
```

Without `GEMINI_API_KEY`, the backend returns a mock pose response so the API and frontend can still be exercised.

API endpoints:

```text
POST /analyze-pose
GET  /
```

## Deploy to Vercel

This repository includes `vercel.json` and a Vite build configuration.

```bash
npm install
npm run build
npx vercel
```

In the Vercel project settings:

1. Use `Vite` as the framework preset.
2. Set the build command to `npm run build`.
3. Set the output directory to `dist`.
4. Add `GEMINI_API_KEY` to the environment variables.

## Repository layout

```text
api/          Vercel serverless Gemini endpoint
app/          Android Jetpack Compose application
backend/      Separate FastAPI backend
src/          Web application source
web/          Alternative web project directory
```

## Privacy and API keys

- Keep Gemini API keys in environment variables or a secrets manager.
- Never commit real credentials or signing passwords.
- The browser may request camera and location permissions; location features remain optional.
- Saved web poses are stored locally in the browser.

## License

No license is currently specified. Add one before using or redistributing the project if reuse rights are not already established privately.
