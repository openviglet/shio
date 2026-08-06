import { useEffect, useRef, useState } from "react";
import { CornerDownRight } from "lucide-react";
import { cn } from "@/lib/utils";
import { TRANSCRIPT } from "@/lib/site-content";

/**
 * The hero's centrepiece: a Claude Code session that builds one page.
 *
 * Three constraints, in the order they constrain the implementation.
 *
 * 1. **It is a recording and it says so.** The badge in the title bar is not
 *    decoration — a scripted demo dressed as a live connection is precisely the
 *    surprise law P7 forbids, and this page is an argument about that law.
 *    SH540 points it at a public read-only instance; until then, this.
 * 2. **It must be complete without motion.** The whole content is text, so the
 *    prerendered HTML carries every step and the reveal is an enhancement on
 *    top. `prefers-reduced-motion` keeps the finished transcript rather than
 *    freezing it half-typed, and so does a client that never hydrates.
 * 3. **The copy is content, not markup.** The steps live in `site-content.ts`,
 *    so re-scripting the session never touches this file.
 *
 * The reveal starts only in an effect: the server and the client's first render
 * both draw all six steps, which is what keeps hydration silent.
 */
export function AgentTranscript({ className }: { className?: string }) {
  const [shown, setShown] = useState(TRANSCRIPT.length);
  const [playing, setPlaying] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLOListElement>(null);

  useEffect(() => {
    if (globalThis.matchMedia?.("(prefers-reduced-motion: reduce)").matches) return;

    // Don't run the reveal off-screen: a visitor who scrolls down and back
    // should find the session at the start, not already finished.
    const node = rootRef.current;
    if (!node) return;
    let timer: number | undefined;

    const play = () => {
      setPlaying(true);
      setShown(1);
      let i = 1;
      timer = window.setInterval(() => {
        i += 1;
        setShown(i);
        if (i >= TRANSCRIPT.length) window.clearInterval(timer);
      }, 1100);
    };

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((e) => e.isIntersecting)) {
          observer.disconnect();
          play();
        }
      },
      { threshold: 0.25 }
    );
    observer.observe(node);

    return () => {
      observer.disconnect();
      if (timer) window.clearInterval(timer);
    };
  }, []);

  // Follow the newest step down. The panel is shorter than the finished
  // transcript on purpose — a session scrolls — but only while it is playing:
  // scrolling a static block of text the visitor has not started reading is the
  // opposite of helpful.
  useEffect(() => {
    if (!playing) return;
    const list = listRef.current;
    const last = list?.children[shown - 1];
    last?.scrollIntoView({ behavior: "smooth", block: "nearest" });
  }, [shown, playing]);

  const done = shown >= TRANSCRIPT.length;

  return (
    <div
      ref={rootRef}
      className={cn(
        "scanlines relative overflow-hidden rounded-2xl border border-white/12 bg-[#150e0c]/95 shadow-2xl shadow-black/40 backdrop-blur",
        className
      )}
    >
      <div className="flex items-center gap-2 border-b border-white/10 px-4 py-2.5">
        <span className="flex gap-1.5" aria-hidden>
          <span className="size-2.5 rounded-full bg-[#ff6347]/70" />
          <span className="size-2.5 rounded-full bg-[#f5c46b]/70" />
          <span className="size-2.5 rounded-full bg-[#7dd3a0]/70" />
        </span>
        <span className="ml-1 font-mono text-xs font-semibold text-[#b39c94]">
          claude &mdash; shio (MCP)
        </span>
        <span className="ml-auto rounded-full border border-white/12 px-2 py-0.5 font-mono text-[0.65rem] font-bold tracking-wide text-[#a89189] uppercase">
          scripted
        </span>
      </div>

      <ol
        ref={listRef}
        className="max-h-[26rem] space-y-4 overflow-y-auto px-4 py-4 font-mono text-[0.8rem] leading-relaxed sm:text-[0.84rem]"
      >
        {TRANSCRIPT.map((step, i) => (
          <li
            key={step.tool}
            className={cn(
              "transition-opacity duration-500",
              i < shown ? "opacity-100" : "opacity-0"
            )}
            aria-hidden={i >= shown ? true : undefined}
          >
            <p className="flex gap-2 text-[#c9b6ae]">
              <span className="shrink-0 select-none text-[#ff8a6b]">&gt;</span>
              <span className="min-w-0">{step.call}</span>
            </p>
            <p className="mt-1.5 flex gap-2">
              <span className="shrink-0 select-none text-[#7dd3a0]">&#9679;</span>
              <span className="min-w-0 break-words text-[#ffb08a]">{step.tool}</span>
            </p>
            <p className="mt-1 flex gap-2 pl-[1.1rem] text-[#8d7b74]">
              <CornerDownRight className="mt-0.5 size-3 shrink-0" aria-hidden />
              <span className="min-w-0 break-words">{step.result}</span>
            </p>
            <span className="mt-1.5 ml-[1.1rem] inline-flex items-center rounded border border-[#ff6347]/30 bg-[#ff6347]/10 px-1.5 py-0.5 text-[0.65rem] font-bold text-[#ff9478]">
              {step.law}
            </span>
          </li>
        ))}
      </ol>

      <div className="flex items-center gap-2 border-t border-white/10 px-4 py-2.5 font-mono text-xs text-[#8d7b74]">
        <span className="text-[#ff8a6b]">&gt;</span>
        {done ? (
          <>
            <span>6 calls. Nothing published.</span>
            <span className="animate-caret ml-0.5 inline-block h-3.5 w-1.5 bg-[#ff8a6b] align-middle" />
          </>
        ) : (
          <span className="animate-caret inline-block h-3.5 w-1.5 bg-[#ff8a6b] align-middle" />
        )}
      </div>
    </div>
  );
}
