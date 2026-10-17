import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Container } from "@/components/brand";
import { CopyCommand } from "@/components/copy-command";
import { LINKS } from "@/lib/site-content";

export function CallToAction() {
  return (
    // `data-md-skip`: the Markdown twin of every route would otherwise carry
    // this identically (SH537). It converts a reader; it costs an agent forty
    // words ten times over.
    <section className="py-20" data-md-skip>
      <Container>
        <div className="relative isolate overflow-hidden rounded-3xl border border-white/10 bg-[#150e0c] px-6 py-16 text-center text-white">
          <div className="grid-bg pointer-events-none absolute inset-0 -z-10" />
          <div className="animate-orb-a pointer-events-none absolute -top-24 left-1/2 -z-10 h-80 w-80 -translate-x-1/2 rounded-full bg-[radial-gradient(circle,#7c3aed,transparent_70%)] opacity-45 blur-[70px]" />

          <h2 className="text-3xl font-extrabold tracking-tight sm:text-4xl">
            Run the CMS. Attach the agent whenever you feel like it.
          </h2>
          <p className="mx-auto mt-3 max-w-xl text-base text-[#c9b6ae] sm:text-lg">
            One container and a database, and a console your team can work in on the
            first afternoon. Open source, no hosted tier you are being funnelled
            towards, and the whole design argument checked into the repository.
          </p>

          <div className="mt-8 flex flex-wrap justify-center gap-3">
            <Button asChild size="lg">
              <a href={LINKS.docs}>
                Install it <ArrowRight className="size-4" />
              </a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="outline"
              className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white dark:bg-white/10"
            >
              <a href="/console/">Use it without an agent</a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="ghost"
              className="text-[#c9b6ae] hover:bg-white/10 hover:text-white"
            >
              <a href={LINKS.repo} target="_blank" rel="noreferrer">
                Read the source
              </a>
            </Button>
          </div>

          <p className="mt-8 font-mono text-xs text-[#8d7b74]">
            and when you do want one:
          </p>
          <div className="mt-2 flex justify-center">
            <CopyCommand command="claude mcp add shio -- npx @viglet/shio mcp" />
          </div>
        </div>
      </Container>
    </section>
  );
}
