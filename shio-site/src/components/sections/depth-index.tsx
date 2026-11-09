import { ArrowRight } from "lucide-react";
import { Container, SectionHeader } from "@/components/brand";
import { DEPTH_CARDS } from "@/lib/site-content";

/**
 * SH1372: the depth, as an index. The home carried ten sections of it in full (the laws, the
 * token measurements, the tool inventory, errors, verification, files, blueprints, replication,
 * delivery, the comparison), which made the page 36,000 pixels tall on a phone before a visitor
 * reached the install step. Each now has one card here, carrying its old anchor, and the full
 * section lives on the page that is about it.
 */
export function DepthIndex() {
  return (
    <section id="depth" className="py-20">
      <Container>
        <SectionHeader
          eyebrow="In depth"
          title={<>The detail, on the page that is about it</>}
          description="Each card is a section this page used to carry in full. Pick the one your decision depends on."
        />
        <div className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {DEPTH_CARDS.map((card) => (
            <a
              key={card.id}
              id={card.id}
              href={card.href}
              className="group flex scroll-mt-24 flex-col rounded-xl border border-border bg-background p-5 transition-colors hover:border-primary/40 hover:bg-muted/40"
            >
              <card.icon className="size-5 text-primary" aria-hidden />
              <span className="mt-3 font-bold">{card.title}</span>
              <span className="mt-1.5 flex-1 text-sm leading-relaxed text-muted-foreground">{card.summary}</span>
              <span className="mt-4 inline-flex items-center gap-1 text-sm font-semibold text-primary">
                {card.go}
                <ArrowRight className="size-3.5 transition-transform group-hover:translate-x-0.5" aria-hidden />
              </span>
            </a>
          ))}
        </div>
      </Container>
    </section>
  );
}
