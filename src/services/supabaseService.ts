import { SavedPose, WireframeStyle } from '../types/pose';

export interface SupabaseConfig {
  url: string;
  anonKey: string;
}

const STORAGE_KEY_SUPABASE_URL = 'smart_pose_supabase_url';
const STORAGE_KEY_SUPABASE_KEY = 'smart_pose_supabase_anon_key';

export function getSupabaseConfig(): SupabaseConfig | null {
  const env = (import.meta as unknown as { env?: Record<string, string> }).env || {};
  const url = (localStorage.getItem(STORAGE_KEY_SUPABASE_URL) || env.VITE_SUPABASE_URL || '').trim().replace(/\/$/, '');
  const anonKey = (localStorage.getItem(STORAGE_KEY_SUPABASE_KEY) || env.VITE_SUPABASE_ANON_KEY || '').trim();

  if (!url || !anonKey) return null;
  return { url, anonKey };
}

export function saveSupabaseConfig(url: string, anonKey: string): void {
  if (!url.trim() || !anonKey.trim()) {
    localStorage.removeItem(STORAGE_KEY_SUPABASE_URL);
    localStorage.removeItem(STORAGE_KEY_SUPABASE_KEY);
    return;
  }
  localStorage.setItem(STORAGE_KEY_SUPABASE_URL, url.trim().replace(/\/$/, ''));
  localStorage.setItem(STORAGE_KEY_SUPABASE_KEY, anonKey.trim());
}

export async function fetchPosesFromSupabase(config: SupabaseConfig): Promise<SavedPose[] | null> {
  try {
    const res = await fetch(`${config.url}/rest/v1/saved_poses?select=*`, {
      method: 'GET',
      headers: {
        apikey: config.anonKey,
        Authorization: `Bearer ${config.anonKey}`,
        'Content-Type': 'application/json'
      }
    });
    if (!res.ok) return null;
    const rows = await res.json();
    if (!Array.isArray(rows)) return null;

    return rows.map((row: Record<string, unknown>) => ({
      id: String(row.id || `${Date.now()}`),
      poseName: String(row.pose_name || row.poseName || 'Saved Pose'),
      detectedEnvironment: String(row.detected_environment || row.detectedEnvironment || ''),
      detectedOutfit: String(row.detected_outfit || row.detectedOutfit || ''),
      photographerTips: String(row.photographer_tips || row.photographerTips || ''),
      wireframeStyle: (row.wireframe_style || row.wireframeStyle || 'classic') as WireframeStyle,
      locationName: row.location_name ? String(row.location_name) : undefined,
      bestPhotoSpot: row.best_photo_spot ? String(row.best_photo_spot) : undefined,
      timestamp: Number(row.timestamp || Date.now())
    }));
  } catch {
    return null;
  }
}

export async function insertPoseToSupabase(config: SupabaseConfig, pose: SavedPose): Promise<boolean> {
  try {
    const res = await fetch(`${config.url}/rest/v1/saved_poses`, {
      method: 'POST',
      headers: {
        apikey: config.anonKey,
        Authorization: `Bearer ${config.anonKey}`,
        'Content-Type': 'application/json',
        Prefer: 'return=minimal'
      },
      body: JSON.stringify({
        id: pose.id,
        pose_name: pose.poseName,
        detected_environment: pose.detectedEnvironment,
        detected_outfit: pose.detectedOutfit,
        photographer_tips: pose.photographerTips,
        wireframe_style: pose.wireframeStyle,
        location_name: pose.locationName || null,
        best_photo_spot: pose.bestPhotoSpot || null,
        timestamp: pose.timestamp
      })
    });
    return res.ok;
  } catch {
    return false;
  }
}

export async function deletePoseFromSupabase(config: SupabaseConfig, id: string): Promise<boolean> {
  try {
    const res = await fetch(`${config.url}/rest/v1/saved_poses?id=eq.${encodeURIComponent(id)}`, {
      method: 'DELETE',
      headers: {
        apikey: config.anonKey,
        Authorization: `Bearer ${config.anonKey}`,
        'Content-Type': 'application/json'
      }
    });
    return res.ok;
  } catch {
    return false;
  }
}

export const SUPABASE_TABLE_SQL = `create table if not exists public.saved_poses (
  id text primary key,
  pose_name text not null,
  detected_environment text,
  detected_outfit text,
  photographer_tips text,
  wireframe_style text,
  location_name text,
  best_photo_spot text,
  timestamp bigint not null
);

alter table public.saved_poses enable row level security;

create policy "Allow public access to saved_poses"
  on public.saved_poses for all
  using (true)
  with check (true);`;
