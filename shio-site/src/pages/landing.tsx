import { SiteNav } from "@/components/sections/site-nav";
import { Hero } from "@/components/sections/hero";
import { Paths } from "@/components/sections/paths";
import { UseCases } from "@/components/sections/use-cases";
import { Roles } from "@/components/sections/roles";
import { Showcase } from "@/components/sections/showcase";
import { Pillars } from "@/components/sections/pillars";
import { CuratorHalf } from "@/components/sections/curator-half";
import { DepthIndex } from "@/components/sections/depth-index";
import { SelfHost } from "@/components/sections/self-host";
import { Resources } from "@/components/sections/resources";
import { CallToAction } from "@/components/sections/cta";
import { SiteFooter } from "@/components/sections/site-footer";

/**
 * The landing page: the marketing front door.
 *
 * The section order is an argument, not a feature list. It opens on the same page built by a
 * person and by an agent, then the four ways in, then who runs it, so a visitor who wants an
 * ordinary CMS learns in the first screenful that it suits them.
 *
 * SH1372: each remaining section helps a visitor decide something, and the depth moved to the page
 * that is about it. The home was 36,097 pixels tall at 390 wide with twenty sections; the ten
 * depth sections (laws, one call, the surface, errors, verify, files, blueprints, replicate,
 * delivery, compare) are now one index whose cards keep their anchors. The agent protocol renders
 * in full on /claude-code/, the rest on its own page.
 *
 * what it is (two sessions) → which way in → who runs it → a finished site → the three seats → what
 * it can do → what stops an agent → the depth, indexed → run it → resources → install.
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
        <Pillars />
        <CuratorHalf />
        <DepthIndex />
        <SelfHost />
        <Resources />
        <CallToAction />
      </main>
      <SiteFooter />
    </div>
  );
}
