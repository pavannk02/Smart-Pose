export interface PlaceInfo {
  place_name?: string | null;
  location_tips?: string | null;
  best_photo_spot?: string | null;
}

export interface PoseAnalysis {
  detected_environment: string;
  detected_outfit: string;
}

export interface RecommendedPose {
  pose_name: string;
  wireframe_url?: string;
  photographer_tips: string;
  wireframe_style: 'seated' | 'walking' | 'over-the-shoulder' | 'crossed arms' | 'classic' | string;
}

export interface GroundingSource {
  title: string;
  uri?: string;
}

export interface PoseResponse {
  status: string;
  demoMode?: boolean;
  analysis: PoseAnalysis;
  recommended_pose: RecommendedPose;
  place_info?: PlaceInfo | null;
  groundingQueries?: string[];
  groundingSources?: GroundingSource[];
}

export interface SavedPose {
  id: string;
  poseName: string;
  detectedEnvironment: string;
  detectedOutfit: string;
  photographerTips: string;
  wireframeStyle: string;
  locationName?: string | null;
  savedAt: number;
}

export interface AnalyzeRequestPayload {
  imageBase64: string;
  mimeType?: string;
  placeQuery?: string;
  detectedLocationName?: string | null;
  coordinates?: { lat: number; lng: number } | null;
  useMapsGrounding?: boolean;
  useSearchGrounding?: boolean;
}
