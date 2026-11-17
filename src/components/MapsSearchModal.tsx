import React, { useState } from 'react';
import {
  MapPin,
  Search,
  Navigation,
  Sparkles,
  X,
  Globe,
  CheckCircle2,
  Compass
} from 'lucide-react';

interface MapsSearchModalProps {
  isOpen: boolean;
  onClose: () => void;
  isMapsEnabled: boolean;
  isSearchEnabled: boolean;
  detectedLocation: string;
  customPlaceQuery: string;
  isFetchingGps: boolean;
  gpsError: string | null;
  onToggleMapsGps: (enabled: boolean) => void;
  onToggleSearchGrounding: (enabled: boolean) => void;
  onUpdatePlaceQuery: (query: string) => void;
  onApplyAndAnalyze: () => void;
  onDisableAllAndReset: () => void;
}

const POPULAR_PLACES = [
  'Central Park, NYC',
  'Eiffel Tower, Paris',
  'Shibuya Crossing, Tokyo',
  'Santorini Caldera, Greece',
  'Artisan Coffee Shop',
  'Golden Hour Sunset Beach',
  'Modern City Rooftop',
  'Botanical Garden Glasshouse'
];

export const MapsSearchModal: React.FC<MapsSearchModalProps> = ({
  isOpen,
  onClose,
  isMapsEnabled,
  isSearchEnabled,
  detectedLocation,
  customPlaceQuery,
  isFetchingGps,
  gpsError,
  onToggleMapsGps,
  onToggleSearchGrounding,
  onUpdatePlaceQuery,
  onApplyAndAnalyze,
  onDisableAllAndReset
}) => {
  const [localInput, setLocalInput] = useState(customPlaceQuery);

  if (!isOpen) return null;

  const activeMapTarget = localInput.trim() || detectedLocation.trim();

  const handleApply = () => {
    onUpdatePlaceQuery(localInput.trim());
    if (localInput.trim() && !isSearchEnabled) {
      onToggleSearchGrounding(true);
    }
    onApplyAndAnalyze();
    onClose();
  };

  const handleSelectChip = (place: string) => {
    setLocalInput(place);
    onUpdatePlaceQuery(place);
    if (!isSearchEnabled) onToggleSearchGrounding(true);
    if (!isMapsEnabled) onToggleMapsGps(false);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-black/75 backdrop-blur-sm p-0 sm:p-4">
      <div className="w-full max-w-lg rounded-t-3xl sm:rounded-3xl border border-white/15 bg-[#12141F] p-5 sm:p-6 text-white shadow-2xl max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-cyan-500/15 border border-cyan-400/30 text-cyan-300">
              <Compass className="h-6 w-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="text-lg font-bold tracking-tight">Google Maps & Place Search</h2>
                <span className="rounded-full bg-amber-400/15 px-2.5 py-0.5 text-[11px] font-semibold text-amber-300 border border-amber-400/30">
                  Optional
                </span>
              </div>
              <p className="text-xs text-gray-400 mt-0.5">
                Enable only if you want location-specific poses & backdrops. Otherwise use normal camera mode.
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="rounded-full p-2 text-gray-400 hover:bg-white/10 hover:text-white transition"
            aria-label="Close modal"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* 1. Live GPS Maps Toggle */}
        <div className="mt-5 rounded-2xl border border-white/10 bg-white/[0.04] p-4">
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-500/15 text-emerald-400">
                <Navigation className="h-5 w-5" />
              </div>
              <div>
                <div className="text-sm font-semibold">Live Browser GPS Location</div>
                <div className="text-xs text-gray-400">
                  {isFetchingGps
                    ? 'Detecting coordinates via browser Geolocation...'
                    : detectedLocation
                    ? `Active: ${detectedLocation}`
                    : 'Off by default — requests browser permission only when toggled'}
                </div>
              </div>
            </div>
            <button
              type="button"
              onClick={() => onToggleMapsGps(!isMapsEnabled)}
              className={`relative inline-flex h-7 w-12 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ${
                isMapsEnabled ? 'bg-cyan-400' : 'bg-gray-700'
              }`}
            >
              <span
                className={`inline-block h-6 w-6 transform rounded-full bg-black shadow transition duration-200 ${
                  isMapsEnabled ? 'translate-x-5' : 'translate-x-0'
                }`}
              />
            </button>
          </div>
          {gpsError && (
            <p className="mt-2 text-xs text-amber-300 bg-amber-500/10 border border-amber-500/20 rounded-lg px-3 py-1.5">
              {gpsError} — You can still search any place manually below!
            </p>
          )}
        </div>

        {/* 2. Search Any Place / Landmark */}
        <div className="mt-4 rounded-2xl border border-white/10 bg-white/[0.04] p-4">
          <label className="block text-xs font-semibold uppercase tracking-wider text-cyan-300 mb-2">
            Search Any Place, Landmark, or Vibe
          </label>
          <div className="relative flex items-center">
            <Search className="absolute left-3.5 h-4 w-4 text-gray-400" />
            <input
              type="text"
              value={localInput}
              onChange={(e) => setLocalInput(e.target.value)}
              placeholder="e.g., Eiffel Tower, Central Park, Cozy Cafe..."
              className="w-full rounded-xl border border-white/15 bg-black/50 pl-10 pr-9 py-2.5 text-sm text-white placeholder-gray-500 focus:border-cyan-400 focus:outline-none"
            />
            {localInput && (
              <button
                type="button"
                onClick={() => {
                  setLocalInput('');
                  onUpdatePlaceQuery('');
                }}
                className="absolute right-2.5 text-gray-400 hover:text-white"
              >
                <X className="h-4 w-4" />
              </button>
            )}
          </div>

          {/* Quick Landmark Chips */}
          <div className="mt-3 flex flex-wrap gap-1.5">
            {POPULAR_PLACES.map((place) => {
              const selected = localInput.toLowerCase() === place.toLowerCase();
              return (
                <button
                  key={place}
                  type="button"
                  onClick={() => handleSelectChip(place)}
                  className={`rounded-full px-3 py-1 text-xs font-medium transition ${
                    selected
                      ? 'bg-cyan-400 text-black font-semibold'
                      : 'bg-white/10 text-gray-300 hover:bg-white/20'
                  }`}
                >
                  {place}
                </button>
              );
            })}
          </div>
        </div>

        {/* 3. Live Interactive Google Maps Preview (when a location or search query is present) */}
        {activeMapTarget && (
          <div className="mt-4 overflow-hidden rounded-2xl border border-cyan-400/30 bg-black/40">
            <div className="flex items-center justify-between px-3.5 py-2 bg-cyan-500/10 border-b border-white/10">
              <div className="flex items-center gap-1.5 text-xs font-medium text-cyan-300 truncate">
                <MapPin className="h-3.5 w-3.5 shrink-0" />
                <span className="truncate">Live Google Maps Preview: {activeMapTarget}</span>
              </div>
              <a
                href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(activeMapTarget)}`}
                target="_blank"
                rel="noopener noreferrer"
                className="text-[11px] font-semibold text-amber-300 hover:underline shrink-0"
              >
                Open Maps ↗
              </a>
            </div>
            <iframe
              title="Live Google Maps Place View"
              src={`https://www.google.com/maps?q=${encodeURIComponent(activeMapTarget)}&output=embed`}
              className="h-44 w-full border-0"
              loading="lazy"
              referrerPolicy="no-referrer-when-downgrade"
            />
          </div>
        )}

        {/* 4. Google Search Grounding Toggle */}
        <div className="mt-4 flex items-center justify-between rounded-2xl border border-white/10 bg-white/[0.04] px-4 py-3">
          <div className="flex items-center gap-3">
            <Globe className="h-5 w-5 text-amber-400" />
            <div>
              <div className="text-sm font-semibold">Google Search Grounding</div>
              <div className="text-xs text-gray-400">
                Fetch best angles, golden hour tips & photo spots for the selected place
              </div>
            </div>
          </div>
          <button
            type="button"
            onClick={() => onToggleSearchGrounding(!isSearchEnabled)}
            className={`relative inline-flex h-7 w-12 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ${
              isSearchEnabled ? 'bg-amber-400' : 'bg-gray-700'
            }`}
          >
            <span
              className={`inline-block h-6 w-6 transform rounded-full bg-black shadow transition duration-200 ${
                isSearchEnabled ? 'translate-x-5' : 'translate-x-0'
              }`}
            />
          </button>
        </div>

        {/* Action Buttons */}
        <div className="mt-5 flex flex-col sm:flex-row gap-2.5">
          <button
            type="button"
            onClick={() => {
              setLocalInput('');
              onDisableAllAndReset();
              onClose();
            }}
            className="flex-1 rounded-xl border border-white/15 bg-white/5 px-4 py-3 text-xs font-semibold text-gray-300 hover:bg-white/10 transition"
          >
            Use Normal Mode (No Maps)
          </button>
          <button
            type="button"
            onClick={handleApply}
            className="flex-1 inline-flex items-center justify-center gap-2 rounded-xl bg-gradient-to-r from-cyan-400 to-teal-300 px-5 py-3 text-xs font-bold text-black shadow-lg shadow-cyan-500/20 hover:brightness-110 transition"
          >
            <Sparkles className="h-4 w-4" />
            <span>Apply & Recommend Pose</span>
          </button>
        </div>
      </div>
    </div>
  );
};
