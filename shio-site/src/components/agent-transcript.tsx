import { useCallback, useEffect, useRef, useState } from "react";
import { ChevronLeft, ChevronRight, CornerDownRight, Pause, Play, RotateCcw } from "lucide-react";
import { cn } from "@/lib/utils";
import { TRANSCRIPT } from "@/lib/site-content";

const LAST = TRANSCRIPT.length - 1;
const STEP_MS = 2600;

/**
 * How far `item` sits outside `box`, signed, and 0 when it already fits — the
 * `block: "nearest"` rule, computed rather than delegated so the scroll it
 * feeds can be applied to one element instead of to every ancestor.
 */
function overflowWithin(box: DOMRect, item: DOMRect): number {
  if (item.top < box.top) return item.top - box.top;
  if (item.bottom > box.bottom) return item.bottom - box.bottom;
  return 0;
}

/**
 * SH531 — the centrepiece: a Claude Code session that builds one page, as a
 * player rather than a still.
 *
 * Four constraints, in the order they constrain the implementation.
 *
 * 1. **It is a recording and it says so.** The badge is not decoration — a
 *    scripted demo dressed as a live connection is precisely the surprise law
 *    P7 forbids, and this page is an argument about that law. SH540 points it
 *    at a public read-only instance; until then, this.
 * 2. **Every step is in the DOM at every moment.** The current one is lit and
 *    the others are dimmed, so a screen reader gets the whole session at once,
 *    the prerendered HTML carries all six, and the Markdown twin does too.
 *    Revealing by mounting would have made all three false.
 * 3. **Motion is an enhancement on top of that.** `prefers-reduced-motion`
 *    means no autoplay at all — it opens on the finished session with the
 *    controls still working, which is a better answer than a frozen animation.
 * 4. **The copy is content, not markup.** The steps and their artefacts live in
 *    `site-content.ts`, so re-scripting the session never touches this file.
 *
 * Autoplay starts only in an effect and only once the panel is on screen, so
 * the server and the client's first render agree, and a visitor who scrolls
 * back finds the session where they left it rather than finished.
 */
export function AgentTranscript({ className }: { className?: string }) {
  // SSR and the first client render both draw the finished session.
  const [current, setCurrent] = useState(LAST);
  const [playing, setPlaying] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLOListElement>(null);
  const startedRef = useRef(false);

  const step = TRANSCRIPT[current];

  /** Any manual control stops the playback — the reader is driving now. */
  const goTo = useCallback((next: number) => {
    setPlaying(false);
    setCurrent(Math.max(0, Math.min(LAST, next)));
  }, []);

  const replay = useCallback(() => {
    setCurrent(0);
    setPlaying(true);
  }, []);

  // Arm autoplay once, when the panel first comes into view.
  useEffect(() => {
    if (globalThis.matchMedia?.("(prefers-reduced-motion: reduce)").matches) return;
    const node = rootRef.current;
    if (!node) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (!entries.some((e) => e.isIntersecting) || startedRef.current) return;
        startedRef.current = true;
        observer.disconnect();
        setCurrent(0);
        setPlaying(true);
      },
      { threshold: 0.3 }
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  // Advance while playing, and stop at the end rather than looping: a loop makes
  // the section never settle, which is what makes an animated hero tiring.
  useEffect(() => {
    if (!playing) return;
    if (current >= LAST) {
      setPlaying(false);
      return;
    }
    const timer = window.setTimeout(() => setCurrent((c) => c + 1), STEP_MS);
    return () => window.clearTimeout(timer);
  }, [playing, current]);

  /**
   * Keep the lit step in view *inside the list*, and nowhere else (SH565).
   *
   * `scrollIntoView` was the obvious call and it was the bug: even with
   * `block: "nearest"` it scrolls **every** scrollable ancestor, the document
   * included. Once a reader scrolled past the hero the panel kept playing, and
   * every 2.6s tick dragged the page back up to it — the section reading as a
   * scroll trap. Scrolling the `<ol>` by hand is the only form that cannot
   * touch the window: `Element.scrollBy` moves that element and stops there.
   */
  useEffect(() => {
    const list = listRef.current;
    const item = list?.children[current];
    if (!list || !item) return;

    const delta = overflowWithin(list.getBoundingClientRect(), item.getBoundingClientRect());
    if (delta !== 0) list.scrollBy({ top: delta, behavior: "smooth" });
  }, [current]);

  /**
   * Arrow-key stepping, attached to each control rather than to a container:
   * the buttons are natively interactive, so this needs no role and no
   * tabindex on a div that is not a widget.
   */
  const onKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "ArrowRight") {
      e.preventDefault();
      goTo(current + 1);
    } else if (e.key === "ArrowLeft") {
      e.preventDefault();
      goTo(current - 1);
    }
  };

  return (
    <div
      ref={rootRef}
      className={cn(
        "scanlines relative overflow-hidden rounded-2xl border border-white/12 bg-[#150e0c]/95 shadow-2xl shadow-black/40 backdrop-blur",
        className
      )}
    >
      {/* Title bar + controls */}
      <div className="flex flex-wrap items-center gap-2 border-b border-white/10 px-4 py-2.5">
        <span className="flex gap-1.5" aria-hidden>
          <span className="size-2.5 rounded-full bg-[#ff6347]/70" />
          <span className="size-2.5 rounded-full bg-[#f5c46b]/70" />
          <span className="size-2.5 rounded-full bg-[#7dd3a0]/70" />
        </span>
        <span className="ml-1 font-mono text-xs font-semibold text-[#b39c94]">
          claude &mdash; shio (MCP)
        </span>
        <span className="rounded-full border border-white/12 px-2 py-0.5 font-mono text-[0.65rem] font-bold tracking-wide text-[#a89189] uppercase">
          scripted
        </span>

        <div className="ml-auto flex items-center gap-0.5">
          <PlayerButton
            label="Previous call"
            onClick={() => goTo(current - 1)}
            onKeyDown={onKeyDown}
            disabled={current === 0}
          >
            <ChevronLeft className="size-4" />
          </PlayerButton>
          {current >= LAST ? (
            <PlayerButton label="Replay the session" onClick={replay} onKeyDown={onKeyDown}>
              <RotateCcw className="size-3.5" />
            </PlayerButton>
          ) : (
            <PlayerButton
              label={playing ? "Pause" : "Play"}
              onClick={() => setPlaying((p) => !p)}
              onKeyDown={onKeyDown}
            >
              {playing ? <Pause className="size-3.5" /> : <Play className="size-3.5" />}
            </PlayerButton>
          )}
          <PlayerButton
            label="Next call"
            onClick={() => goTo(current + 1)}
            onKeyDown={onKeyDown}
            disabled={current >= LAST}
          >
            <ChevronRight className="size-4" />
          </PlayerButton>
        </div>
      </div>

      {/* The session */}
      <ol
        ref={listRef}
        className="max-h-[15rem] space-y-3.5 overflow-y-auto px-4 py-3.5 font-mono text-[0.78rem] leading-relaxed"
      >
        {TRANSCRIPT.map((s, i) => (
          <li
            key={s.tool}
            className={cn(
              "transition-opacity duration-500",
              i === current ? "opacity-100" : "opacity-35"
            )}
          >
            <p className="flex gap-2 text-[#c9b6ae]">
              <span className="shrink-0 select-none text-[#ff8a6b]">&gt;</span>
              <span className="min-w-0">{s.call}</span>
            </p>
            <p className="mt-1 flex gap-2">
              <span className="shrink-0 select-none text-[#7dd3a0]">&#9679;</span>
              <span className="min-w-0 break-words text-[#ffb08a]">{s.tool}</span>
              <span className="ml-auto shrink-0 rounded border border-[#ff6347]/30 bg-[#ff6347]/10 px-1.5 text-[0.62rem] font-bold text-[#ff9478]">
                {s.law}
              </span>
            </p>
            <p className="mt-0.5 flex gap-2 pl-[1.1rem] text-[#8d7b74]">
              <CornerDownRight className="mt-0.5 size-3 shrink-0" aria-hidden />
              <span className="min-w-0 break-words">{s.result}</span>
            </p>
          </li>
        ))}
      </ol>

      {/* What that call produced */}
      <div className="border-t border-white/10 bg-black/25">
        {/* Not uppercased: half of these labels are a path. */}
        <p className="flex items-center gap-2 px-4 pt-2.5 pb-1 font-mono text-[0.68rem] font-semibold tracking-wide text-[#8d7b74]">
          {step.artefact.label}
        </p>
        <pre className="h-[9.5rem] overflow-auto px-4 pb-3 font-mono text-[0.72rem] leading-relaxed">
          <code>
            {step.artefact.lines.map((line, i) => (
              <span
                key={`${line}-${i}`}
                className={cn(
                  "block whitespace-pre",
                  step.artefact.diff && line.startsWith("+") && "text-[#7dd3a0]",
                  step.artefact.diff && line.startsWith("-") && "text-[#ff8a6b]",
                  (!step.artefact.diff ||
                    (!line.startsWith("+") && !line.startsWith("-"))) &&
                    "text-[#c9b6ae]"
                )}
              >
                {line || " "}
              </span>
            ))}
          </code>
        </pre>
      </div>

      {/* Progress */}
      <div className="flex items-center gap-3 border-t border-white/10 px-4 py-2.5">
        <ol className="flex gap-1.5">
          {TRANSCRIPT.map((s, i) => (
            <li key={s.tool}>
              <button
                type="button"
                onClick={() => goTo(i)}
                onKeyDown={onKeyDown}
                aria-label={`Call ${i + 1}: ${s.tool}`}
                aria-current={i === current ? "step" : undefined}
                className={cn(
                  "h-1.5 rounded-full transition-all",
                  i === current ? "w-6 bg-[#ff8a6b]" : "w-1.5 bg-white/25 hover:bg-white/50"
                )}
              />
            </li>
          ))}
        </ol>
        <span className="ml-auto font-mono text-[0.7rem] text-[#8d7b74]">
          {current >= LAST
            ? "6 calls. Nothing published."
            : `call ${current + 1} of ${TRANSCRIPT.length}`}
        </span>
      </div>
    </div>
  );
}

function PlayerButton({
  label,
  onClick,
  onKeyDown,
  disabled,
  children,
}: {
  label: string;
  onClick: () => void;
  onKeyDown?: (e: React.KeyboardEvent) => void;
  disabled?: boolean;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      onKeyDown={onKeyDown}
      disabled={disabled}
      aria-label={label}
      title={label}
      className="flex size-7 items-center justify-center rounded-md text-[#b39c94] transition-colors hover:bg-white/10 hover:text-white disabled:pointer-events-none disabled:opacity-30"
    >
      {children}
    </button>
  );
}
