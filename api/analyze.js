// Vercel Serverless Function: /api/analyze
// Automatically invoked when deployed on Vercel.
// Reads GEMINI_API_KEY from Vercel Environment Variables (or client header fallback).

export default async function handler(req, res) {
  // Enable CORS so the endpoint works from any domain or local dev
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-Gemini-Api-Key');

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed. Use POST.' });
  }

  try {
    const {
      imageBase64,
      enableMaps = false,
      enableSearch = false,
      locationContext = '',
      customQuery = '',
      apiKey: bodyApiKey
    } = req.body || {};

    const apiKey =
      bodyApiKey ||
      req.headers['x-gemini-api-key'] ||
      process.env.GEMINI_API_KEY ||
      process.env.VITE_GEMINI_API_KEY ||
      '';

    if (!apiKey) {
      return res.status(200).json({
        fallback: true,
        message: 'No GEMINI_API_KEY configured on server; using smart client fallback.'
      });
    }

    const activeLocation = [customQuery, locationContext].filter(Boolean).join(' — ');
    const useGrounding = Boolean((enableMaps || enableSearch) && activeLocation);

    const prompt = useGrounding
      ? `You are an expert fashion & travel photography director with live access to Google Maps and Google Search.
Target Place / Location Context: "${activeLocation}".
Analyze the camera frame (or scene context) and use Google Search and Google Maps knowledge about "${activeLocation}" (iconic backdrops, lighting, architecture, best angles, crowd tips) to recommend the single best photography pose.

Respond ONLY in valid JSON matching this exact structure (no markdown fences):
{
  "status": "success",
  "analysis": {
    "detected_environment": "Specific description of the scene & landmark backdrop",
    "detected_outfit": "Detected outfit style or recommended complementary look"
  },
  "recommended_pose": {
    "pose_name": "Creative location-aware pose title",
    "wireframe_url": "local_wireframe",
    "photographer_tips": "2-3 sentences of actionable posing, camera angle, and framing directions.",
    "wireframe_style": "MUST be one of: classic, walking, seated, over_shoulder, crossed_arms"
  },
  "place_info": {
    "place_name": "Official name of the landmark/place",
    "location_tips": "Site-specific lighting, angle, or timing advice from Maps/Search",
    "best_photo_spot": "Specific spot or vantage point at this location"
  }
}`
      : `You are an expert photography & posing director.
Analyze the user's camera frame carefully for environment, lighting, and outfit.
Recommend the single best natural pose that flatters the subject in this setting.

Respond ONLY in valid JSON matching this exact structure (no markdown fences):
{
  "status": "success",
  "analysis": {
    "detected_environment": "Short descriptive scene environment summary",
    "detected_outfit": "Short descriptive outfit style summary"
  },
  "recommended_pose": {
    "pose_name": "Catchy Pose Title",
    "wireframe_url": "local_wireframe",
    "photographer_tips": "2-3 sentences of clear, actionable posing and camera angle instructions.",
    "wireframe_style": "MUST be one of: classic, walking, seated, over_shoulder, crossed_arms"
  }
}`;

    const parts = [{ text: prompt }];
    if (imageBase64) {
      const cleanBase64 = imageBase64.replace(/^data:image\/\w+;base64,/, '');
      parts.push({
        inlineData: {
          mimeType: 'image/jpeg',
          data: cleanBase64
        }
      });
    }

    const requestBody = {
      contents: [{ parts }],
      generationConfig: {
        temperature: 0.4
      }
    };

    if (useGrounding) {
      const tools = [];
      if (enableSearch) tools.push({ googleSearch: {} });
      if (enableMaps) tools.push({ googleMaps: {} });
      if (tools.length > 0) {
        requestBody.tools = tools;
      }
    }

    const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${encodeURIComponent(apiKey)}`;
    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(requestBody)
    });

    if (!response.ok) {
      const errText = await response.text();
      return res.status(response.status).json({ error: errText });
    }

    const data = await response.json();
    return res.status(200).json(data);
  } catch (err) {
    return res.status(500).json({ error: err?.message || 'Internal Server Error' });
  }
}
