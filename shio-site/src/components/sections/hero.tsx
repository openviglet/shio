import { ArrowRight, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Container } from "@/components/brand";
import { CopyCommand } from "@/components/copy-command";
import { AgentTranscript } from "@/components/agent-transcript";
import { LINKS } from "@/lib/site-content";

const PROMISES = [
  "Drafts by default — nothing publishes itself",
  "Self-hosted, your database",
  "No model, no prompts, no keys in the CMS",
];

export function Hero() {
  return (
    <section id="top" className="relative isolate overflow-hidden bg-[#150e0c] text-[#f6ece8]">
      <div className="grid-bg pointer-events-none absolute inset-0 -z-10" />
      <div className="animate-orb-a pointer-events-none absolute -top-44 -left-20 -z-10 h-[520px] w-[520px] rounded-full bg-[radial-gradient(circle,#ff6347,transparent_70%)] opacity-45 blur-[70px]" />
      <div className="animate-orb-b pointer-events-none absolute -top-28 -right-24 -z-10 h-[460px] w-[460px] rounded-full bg-[radial-gradient(circle,#f59e0b,transparent_70%)] opacity-35 blur-[70px]" />

      <Container className="grid grid-cols-1 items-center gap-12 py-20 lg:grid-cols-[1.02fr_0.98fr] lg:py-28">
        <div className="min-w-0">
          <span className="mb-6 inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/5 px-3.5 py-1.5 font-mono text-xs font-semibold text-[#d3bcb3]">
            <span className="size-1.5 rounded-full bg-[#7dd3a0] shadow-[0_0_8px] shadow-[#7dd3a0]" />
            Open source &middot; Self-hosted &middot; MCP-native
          </span>

          <h1 className="text-4xl font-extrabold leading-[1.06] tracking-tight sm:text-5xl lg:text-[3.4rem]">
            The CMS whose operator is{" "}
            <span className="text-gradient">Claude Code</span>.
          </h1>

          <p className="mt-5 max-w-xl text-lg leading-relaxed text-[#c9b6ae]">
            Your agent builds the site. You approve it. Shio is a headless CMS
            designed backwards from that sentence &mdash; one call replaces a discovery
            session, content lives as files an agent can <code className="font-mono text-[#ffb08a]">Grep</code>,
            and every write lands as a draft with your name on the approve button.
          </p>
          <p className="mt-3 max-w-xl text-sm leading-relaxed text-[#a89189]">
            Judged in agent cycles and tokens, not in clicks. Shio hosts no model and
            no prompts &mdash; it is the substrate your agent drives.
          </p>

          <div className="mt-7">
            <CopyCommand command="claude mcp add shio -- npx @viglet/shio mcp" />
          </div>

          <div className="mt-6 flex flex-wrap gap-3">
            <Button asChild size="lg">
              <a href={LINKS.docs}>
                Get started <ArrowRight className="size-4" />
              </a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="outline"
              className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white dark:bg-white/10"
            >
              <a href="#laws">The ten laws</a>
            </Button>
          </div>

          <div className="mt-7 flex flex-wrap gap-x-6 gap-y-2 text-sm text-[#a89189]">
            {PROMISES.map((item) => (
              <span key={item} className="inline-flex items-center gap-1.5">
                <Check className="size-4 text-[#7dd3a0]" />
                {item}
              </span>
            ))}
          </div>
        </div>

        <div className="min-w-0">
          <AgentTranscript />
          <p className="mt-3 text-center font-mono text-xs text-[#8d7b74]">
            One session. One page. Six calls, and you still decide.
          </p>
        </div>
      </Container>
    </section>
  );
}
