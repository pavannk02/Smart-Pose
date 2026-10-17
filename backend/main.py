import os
import json
import base64
from typing import Dict, Any
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import google.generativeai as genai

# Initialize FastAPI app
app = FastAPI(
    title="Smart Pose AI Director Backend",
    description="Analyze scene environment/outfit details and recommend overlays via Gemini API",
    version="1.0.0"
)

# Host can inject their own API key or let the app fallback
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
if GEMINI_API_KEY:
    genai.configure(api_key=GEMINI_API_KEY)

class ImagePayload(BaseModel):
    image_base64: str

# Define the strict platform-agnostic JSON output response contract
POSE_CONTRACT_PROMPT = """
Analyze the user's uploaded camera perspective carefully. Determine:
1. "detectedEnvironment" (e.g., modern coffee shop interior, sunny urban street path, cozy library reading room).
2. "detectedOutfit" (e.g., casual active denim jacket and sneakers, elegant minimal black dress with boots).

Then, suggest one extremely aesthetic, natural pose suited for this specific scene and outfit.
Pick one of the following canonical style overlay wireframes that fits the scene best:
- "seated" (sitting gracefully with structural alignment lines, handy for cafes or benches).
- "walking" (a dynamic candid walking stride outline with swing guides, handy for outdoor streets).
- "over-the-shoulder" (elegant portraits leaning on walls, looking back at the lens).
- "crossed-arms" (confident stance, handy for modern professional headshots).
- "classic-standing" (asymmetric golden ratio resting stance, default fallback).

You MUST respond strictly in clean, valid JSON format matching this EXACT structure with no markdown blocks:
{
  "analysis": {
    "detectedEnvironment": "Short descriptive scene environment summary",
    "detectedOutfit": "Short descriptive outfit style summary"
  },
  "recommendedPose": {
    "poseName": "Catchy Pose Title (e.g. Sartorial Seated)",
    "photographerTips": "Actionable directions (e.g., Hold the coffee cup in your left hand slightly tilted. Let your shoulders tilt, and look slightly past the lens.)",
    "wireframeStyle": "MUST be exactly one of: 'seated', 'walking', 'over-the-shoulder', 'crossed-arms', 'classic-standing'"
  }
}
"""

@app.post("/analyze-pose")
async def analyze_pose(payload: ImagePayload) -> Dict[str, Any]:
    """
    Accepts low-res base64 image data, sends it directly to Gemini Vision,
    and returns a structured director pose JSON response.
    """
    if not GEMINI_API_KEY:
        # If API key is missing, return a polite mocked preview so frontend doesn't crash!
        return {
            "analysis": {
                "detectedEnvironment": "Cozy Modern Coffee Corner (Local Mock)",
                "detectedOutfit": "Smart Casual Blazer & Jeans"
            },
            "recommendedPose": {
                "poseName": "Sartorial Seated",
                "photographerTips": "Sit slightly angled to the lens. Place your left hand on the tabletop holding a coffee cup, and look off-camera to the right for a warm candid vibe.",
                "wireframeStyle": "seated"
            }
        }

    try:
        # Decode base64 frame
        img_data = base64.b64decode(payload.image_base64)
        
        # Initialize the model (using model recommended by user instructions / guidelines)
        # Note: Do not hardcode disallowed models. In FastAPI backend, we use standard gemini-1.5-flash or gemini-2.5-flash as available in standard REST SDK.
        model = genai.GenerativeModel('gemini-1.5-flash-8b') # extremely lightweight & fast
        
        # Construct Gemini API content elements
        contents = [
            POSE_CONTRACT_PROMPT,
            {"mime_type": "image/jpeg", "data": img_data}
        ]
        
        response = model.generate_content(contents)
        
        # Clean text response for safe JSON parsing
        raw_text = response.text.strip()
        if raw_text.startswith("```json"):
            raw_text = raw_text.split("```json")[1].split("```")[0].strip()
        elif raw_text.startswith("```"):
            raw_text = raw_text.split("```")[1].split("```")[0].strip()
            
        json_data = json.loads(raw_text)
        return json_data
        
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Gemini Analysis pipeline failed: {str(e)}")

@app.get("/")
def read_root():
    return {"message": "Smart Pose AI Director API is running on FastAPI!"}
