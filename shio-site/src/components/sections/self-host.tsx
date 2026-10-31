import { Database, Server, Users } from "lucide-react";
import { Container, SectionHeader } from "@/components/brand";
import { CopyCommand } from "@/components/copy-command";
import { CodeBlock } from "@/components/code-block";

const FACTS = [
  {
    icon: Server,
    title: "Spring Boot 4, Java 21",
    description:
      "One JVM service. The content engine, the agent gateway, the MCP server and the renderer are the same process. Nothing to orchestrate on day one.",
  },
  {
    icon: Database,
    title: "Your database",
    description:
      "The schema is Liquibase's. H2 out of the box for a trial; PostgreSQL and MariaDB are proved on every release by containerized integration tests.",
  },
  {
    icon: Users,
    title: "Multi-tenant, if you need it",
    description:
      "Tenant isolation reaches every surface, including the agent's. The agent path is not a privilege escalation: the same scopes apply on all of them.",
  },
];

const COMPOSE = `services:
  shio:
    image: ghcr.io/openviglet/shio:2026.3
    ports:
      - 2710:2710
    volumes:
      - ./volumes/shio/store:/app/store
    environment:
      SPRING_DATASOURCE_URL: jdbc:mariadb://shio-mariadb:3306/shio
      SPRING_DATASOURCE_USERNAME: shio
      SPRING_DATASOURCE_PASSWORD: shio`;

export function SelfHost() {
  return (
    <section id="self-host" className="py-20">
      <Container>
        <SectionHeader
          eyebrow="Run it"
          title={
            <>
              Your infrastructure,{" "}
              <span className="text-primary">your content, your call</span>
            </>
          }
          description="Nothing here phones home, and there is no hosted tier you are being funnelled towards. Clone it, read it, run it."
        />

        <div className="grid items-start gap-8 lg:grid-cols-[1fr_1fr] [&>*]:min-w-0">
          <div className="space-y-4">
            {FACTS.map((f) => (
              <div key={f.title} className="flex gap-4 rounded-xl border border-border bg-card p-5">
                <div className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                  <f.icon className="size-5" />
                </div>
                <div className="min-w-0">
                  <h3 className="font-bold">{f.title}</h3>
                  <p className="mt-1 text-sm leading-relaxed text-muted-foreground">
                    {f.description}
                  </p>
                </div>
              </div>
            ))}
          </div>

          <div>
            <CodeBlock caption="docker-compose.yaml" code={COMPOSE} />
            <p className="mt-5 text-sm font-semibold">Then hand it to the agent:</p>
            <CopyCommand
              command="claude mcp add shio -- npx @viglet/shio mcp"
              className="mt-2 border-border bg-card text-foreground"
            />
            <p className="mt-3 text-xs leading-relaxed text-muted-foreground">
              The bridge speaks stdio, so it works from any MCP client, and the
              agent's first call is the manifest, which tells it everything this
              particular instance can do.
            </p>
          </div>
        </div>
      </Container>
    </section>
  );
}
