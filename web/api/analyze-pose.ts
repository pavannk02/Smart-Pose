import type { VercelRequest, VercelResponse } from '@vercel/node';

interface AnalyzeBody {
  imageBase64?: string;
  mimeType?: string;
  placeQuery?: string;
  detectedLocationName?: string | null;
  coordinates?: { lat: number; lng: number } | null;
  useMapsGrounding?: boolean;
  useSearchGrounding?: boolean;
}

interface GeminiCandidate {
  content?: {
    parts?: Array<{ text?: string }>;
  };
  groundingMetadata?: {
    webSearchQueries?: string[];
    groundingChunks?: Array<{
      web?: { uri?: string; title?: string };
      maps?: { uri?: string; title?: string };
    }>;
  };
}

function getBackupDemoPose(locationDescriptor: string | null) {
  const presets = [
    {
      pose_name: 'Sartorial Seated',
      environment: 'Modern Cafe Corner [Demo Mode]',
      outfit: 'Smart Casual Layering [Demo Mode]',
      tips: 'Sit slightly angled toward the lens. Hold your drink or rest one hand gently near your chin while looking slightly off-camera.',
      style: 'seated',
    },
    {
      pose_name: 'Golden Hour Stride',
      environment: 'Outdoor Urban Walkway [Demo Mode]',
      outfit: 'Contemporary Streetwear [Demo Mode]',
      tips: 'Take slow, natural steps toward the camera. Keep your shoulders relaxed and let your arms swing lightly.',
      style: 'walking',
    },
    {
      pose_name: 'Candid Lookback',
      environment: 'Scenic Architectural Backdrop [Demo Mode]',
      outfit: 'Minimalist Chic [Demo Mode]',
      tips: 'Angle your body 45 degrees away from the camera, then glance back softly over your shoulder toward the lens.',
      style: 'over-the-shoulder',
    },
    {
      pose_name: 'Confident Creator',
      environment: 'Textured Studio Wall [Demo Mode]',
      outfit: 'Tailored Casual Fit [Demo Mode]',
      tips: 'Stand tall with weight shifted onto your back foot, cross your arms lightly, and tilt your chin slightly upward.',
      style: 'crossed arms',
    },
    {
      pose_name: 'Classic Streetwise',
      environment: 'Open Daylight Scene [Demo Mode]',
      outfit: 'Everyday Casual [Demo Mode]',
      tips: 'Rest one hand casually in your pocket, keep your feet shoulder-width apart, and relax your shoulders.',
      style: 'classic',
    },
  ];

  const pick = presets[Math.floor(Math.random() * presets.length)];

  return {
    status: 'success',
    demoMode: true,
    analysis: {
      detected_environment: pick.environment,
      detected_outfit: pick.outfit,
    },
    place_info: locationDescriptor
      ? {
          place_name: locationDescriptor,
          location_tips: `Frame the shot to highlight iconic architectural lines or natural depth around ${locationDescriptor}.`,
          best_photo_spot: `Open vantage point near ${locationDescriptor}`,
        }
      : null,
    recommended_pose: {
      pose_name: pick.pose_name,
      wireframe_url: '',
      photographer_tips: pick.tips,
      wireframe_style: pick.style,
    },
    groundingQueries: locationDescriptor
      ? [`best photo spots at ${locationDescriptor}`, `aesthetic poses for ${locationDescriptor}`]
      : undefined,
    groundingSources: [],
  };
}

function parseJsonFromGemini(rawText: string) {
  let clean = rawText.trim();
  const startIdx = clean.indexOf('{');
  const endIdx = clean.lastIndexOf('}');
  if (startIdx !== -1 && endIdx !== -1 && endIdx > startIdx) {
    clean = clean.substring(startIdx, endIdx + 1);
  } else if (clean.startsWith('```')) {
    clean = clean.replace(/^```json\s*/i, '').replace(/^```\s*/, '').replace(/```\s*$/, '').trim();
  }
  return JSON.parse(clean);
}

async function callGeminiRest(
  apiKey: string,
  payload: Record<string, unknown>
): Promise<{ ok: boolean; status: number; data: unknown; errorText?: string }> {
  const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=${encodeURIComponent(
    apiKey
  )}`;

  const response = await fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const errorText = await response.text();
    return { ok: false, status: response.status, data: null, errorText };
  }

  const data = await response.json();
  return { ok: true, status: response.status, data };
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  if (req.method === 'OPTIONS') {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    return res.status(204).end();
  }

  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Method not allowed. Use POST.' });
  }

  const body: AnalyzeBody =
    typeof req.body === 'string' ? JSON.parse(req.body || '{}') : req.body || {};

  const {
    imageBase64,
    mimeType = 'image/jpeg',
    placeQuery = '',
    detectedLocationName = null,
    coordinates = null,
    useMapsGrounding = false,
    useSearchGrounding = true,
  } = body;

  if (!imageBase64 || typeof imageBase64 !== 'string') {
    return res.status(400).json({ error: 'Missing imageBase64 payload.' });
  }

  const cleanBase64 = imageBase64.includes(',') ? imageBase64.split(',')[1] : imageBase64;
  const hasCustomPlace = Boolean(placeQuery && placeQuery.trim().length > 0);
  const isGroundingRequested = Boolean(useMapsGrounding || hasCustomPlace);

  const locationDescriptor = hasCustomPlace
    ? placeQuery.trim()
    : useMapsGrounding && detectedLocationName
      ? detectedLocationName
      : useMapsGrounding && coordinates
        ? `Coordinates: ${coordinates.lat.toFixed(4)}, ${coordinates.lng.toFixed(4)}`
        : null;

  const apiKey = process.env.GEMINI_API_KEY?.trim();

  if (!apiKey || apiKey === 'MY_GEMINI_API_KEY') {
    return res.status(200).json(getBackupDemoPose(locationDescriptor));
  }

  const locationPromptSection =
    isGroundingRequested && locationDescriptor
      ? `LOCATION CONTEXT: The user is currently at or interested in this specific location: "${locationDescriptor}".
Use Google Maps and Google Search grounding to discover the unique architectural landmarks, famous vantage points, natural lighting, and photographic ambiance of this location.
In your response, populate the "place_info" object with:
- "place_name": The verified place or landmark name.
- "location_tips": Specific advice on how to pose against this landmark or environment (e.g. angle relative to monuments, sun direction, framing).
- "best_photo_spot": The most photogenic spot or backdrop here.`
      : 'Analyze the environment visible in the camera image directly.';

  const prompt = `Analyze this photo of the user's environment, surroundings, and outfit/clothing style.
${locationPromptSection}
Generate a creative posing guide tailored specifically to their environment, outfit, and location.
Respond strictly with a valid JSON object matching this exact structure:

{
  "status": "success",
  "analysis": {
    "detected_environment": "Describe the scene/lighting",
    "detected_outfit": "Describe the clothing/style"
  },
  "place_info": {
    "place_name": "Name of location or landmark if detected/searched, else empty string",
    "location_tips": "Photographic tip tailored to this place",
    "best_photo_spot": "Best backdrop recommendation"
  },
  "recommended_pose": {
    "pose_name": "Catchy Pose Name",
    "wireframe_url": "",
    "photographer_tips": "Actionable, encouraging instructions for how the subject should stand/sit and angle their body.",
    "wireframe_style": "classic"
  }
}

The "wireframe_style" key MUST be exactly one of these strings:
- "seated" (sitting down, resting, holding objects)
- "walking" (moving forward, walking, street active)
- "over-the-shoulder" (look back, back angled, aesthetic portrait)
- "crossed arms" (confident stance, stand bold)
- "classic" (standard standing pose, hands around waist or pockets)`;

  const tools: Array<Record<string, Record<string, never>>> = [];
  if (isGroundingRequested) {
    if (useSearchGrounding) tools.push({ googleSearch: {} });
    tools.push({ googleMaps: {} });
  } else if (useSearchGrounding && locationDescriptor) {
    tools.push({ googleSearch: {} });
  }

  const basePayload: Record<string, unknown> = {
    contents: [
      {
        parts: [
          { text: prompt },
          {
            inlineData: {
              mimeType,
              data: cleanBase64,
            },
          },
        ],
      },
    ],
    generationConfig: {
      temperature: 0.7,
      ...(tools.length === 0 ? { responseMimeType: 'application/json' } : {}),
    },
    ...(tools.length > 0 ? { tools } : {}),
  };

  try {
    let result = await callGeminiRest(apiKey, basePayload);

    // Graceful fallback if googleMaps tool is rejected by project/model permissions -> retry with googleSearch only
    if (!result.ok && tools.some((t) => 'googleMaps' in t)) {
      const searchOnlyTools = useSearchGrounding ? [{ googleSearch: {} }] : [];
      const fallbackPayload: Record<string, unknown> = {
        ...basePayload,
        generationConfig: {
          temperature: 0.7,
          ...(searchOnlyTools.length === 0 ? { responseMimeType: 'application/json' } : {}),
        },
      };
      if (searchOnlyTools.length > 0) {
        fallbackPayload.tools = searchOnlyTools;
      } else {
        delete fallbackPayload.tools;
      }
      result = await callGeminiRest(apiKey, fallbackPayload);
    }

    if (!result.ok) {
      return res.status(502).json({
        error: `Gemini API returned ${result.status}`,
        details: result.errorText,
      });
    }

    const geminiJson = result.data as { candidates?: GeminiCandidate[] };
    const candidate = geminiJson.candidates?.[0];
    const rawText = candidate?.content?.parts?.map((p) => p.text || '').join('\n') || '';

    if (!rawText) {
      return res.status(502).json({ error: 'Gemini returned an empty response.' });
    }

    const parsed = parseJsonFromGemini(rawText);
    const queries = candidate?.groundingMetadata?.webSearchQueries || [];
    const sources =
      candidate?.groundingMetadata?.groundingChunks
        ?.map((chunk) => {
          const item = chunk.maps || chunk.web;
          if (!item) return null;
          return {
            title: item.title || item.uri || 'Source',
            uri: item.uri,
          };
        })
        .filter((item): item is { title: string; uri: string | undefined } => Boolean(item)) || [];

    const placeInfo =
      parsed.place_info && parsed.place_info.place_name
        ? parsed.place_info
        : locationDescriptor
          ? {
              place_name: locationDescriptor,
              location_tips:
                parsed.place_info?.location_tips || `Tailored pose recommendations for ${locationDescriptor}`,
              best_photo_spot: parsed.place_info?.best_photo_spot || null,
            }
          : null;

    return res.status(200).json({
      status: parsed.status || 'success',
      demoMode: false,
      analysis: parsed.analysis,
      recommended_pose: parsed.recommended_pose,
      place_info: placeInfo,
      groundingQueries: queries,
      groundingSources: sources,
    });
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Unexpected server error';
    return res.status(500).json({ error: message });
  }
}
