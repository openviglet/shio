import { ArrowRight, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Container } from "@/components/brand";
import { HeroSessions } from "@/components/hero-sessions";
import { LINKS } from "@/lib/site-content";
import { withBase } from "@/lib/base";

const PROMISES = [
  "A full console, no agent required",
  "Drafts by default: nothing publishes itself",
  "Self-hosted, your database, open source",
];

export function Hero() {
  return (
    <section id="top" className="relative isolate overflow-hidden bg-[#150e0c] text-[#f6ece8]">
      <div className="grid-bg pointer-events-none absolute inset-0 -z-10" />
      <div className="animate-orb-a pointer-events-none absolute -top-44 -left-20 -z-10 h-[520px] w-[520px] rounded-full bg-[radial-gradient(circle,#7c3aed,transparent_70%)] opacity-45 blur-[70px]" />
      <div className="animate-orb-b pointer-events-none absolute -top-28 -right-24 -z-10 h-[460px] w-[460px] rounded-full bg-[radial-gradient(circle,#f59e0b,transparent_70%)] opacity-35 blur-[70px]" />

      <Container className="grid grid-cols-1 items-center gap-12 py-20 lg:grid-cols-[1.02fr_0.98fr] lg:py-28">
        <div className="min-w-0">
          <span className="mb-6 inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/5 px-3.5 py-1.5 font-mono text-xs font-semibold text-[#d3bcb3]">
            <span className="size-1.5 rounded-full bg-[#7dd3a0] shadow-[0_0_8px] shadow-[#7dd3a0]" />
            Open source &middot; Self-hosted &middot; Agent-ready &middot; MCP-native
          </span>

          <h1 className="text-4xl font-extrabold leading-[1.06] tracking-tight sm:text-5xl lg:text-[3.4rem]">
            A complete CMS.{" "}
            <span className="text-gradient">Your agent can drive it too.</span>
          </h1>

          <p className="mt-5 max-w-xl text-lg leading-relaxed text-[#c9b6ae]">
            Write the pages yourself in the console, hand the work to Claude Code,
            keep the content as files in your repository, or read it all through
            REST and GraphQL. Four ways in, one content engine, and the same
            drafts, previews and publish button behind every one of them.
          </p>
          <p className="mt-3 max-w-xl text-sm leading-relaxed text-[#a89189]">
            The agent path is what makes Viglet Shio unusual, not what makes it usable. Never
            attach one and nothing is withheld: it is an ordinary CMS, and it
            hosts no model, no prompts and no provider keys.
          </p>

          <div className="mt-7 flex flex-wrap gap-3">
            <Button asChild size="lg">
              <a href="#paths">
                Find your way in <ArrowRight className="size-4" />
              </a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="outline"
              className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white dark:bg-white/10"
            >
              <a href={withBase("/console/")}>Use it without an agent</a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="ghost"
              className="text-[#c9b6ae] hover:bg-white/10 hover:text-white"
            >
              <a href={LINKS.docs}>Docs</a>
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
          <HeroSessions />
        </div>
      </Container>
    </section>
  );
}
