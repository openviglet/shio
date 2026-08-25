/**
 * VigletLogo — generic product logo component.
 *
 * Faithful port of the original mark from the viglet.org site
 * (openviglet.github.io `src/components/VigletLogo.tsx`):
 * - Solid coloured square with rounded corners (NOT a gradient)
 * - Top-right: product section (DEP / CMS / ES), light weight
 * - Bottom-left: product acronym (Du / Sh / Tu), ultra-bold
 * - Navajowhite (#FFDEAD) text and border — the Viglet brand identity
 * - Drop shadow as in the original HTML spec
 *
 * Size modes
 * ──────────
 * size < 56   → "badge"  — centred acronym only (for nav / header chips)
 * size ≥ 56   → "full"   — section label + acronym (for heroes, cards, etc.)
 *
 * `glow` requires the `.vg-logo-glow` keyframes in index.css — this site does not
 * define them, so it never passes `glow`.
 */

export const PRODUCT_COLORS: Record<string, string> = {
  dumont: "#006400", // darkgreen
  shio: "#FF6347", // tomato
  turing: "#4169E1", // royalblue
};

const PRODUCT_META: Record<string, { acronym: string; section: string }> = {
  dumont: { acronym: "Du", section: "DEP" },
  shio: { acronym: "Sh", section: "CMS" },
  turing: { acronym: "Tu", section: "ES" },
};

const TEXT_COLOR = "#FFDEAD"; // navajowhite, matching the original spec

interface VigletLogoProps {
  readonly identifier: string;
  readonly size?: number;
  /** Animate acronym with a coloured glow wave. Default false. */
  readonly glow?: boolean;
  readonly className?: string;
}

/** Renders acronym letters as individual <span>s with staggered glow delays. */
function GlowAcronym({ acronym, duration = 2 }: { acronym: string; duration?: number }) {
  return (
    <>
      {acronym.split("").map((char, i) => (
        <span
          key={`${char}-${i}`}
          className="vg-logo-glow"
          style={{ animationDelay: `${-((i + 1) * (duration / acronym.length)).toFixed(4)}s` }}
        >
          {char}
        </span>
      ))}
    </>
  );
}

export function VigletLogo({ identifier, size = 48, glow = false, className }: VigletLogoProps) {
  const meta = PRODUCT_META[identifier];
  const color = PRODUCT_COLORS[identifier] ?? "#555555";
  if (!meta) return null;

  // Proportional helpers (all relative to the reference 280 px design)
  const r = (ratio: number) => Math.round(size * ratio);
  const borderRadius = Math.max(6, r(0.107)); // 30/280, min 6 px for legibility
  const borderWidth = Math.max(1, r(0.029)); // 8/280
  const shadow = `0 ${r(0.014)}px ${r(0.029)}px rgba(0,0,0,0.22), 0 ${r(0.021)}px ${r(0.071)}px rgba(0,0,0,0.19)`;

  const isSmall = size < 56;

  if (isSmall) {
    // ── Badge mode — no section label, acronym centred and nudged left ──────
    const padLeft = Math.max(4, r(0.09)); // slightly more left than centre
    const smallOffsetY = r(0.25); // proportional downward nudge → bottom-left feel
    return (
      <div
        className={className}
        aria-label={meta.acronym}
        style={{
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "flex-start",
          width: size,
          height: size,
          flexShrink: 0,
          backgroundColor: color,
          color: TEXT_COLOR,
          paddingLeft: padLeft,
          borderRadius,
          borderWidth,
          borderStyle: "solid",
          borderColor: TEXT_COLOR,
          boxShadow: shadow,
          boxSizing: "border-box",
          overflow: "hidden",
          userSelect: "none",
        }}
      >
        <span
          style={{
            fontSize: Math.max(10, r(0.46)),
            fontWeight: 900,
            lineHeight: 1,
            marginTop: smallOffsetY,
          }}
        >
          {glow ? <GlowAcronym acronym={meta.acronym} /> : meta.acronym}
        </span>
      </div>
    );
  }

  // ── Periodic-table layout — full size ───────────────────────────────────
  // Section (DEP/CMS/ES) pinned top-right  ←→  Acronym (Du/Sh/Tu) bottom-left
  const pad = r(0.089); // 25/280, uniform padding on all edges
  return (
    <div
      className={className}
      aria-label={`${meta.acronym} ${meta.section}`}
      style={{
        display: "inline-flex",
        flexDirection: "column",
        justifyContent: "space-between", // section ↑ top, acronym ↓ bottom
        width: size,
        height: size,
        flexShrink: 0,
        backgroundColor: color,
        color: TEXT_COLOR,
        padding: `${pad}px`,
        borderRadius,
        borderWidth,
        borderStyle: "solid",
        borderColor: TEXT_COLOR,
        boxShadow: shadow,
        boxSizing: "border-box",
        overflow: "hidden",
        userSelect: "none",
      }}
    >
      {/* Section label: top-right, light weight */}
      <div
        style={{
          fontSize: Math.max(6, r(0.125)),
          fontWeight: 300,
          textAlign: "right",
          lineHeight: 1,
          opacity: 0.85,
          letterSpacing: "0.04em",
          alignSelf: "flex-end",
        }}
      >
        {meta.section}
      </div>
      {/* Acronym: bottom-left, ultra-bold */}
      <div
        style={{
          fontSize: r(0.46),
          fontWeight: 900,
          lineHeight: 1,
          alignSelf: "flex-start",
        }}
      >
        {glow ? <GlowAcronym acronym={meta.acronym} /> : meta.acronym}
      </div>
    </div>
  );
}

export default VigletLogo;
