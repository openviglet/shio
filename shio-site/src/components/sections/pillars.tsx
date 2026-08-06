import { ArrowRight } from "lucide-react";
import { Container, SectionHeader } from "@/components/brand";
import { PILLARS } from "@/lib/site-content";
import { cn } from "@/lib/utils";

export function Pillars() {
  return (
    <section id="pillars" className="py-20">
      <Container>
        <SectionHeader
          eyebrow="The product"
          title={<>Six surfaces, one operator</>}
          description="Each of these exists because a coding agent needed it, and each one has a CLI verb and an MCP tool before it has a console screen."
        />

        <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {PILLARS.map((p) => (
            <a
              key={p.title}
              href={p.anchor}
              className="group flex flex-col rounded-2xl border border-border bg-card p-6 transition-all hover:-translate-y-0.5 hover:border-primary/40 hover:shadow-lg"
            >
              <div
                className={cn(
                  "flex size-11 items-center justify-center rounded-xl bg-gradient-to-br text-white shadow-sm",
                  p.iconClass
                )}
              >
                <p.icon className="size-5" />
              </div>
              <h3 className="mt-4 text-lg font-bold">{p.title}</h3>
              <p className="mt-2 flex-1 text-sm leading-relaxed text-muted-foreground">
                {p.description}
              </p>
              <span className="mt-4 inline-flex items-center gap-1 text-sm font-semibold text-primary">
                See it
                <ArrowRight className="size-3.5 transition-transform group-hover:translate-x-0.5" />
              </span>
            </a>
          ))}
        </div>
      </Container>
    </section>
  );
}
