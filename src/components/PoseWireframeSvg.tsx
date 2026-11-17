import React from 'react';
import { WireframeStyle } from '../types/pose';

interface PoseWireframeSvgProps {
  style: WireframeStyle;
  showGrid?: boolean;
  mirrored?: boolean;
  opacity?: number;
}

export const PoseWireframeSvg: React.FC<PoseWireframeSvgProps> = ({
  style,
  showGrid = true,
  mirrored = false,
  opacity = 0.92
}) => {
  return (
    <div
      className="pointer-events-none absolute inset-0 flex items-center justify-center overflow-hidden select-none"
      style={{
        opacity,
        transform: mirrored ? 'scaleX(-1)' : undefined
      }}
    >
      {/* Optional Rule-of-Thirds & Golden Horizon Grid */}
      {showGrid && (
        <svg className="absolute inset-0 h-full w-full" viewBox="0 0 100 100" preserveAspectRatio="none">
          <line x1="33.33" y1="0" x2="33.33" y2="100" stroke="rgba(255,255,255,0.14)" strokeWidth="0.2" strokeDasharray="1.5,1.5" />
          <line x1="66.66" y1="0" x2="66.66" y2="100" stroke="rgba(255,255,255,0.14)" strokeWidth="0.2" strokeDasharray="1.5,1.5" />
          <line x1="0" y1="33.33" x2="100" y2="33.33" stroke="rgba(255,255,255,0.14)" strokeWidth="0.2" strokeDasharray="1.5,1.5" />
          <line x1="0" y1="66.66" x2="100" y2="66.66" stroke="rgba(255,255,255,0.14)" strokeWidth="0.2" strokeDasharray="1.5,1.5" />
          {/* Center Framing Crosshair */}
          <line x1="48" y1="50" x2="52" y2="50" stroke="rgba(0,229,255,0.35)" strokeWidth="0.25" />
          <line x1="50" y1="48" x2="50" y2="52" stroke="rgba(0,229,255,0.35)" strokeWidth="0.25" />
        </svg>
      )}

      {/* Main Pose Vector Wireframe */}
      <svg
        className="h-[78%] w-[78%] max-w-[440px] drop-shadow-[0_0_12px_rgba(0,229,255,0.65)] transition-all duration-500"
        viewBox="0 0 300 500"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <defs>
          <linearGradient id="neonStroke" x1="0" y1="0" x2="300" y2="500" gradientUnits="userSpaceOnUse">
            <stop offset="0%" stopColor="#00E5FF" />
            <stop offset="55%" stopColor="#64FFDA" />
            <stop offset="100%" stopColor="#FFAB00" />
          </linearGradient>
        </defs>

        {/* Top Head Alignment Guide Box */}
        <rect
          x="105"
          y="38"
          width="90"
          height="95"
          rx="14"
          stroke="rgba(0,229,255,0.32)"
          strokeWidth="1.5"
          strokeDasharray="6 6"
        />

        {style === 'classic' && (
          <g stroke="url(#neonStroke)" strokeWidth="4.5" strokeLinecap="round" strokeLinejoin="round">
            {/* Head */}
            <circle cx="150" cy="84" r="26" fill="rgba(0,229,255,0.08)" />
            {/* Eye line tilt */}
            <line x1="134" y1="80" x2="166" y2="85" stroke="#FFAB00" strokeWidth="2.5" strokeDasharray="4 4" />
            {/* Neck & Spine S-Curve */}
            <path d="M150 110 C150 132, 145 180, 148 245" />
            {/* Shoulders (angled) */}
            <line x1="104" y1="142" x2="196" y2="152" />
            {/* Left Arm (relaxed down) */}
            <path d="M104 142 L92 212 L106 270" />
            {/* Right Arm (hand on hip / pocket) */}
            <path d="M196 152 L218 202 L182 246" />
            {/* Hips */}
            <line x1="120" y1="245" x2="178" y2="250" />
            {/* Left Leg (weight bearing) */}
            <path d="M124 246 L128 355 L126 456" />
            {/* Right Leg (relaxed bent knee) */}
            <path d="M174 250 L192 352 L176 452" />
            {/* Joint Nodes */}
            {[
              [104, 142],
              [196, 152],
              [92, 212],
              [218, 202],
              [182, 246],
              [124, 246],
              [174, 250],
              [128, 355],
              [192, 352]
            ].map(([cx, cy], idx) => (
              <circle key={idx} cx={cx} cy={cy} r="5.5" fill="#FFAB00" stroke="#0B0C10" strokeWidth="2" />
            ))}
          </g>
        )}

        {style === 'walking' && (
          <g stroke="url(#neonStroke)" strokeWidth="4.5" strokeLinecap="round" strokeLinejoin="round">
            {/* Head */}
            <circle cx="154" cy="82" r="25" fill="rgba(0,229,255,0.08)" />
            {/* Gaze direction arrow */}
            <path d="M168 78 L198 72" stroke="#FFAB00" strokeWidth="2.5" strokeDasharray="4 4" />
            {/* Torso with slight forward motion */}
            <path d="M152 108 L148 242" />
            {/* Shoulders */}
            <line x1="108" y1="144" x2="194" y2="148" />
            {/* Left Arm swinging forward */}
            <path d="M108 144 L86 200 L114 248" />
            {/* Right Arm swinging back */}
            <path d="M194 148 L216 206 L204 262" />
            {/* Hips */}
            <line x1="122" y1="242" x2="174" y2="244" />
            {/* Leading Stride Leg */}
            <path d="M126 242 L106 350 L96 456" />
            {/* Trailing Stride Leg */}
            <path d="M170 244 L188 352 L214 446" />
            {/* Motion Arc */}
            <path d="M75 462 Q150 478 228 456" stroke="#FFAB00" strokeWidth="2.5" strokeDasharray="6 6" />
            {[
              [108, 144],
              [194, 148],
              [86, 200],
              [216, 206],
              [126, 242],
              [170, 244],
              [106, 350],
              [188, 352]
            ].map(([cx, cy], idx) => (
              <circle key={idx} cx={cx} cy={cy} r="5.5" fill="#FFAB00" stroke="#0B0C10" strokeWidth="2" />
            ))}
          </g>
        )}

        {style === 'seated' && (
          <g stroke="url(#neonStroke)" strokeWidth="4.5" strokeLinecap="round" strokeLinejoin="round">
            {/* Head */}
            <circle cx="152" cy="96" r="26" fill="rgba(0,229,255,0.08)" />
            {/* Neck & Seated Torso Lean */}
            <path d="M152 122 L144 262" />
            {/* Shoulders */}
            <line x1="102" y1="156" x2="196" y2="160" />
            {/* Left Arm resting on cafe table / knee */}
            <path d="M102 156 L88 224 L132 230" />
            {/* Right Arm raised near chin/cup */}
            <path d="M196 160 L212 222 L172 184" />
            {/* Tabletop / Bench Guide Line */}
            <line x1="56" y1="236" x2="244" y2="236" stroke="#FFAB00" strokeWidth="2.2" strokeDasharray="8 6" />
            {/* Seated Hips & Knees */}
            <line x1="116" y1="262" x2="174" y2="264" />
            <path d="M120 262 L98 338 L108 446" />
            <path d="M170 264 L196 340 L176 446" />
            {[
              [102, 156],
              [196, 160],
              [88, 224],
              [212, 222],
              [120, 262],
              [170, 264],
              [98, 338],
              [196, 340]
            ].map(([cx, cy], idx) => (
              <circle key={idx} cx={cx} cy={cy} r="5.5" fill="#FFAB00" stroke="#0B0C10" strokeWidth="2" />
            ))}
          </g>
        )}

        {style === 'over_shoulder' && (
          <g stroke="url(#neonStroke)" strokeWidth="4.5" strokeLinecap="round" strokeLinejoin="round">
            {/* Head looking back over shoulder */}
            <circle cx="146" cy="84" r="26" fill="rgba(0,229,255,0.08)" />
            <path d="M126 82 L156 80" stroke="#FFAB00" strokeWidth="3" />
            {/* Angled Back / Torso */}
            <path d="M148 110 C156 155, 160 200, 152 248" />
            {/* Steeply angled over-the-shoulder line */}
            <line x1="114" y1="138" x2="192" y2="158" />
            {/* Near Arm */}
            <path d="M114 138 L96 206 L118 264" />
            {/* Far Arm */}
            <path d="M192 158 L206 218 L188 268" />
            {/* Hips */}
            <line x1="126" y1="248" x2="176" y2="252" />
            {/* Legs */}
            <path d="M130 248 L122 356 L118 456" />
            <path d="M172 252 L180 356 L192 452" />
            {[
              [114, 138],
              [192, 158],
              [96, 206],
              [130, 248],
              [172, 252],
              [122, 356],
              [180, 356]
            ].map(([cx, cy], idx) => (
              <circle key={idx} cx={cx} cy={cy} r="5.5" fill="#FFAB00" stroke="#0B0C10" strokeWidth="2" />
            ))}
          </g>
        )}

        {style === 'crossed_arms' && (
          <g stroke="url(#neonStroke)" strokeWidth="4.5" strokeLinecap="round" strokeLinejoin="round">
            {/* Head */}
            <circle cx="150" cy="82" r="26" fill="rgba(0,229,255,0.08)" />
            {/* Spine */}
            <line x1="150" y1="108" x2="150" y2="246" />
            {/* Broad Shoulders */}
            <line x1="100" y1="144" x2="200" y2="146" />
            {/* Crossed Arms */}
            <path d="M100 144 L88 204 L178 202" />
            <path d="M200 146 L212 204 L122 206" />
            {/* Hips */}
            <line x1="120" y1="246" x2="180" y2="246" />
            {/* Balanced Stance Legs */}
            <path d="M124 246 L118 354 L114 456" />
            <path d="M176 246 L184 354 L188 456" />
            {[
              [100, 144],
              [200, 146],
              [88, 204],
              [212, 204],
              [124, 246],
              [176, 246],
              [118, 354],
              [184, 354]
            ].map(([cx, cy], idx) => (
              <circle key={idx} cx={cx} cy={cy} r="5.5" fill="#FFAB00" stroke="#0B0C10" strokeWidth="2" />
            ))}
          </g>
        )}
      </svg>
    </div>
  );
};
