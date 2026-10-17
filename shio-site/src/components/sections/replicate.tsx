import { ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Container, Eyebrow } from "@/components/brand";
import { CLONE_STEPS } from "@/lib/site-content";

/**
 * The one capability a visitor can test against a URL they already own. SH535
 * turns this into a walkthrough with the real artefacts; here it is the shape
 * and the constraint, because the constraint is what makes it credible.
 */
export function Replicate() {
  return (
    <section
      id="replicate"
      className="relative isolate overflow-hidden border-y border-border bg-muted/40 py-20"
    >
      <Container>
        <div className="mx-auto mb-12 max-w-2xl text-center">
          <Eyebrow>Replication</Eyebrow>
          <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">
            Point it at a site you{" "}
            <span className="text-primary">already have</span>
          </h2>
          <p className="mt-3 text-base leading-relaxed text-muted-foreground sm:text-lg">
            Not a mirror: a site a curator can edit. The capture reads the pages,
            the conversion derives the content model from them, and the proof says
            whether the replica serves what the source did.
          </p>
        </div>

        <ol className="relative grid gap-6 md:grid-cols-3 [&>*]:min-w-0">
          {CLONE_STEPS.map((s, i) => (
            <li key={s.step} className="rounded-xl border border-border bg-card p-6">
              <div className="flex items-center gap-2">
                <span className="flex size-7 items-center justify-center rounded-full bg-primary/12 font-mono text-xs font-black text-primary">
                  {i + 1}
                </span>
                <h3 className="font-bold">{s.step}</h3>
              </div>
              <pre className="mt-4 overflow-x-auto rounded-lg bg-[#17100e] px-3 py-2.5 font-mono text-[0.72rem] leading-relaxed text-[#ddd6fe]">
                <code>{s.code}</code>
              </pre>
              <p className="mt-3 text-sm leading-relaxed text-muted-foreground">{s.gloss}</p>
            </li>
          ))}
        </ol>

        <div className="mx-auto mt-10 max-w-3xl rounded-xl border border-border bg-background/60 p-5">
          <h3 className="text-sm font-bold">The bounds, because they are the point</h3>
          <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
            One origin, and every off-host link recorded rather than followed.{" "}
            <span className="font-mono text-foreground">robots.txt</span> is obeyed, and
            a capture taken with <span className="font-mono">--ignore-robots</span>{" "}
            records that in the inventory, so it can never later be mistaken for
            one taken with permission. A ceiling marks the capture{" "}
            <span className="font-mono">PARTIAL</span>: there are no silent caps,
            because a bounded capture read as a complete one is the failure the whole
            thing is built to prevent. And the capture writes{" "}
            <em>no content at all</em>, so the conversion you are asked to review does
            not already exist.
          </p>
        </div>

        <div className="mt-8 flex justify-center">
          <Button
            asChild
            variant="outline"
            className="h-auto max-w-full whitespace-normal px-5 py-2.5 text-center"
          >
            <a href="/replicate/">
              The four verbs, the two modes, and what the crawl refuses
              <ArrowRight className="size-4" />
            </a>
          </Button>
        </div>
      </Container>
    </section>
  );
}
