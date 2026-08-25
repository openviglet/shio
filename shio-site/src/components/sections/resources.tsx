import { ArrowUpRight } from "lucide-react";
import { Container, SectionHeader } from "@/components/brand";
import { RESOURCES } from "@/lib/site-content";

export function Resources() {
  return (
    <section id="resources" className="border-t border-border bg-muted/40 py-20">
      <Container>
        <SectionHeader
          eyebrow="Start here"
          title="Read it before you run it"
          description="The design is written down, including the parts that are unfinished, and the ones that were deliberately not built."
        />

        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {RESOURCES.map((r) => (
            <a
              key={r.title}
              href={r.href}
              target={r.href.startsWith("http") ? "_blank" : undefined}
              rel={r.href.startsWith("http") ? "noreferrer" : undefined}
              className="group flex flex-col rounded-xl border border-border bg-card p-5 transition-colors hover:border-primary/40"
            >
              <div className="flex size-9 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <r.icon className="size-4.5" />
              </div>
              <h3 className="mt-3 flex items-center gap-1 font-bold">
                {r.title}
                <ArrowUpRight className="size-3.5 opacity-0 transition-opacity group-hover:opacity-100" />
              </h3>
              <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
                {r.description}
              </p>
            </a>
          ))}
        </div>
      </Container>
    </section>
  );
}
