import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Container } from "@/components/brand";
import { CopyCommand } from "@/components/copy-command";
import { LINKS } from "@/lib/site-content";
import { withBase } from "@/lib/base";

export function CallToAction() {
  return (
    // `data-md-skip`: the Markdown twin of every route would otherwise carry
    // this identically (SH537). It converts a reader; it costs an agent forty
    // words ten times over.
    <section className="py-20" data-md-skip>
      <Container>
        <div className="rounded-3xl border border-border bg-muted/40 px-6 py-16 text-center text-foreground">

          <h2 className="text-3xl font-extrabold tracking-tight sm:text-4xl">
            Run the CMS. Attach the agent whenever you feel like it.
          </h2>
          <p className="mx-auto mt-3 max-w-xl text-base text-muted-foreground sm:text-lg">
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
            >
              <a href={withBase("/console/")}>Use it without an agent</a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="ghost"
            >
              <a href={LINKS.repo} target="_blank" rel="noreferrer">
                Read the source
              </a>
            </Button>
          </div>

          <p className="mt-8 font-mono text-xs text-muted-foreground">
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
