export type WireframeStyle =
  | 'classic'
  | 'walking'
  | 'seated'
  | 'over_shoulder'
  | 'crossed_arms';

export interface PoseAnalysis {
  detected_environment: string;
  detected_outfit: string;
}

export interface RecommendedPose {
  pose_name: string;
  wireframe_url: string;
  photographer_tips: string;
  wireframe_style: WireframeStyle;
}

export interface PlaceInfo {
  place_name?: string;
  location_tips?: string;
  best_photo_spot?: string;
}

export interface PoseResponse {
  status: string;
  analysis: PoseAnalysis;
  recommended_pose: RecommendedPose;
  place_info?: PlaceInfo;
  groundingQueries?: string[];
  groundingSources?: string[];
}

export interface SavedPose {
  id: string;
  poseName: string;
  detectedEnvironment: string;
  detectedOutfit: string;
  photographerTips: string;
  wireframeStyle: WireframeStyle;
  locationName?: string;
  bestPhotoSpot?: string;
  timestamp: number;
}
