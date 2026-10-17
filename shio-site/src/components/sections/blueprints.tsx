import { Container, LawChip, SectionHeader } from "@/components/brand";
import { BLUEPRINT_STEPS } from "@/lib/site-content";

export function Blueprints() {
  return (
    <section id="blueprints" className="py-20">
      <Container>
        <SectionHeader
          eyebrow="P8 &middot; skipping steps"
          title={
            <>
              A recipe is prose.{" "}
              <span className="text-primary">A blueprint applies.</span>
            </>
          }
          description="Everything an agent is told to do in a tutorial is something it could instead be handed as a unit: post types, folders, content and the files a front end needs, converged in one transaction."
        />

        <ol className="grid gap-4 md:grid-cols-3 [&>*]:min-w-0">
          {BLUEPRINT_STEPS.map((s, i) => (
            <li key={s.title} className="relative rounded-xl border border-border bg-card p-5">
              <span className="font-mono text-xs font-bold text-muted-foreground">
                {String(i + 1).padStart(2, "0")}
              </span>
              <h3 className="mt-1 font-bold">{s.title}</h3>
              <pre className="mt-3 overflow-x-auto rounded-lg bg-[#17100e] px-3 py-2.5 font-mono text-[0.72rem] leading-relaxed text-[#ddd6fe]">
                <code>{s.code}</code>
              </pre>
              <p className="mt-2.5 text-xs leading-relaxed text-muted-foreground">{s.gloss}</p>
            </li>
          ))}
        </ol>

        <p className="mx-auto mt-8 max-w-3xl text-center text-sm leading-relaxed text-muted-foreground">
          <LawChip id="P8" /> An apply is idempotent and can be planned first, so
          running it twice is not a mistake and running it once is not a leap of
          faith.
        </p>
      </Container>
    </section>
  );
}
