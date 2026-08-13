import { SiteNav } from "@/components/sections/site-nav";
import { Hero } from "@/components/sections/hero";
import { Roles } from "@/components/sections/roles";
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
 * The landing page — the marketing front door.
 *
 * The section order is an argument, not a feature list. Turing's equivalent
 * opens on a live search box because a visitor can type a query and judge the
 * answer; here the hook is a session, because what is being sold is how cheaply
 * an agent operates the product.
 *
 * hook (a session) → who does what → the contract → the map → then one section
 * per law, in the order an agent meets them: discover (P2/P3) → author (P4/P5) →
 * the surface (P1/P10) → fail (P6) → prove (P9) → what stops it (P7) → skip
 * steps (P8) → replicate → deliver → why not the alternative → run it → convert.
 */
export function LandingPage() {
  return (
    <div className="flex min-h-screen flex-col">
      <SiteNav />
      <main className="flex-1">
        <Hero />
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
