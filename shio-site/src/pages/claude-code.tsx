import { ArrowRight, Ban, Check, ShieldCheck } from "lucide-react";
import { SiteNav } from "@/components/sections/site-nav";
import { SiteFooter } from "@/components/sections/site-footer";
import { PageHero } from "@/components/sections/page-hero";
import { CallToAction } from "@/components/sections/cta";
import { Container, LawChip, SectionHeader, TableScroll } from "@/components/brand";
import { CodeBlock } from "@/components/code-block";
import { Button } from "@/components/ui/button";
import {
  BRIDGE_INSTALL,
  FIRST_CALLS,
  LINKS,
  PLUGIN_INSTALL,
  PLUGIN_PARTS,
  PLUGIN_RULES,
  TOKEN_SCOPES,
} from "@/lib/site-content";

/**
 * SH532: the product's first five minutes, which until now lived only in a
 * repository file nobody browsing a site will open.
 *
 * The order is the order it happens in: install, what that bought you, the
 * three calls a session actually opens with, the credential it runs on, the
 * rules that are expensive to learn by making the mistake, and last the two
 * boundaries, because "what is Shio NOT going to do" is the question a reader
 * of an agent-native CMS is holding the whole way down.
 */
export function ClaudeCodePage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteNav />
      <main className="flex-1">
        <PageHero
          eyebrow="A CMS for Claude Code"
          title={
            <>
              An MCP CMS you hand to Claude Code in{" "}
              <span className="text-gradient">one command</span>
            </>
          }
          lead={
            <>
              Viglet Shio builds every capability for the agent surface first, so
              getting from a running instance to an agent doing work is one install
              and three calls, and the first call is the instance telling the agent
              what it can do.
            </>
          }
        >
          <div className="flex flex-wrap gap-3">
            <Button asChild size="lg">
              <a href="#install">
                Install it <ArrowRight className="size-4" />
              </a>
            </Button>
            <Button
              asChild
              size="lg"
              variant="outline"
              className="border-white/20 bg-white/10 text-white hover:bg-white/20 hover:text-white dark:bg-white/10"
            >
              <a href="#credential">The credential it runs on</a>
            </Button>
          </div>
        </PageHero>

        {/* ── Install ─────────────────────────────────────────────────── */}
        <section id="install" className="py-20">
          <Container>
            <SectionHeader
              eyebrow="Step one"
              title={
                <>
                  Two ways in, and{" "}
                  <span className="text-primary">the second needs no plugin</span>
                </>
              }
              description="The plugin is the shortcut: it arrives with the MCP server wired up and the workflows already written. Everything it does is reachable without it."
            />

            <div className="grid items-start gap-6 lg:grid-cols-2 [&>*]:min-w-0">
              <div>
                <h3 className="mb-3 flex items-center gap-2 font-bold">
                  <span className="flex size-6 items-center justify-center rounded-full bg-primary/12 font-mono text-xs font-black text-primary">
                    A
                  </span>
                  The plugin
                </h3>
                <CodeBlock caption="claude, the marketplace route" code={PLUGIN_INSTALL} />
                <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
                  The plugin's MCP server runs the CLI's own stdio bridge through{" "}
                  <span className="font-mono text-foreground">npx</span>, so there is
                  nothing else to install.
                </p>
              </div>

              <div>
                <h3 className="mb-3 flex items-center gap-2 font-bold">
                  <span className="flex size-6 items-center justify-center rounded-full bg-primary/12 font-mono text-xs font-black text-primary">
                    B
                  </span>
                  The bare bridge
                </h3>
                <CodeBlock caption="any MCP client" code={BRIDGE_INSTALL} />
                <p className="mt-3 text-sm leading-relaxed text-muted-foreground">
                  Same server, no marketplace. You get the twelve tools and six
                  resources and none of the skills or commands, which is the
                  right trade if you are wiring Shio into something that is not
                  Claude Code.
                </p>
              </div>
            </div>
          </Container>
        </section>

        {/* ── What the plugin adds ────────────────────────────────────── */}
        <section id="plugin" className="border-y border-border bg-muted/40 py-20">
          <Container>
            <SectionHeader
              eyebrow="Step two"
              title="A session that starts already knowing Shio"
              description="The skills load when the work calls for them rather than on every turn: a schema you pay for each turn is a schema you pay for whether you use it or not."
            />

            <div className="grid gap-5 md:grid-cols-3 [&>*]:min-w-0">
              {PLUGIN_PARTS.map((part) => (
                <div key={part.title} className="rounded-2xl border border-border bg-card p-6">
                  <div className="flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                    <part.icon className="size-5" />
                  </div>
                  <h3 className="mt-4 font-bold">{part.title}</h3>
                  <dl className="mt-4 space-y-3">
                    {part.items.map((item) => (
                      <div key={item.name}>
                        <dt className="font-mono text-xs font-bold break-words text-primary">
                          {item.name}
                        </dt>
                        <dd className="mt-0.5 text-xs leading-relaxed text-muted-foreground">
                          {item.gloss}
                        </dd>
                      </div>
                    ))}
                  </dl>
                </div>
              ))}
            </div>
          </Container>
        </section>

        {/* ── The first three calls ───────────────────────────────────── */}
        <section id="first-calls" className="py-20">
          <Container>
            <SectionHeader
              eyebrow="Step three"
              title={
                <>
                  What a session{" "}
                  <span className="text-primary">actually opens with</span>
                </>
              }
              description="Three calls, in this order, whether the agent came through the plugin or through a bare bridge. The first two are discovery and the third is the work."
            />

            <ol className="space-y-4">
              {FIRST_CALLS.map((c, i) => (
                <li
                  key={c.call}
                  className="grid gap-4 rounded-xl border border-border bg-card p-6 sm:grid-cols-[auto_1fr] [&>*]:min-w-0"
                >
                  <span className="flex size-8 items-center justify-center rounded-full bg-primary/12 font-mono text-sm font-black text-primary">
                    {i + 1}
                  </span>
                  <div>
                    <p className="text-sm font-semibold text-muted-foreground">{c.step}</p>
                    <pre className="mt-2 overflow-x-auto rounded-lg bg-[#17100e] px-3 py-2.5 font-mono text-[0.75rem] leading-relaxed text-[#ddd6fe]">
                      <code>{c.call}</code>
                    </pre>
                    <p className="mt-3 text-sm leading-relaxed">{c.gets}</p>
                    <span className="mt-3 inline-flex rounded-md border border-border bg-muted px-2 py-0.5 font-mono text-[0.7rem] font-semibold text-muted-foreground">
                      {c.cost}
                    </span>
                  </div>
                </li>
              ))}
            </ol>

            <p className="mx-auto mt-8 max-w-3xl text-center text-sm leading-relaxed text-muted-foreground">
              <LawChip id="P2" /> Over MCP, the second and third calls are tools,{" "}
              <span className="font-mono text-foreground">shio_context</span> and{" "}
              <span className="font-mono text-foreground">shio_write</span>, and the
              first is a resource a client reads directly,{" "}
              <span className="font-mono text-foreground">shio://manifest</span>, beside{" "}
              <span className="font-mono text-foreground">shio://context</span> and{" "}
              <span className="font-mono text-foreground">shio://ops</span>.
            </p>
          </Container>
        </section>

        {/* ── The credential ──────────────────────────────────────────── */}
        <section id="credential" className="border-y border-border bg-muted/40 py-20">
          <Container>
            <SectionHeader
              eyebrow="The credential"
              title={
                <>
                  A scope of its own, and{" "}
                  <span className="text-primary">deliberately not a superset</span>
                </>
              }
              description="An agent needs to write, and the two credentials that already existed were both wrong: one can patch live content outside the agent protocol's guards, and the other is every capability the account holder has, with no expiry."
            />

            <TableScroll
              fadeClassName="from-muted/40"
              className="rounded-xl border border-border bg-card"
            >
              <table className="w-full min-w-[42rem] border-collapse text-sm">
                <thead>
                  <tr className="border-b border-border">
                    <th className="sticky left-0 bg-card px-4 py-3 text-left font-semibold">
                      Scope
                    </th>
                    <th className="px-4 py-3 text-left font-semibold">Reads</th>
                    <th className="px-4 py-3 text-left font-semibold">Writes</th>
                  </tr>
                </thead>
                <tbody>
                  {TOKEN_SCOPES.map((s) => (
                    <tr
                      key={s.scope}
                      className={
                        s.agent
                          ? "border-b border-border/60 bg-primary/5 last:border-0"
                          : "border-b border-border/60 last:border-0"
                      }
                    >
                      <td className="sticky left-0 bg-inherit px-4 py-3">
                        <span
                          className={
                            s.agent
                              ? "font-mono text-xs font-bold text-primary"
                              : "font-mono text-xs font-bold"
                          }
                        >
                          {s.scope}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">{s.reads}</td>
                      <td className="px-4 py-3 text-muted-foreground">{s.writes}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </TableScroll>

            <div className="mx-auto mt-8 max-w-3xl rounded-xl border border-border bg-background/60 p-5">
              <h3 className="flex items-center gap-2 text-sm font-bold">
                <ShieldCheck className="size-4 text-primary" />
                The invariant this exists to hold
              </h3>
              <p className="mt-2 text-sm leading-relaxed text-muted-foreground">
                The agent path is never a privilege escalation, and since{" "}
                <span className="font-mono text-foreground">AGENT</span> exists, it is not
                a privilege <em>superset</em> either. It reads like a preview token,
                because an agent works on drafts; it writes on{" "}
                <span className="font-mono text-foreground">/api/v2/agent/**</span> and{" "}
                <span className="font-mono text-foreground">/mcp</span> and nowhere else,
                so it cannot reach the delivery write path that patches published content
                directly. Tenant isolation and the content ACL apply identically on every
                surface. <LawChip id="P7" />
              </p>
            </div>
          </Container>
        </section>

        {/* ── The rules ───────────────────────────────────────────────── */}
        <section id="rules" className="py-20">
          <Container>
            <div className="grid items-start gap-10 lg:grid-cols-[1fr_1fr] [&>*]:min-w-0">
              <div>
                <SectionHeader
                  eyebrow="What it encodes"
                  title="Four rules, all of them expensive to learn the other way"
                />
                <ul className="space-y-3">
                  {PLUGIN_RULES.map((rule) => (
                    <li key={rule} className="flex gap-3">
                      <Check className="mt-0.5 size-4 shrink-0 text-primary" />
                      <span className="text-sm leading-relaxed">{rule}</span>
                    </li>
                  ))}
                </ul>
                <p className="mt-6 text-sm leading-relaxed text-muted-foreground">
                  They are in the plugin so a session does not have to discover them, and
                  in{" "}
                  <span className="font-mono text-foreground">AGENTS.fragment.md</span>{" "}
                  so they survive a session that does not have the plugin.
                </p>
              </div>

              <div className="rounded-2xl border border-border bg-card p-6">
                <h3 className="flex items-center gap-2 font-bold">
                  <Ban className="size-4 text-muted-foreground" />
                  Two things Shio will not do
                </h3>
                <div className="mt-4 space-y-4 text-sm leading-relaxed text-muted-foreground">
                  <p>
                    <span className="font-semibold text-foreground">
                      It hosts no model and runs no prompts.
                    </span>{" "}
                    There is no provider API key in the CMS, no bundled inference and no
                    "generate this page for me" button. Shio is the substrate your agent
                    drives; the agent is yours, and so is the model behind it.
                  </p>
                  <p>
                    <span className="font-semibold text-foreground">
                      It does not make MCP the only door.
                    </span>{" "}
                    Every capability lands on MCP, the CLI and REST, in that order and
                    by law. A reader who does not use Claude Code loses the skills and the
                    slash commands, and nothing else. <LawChip id="P1" />
                  </p>
                </div>
                <p className="mt-5 border-t border-border pt-4 text-xs text-muted-foreground">
                  <a
                    href={LINKS.repo}
                    target="_blank"
                    rel="noreferrer"
                    className="underline underline-offset-4 hover:text-foreground"
                  >
                    claude-plugin/
                  </a>{" "}
                  in the repository is the source for everything on this page.
                </p>
              </div>
            </div>
          </Container>
        </section>

        <CallToAction />
      </main>
      <SiteFooter />
    </div>
  );
}
