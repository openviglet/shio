import { Container, SectionHeader } from "@/components/brand";
import { Card } from "@/components/ui/card";
import { ROLES } from "@/lib/site-content";

/**
 * Three seats. The sentence under the cards is the part that matters, not
 * the cards. The earlier version of this section read "the agent builds, the
 * curator approves", which describes one arrangement as though it were the only
 * one and quietly demotes the person to a reviewer. A person can hold every
 * seat; the agent's can stay empty forever.
 */
export function Roles() {
  return (
    <section id="roles" className="py-20">
      <Container>
        <SectionHeader
          eyebrow="Three seats"
          title={
            <>
              The person creates and decides. The agent builds, if there is one.{" "}
              <span className="text-primary">The API delivers.</span>
            </>
          }
          description="Three seats over one content engine, each with the surface it is actually good at, and any of them can be empty."
        />

        <div className="grid gap-6 md:grid-cols-3">
          {ROLES.map((role) => (
            <Card key={role.actor} className="gap-4 p-6">
              <div className="flex size-11 items-center justify-center rounded-xl bg-primary/10 text-primary">
                <role.icon className="size-5" />
              </div>
              <div>
                <h3 className="text-lg font-bold">
                  {role.actor}{" "}
                  <span className="font-mono text-sm font-semibold text-primary">
                    {role.verb}
                  </span>
                </h3>
                <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                  {role.description}
                </p>
              </div>
              <ul className="mt-auto flex flex-wrap gap-1.5">
                {role.surface.map((s) => (
                  <li
                    key={s}
                    className="rounded-md border border-border bg-muted px-2 py-0.5 font-mono text-[0.7rem] font-medium text-muted-foreground"
                  >
                    {s}
                  </li>
                ))}
              </ul>
            </Card>
          ))}
        </div>

        <p className="mx-auto mt-8 max-w-3xl text-center text-sm leading-relaxed text-muted-foreground">
          One person can hold all three. Most instances start that way: somebody
          creates the site, writes the pages and points a front end at them, and no
          agent is ever issued a token. The seats describe what a surface is{" "}
          <em>for</em>, not a staffing requirement.
        </p>
      </Container>
    </section>
  );
}
