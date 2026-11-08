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
    // SH1370: the hub's grammar. A surface that follows the theme instead of a permanent dark band,
    // plain metadata text instead of a chip, a solid violet accent instead of gradient text, and no
    // grid or orbs moving behind the product evidence.
    <section id="top" className="border-b border-border bg-background text-foreground">
      <Container className="grid grid-cols-1 items-center gap-12 py-16 lg:grid-cols-[1.02fr_0.98fr] lg:py-24">
        <div className="min-w-0">
          <p className="mb-5 font-mono text-xs font-semibold tracking-[0.08em] text-muted-foreground uppercase">
            Open source &middot; Self-hosted &middot; Agent-ready &middot; MCP-native
          </p>

          <h1 className="text-4xl font-extrabold leading-[1.06] tracking-tight sm:text-5xl lg:text-[3.4rem]">
            A complete CMS.{" "}
            <span className="text-primary">Your agent can drive it too.</span>
          </h1>

          <p className="mt-5 max-w-xl text-lg leading-relaxed text-muted-foreground">
            Write the pages yourself in the console, hand the work to Claude Code,
            keep the content as files in your repository, or read it all through
            REST and GraphQL. Four ways in, one content engine, and the same
            drafts, previews and publish button behind every one of them.
          </p>
          <p className="mt-3 max-w-xl text-sm leading-relaxed text-muted-foreground">
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
            <Button asChild size="lg" variant="outline">
              <a href={withBase("/console/")}>Use it without an agent</a>
            </Button>
            <Button asChild size="lg" variant="ghost">
              <a href={LINKS.docs}>Docs</a>
            </Button>
          </div>

          <div className="mt-7 flex flex-wrap gap-x-6 gap-y-2 text-sm text-muted-foreground">
            {PROMISES.map((item) => (
              <span key={item} className="inline-flex items-center gap-1.5">
                <Check className="size-4 text-primary" />
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
