import { Container, LawChip, SectionHeader } from "@/components/brand";
import { CodeBlock } from "@/components/code-block";
import { PROOFS } from "@/lib/site-content";

const VERIFY_OUTPUT = `$ shio verify --delivery

  ok    post:acme/            200  layout Home  4 regions resolved
  ok    post:acme/launch      200  layout Page  hero, prose, cta
  warn  post:acme/launch      hero image has no alt text
  ok    post:acme/blog/hello  200  layout Article

  4 checked, 0 errors, 1 warning   exit 0`;

export function Verify() {
  return (
    <section id="verify" className="border-y border-border bg-muted/40 py-20">
      <Container>
        <SectionHeader
          eyebrow="P9 &mdash; the perception loop"
          title={
            <>
              The agent cannot see the page.{" "}
              <span className="text-primary">So the page describes itself.</span>
            </>
          }
          description="Asking a human whether it looks right is the round trip that makes an agent slow. Four instruments replace it — three of them text, and the fourth deliberately for a person."
        />

        <div className="grid items-start gap-8 lg:grid-cols-[1fr_1fr] [&>*]:min-w-0">
          <div className="grid gap-4 sm:grid-cols-2 [&>*]:min-w-0">
            {PROOFS.map((p) => (
              <div key={p.command} className="rounded-xl border border-border bg-card p-5">
                <div className="flex size-9 items-center justify-center rounded-lg bg-primary/10 text-primary">
                  <p.icon className="size-4.5" />
                </div>
                <p className="mt-3 font-mono text-xs font-bold text-primary">{p.command}</p>
                <h3 className="mt-1 text-sm font-bold">{p.title}</h3>
                <p className="mt-1.5 text-xs leading-relaxed text-muted-foreground">
                  {p.description}
                </p>
              </div>
            ))}
          </div>

          <div>
            <CodeBlock caption="the proof, as text" code={VERIFY_OUTPUT} />
            <p className="mt-4 text-sm leading-relaxed text-muted-foreground">
              <LawChip id="P9" /> A warning exits 0 on purpose. A gate that fires on
              something a designer signed off is the exit code everybody wraps in{" "}
              <span className="font-mono">|| true</span>, and then the errors stop
              being read too. <span className="font-mono">--strict</span> is there for
              a project that decided otherwise.
            </p>
            <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
              The warnings do not vanish either &mdash;{" "}
              <span className="font-mono text-foreground">shio report</span> restates
              them as open questions in the handoff a human reads.
            </p>
          </div>
        </div>
      </Container>
    </section>
  );
}
