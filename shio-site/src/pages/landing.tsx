import { SiteNav } from "@/components/sections/site-nav";
import { Hero } from "@/components/sections/hero";
import { Paths } from "@/components/sections/paths";
import { UseCases } from "@/components/sections/use-cases";
import { Roles } from "@/components/sections/roles";
import { Showcase } from "@/components/sections/showcase";
import { Laws } from "@/components/sections/laws";
import { Pillars } from "@/components/sections/pillars";
import { OneCall } from "@/components/sections/one-call";
import { ContentFiles } from "@/components/sections/content-files";
import { Surface } from "@/components/sections/surface";
import { Errors } from "@/components/sections/errors";
import { Verify } from "@/components/sections/verify";
import { CuratorHalf } from "@/components/sections/curator-half";
import { Blueprints } from "@/components/sections/blueprints";
import { Replicate } from "@/components/sections/replicate";
import { Delivery } from "@/components/sections/delivery";
import { Comparison } from "@/components/sections/comparison";
import { SelfHost } from "@/components/sections/self-host";
import { Resources } from "@/components/sections/resources";
import { CallToAction } from "@/components/sections/cta";
import { SiteFooter } from "@/components/sections/site-footer";

/**
 * The landing page: the marketing front door.
 *
 * The section order is an argument, not a feature list, and the argument was
 * wrong in its first two screenfuls. It opened on an agent session and led with
 * "your agent builds the site, you approve it", true of one way to use the
 * product, and read by everybody else as *you cannot use this by hand*. Nothing
 * about the product said that; the page did. A visitor who wants an ordinary
 * CMS was being turned away by the headline of a CMS that would have suited
 * them.
 *
 * So the hook is now two sessions under one switch (the same page built by a
 * person and by an agent), and the section immediately below it is the four
 * ways in, followed by six deployments of which only three involve an agent.
 * The depth sections that follow are unchanged and still ordered by law,
 * because once a reader knows which door is theirs, the argument about protocol
 * quality is the interesting part rather than the disqualifying one.
 *
 * SH1258 put a finished site (the showcase) straight after the deployments, so the proof that
 * the surfaces add up to something comes before the argument about each one.
 *
 * hook (two sessions) → the four ways in → who runs it → a finished site → the three seats → the
 * contract → the map → then one section per law, in the order an agent meets
 * them: discover (P2/P3) → author (P4/P5) → the surface (P1/P10) → fail (P6) →
 * prove (P9) → what stops it (P7) → skip steps (P8) → replicate → deliver → why
 * not the alternative → run it → convert.
 */
export function LandingPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteNav />
      <main className="flex-1">
        <Hero />
        <Paths />
        <UseCases />
        <Showcase />
        <Roles />
        <Laws />
        <Pillars />
        <OneCall />
        <ContentFiles />
        <Surface />
        <Errors />
        <Verify />
        <CuratorHalf />
        <Blueprints />
        <Replicate />
        <Delivery />
        <Comparison />
        <SelfHost />
        <Resources />
        <CallToAction />
      </main>
      <SiteFooter />
    </div>
  );
}
