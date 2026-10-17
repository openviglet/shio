import { useCallback, useEffect, useRef, useState } from "react";
import { ChevronLeft, ChevronRight, CornerDownRight, MousePointerClick } from "lucide-react";
import { cn } from "@/lib/utils";
import { CONSOLE_SESSION } from "@/lib/site-content";

const LAST = CONSOLE_SESSION.length - 1;

/**
 * The same page the agent transcript builds, built by a person in the console.
 *
 * It is deliberately the *same shape* as `AgentTranscript` (six steps, an
 * artefact under each, one panel) because the claim the pair makes is that the
 * two paths end at the same draft. Two panels drawn in different visual
 * languages would have made that an assertion; drawn in one, it is visible.
 *
 * Three differences from the agent panel, and each is a property of what it
 * shows rather than a style choice:
 *
 * 1. **No autoplay.** Clicks are not a stream of tool calls arriving; a person
 *    moves at their own pace, and a panel that advances on its own would be
 *    animating something that does not animate.
 * 2. **Browser chrome, not a terminal.** The window is the console, so the bar
 *    carries a path rather than an MCP session name.
 * 3. **Every step is in the DOM at all times**, dimmed rather than unmounted:
 *    the same constraint the agent panel is built under, for the same three
 *    reasons: screen readers, the prerendered HTML, and the Markdown twin.
 */
export function ConsoleSession({ className }: { className?: string }) {
  const [current, setCurrent] = useState(0);
  const listRef = useRef<HTMLOListElement>(null);
  const step = CONSOLE_SESSION[current];

  const goTo = useCallback((next: number) => {
    setCurrent(Math.max(0, Math.min(LAST, next)));
  }, []);

  /**
   * Keep the selected step inside the list, and nowhere else. `scrollIntoView`
   * would scroll every scrollable ancestor including the document: the scroll
   * trap SH566 found in the agent panel. `Element.scrollBy` cannot.
   */
  useEffect(() => {
    const list = listRef.current;
    const item = list?.children[current];
    if (!list || !item) return;
    const box = list.getBoundingClientRect();
    const rect = item.getBoundingClientRect();
    const delta =
      rect.top < box.top ? rect.top - box.top : rect.bottom > box.bottom ? rect.bottom - box.bottom : 0;
    if (delta !== 0) list.scrollBy({ top: delta, behavior: "smooth" });
  }, [current]);

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
      className={cn(
        "relative overflow-hidden rounded-2xl border border-white/12 bg-[#150e0c]/95 shadow-2xl shadow-black/40 backdrop-blur",
        className
      )}
    >
      {/* Browser chrome */}
      <div className="flex flex-wrap items-center gap-2 border-b border-white/10 px-4 py-2.5">
        <span className="flex gap-1.5" aria-hidden>
          <span className="size-2.5 rounded-full bg-[#7c3aed]/70" />
          <span className="size-2.5 rounded-full bg-[#f5c46b]/70" />
          <span className="size-2.5 rounded-full bg-[#7dd3a0]/70" />
        </span>
        <span className="ml-1 truncate font-mono text-xs font-semibold text-[#b39c94]">
          shio console &middot; /console/content
        </span>
        <span className="rounded-full border border-white/12 px-2 py-0.5 font-mono text-[0.65rem] font-bold tracking-wide text-[#a89189] uppercase">
          no agent
        </span>

        <div className="ml-auto flex items-center gap-0.5">
          <StepButton label="Previous step" onClick={() => goTo(current - 1)} onKeyDown={onKeyDown} disabled={current === 0}>
            <ChevronLeft className="size-4" />
          </StepButton>
          <StepButton label="Next step" onClick={() => goTo(current + 1)} onKeyDown={onKeyDown} disabled={current >= LAST}>
            <ChevronRight className="size-4" />
          </StepButton>
        </div>
      </div>

      {/* The session */}
      <ol
        ref={listRef}
        className="max-h-[15rem] space-y-3.5 overflow-y-auto px-4 py-3.5 font-mono text-[0.78rem] leading-relaxed"
      >
        {CONSOLE_SESSION.map((s, i) => (
          <li key={s.screen} className="relative">
            {/*
             * The click target sits beside the text rather than wrapping it:
             * `markdown.mjs` drops a `<button>` whole, children included, so
             * wrapping the lines would delete the whole session from every `.md`
             * twin. `z-10` keeps the dimmed items clickable: an opacity below 1
             * makes a stacking context, and without it a step is clickable only
             * once it is already selected.
             */}
            <button
              type="button"
              onClick={() => goTo(i)}
              onKeyDown={onKeyDown}
              aria-label={`Show step ${i + 1}: ${s.action}`}
              aria-current={i === current ? "step" : undefined}
              className="peer absolute -inset-x-2 -inset-y-1 z-10 cursor-pointer rounded-lg focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-[#ff8a6b]"
            />
            <div
              className={cn(
                "-mx-2 -my-1 rounded-lg px-2 py-1 transition-all duration-500 peer-hover:bg-white/5",
                i === current ? "opacity-100" : "opacity-35 peer-hover:opacity-80"
              )}
            >
              <p className="flex gap-2 text-[#c9b6ae]">
                <MousePointerClick className="mt-0.5 size-3.5 shrink-0 text-[#ff8a6b]" aria-hidden />
                <span className="min-w-0">{s.action}</span>
              </p>
              <p className="mt-1 flex gap-2">
                <span className="shrink-0 select-none text-[#7dd3a0]">&#9679;</span>
                <span className="min-w-0 break-words text-[#ddd6fe]">{s.screen}</span>
              </p>
              <p className="mt-0.5 flex gap-2 pl-[1.1rem] text-[#8d7b74]">
                <CornerDownRight className="mt-0.5 size-3 shrink-0" aria-hidden />
                <span className="min-w-0 break-words">{s.result}</span>
              </p>
            </div>
          </li>
        ))}
      </ol>

      {/* What the screen shows afterwards */}
      <div className="border-t border-white/10 bg-black/25">
        <p className="flex items-center gap-2 px-4 pt-2.5 pb-1 font-mono text-[0.68rem] font-semibold tracking-wide text-[#8d7b74]">
          {step.artefact.label}
        </p>
        <pre className="h-[9.5rem] overflow-auto px-4 pb-3 font-mono text-[0.72rem] leading-relaxed text-[#c9b6ae]">
          <code>
            {step.artefact.lines.map((line, i) => (
              <span key={`${line}-${i}`} className="block whitespace-pre">
                {line || " "}
              </span>
            ))}
          </code>
        </pre>
      </div>

      {/* Progress */}
      <div className="flex items-center gap-3 border-t border-white/10 px-4 py-2.5">
        <ol className="flex gap-1.5">
          {CONSOLE_SESSION.map((s, i) => (
            <li key={s.screen}>
              <button
                type="button"
                onClick={() => goTo(i)}
                onKeyDown={onKeyDown}
                aria-label={`Step ${i + 1}: ${s.action}`}
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
          {current >= LAST ? "6 steps. One page, live." : `step ${current + 1} of ${CONSOLE_SESSION.length}`}
        </span>
      </div>
    </div>
  );
}

function StepButton({
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
