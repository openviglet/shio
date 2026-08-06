import { ArrowUpRight } from "lucide-react";
import { Container, SectionHeader } from "@/components/brand";
import { CodeBlock } from "@/components/code-block";
import { PACKAGES } from "@/lib/site-content";

const DELIVERY_SNIPPET = `import { createShioClient } from "@viglet/shio-client";

const shio = createShioClient({
  url: "https://cms.example.com",
  token: process.env.SHIO_CDA_TOKEN,
});

// Zero runtime dependencies — Node, browser, edge, server components.
const page = await shio.post("acme", "launch");`;

export function Delivery() {
  return (
    <section id="delivery" className="py-20">
      <Container>
        <SectionHeader
          eyebrow="Delivery"
          title={
            <>
              Headless where it counts:{" "}
              <span className="text-primary">the front end is yours</span>
            </>
          }
          description="REST and GraphQL, a zero-dependency TypeScript client, a React SDK, a shared section vocabulary and a Next.js starter that is generated rather than copied out of a tutorial."
        />

        <div className="grid items-start gap-8 lg:grid-cols-[0.95fr_1.05fr] [&>*]:min-w-0">
          <div>
            <CodeBlock caption="the delivery client" code={DELIVERY_SNIPPET} />
            <div className="mt-4 rounded-xl border border-border bg-card p-5">
              <p className="font-mono text-xs font-bold text-primary">
                npm create @viglet/shio-app
              </p>
              <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
                Scaffolds a Next.js App Router site already wired to a site's content
                &mdash; and with <span className="font-mono">--blueprint</span>, its
                content half in the same command.
              </p>
            </div>
          </div>

          <ul className="grid gap-3 [&>*]:min-w-0">
            {PACKAGES.map((p) => (
              <li key={p.name}>
                <a
                  href={p.href}
                  target="_blank"
                  rel="noreferrer"
                  className="group flex gap-3 rounded-xl border border-border bg-card p-4 transition-colors hover:border-primary/40"
                >
                  <span className="min-w-0">
                    <span className="flex items-center gap-1.5 font-mono text-sm font-bold text-primary">
                      {p.name}
                      <ArrowUpRight className="size-3.5 shrink-0 opacity-0 transition-opacity group-hover:opacity-100" />
                    </span>
                    <span className="mt-1 block text-xs leading-relaxed text-muted-foreground">
                      {p.gloss}
                    </span>
                  </span>
                </a>
              </li>
            ))}
          </ul>
        </div>
      </Container>
    </section>
  );
}
