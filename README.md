# Smart Pose — Web App (Vercel Ready)

Smart Pose is an AI-powered Web Camera Director with live SVG pose wireframes and **optional Google Maps & Google Search Grounding**.

## 🚀 Deploy to Vercel in 1 Minute

This repository is pre-configured at the root level with `package.json`, `vite.config.ts`, `vercel.json`, `index.html`, `/src`, and a Vercel Serverless API endpoint (`/api/analyze.js`).

1. **Export or Push to GitHub**:
   - Export this project as a ZIP or push directly to a GitHub repository.
2. **Import into Vercel**:
   - Go to [vercel.com/new](https://vercel.com/new) and import your repository (or run `npx vercel` from the project root).
   - **Framework Preset**: `Vite` (detected automatically via `vercel.json`).
   - **Build Command**: `npm run build`
   - **Output Directory**: `dist`
3. **Environment Variable (Optional)**:
   - Add `GEMINI_API_KEY` (or `VITE_GEMINI_API_KEY`) in your Vercel Project Settings → **Environment Variables**.
   - You can also enter your Gemini API key directly inside the web app's **Key Settings** button in the top bar, or use the built-in location-aware pose presets immediately with no key required.

## 💻 Run Locally in Any Browser

```bash
npm install
npm run dev
```

Then open `http://localhost:3000` in your browser.

## ✨ Web Features

- **Live WebRTC Camera (`getUserMedia`)**: Works on mobile and desktop browsers with Front/Back camera switching, mirror toggle, rule-of-thirds grid, and photo upload fallback.
- **5 Interactive SVG Pose Wireframes**: `Classic Stand`, `Walking Stride`, `Seated Cafe`, `Over Shoulder`, and `Crossed Arms`.
- **Optional Live Google Maps & Place Search**:
  - Disabled by default so you can use normal camera mode without location permissions.
  - When enabled, uses Browser Geolocation (`navigator.geolocation`), reverse geocoding, an interactive embedded Google Maps preview, and Gemini `googleMaps` + `googleSearch` grounding tools to recommend site-specific poses and best photo spots.
- **Saved Poses Library**: Bookmark poses to `localStorage` and reload them onto the live viewfinder anytime.
