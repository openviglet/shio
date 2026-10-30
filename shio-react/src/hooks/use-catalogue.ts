/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useQuery, useQueryClient } from "@tanstack/react-query";

import { addressKey } from "@/lib/query-keys";
import type { ShPostType } from "@/models/post-type/post-type";
import type { ShSite, ShSiteLocalePolicy } from "@/models/site/site";
import type { ShWidget } from "@/models/widget/widget";
import type { ShTenant } from "@/models/tenant/tenant.model";
import { ShPostTypeService } from "@/services/post-type/post-type.service";
import { ShSiteService } from "@/services/site/site.service";
import { ShWidgetService } from "@/services/widget/widget.service";
import { ShTenantService, type ShCurrentTenant } from "@/services/tenant/tenant.service";
import { ShLocaleService, type ShLocale } from "@/services/locale/locale.service";

/**
 * SH1046 — the three catalogues every screen reads, read once.
 *
 * <h2>What these replace</h2>
 *
 * <p>The same six lines in a dozen files: a service constructed at module scope, a `useState`, and
 * a `useEffect` calling `.query().then(setState).catch(() => setState([]))`. Two things were wrong
 * with it and only one of them is the request count.</p>
 *
 * <p>The **request count** is the one the task is named for. The content browser refetched the
 * entire post-type catalogue inside an effect keyed on the folder id, so walking a tree cost one
 * full catalogue per folder — for a list that decides a default and populates a filter and does not
 * change while somebody navigates.</p>
 *
 * <p>The **error** is the one that was quietly worse. `catch(() => setState([]))` makes "the server
 * did not answer" and "there is nothing here" the same screen, which is the empty state lying. Two
 * pages had already worked around it by hand with a separate `error` state and a comment explaining
 * that `undefined` means loading; the other ten had not. `useQuery` answers the three states apart
 * — `isPending`, `isError`, `data` — so the distinction is the default rather than a thing each
 * page remembers.</p>
 *
 * <h2>The services stay at module scope</h2>
 *
 * <p>One instance each, here. They are stateless wrappers over axios, so sharing the instance saves
 * nothing — which is exactly why `ShioServiceProvider` was dead code (SH1046 deleted it). What is
 * worth sharing is the **answer**, and that is what the key is for.</p>
 */
const postTypeService = new ShPostTypeService();
const siteService = new ShSiteService();
const widgetService = new ShWidgetService();
const tenantService = new ShTenantService();
const localeService = new ShLocaleService();

/** Every post type, as the console's own name-keyed surface reports them. */
export function usePostTypes() {
  return useQuery<ShPostType[]>({
    queryKey: addressKey("postType:"),
    queryFn: () => postTypeService.query(),
  });
}

/**
 * Every site. Read by nine screens, four of them only to fill a picker.
 *
 * @param enabled SH1338 — false defers the read, as the palette does until it is opened, so a route
 *                that never opens it pays nothing for the shortcut
 */
export function useSites({ enabled = true }: { enabled?: boolean } = {}) {
  return useQuery<ShSite[]>({
    queryKey: addressKey("site:"),
    queryFn: () => siteService.query(),
    enabled,
  });
}

/** The widget catalogue — the one list in the console that genuinely never changes at runtime. */
export function useWidgets() {
  return useQuery<ShWidget[]>({
    queryKey: addressKey("widget:"),
    queryFn: () => widgetService.query(),
  });
}

/**
 * SH1151 — the tenant's own two reads, shared rather than made twice.
 *
 * <h2>The measurement that made these hooks worth having</h2>
 *
 * <p>`BentoTenantSwitcher` and `BentoTenantLinkNotice` each read `mine()` and `current()` in their
 * own effect, and `bento.root.page.tsx` <b>mounts both</b>. So the shell asked the server for two
 * answers <b>four times</b> on every visit to the bento root — the exact shape SH1113's per-route
 * budget was built to see, and that route's entry is eight requests. Two components independently
 * correct and collectively wasteful is what a shared read is for (SH1046's argument, one level out
 * from the catalogues).</p>
 *
 * <p>Both are cached under `tenant:`, so `useInvalidate()("tenant:")` after a switch drops them
 * together — which is what the switcher needs, since changing tenant changes both answers.</p>
 */
export function useMyTenants() {
  return useQuery<ShTenant[]>({
    queryKey: addressKey("tenant:mine"),
    queryFn: ({ signal }) => tenantService.mine({ signal }),
  });
}

/** Which tenant this session is in — the other half of the pair above. */
export function useCurrentTenant() {
  return useQuery<ShCurrentTenant>({
    queryKey: addressKey("tenant:current"),
    queryFn: ({ signal }) => tenantService.current({ signal }),
  });
}

/**
 * SH1152 — the locale catalogue and one site's locale policy, shared for the same reason the tenant
 * pair is.
 *
 * <p>`site.edit.page.tsx` and `post-translations.menu.tsx` both read these two, and the menu is
 * mounted from the post editor while the editor's own screens read the same catalogue — so the
 * instance's locale list was fetched once per surface that wanted it. The catalogue is the same answer
 * for every site and never changes at runtime; the policy changes only when somebody edits it, and
 * `useInvalidate()("locale:")` or `("site:")` is what says so.</p>
 */
export function useLocales() {
  return useQuery<ShLocale[]>({
    queryKey: addressKey("locale:"),
    queryFn: ({ signal }) => localeService.list({ signal }),
  });
}

/** One site's declared languages, default and fallbacks (SH1056). */
export function useSiteLocalePolicy(siteId: string | undefined) {
  return useQuery<ShSiteLocalePolicy>({
    queryKey: addressKey(`site:${siteId ?? ""}`),
    queryFn: ({ signal }) => siteService.locales(siteId ?? "", { signal }),
    enabled: Boolean(siteId),
  });
}

/**
 * Forget everything cached under one scheme, after a write.
 *
 * <p>The half a cache is wrong without. `invalidate("postType:")` drops the catalogue <em>and</em>
 * every single type under it, because the scheme is the key's first segment and TanStack matches by
 * prefix — so a rename, a create or a delete leaves no screen showing the old list. Passing a whole
 * address narrows it to one thing: `invalidate("site:Cafeteria")`.</p>
 */
export function useInvalidate() {
  const client = useQueryClient();
  return (address: string) => client.invalidateQueries({ queryKey: addressKey(address) });
}
