import {
  createBrowserRouter, createRoutesFromElements, Navigate, Outlet, Route, RouterProvider,
} from "react-router-dom"
import BentoHomePage from "./app/system/home.page"
import BentoFolderEditPage from "./app/content/folder.edit.page"
import BentoFolderNewPage from "./app/content/folder.new.page"
import BentoPostTypeSelectPage from "./app/content/post-type-select.page"
import BentoSiteEditPage from "./app/content/site.edit.page"
import BentoSiteImportPage from "./app/content/site.import.page"
import BentoSiteListPage from "./app/content/site.list.page"
import BentoSiteNewPage from "./app/content/site.new.page"
import BentoRootPage from "./app/system/bento.root.page"
import BentoVocabularyPage from "./app/system/bento-vocabulary.page"
import BentoGroupEditPage from "./app/admin/group.edit.page"
import BentoGroupListPage from "./app/admin/group.list.page"
import BentoRoleEditPage from "./app/admin/role.edit.page"
import BentoRoleListPage from "./app/admin/role.list.page"
import BentoUserListPage from "./app/admin/user.list.page"
import AdminUserPage from "./app/admin/admin.user.page"
import BentoApiTokenListPage from "./app/admin/api-token.list.page"
import BentoAuthProviderListPage from "./app/admin/auth-provider.list.page"
import BentoExchangeProviderListPage from "./app/admin/exchange-provider.list.page"
import BentoSiteWebhookListPage from "./app/admin/site-webhook.list.page"
import BentoTenantListPage from "./app/admin/tenant.list.page"
import ApiTokenPage from "./app/admin/api-token/api-token.page"
import AuthProviderPage from "./app/admin/auth-provider/auth-provider.page"
import EmailConfigPage from "./app/admin/email-config/email-config.page"
import ExchangeProviderPage from "./app/admin/exchange-provider/exchange-provider.page"
import SiteWebhookPage from "./app/admin/site-webhook/site-webhook.page"
import TenantPage from "./app/admin/tenant/tenant.page"
import ActivityListPage from "./app/admin/activity/activity.list.page"
import MarketplaceListPage from "./app/admin/marketplace/marketplace.list.page"
import ReviewListPage from "./app/admin/review/review.list.page"
import EditorSitesPage from "./app/editor/editor-sites.page"
import VisualEditorPage from "./app/editor/visual-editor.page"
import ObjectChildrenPage from "./app/content/object.children.page"
import PostEditPage from "./app/content/post.edit.page"
import PostNewPage from "./app/content/post.new.page"
import PostTypeEditPage from "./app/content/post-type.edit.page"
import PostTypeListPage from "./app/content/post-type.list.page"
import PostTypeNewPage from "./app/content/post-type.new.page"
import SearchPage from "./app/content/search.page"
import StaticFileListPage from "./app/content/static-file.list.page"
import TrashPage from "./app/content/trash.page"
import FormsPage from "./app/content/forms.page"
import LanguagesPage from "./app/content/languages.page"
import ScheduledPage from "./app/content/scheduled.page"
import TranslatePage from "./app/content/translate.page"
import NotFoundPage from "./app/system/not-found.page"
import LoginPage from "./app/login/login.page"
import SetupPage from "./app/setup/setup.page"
import { BentoGraphQLRoute } from "./app/routes"
import LegacyConsoleRedirect from "./app/legacy-console-redirect"
import { LEGACY_CONSOLE_PATTERNS, ROUTES } from "./app/routes.const"
import { ThemeProvider } from "./components/theme-provider"
import { SHIO_DEFAULT_THEME, SHIO_THEME_STORAGE_KEY } from "./theme"
import { Toaster } from "./components/ui/sonner"
import { ShioQueryProvider } from "./contexts/query.provider"

/** SH1164 — any type React does not read as JavaScript makes the theme script a data block. */
const INERT_THEME_SCRIPT = { type: "text/x-inert-theme" } as const

/**
 * The providers every route renders inside, as the router's root layout.
 *
 * SH1090 — this used to wrap `<Routes>` under a `<BrowserRouter>` in `main.tsx`, which is not a data
 * router, so `useBlocker` threw and no page could stop an in-app navigation away from unsaved work;
 * only closing the tab was caught. The same tree is now a data router's route objects, built from the
 * JSX below by `createRoutesFromElements`, so every `<Route>` still reads the way it did.
 */
function AppLayout() {
  return (
    <div className="App">
      {/* SH918 — the same two constants the boot-loader seed reads, so the splash and the app
          cannot disagree about the theme. Spelling them here again is what caused the defect. */}
      {/* SH1164 — next-themes renders a blocking script for a server render's first paint. This
          console is never server-rendered, a script React inserts on the client never runs, and the
          first paint is the boot-loader's (SH918). A non-JavaScript type says so, and stops React's
          dev build reporting that script as an error on every route. */}
      <ThemeProvider defaultTheme={SHIO_DEFAULT_THEME} storageKey={SHIO_THEME_STORAGE_KEY}
        scriptProps={INERT_THEME_SCRIPT}>
          <ShioQueryProvider>
            <Toaster />
            <Outlet />
          </ShioQueryProvider>
      </ThemeProvider>
    </div>
  )
}

/** Created once, at module load, the way a data router is meant to be (never per render). */
const router = createBrowserRouter(createRoutesFromElements(
            <Route element={<AppLayout />}>
              <Route path={ROUTES.ROOT} element={<Navigate to={ROUTES.CONSOLE} replace />} />
              <Route path={ROUTES.SETUP} element={<SetupPage />} />
              <Route path={ROUTES.LOGIN} element={<LoginPage />} />
              {/*
                SH887 — the design system's proving page. Top-level, no nav entry and no data: it
                composes the layer's runtime exports so a break in the package shows here first,
                and a harness belongs outside the product's own shell.
              */}
              <Route path={ROUTES.BENTO_VOCABULARY} element={<BentoVocabularyPage />} />
              {/*
                SH907 — the Universal Editor is mounted OUTSIDE the shell, by measurement.

                `visual-editor.page.tsx` is `flex flex-col h-screen`: it owns the viewport, draws
                its own toolbar and splits the rest between a preview iframe and a properties
                panel. Inside the shell it is 100vh *plus* the shell's 64px `sticky top-0` header,
                so — measured in a browser at 1600x1000 — the iframe's bottom goes from 992
                (visible) to 1048 (**below the fold**), while two sticky bars stack at the top.
              */}
              <Route path="/bento/universal-editor/:siteId" element={<VisualEditorPage />} />
              {/*
                The shell and every page inside it. `bento-is-the-default.test.mjs` is what stops a
                second shell from arriving in a diff that had a good local reason.
              */}
              <Route path={ROUTES.BENTO} element={<BentoRootPage />}>
                <Route index element={<BentoHomePage />} />
                {/*
                  `new` and `import` are declared BEFORE `:siteId`, or the parameter route matches
                  them first and the editor tries to load a site called "new" (SH899).
                */}
                <Route path="content" element={<BentoSiteListPage />} />
                <Route path="content/site/new" element={<BentoSiteNewPage />} />
                <Route path="content/site/import" element={<BentoSiteImportPage />} />
                <Route path="content/site/:siteId" element={<BentoSiteEditPage />} />
                <Route
                  path="content/object/:objectId"
                  element={<ObjectChildrenPage />}
                />
                <Route path="content/folder/:folderId/edit" element={<BentoFolderEditPage />} />
                <Route path="content/folder/:folderId/new-folder" element={<BentoFolderNewPage />} />
                <Route
                  path="content/post/:postId"
                  element={<PostEditPage />}
                />
                <Route
                  path="content/folder/:folderId/post-type/:postTypeName/new"
                  element={<PostNewPage />}
                />
                <Route
                  path="content/folder/:folderId/post-type-select"
                  element={<BentoPostTypeSelectPage />}
                />
                {/* `new` before `:postTypeName`, or the editor loads a post type called "new". */}
                <Route
                  path="content/post-type"
                  element={<PostTypeListPage />}
                />
                <Route
                  path="content/post-type/new"
                  element={<PostTypeNewPage />}
                />
                <Route
                  path="content/post-type/:postTypeName"
                  element={<PostTypeEditPage />}
                />
                {/*
                  SH903 — none of these three creates anything, so `BentoListPage`, which requires
                  a `newRoute` and renders a create-tile, is the wrong shell for all of them.
                */}
                <Route
                  path="content/search"
                  element={<SearchPage />}
                />
                <Route
                  path="content/trash"
                  element={<TrashPage />}
                />
                <Route
                  path="content/scheduled"
                  element={<ScheduledPage />}
                />
                {/* SH1066 — the inbox for what a site's form wrote, and the form's settings. */}
                <Route
                  path="content/forms"
                  element={<FormsPage />}
                />
                {/* SH1060 — one site's translation coverage, reached from its Languages card. */}
                <Route
                  path="content/site/:siteId/languages"
                  element={<LanguagesPage />}
                />
                {/* SH1061 — one page beside the language it is being translated into. */}
                <Route
                  path="content/post/:postId/translate"
                  element={<TranslatePage />}
                />
                <Route
                  path="content/files"
                  element={<StaticFileListPage />}
                />
                {/*
                  SH904 — groups and roles are `{id, name, description}` and nothing else, so both
                  go through one shared entity shell and one shared list. The user *editor* is not
                  an entity shell: a user has no title and no description — its identity is a login
                  key — so a hero that inline-edits `title` would rename the login on blur.

                  One route each, with `new` as a **param value** rather than a static sibling. A
                  static `admin/groups/new` route beside `:groupId` matches first and carries no
                  param at all, so `useParams().groupId` is undefined and the page renders `null`.

                  SH909 — `admin` is a redirect and nothing else, and it has to exist: `ADMIN_ROOT`
                  is the `urlBase` of eleven admin surfaces, and their hero back-links point here.
                */}
                <Route path="admin" element={<Navigate to={ROUTES.BENTO_USERS} replace />} />
                <Route path="admin/groups" element={<BentoGroupListPage />} />
                <Route path="admin/groups/:groupId" element={<BentoGroupEditPage />} />
                <Route path="admin/roles" element={<BentoRoleListPage />} />
                <Route path="admin/roles/:roleId" element={<BentoRoleEditPage />} />
                <Route path="admin/users" element={<BentoUserListPage />} />
                <Route
                  path="admin/users/:userId"
                  element={<AdminUserPage />}
                />
                {/*
                  SH905 — the infrastructure pages split the way the data splits. The five
                  **lists** are configuration on one shared `AdminListPage`. The six **editors** are
                  forms whose bodies are the substance — a vendor property map, a scope and site
                  picker, an event set, a provisioning flow — so they keep their own forms under a
                  `PageHero` rather than taking the entity shell, which would lift three fields of
                  twelve.
                */}
                <Route path="admin/tokens" element={<BentoApiTokenListPage />} />
                <Route
                  path="admin/tokens/:tokenId"
                  element={<ApiTokenPage />}
                />
                <Route path="admin/auth-providers" element={<BentoAuthProviderListPage />} />
                <Route
                  path="admin/auth-providers/:authProviderId"
                  element={<AuthProviderPage />}
                />
                <Route path="admin/exchange-providers" element={<BentoExchangeProviderListPage />} />
                <Route
                  path="admin/exchange-providers/:exchangeProviderId"
                  element={<ExchangeProviderPage />}
                />
                <Route path="admin/webhooks" element={<BentoSiteWebhookListPage />} />
                <Route
                  path="admin/webhooks/:webhookId"
                  element={<SiteWebhookPage />}
                />
                <Route path="admin/tenants" element={<BentoTenantListPage />} />
                <Route
                  path="admin/tenants/:tenantId"
                  element={<TenantPage />}
                />
                <Route
                  path="admin/email-config"
                  element={<EmailConfigPage />}
                />
                {/*
                  SH906 — the review queue is a list of expandable agent sessions, the activity log
                  an audit table, the marketplace a filtered catalogue: a hero over a list, which the
                  package has no page component for, and `BentoListPage` on an audit log would trade
                  a readable column for a mosaic.
                */}
                <Route
                  path="admin/review"
                  element={<ReviewListPage />}
                />
                <Route
                  path="admin/activity"
                  element={<ActivityListPage />}
                />
                <Route
                  path="admin/marketplace"
                  element={<MarketplaceListPage />}
                />
                <Route
                  path="universal-editor"
                  element={<EditorSitesPage />}
                />
                {/*
                  SH908 — the GraphQL explorer is the one route not named in this file: its page is
                  `lazy()` so Monaco stays out of the main bundle, and naming the component here
                  would import it eagerly — invisibly, because a bigger bundle fails no check.
                */}
                {BentoGraphQLRoute}
                {/*
                  An unmatched path under the shell. Without it, it would fall through to the bare
                  `*` below and lose the rail. Last, so it only catches what every route declined.
                */}
                <Route
                  path="*"
                  element={<NotFoundPage />}
                />
              </Route>
              {/*
                SH909 — old `/console/**` bookmarks redirect into the shell. Both patterns, because
                the old path was tenant-prefixed and the bare one is what an external redirect uses.
                The prefix is dropped rather than carried: the tenant a request runs in comes from
                the session, a token or an OIDC claim, never from the URL (SH931 is how a shared
                link names one).
              */}
              {LEGACY_CONSOLE_PATTERNS.map((pattern) => (
                <Route key={pattern} path={pattern} element={<LegacyConsoleRedirect />} />
              ))}
              {/* And outside it, for a path that is not under the console at all. */}
              <Route path="*" element={<NotFoundPage />} />
            </Route>,
))

function App() {
  return <RouterProvider router={router} />
}

export default App
