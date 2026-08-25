import { Navigate, Route, Routes } from "react-router-dom"
import BentoHomePage from "./app/bento/home/home.page"
import BentoFolderEditPage from "./app/bento/content/folder.edit.page"
import BentoFolderNewPage from "./app/bento/content/folder.new.page"
import BentoPostTypeSelectPage from "./app/bento/content/post-type-select.page"
import BentoSiteEditPage from "./app/bento/content/site.edit.page"
import BentoSiteImportPage from "./app/bento/content/site.import.page"
import BentoSiteListPage from "./app/bento/content/site.list.page"
import BentoSiteNewPage from "./app/bento/content/site.new.page"
import BentoRootPage from "./app/bento/bento.root.page"
import BentoVocabularyPage from "./app/bento/bento-vocabulary.page"
import BentoGroupEditPage from "./app/bento/admin/group.edit.page"
import BentoGroupListPage from "./app/bento/admin/group.list.page"
import BentoRoleEditPage from "./app/bento/admin/role.edit.page"
import BentoRoleListPage from "./app/bento/admin/role.list.page"
import BentoUserListPage from "./app/bento/admin/user.list.page"
import AdminUserPage from "./app/console/admin/admin.user.page"
import BentoApiTokenListPage from "./app/bento/admin/api-token.list.page"
import BentoAuthProviderListPage from "./app/bento/admin/auth-provider.list.page"
import BentoExchangeProviderListPage from "./app/bento/admin/exchange-provider.list.page"
import BentoSiteWebhookListPage from "./app/bento/admin/site-webhook.list.page"
import BentoTenantListPage from "./app/bento/admin/tenant.list.page"
import ApiTokenPage from "./app/console/admin/api-token/api-token.page"
import AuthProviderPage from "./app/console/admin/auth-provider/auth-provider.page"
import EmailConfigPage from "./app/console/admin/email-config/email-config.page"
import ExchangeProviderPage from "./app/console/admin/exchange-provider/exchange-provider.page"
import SiteWebhookPage from "./app/console/admin/site-webhook/site-webhook.page"
import TenantPage from "./app/console/admin/tenant/tenant.page"
import ActivityListPage from "./app/console/admin/activity/activity.list.page"
import MarketplaceListPage from "./app/console/admin/marketplace/marketplace.list.page"
import ReviewListPage from "./app/console/admin/review/review.list.page"
import EditorSitesPage from "./app/console/universal-editor/editor-sites.page"
import VisualEditorPage from "./app/console/universal-editor/visual-editor.page"
import ObjectChildrenPage from "./app/console/content/object.children.page"
import PostEditPage from "./app/console/content/post.edit.page"
import PostNewPage from "./app/console/content/post.new.page"
import PostTypeEditPage from "./app/console/content/post-type.edit.page"
import PostTypeListPage from "./app/console/content/post-type.list.page"
import PostTypeNewPage from "./app/console/content/post-type.new.page"
import SearchPage from "./app/console/content/search.page"
import StaticFileListPage from "./app/console/content/static-file.list.page"
import TrashPage from "./app/console/content/trash.page"
import NotFoundPage from "./app/console/not-found.page"
import LoginPage from "./app/login/login.page"
import SetupPage from "./app/setup/setup.page"
import { BentoGraphQLRoute } from "./app/routes"
import LegacyConsoleRedirect from "./app/legacy-console-redirect"
import { LEGACY_CONSOLE_PATTERNS, ROUTES } from "./app/routes.const"
import { SectionCardChromeProvider } from "@viglet/viglet-design-system/bento"
import { ThemeProvider } from "./components/theme-provider"
import { SHIO_DEFAULT_THEME, SHIO_THEME_STORAGE_KEY } from "./theme"
import { Toaster } from "./components/ui/sonner"
import { ShioServiceProvider } from "./contexts/ShioServiceContext"
import { BreadcrumbProvider } from "./contexts/breadcrumb.context"

function App() {
  return (
    <div className="App">
      {/* SH918 — the same two constants the boot-loader seed reads, so the splash and the app
          cannot disagree about the theme. Spelling them here again is what caused the defect. */}
      <ThemeProvider defaultTheme={SHIO_DEFAULT_THEME} storageKey={SHIO_THEME_STORAGE_KEY}>
        <BreadcrumbProvider>
          <ShioServiceProvider>
            <Toaster />
            <Routes>
              {/* SH909 — the default. `ROUTES.CONSOLE` is `/bento` now, and this line did not change. */}
              <Route path={ROUTES.ROOT} element={<Navigate to={ROUTES.CONSOLE} replace />} />
              <Route path={ROUTES.SETUP} element={<SetupPage />} />
              <Route path={ROUTES.LOGIN} element={<LoginPage />} />
              {/*
                SH887 — the bento layer's proving page. Top-level, no nav entry, no data: it
                composes 23 of the layer's runtime exports so a break in the package is found
                here rather than four surfaces into a slice. It was mounted outside the console
                shell because the two chromes could not nest; SH909 removed that shell, and the
                route stays top-level because a harness belongs outside the product's own shell.
              */}
              <Route path={ROUTES.BENTO_VOCABULARY} element={<BentoVocabularyPage />} />
              {/*
                SH907 — the Universal Editor is mounted OUTSIDE the bento shell, and this is the
                one route in Block AR placed by measurement rather than by pattern.

                `visual-editor.page.tsx` is `flex flex-col h-screen`: it owns the viewport, draws
                its own toolbar and splits the rest between a preview iframe and a properties
                panel. Inside the shell it is 100vh *plus* the shell's 64px `sticky top-0` header,
                so — measured in a browser at 1600x1000 — the iframe's top moves from 81 to 137
                and its bottom goes from 992 (visible) to 1048 (**below the fold**), while two
                sticky bars stack at the top of the page.

                §LV.10 predicted exactly this: "the bridge depends on what surrounds the frame —
                the sizing, the scroll container, the overlay stacking — and the bento shell
                changes all three". The answer is not to fight the shell with a taller container;
                it is that a surface owning the viewport has no chrome to migrate. It renders the
                same in both eras because it renders neither era's chrome.
              */}
              <Route path="/bento/universal-editor/:siteId" element={<VisualEditorPage />} />
              {/*
                SH888 — the bento route tree, which since SH909 is **the** route tree.

                It was built beside the console one because the two shells could not nest: the
                console root mounted a sidebar provider and its header reached for that context,
                while the bento shell ships none — the rail is fixed, one width, hidden below md.
                So every surface moved under its own task (SH898–SH908) and each slice stayed
                independently reversible, which across 43 pages was worth two shells for a block.

                SH909 claimed the reversal rather than leaving it: the console tree is gone, its
                URLs redirect here, and `bento-is-the-default.test.mjs` is what stops a second
                shell from arriving again in a diff that had a good local reason.

                SH898 — the index is the home page, the first migrated surface.
              */}
              <Route path={ROUTES.BENTO} element={<BentoRootPage />}>
                <Route index element={<BentoHomePage />} />
                {/*
                  SH899 — the site slice. `new` and `import` are declared BEFORE `:siteId`, or
                  the parameter route matches them first and the editor tries to load a site
                  called "new" — a 404 behind a form, which is the shape SH921 was about.
                */}
                <Route path="content" element={<BentoSiteListPage />} />
                <Route path="content/site/new" element={<BentoSiteNewPage />} />
                <Route path="content/site/import" element={<BentoSiteImportPage />} />
                <Route path="content/site/:siteId" element={<BentoSiteEditPage />} />
                {/*
                  SH900 — the content browser is the SAME component the console mounts, wrapped in
                  the provider that flips its chrome. 965 lines, of which four were chrome: copying
                  it the way SH898 and SH899 copied their surfaces would have duplicated the most
                  defect-prone code in the console so that a header and three panels could differ,
                  and every fix would then need landing twice until SH909.
                */}
                <Route
                  path="content/object/:objectId"
                  element={
                    <SectionCardChromeProvider chrome="bento">
                      <ObjectChildrenPage />
                    </SectionCardChromeProvider>
                  }
                />
                <Route path="content/folder/:folderId/edit" element={<BentoFolderEditPage />} />
                <Route path="content/folder/:folderId/new-folder" element={<BentoFolderNewPage />} />
                {/*
                  SH901 — the post editor and the new-post flow are the SAME components the
                  console mounts, under the provider. 375 and 133 lines of save/publish/preview/
                  schedule handling around fifteen dynamic widgets, of which the chrome is a
                  header, a save bar and one panel: the adapter case, exactly as SH900 framed it.
                  The post-type chooser is a copy, because its cards are choices rather than
                  chrome and their bento counterpart is a tile.
                */}
                <Route
                  path="content/post/:postId"
                  element={
                    <SectionCardChromeProvider chrome="bento">
                      <PostEditPage />
                    </SectionCardChromeProvider>
                  }
                />
                <Route
                  path="content/folder/:folderId/post-type/:postTypeName/new"
                  element={
                    <SectionCardChromeProvider chrome="bento">
                      <PostNewPage />
                    </SectionCardChromeProvider>
                  }
                />
                <Route
                  path="content/folder/:folderId/post-type-select"
                  element={<BentoPostTypeSelectPage />}
                />
                {/*
                  SH902 — the three post-type pages, all adapter surfaces. `new` before `:name`,
                  or the editor tries to load a post type called "new".
                */}
                <Route
                  path="content/post-type"
                  element={
                    <SectionCardChromeProvider chrome="bento">
                      <PostTypeListPage />
                    </SectionCardChromeProvider>
                  }
                />
                <Route
                  path="content/post-type/new"
                  element={
                    <SectionCardChromeProvider chrome="bento">
                      <PostTypeNewPage />
                    </SectionCardChromeProvider>
                  }
                />
                <Route
                  path="content/post-type/:postTypeName"
                  element={
                    <SectionCardChromeProvider chrome="bento">
                      <PostTypeEditPage />
                    </SectionCardChromeProvider>
                  }
                />
                {/*
                  SH903 — the three surfaces where rendering nothing is the normal case. All
                  adapter surfaces: none of the three creates anything, so `BentoListPage` —
                  which requires a `newRoute` and renders a create-tile — is the wrong shell for
                  all of them, and the mosaic §LV.6 asked for is not what these pages are.
                */}
                <Route
                  path="content/search"
                  element={
                    <SectionCardChromeProvider chrome="bento">
                      <SearchPage />
                    </SectionCardChromeProvider>
                  }
                />
                <Route
                  path="content/trash"
                  element={
                    <SectionCardChromeProvider chrome="bento">
                      <TrashPage />
                    </SectionCardChromeProvider>
                  }
                />
                <Route
                  path="content/files"
                  element={
                    <SectionCardChromeProvider chrome="bento">
                      <StaticFileListPage />
                    </SectionCardChromeProvider>
                  }
                />
                {/*
                  SH904 — the identity slice. Groups and roles are `{id, name, description}` and
                  nothing else, so both go through one shared entity shell and one shared list;
                  `new` before `:id`, or the editor loads an entity called "new".

                  The user pair splits: the *list* is a mosaic like the other two, but the
                  *editor* stays the console component under the provider. A user has no title
                  and no description — its identity is a login key and its name is two columns —
                  so a hero that inline-edits `title` would rename the login on blur.
                */}
                {/*
                  One route each, with `new` as a **param value** rather than a static sibling —
                  the sentinel the console already uses. A static `admin/groups/new` route beside
                  `:groupId` matches first and carries no param at all, so `useParams().groupId`
                  is undefined, `isNew` is false, the load never runs and the page renders `null`.
                  It looked like a routing-order problem and was the opposite one; the live check
                  caught it as "no hero heading on a bento surface".
                */}
                {/*
                  SH909 — the admin hub, which under the console was `admin.root.page.tsx`: a bare
                  Outlet with an index redirect to the user list. There is no hub page to migrate,
                  so this is that redirect and nothing else. It matters because `ADMIN_ROOT` is the
                  `urlBase` of eleven admin surfaces — every one of their hero back-links pointed
                  at a path no route claimed.
                */}
                <Route path="admin" element={<Navigate to={ROUTES.BENTO_USERS} replace />} />
                <Route path="admin/groups" element={<BentoGroupListPage />} />
                <Route path="admin/groups/:groupId" element={<BentoGroupEditPage />} />
                <Route path="admin/roles" element={<BentoRoleListPage />} />
                <Route path="admin/roles/:roleId" element={<BentoRoleEditPage />} />
                <Route path="admin/users" element={<BentoUserListPage />} />
                <Route
                  path="admin/users/:userId"
                  element={
                    <SectionCardChromeProvider chrome="bento">
                      <AdminUserPage />
                    </SectionCardChromeProvider>
                  }
                />
                {/*
                  SH905 — the eleven infrastructure pages, split the way the data splits.

                  The five **lists** are configuration on one shared `AdminListPage`: they differ
                  only in which two columns sat between the name and the status, which is what
                  §LV.8 meant by the most repetitive surface here.

                  The six **editors** stay their console components under the provider. Their
                  identity is name + description + enabled, which the entity shell handles well —
                  but their *bodies* are the substance: a vendor property map, a scope and site
                  picker, an event set, a provisioning flow. The shell's value is lifting identity
                  out of a form, and on a 300-line token form that is three fields of twelve. The
                  adapter gets the chrome right at a fraction of the risk (SH900's rule).
                */}
                <Route path="admin/tokens" element={<BentoApiTokenListPage />} />
                <Route
                  path="admin/tokens/:tokenId"
                  element={<SectionCardChromeProvider chrome="bento"><ApiTokenPage /></SectionCardChromeProvider>}
                />
                <Route path="admin/auth-providers" element={<BentoAuthProviderListPage />} />
                <Route
                  path="admin/auth-providers/:authProviderId"
                  element={<SectionCardChromeProvider chrome="bento"><AuthProviderPage /></SectionCardChromeProvider>}
                />
                <Route path="admin/exchange-providers" element={<BentoExchangeProviderListPage />} />
                <Route
                  path="admin/exchange-providers/:exchangeProviderId"
                  element={<SectionCardChromeProvider chrome="bento"><ExchangeProviderPage /></SectionCardChromeProvider>}
                />
                <Route path="admin/webhooks" element={<BentoSiteWebhookListPage />} />
                <Route
                  path="admin/webhooks/:webhookId"
                  element={<SectionCardChromeProvider chrome="bento"><SiteWebhookPage /></SectionCardChromeProvider>}
                />
                <Route path="admin/tenants" element={<BentoTenantListPage />} />
                <Route
                  path="admin/tenants/:tenantId"
                  element={<SectionCardChromeProvider chrome="bento"><TenantPage /></SectionCardChromeProvider>}
                />
                <Route
                  path="admin/email-config"
                  element={<SectionCardChromeProvider chrome="bento"><EmailConfigPage /></SectionCardChromeProvider>}
                />
                {/*
                  SH906 — the three bespoke surfaces. All adapter pages: the review queue is a
                  list of expandable agent sessions, the activity log is an audit table, and the
                  marketplace is a filtered catalogue with a detail dialog. §LV.9 predicted the
                  first two would want "a plain section rather than tiles", and they do — a hero
                  over a list is a shape the package has no page component for, and forcing
                  `BentoListPage` on an audit log would trade a readable column for a mosaic.
                */}
                <Route
                  path="admin/review"
                  element={<SectionCardChromeProvider chrome="bento"><ReviewListPage /></SectionCardChromeProvider>}
                />
                <Route
                  path="admin/activity"
                  element={<SectionCardChromeProvider chrome="bento"><ActivityListPage /></SectionCardChromeProvider>}
                />
                <Route
                  path="admin/marketplace"
                  element={<SectionCardChromeProvider chrome="bento"><MarketplaceListPage /></SectionCardChromeProvider>}
                />
                <Route
                  path="universal-editor"
                  element={<SectionCardChromeProvider chrome="bento"><EditorSitesPage /></SectionCardChromeProvider>}
                />
                {/*
                  SH908 — the last two surfaces, both mostly something this console does not draw.

                  The explorer is the console component under the provider: its chrome is a header
                  and a toolbar, and its body is a third-party editor that sizes itself to whatever
                  container it is given. Copying it would fork a Monaco worker wiring and a fetcher
                  that has to keep the session cookie *off* the request, so that a header could
                  differ (SH900's rule).

                  Declared in `graphql.routes.tsx` beside the console one, and it is the only
                  Block AR route not named in this file: the page is `lazy()` so that Monaco stays
                  out of the main console bundle, and naming the component here would have imported
                  it eagerly and undone that — invisibly, because a bigger bundle fails no check.
                */}
                {BentoGraphQLRoute}
                {/*
                  And the page nobody navigates to on purpose. Without this route an unmatched
                  `/bento/…` path falls through to the bare `*` below — no rail, no shell, and a
                  Back-to-Home that leads into the console — which is the wrong chrome shown to
                  precisely the person §LV.11 describes: somebody who followed a stale link during
                  a cutover and did not choose to be here. Last, so it only catches what every
                  real bento route declined.
                */}
                <Route
                  path="*"
                  element={<SectionCardChromeProvider chrome="bento"><NotFoundPage /></SectionCardChromeProvider>}
                />
              </Route>
              {/*
                SH909 — the console tree is gone and its URLs are not.

                Every surface moved (SH898–SH908), so what stood here was a second shell rendering
                the same components in the chrome this block retires. What the block promised
                instead was reversibility, and §LV.12 is about claiming that: "a reversal nobody
                claims becomes a permanent second product", which is the state the *legacy* console
                sat in for years with forty pages beside its migrated ones.

                Both patterns, because the console path is tenant-prefixed and the bare one is what
                an external redirect uses. The prefix is dropped rather than carried: bento is
                unscoped, and the tenant a request runs in has always come from the session, a
                token or an OIDC claim — never from the URL (SH931 carries the part of that which
                is a real loss, a shared link naming its tenant).
              */}
              {LEGACY_CONSOLE_PATTERNS.map((pattern) => (
                <Route key={pattern} path={pattern} element={<LegacyConsoleRedirect />} />
              ))}
              {/* And outside it, for a path that is not under the console at all. */}
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </ShioServiceProvider>
        </BreadcrumbProvider>
      </ThemeProvider>
    </div>
  )
}

export default App
