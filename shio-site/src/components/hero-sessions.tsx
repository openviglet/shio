import { useState } from "react";
import { Bot, MousePointerClick } from "lucide-react";
import { AgentTranscript } from "@/components/agent-transcript";
import { ConsoleSession } from "@/components/console-session";
import { cn } from "@/lib/utils";

type Tab = "console" | "agent";

const TABS: { id: Tab; label: string; icon: typeof Bot; caption: string }[] = [
  {
    id: "console",
    label: "A person builds it",
    icon: MousePointerClick,
    caption: "Six screens. One page, published, and no agent involved.",
  },
  {
    id: "agent",
    label: "An agent builds it",
    icon: Bot,
    caption: "Six calls. The same page, as a draft, and you still decide.",
  },
];

/**
 * The hero's centrepiece, and the site's whole thesis in one control.
 *
 * The page used to lead with the agent session alone. That is the most
 * interesting thing about the product and it was also, read cold, a claim that
 * the CMS cannot be used without one, which is false, and which turned away
 * exactly the reader who would otherwise have installed it. So the two sessions
 * sit under one switch, the console is the default, and the two panels are drawn
 * in the same language so the reader can see they arrive at the same page.
 *
 * **Both panels stay mounted**, the inactive one hidden with `hidden` rather
 * than unmounted: the tab state's initial value is the same on the server and
 * on the client, so the prerendered markup and the hydrated tree agree, and the
 * hidden panel is still a real tab panel for a screen reader that switches to
 * it.
 *
 * What that costs, stated because it is not obvious: `markdown.mjs` drops a
 * `hidden` element, so the route's `.md` twin carries the **console** session
 * and not the agent one. That is the right trade rather than a regression: the
 * twin is what an agent reads, and an agent does not need a scripted demo of
 * itself, while the substance of the agent path is in the sections below, all of
 * which the twin carries in full. Swapping the default would only move the hole.
 */
export function HeroSessions() {
  const [tab, setTab] = useState<Tab>("console");

  return (
    <div>
      <div
        role="tablist"
        aria-label="Two ways to build the same page"
        className="mb-3 flex gap-1 rounded-xl border border-white/12 bg-white/[0.04] p-1"
      >
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            aria-controls={`session-${t.id}`}
            onClick={() => setTab(t.id)}
            className={cn(
              "flex flex-1 items-center justify-center gap-1.5 rounded-lg px-3 py-2 text-xs font-bold transition-colors sm:text-sm",
              tab === t.id
                ? "bg-[#7c3aed]/15 text-[#ddd6fe] shadow-sm"
                : "text-[#a89189] hover:bg-white/5 hover:text-[#f6ece8]"
            )}
          >
            <t.icon className="size-4 shrink-0" aria-hidden />
            {t.label}
          </button>
        ))}
      </div>

      {TABS.map((t) => (
        <div
          key={t.id}
          id={`session-${t.id}`}
          role="tabpanel"
          aria-label={t.label}
          hidden={tab !== t.id}
        >
          {t.id === "console" ? <ConsoleSession /> : <AgentTranscript />}
          <p className="mt-3 text-center font-mono text-xs text-[#8d7b74]">{t.caption}</p>
        </div>
      ))}
    </div>
  );
}
