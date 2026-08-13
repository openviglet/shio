import { Container, SectionHeader } from "@/components/brand";
import { Card } from "@/components/ui/card";
import { ROLES } from "@/lib/site-content";

/**
 * The positioning statement, as three cards. Everything else on the page is a
 * consequence of this split, so it sits directly under the hero — a reader who
 * only ever sees "the agent writes the site" concludes, correctly, that nobody
 * is minding it.
 */
export function Roles() {
  return (
    <section id="roles" className="py-20">
      <Container>
        <SectionHeader
          eyebrow="Three roles"
          title={
            <>
              The agent builds. The curator approves.{" "}
              <span className="text-primary">The API delivers.</span>
            </>
          }
          description="Not one operator with a chat box bolted on — three, each with the surface it is actually good at."
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
      </Container>
    </section>
  );
}
