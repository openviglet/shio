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
import ObjectChildrenPage from "./app/console/content/object.children.page"
import PostEditPage from "./app/console/content/post.edit.page"
import PostNewPage from "./app/console/content/post.new.page"
import PostTypeEditPage from "./app/console/content/post-type.edit.page"
import PostTypeListPage from "./app/console/content/post-type.list.page"
import PostTypeNewPage from "./app/console/content/post-type.new.page"
import ConsoleRootPage from "./app/console/console.root.page"
import NotFoundPage from "./app/console/not-found.page"
import HomePage from "./app/console/home/home.page"
import LoginPage from "./app/login/login.page"
import SetupPage from "./app/setup/setup.page"
import { AdminRoutes, ContentRoutes, GraphQLRoutes, UniversalEditorRoutes } from "./app/routes"
import { ROUTES } from "./app/routes.const"
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
              <Route path={ROUTES.ROOT} element={<Navigate to={ROUTES.CONSOLE} replace />} />
              <Route path={ROUTES.SETUP} element={<SetupPage />} />
              <Route path={ROUTES.LOGIN} element={<LoginPage />} />
              {/*
                SH887 — the bento layer's proving page, mounted OUTSIDE the console shell on
                purpose: `ConsoleRootPage` wraps its Outlet in a `SidebarProvider`, and the bento
                shell ships no provider at all, so a bento page under the console chrome is a
                page proved in the era this migration retires. Top-level, no nav entry, no data.
                SH888 builds the real parallel route tree; SH909 removes whichever shell loses.
              */}
              <Route path={ROUTES.BENTO_VOCABULARY} element={<BentoVocabularyPage />} />
              {/*
                SH888 — the parallel bento route tree. A sibling of the console route and not a
                child of it: `ConsoleRootPage` mounts a SidebarProvider and its PageHeader calls
                SidebarTrigger, while the bento shell ships no provider at all, so the two cannot
                nest. Both stay mounted for the length of Block AR and each surface moves under
                its own task; SH909 removes whichever shell loses.

                SH898 — the index is the console home, the first migrated surface. The
                vocabulary harness keeps its own bare route above, outside every shell, which is
                where it proves the layer rather than the shell.
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
              </Route>
              <Route path={ROUTES.CONSOLE} element={<ConsoleRootPage />}>
                <Route index element={<Navigate to={ROUTES.HOME} replace />} />
                <Route path="home" element={<HomePage />} />
                {ContentRoutes}
                {AdminRoutes}
                {GraphQLRoutes}
                {UniversalEditorRoutes}
                {/*
                  SH180 — an unmatched console path says so. Inside the shell, so the sidebar
                  and breadcrumb stay put and the user can navigate out of it; last, so it
                  only catches what every real route declined.
                */}
                <Route path="*" element={<NotFoundPage />} />
              </Route>
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
