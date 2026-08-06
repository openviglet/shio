import { Container, Eyebrow, LawChip } from "@/components/brand";
import { CodeBlock } from "@/components/code-block";
import { TEACHING_ERROR } from "@/lib/site-content";

const FIELDS = [
  { key: "title / detail", gloss: "what went wrong, in words the caller can log" },
  { key: "didYouMean", gloss: "the near miss, when the mistake was a typo" },
  { key: "allowed", gloss: "the closed set, so the next call is a choice not a guess" },
  { key: "fix", gloss: "the instruction — what to do instead, in one sentence" },
  { key: "example", gloss: "a call that would have worked, ready to send" },
];

export function Errors() {
  return (
    <section id="errors" className="py-20">
      <Container>
        <div className="grid items-center gap-10 lg:grid-cols-[1fr_1fr]">
          <div>
            <Eyebrow>P6 &mdash; failure</Eyebrow>
            <h2 className="mt-3 text-3xl font-extrabold tracking-tight sm:text-4xl">
              A 400 that costs one retry,{" "}
              <span className="text-primary">not one session</span>
            </h2>
            <p className="mt-4 text-base leading-relaxed text-muted-foreground">
              An agent that gets <span className="font-mono text-foreground">Bad Request</span>{" "}
              and nothing else does the expensive thing: it goes back and re-reads
              documentation, or it guesses. Both cost more than the call did. So every
              4xx on the agent surface carries the fix in the body.
            </p>
            <dl className="mt-6 space-y-3">
              {FIELDS.map((f) => (
                <div key={f.key} className="flex gap-3">
                  <dt className="w-32 shrink-0 font-mono text-xs font-bold text-primary">
                    {f.key}
                  </dt>
                  <dd className="text-sm text-muted-foreground">{f.gloss}</dd>
                </div>
              ))}
            </dl>
            <p className="mt-6 text-sm leading-relaxed text-muted-foreground">
              <LawChip id="P6" /> The same rule applies to the CLI: a refusal that
              teaches nothing is treated as a defect and gets filed like one.
            </p>
          </div>

          <CodeBlock caption="400 — and what to do about it" code={TEACHING_ERROR} />
        </div>
      </Container>
    </section>
  );
}
