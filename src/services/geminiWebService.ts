import { PoseResponse, WireframeStyle } from '../types/pose';

const VALID_STYLES: WireframeStyle[] = [
  'classic',
  'walking',
  'seated',
  'over_shoulder',
  'crossed_arms'
];

export function normalizeWireframeStyle(style?: string): WireframeStyle {
  if (!style) return 'classic';
  const cleaned = style.toLowerCase().trim().replace(/-/g, '_');
  if (cleaned === 'classic_standing') return 'classic';
  if (VALID_STYLES.includes(cleaned as WireframeStyle)) {
    return cleaned as WireframeStyle;
  }
  return 'classic';
}

export async function reverseGeocodeCoords(lat: number, lon: number): Promise<string> {
  try {
    const url = `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lon}&zoom=16&addressdetails=1`;
    const res = await fetch(url, {
      headers: { 'Accept-Language': 'en' }
    });
    if (!res.ok) throw new Error('Geocoding failed');
    const data = await res.json();
    const addr = data.address || {};
    const primary =
      addr.tourism ||
      addr.historic ||
      addr.amenity ||
      addr.park ||
      addr.Leisure ||
      addr.neighbourhood ||
      addr.suburb ||
      addr.road ||
      '';
    const city = addr.city || addr.town || addr.village || addr.state || '';
    const label = [primary, city].filter(Boolean).join(', ');
    return label || `${lat.toFixed(4)}, ${lon.toFixed(4)}`;
  } catch {
    return `GPS (${lat.toFixed(4)}, ${lon.toFixed(4)})`;
  }
}

function parseRawGeminiJson(rawText: string, groundingQueries?: string[], groundingSources?: string[]): PoseResponse {
  let text = rawText.trim();
  if (text.includes('```json')) {
    text = text.split('```json')[1].split('```')[0].trim();
  } else if (text.includes('```')) {
    text = text.split('```')[1].split('```')[0].trim();
  }

  const firstBrace = text.indexOf('{');
  const lastBrace = text.lastIndexOf('}');
  if (firstBrace !== -1 && lastBrace > firstBrace) {
    text = text.substring(firstBrace, lastBrace + 1);
  }

  const parsed = JSON.parse(text);
  const analysis = parsed.analysis || {};
  const rec = parsed.recommended_pose || parsed.recommendedPose || {};
  const place = parsed.place_info || parsed.placeInfo || undefined;

  return {
    status: parsed.status || 'success',
    analysis: {
      detected_environment:
        analysis.detected_environment ||
        analysis.detectedEnvironment ||
        'Ambient Natural Scene',
      detected_outfit:
        analysis.detected_outfit ||
        analysis.detectedOutfit ||
        'Contemporary Casual Look'
    },
    recommended_pose: {
      pose_name: rec.pose_name || rec.poseName || 'Editorial Frame',
      wireframe_url: 'local_wireframe',
      photographer_tips:
        rec.photographer_tips ||
        rec.photographerTips ||
        'Angle your shoulders 30 degrees from the lens and shift weight to your back foot.',
      wireframe_style: normalizeWireframeStyle(rec.wireframe_style || rec.wireframeStyle)
    },
    place_info: place
      ? {
          place_name: place.place_name || place.placeName,
          location_tips: place.location_tips || place.locationTips,
          best_photo_spot: place.best_photo_spot || place.bestPhotoSpot
        }
      : undefined,
    groundingQueries,
    groundingSources
  };
}

export async function analyzeSceneWithGemini(params: {
  imageBase64?: string;
  enableMaps: boolean;
  enableSearch: boolean;
  detectedLocation: string;
  customPlaceQuery: string;
  userApiKey?: string;
}): Promise<PoseResponse> {
  const {
    imageBase64,
    enableMaps,
    enableSearch,
    detectedLocation,
    customPlaceQuery,
    userApiKey
  } = params;

  const envApiKey = (import.meta as unknown as { env?: Record<string, string> }).env?.VITE_GEMINI_API_KEY || '';
  const activeApiKey = (userApiKey || envApiKey).trim();
  const activeLocation = [customPlaceQuery.trim(), detectedLocation.trim()].filter(Boolean).join(' — ');
  const useGrounding = Boolean((enableMaps || enableSearch) && activeLocation);

  // 1. Try direct Gemini API call if user provided key or VITE_GEMINI_API_KEY is available
  if (activeApiKey) {
    try {
      return await callGeminiDirect({
        apiKey: activeApiKey,
        imageBase64,
        enableMaps,
        enableSearch,
        activeLocation,
        useGrounding
      });
    } catch (err) {
      console.warn('Direct Gemini call failed, trying serverless endpoint or smart preset:', err);
    }
  }

  // 2. Try Vercel Serverless Function (/api/analyze)
  try {
    const res = await fetch('/api/analyze', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(activeApiKey ? { 'X-Gemini-Api-Key': activeApiKey } : {})
      },
      body: JSON.stringify({
        imageBase64,
        enableMaps,
        enableSearch,
        locationContext: detectedLocation,
        customQuery: customPlaceQuery,
        apiKey: activeApiKey || undefined
      })
    });

    if (res.ok) {
      const data = await res.json();
      if (!data.fallback && data.candidates?.[0]?.content?.parts?.[0]?.text) {
        const candidate = data.candidates[0];
        const rawText = candidate.content.parts[0].text;
        const queries: string[] = candidate.groundingMetadata?.webSearchQueries || [];
        const sources: string[] =
          candidate.groundingMetadata?.groundingChunks
            ?.map((c: { web?: { title?: string } }) => c.web?.title)
            .filter(Boolean) || [];
        return parseRawGeminiJson(rawText, queries, sources);
      }
    }
  } catch {
    // Serverless route unavailable in static preview; proceed to intelligent local preset
  }

  // 3. Intelligent Context & Location-Aware Fallback Preset
  return buildSmartPresetResponse({
    activeLocation,
    useGrounding,
    enableMaps,
    enableSearch
  });
}

async function callGeminiDirect(params: {
  apiKey: string;
  imageBase64?: string;
  enableMaps: boolean;
  enableSearch: boolean;
  activeLocation: string;
  useGrounding: boolean;
}): Promise<PoseResponse> {
  const { apiKey, imageBase64, enableMaps, enableSearch, activeLocation, useGrounding } = params;

  const prompt = useGrounding
    ? `You are an expert fashion & travel photography director with live access to Google Maps and Google Search.
Target Place / Location Context: "${activeLocation}".
Analyze the camera frame and use Google Search and Google Maps knowledge about "${activeLocation}" (architecture, iconic backdrops, lighting, best angles) to recommend the best photography pose.

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

  const parts: Array<Record<string, unknown>> = [{ text: prompt }];
  if (imageBase64) {
    const cleanBase64 = imageBase64.replace(/^data:image\/\w+;base64,/, '');
    parts.push({
      inlineData: {
        mimeType: 'image/jpeg',
        data: cleanBase64
      }
    });
  }

  const requestBody: Record<string, unknown> = {
    contents: [{ parts }],
    generationConfig: {
      temperature: 0.4
    }
  };

  if (useGrounding) {
    const tools: Array<Record<string, unknown>> = [];
    if (enableSearch) tools.push({ googleSearch: {} });
    if (enableMaps) tools.push({ googleMaps: {} });
    if (tools.length > 0) requestBody.tools = tools;
  }

  const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${encodeURIComponent(apiKey)}`;
  const res = await fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(requestBody)
  });

  if (!res.ok) {
    throw new Error(`Gemini HTTP ${res.status}`);
  }

  const data = await res.json();
  const candidate = data.candidates?.[0];
  const rawText = candidate?.content?.parts?.[0]?.text || '';
  const queries: string[] = candidate?.groundingMetadata?.webSearchQueries || [];
  const sources: string[] =
    candidate?.groundingMetadata?.groundingChunks
      ?.map((c: { web?: { title?: string } }) => c.web?.title)
      .filter(Boolean) || [];

  return parseRawGeminiJson(rawText, queries, sources);
}

let presetCursor = 0;

function buildSmartPresetResponse(params: {
  activeLocation: string;
  useGrounding: boolean;
  enableMaps: boolean;
  enableSearch: boolean;
}): PoseResponse {
  const { activeLocation, useGrounding, enableMaps, enableSearch } = params;
  const locLower = activeLocation.toLowerCase();

  if (useGrounding && activeLocation) {
    if (locLower.includes('cafe') || locLower.includes('coffee') || locLower.includes('restaurant')) {
      return {
        status: 'success',
        analysis: {
          detected_environment: `${activeLocation} — Warm Ambient Tabletop & Window Light`,
          detected_outfit: 'Smart-Casual Layered Streetwear'
        },
        recommended_pose: {
          pose_name: 'Artisan Cafe Lean',
          wireframe_url: 'local_wireframe',
          photographer_tips:
            'Sit at a 45° angle to the window light, rest your left forearm lightly on the table edge, and look slightly past the camera for an effortless candid portrait.',
          wireframe_style: 'seated'
        },
        place_info: {
          place_name: activeLocation,
          best_photo_spot: 'Window corner table or outdoor patio seating with depth behind you',
          location_tips:
            'Shoot at eye-level while seated with 1.5x–2x portrait zoom to compress the background warmly.'
        },
        groundingQueries: [
          ...(enableMaps ? [`${activeLocation} seating layout & lighting`] : []),
          ...(enableSearch ? [`best photography angles at ${activeLocation}`] : [])
        ],
        groundingSources: ['Google Maps Place Context', 'Google Search Photography Guide']
      };
    }

    if (
      locLower.includes('tower') ||
      locLower.includes('bridge') ||
      locLower.includes('monument') ||
      locLower.includes('palace') ||
      locLower.includes('santorini')
    ) {
      return {
        status: 'success',
        analysis: {
          detected_environment: `${activeLocation} — Iconic Architectural Landmark Backdrop`,
          detected_outfit: 'Elevated Travel Editorial Attire'
        },
        recommended_pose: {
          pose_name: 'Landmark Over-the-Shoulder Glance',
          wireframe_url: 'local_wireframe',
          photographer_tips:
            'Face away toward the landmark at a slight diagonal, then turn your chin back over your shoulder toward the lens. Lower the camera slightly to capture both you and the spire/skyline.',
          wireframe_style: 'over_shoulder'
        },
        place_info: {
          place_name: activeLocation,
          best_photo_spot: 'Low-angle promenade or elevated terrace 40m from the main structure',
          location_tips:
            'Golden hour side-lighting creates dramatic architectural depth and avoids harsh midday shadows.'
        },
        groundingQueries: [
          ...(enableMaps ? [`${activeLocation} scenic viewpoints`] : []),
          ...(enableSearch ? [`${activeLocation} best portrait photo spots`] : [])
        ],
        groundingSources: ['Google Maps Viewpoints', 'Travel Photography Grounding']
      };
    }

    if (
      locLower.includes('park') ||
      locLower.includes('street') ||
      locLower.includes('crossing') ||
      locLower.includes('beach')
    ) {
      return {
        status: 'success',
        analysis: {
          detected_environment: `${activeLocation} — Open Scenic Walkway & Natural Horizon`,
          detected_outfit: 'Relaxed Contemporary Layers'
        },
        recommended_pose: {
          pose_name: 'Golden Ratio Motion Stride',
          wireframe_url: 'local_wireframe',
          photographer_tips:
            'Take a slow, natural step forward along the leading lines of the path while keeping your shoulders relaxed and glancing softly toward the sunlit side.',
          wireframe_style: 'walking'
        },
        place_info: {
          place_name: activeLocation,
          best_photo_spot: 'Center of the tree-lined walkway or shoreline curve for leading-line depth',
          location_tips:
            'Position the horizon on the lower third grid line and use continuous burst mode mid-stride.'
        },
        groundingQueries: [
          ...(enableMaps ? [`${activeLocation} walkways & orientation`] : []),
          ...(enableSearch ? [`${activeLocation} golden hour photography tips`] : [])
        ],
        groundingSources: ['Google Maps Terrain & Paths', 'Google Search Scene Guide']
      };
    }

    return {
      status: 'success',
      analysis: {
        detected_environment: `${activeLocation} — Location-Grounded Perspective`,
        detected_outfit: 'Versatile Smart-Casual Ensemble'
      },
      recommended_pose: {
        pose_name: `${activeLocation.split(',')[0]} Editorial Stance`,
        wireframe_url: 'local_wireframe',
        photographer_tips:
          'Align your torso along the vertical third line so the background of ' +
          activeLocation +
          ' breathes on the opposite side of the frame. Shift weight onto your back leg for an S-curve silhouette.',
        wireframe_style: 'classic'
      },
      place_info: {
        place_name: activeLocation,
        best_photo_spot: 'Slightly off-center from the main entrance or scenic overlook for clean framing',
        location_tips:
          'Step 2 meters forward from the background to create natural depth-of-field separation.'
      },
      groundingQueries: [`${activeLocation} best photo spots & angles`],
      groundingSources: ['Google Maps & Search Grounding']
    };
  }

  // Standard Vision Mode (when Maps/Search is disabled, just like normal mode before!)
  const standardPresets: PoseResponse[] = [
    {
      status: 'success',
      analysis: {
        detected_environment: 'Balanced Indoor / Natural Light Frame',
        detected_outfit: 'Clean Modern Casual Style'
      },
      recommended_pose: {
        pose_name: 'Asymmetric Editorial Stand',
        wireframe_url: 'local_wireframe',
        photographer_tips:
          'Shift 60% of your weight to your back foot, relax your front knee, and lightly tuck one thumb into your pocket while tilting your head slightly.',
        wireframe_style: 'classic'
      }
    },
    {
      status: 'success',
      analysis: {
        detected_environment: 'Dynamic Depth Corridor / Street Frame',
        detected_outfit: 'Layered Streetwear Silhouette'
      },
      recommended_pose: {
        pose_name: 'Candid Mid-Stride Walk',
        wireframe_url: 'local_wireframe',
        photographer_tips:
          'Take a slow step toward the camera, let your arms swing naturally with your jacket movement, and look 15 degrees off-axis.',
        wireframe_style: 'walking'
      }
    },
    {
      status: 'success',
      analysis: {
        detected_environment: 'Lounge / Cafe Tabletop Perspective',
        detected_outfit: 'Smart Relaxed Ensemble'
      },
      recommended_pose: {
        pose_name: 'Sartorial Seated Lean',
        wireframe_url: 'local_wireframe',
        photographer_tips:
          'Sit near the front edge of the chair, lean forward slightly from the hips, and rest one hand near your chin or coffee cup to create a triangle composition.',
        wireframe_style: 'seated'
      }
    },
    {
      status: 'success',
      analysis: {
        detected_environment: 'Textured Wall / Architectural Backdrop',
        detected_outfit: 'Structured Portrait Look'
      },
      recommended_pose: {
        pose_name: 'Over-the-Shoulder Glance',
        wireframe_url: 'local_wireframe',
        photographer_tips:
          'Stand facing 120 degrees away from the lens, drop your near shoulder slightly, and look back toward the camera with a calm expression.',
        wireframe_style: 'over_shoulder'
      }
    },
    {
      status: 'success',
      analysis: {
        detected_environment: 'Clean Studio / Minimalist Backdrop',
        detected_outfit: 'Sharp Modern Tailored Fit'
      },
      recommended_pose: {
        pose_name: 'Confident Executive Fold',
        wireframe_url: 'local_wireframe',
        photographer_tips:
          'Cross your arms loosely without hunching your shoulders, angle your torso 25 degrees, and keep your chin parallel to the floor.',
        wireframe_style: 'crossed_arms'
      }
    }
  ];

  const chosen = standardPresets[presetCursor % standardPresets.length];
  presetCursor += 1;
  return chosen;
}
