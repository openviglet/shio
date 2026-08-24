import { Navigate, Route, Routes } from "react-router-dom"
import BentoVocabularyPage from "./app/bento/bento-vocabulary.page"
import ConsoleRootPage from "./app/console/console.root.page"
import NotFoundPage from "./app/console/not-found.page"
import HomePage from "./app/console/home/home.page"
import LoginPage from "./app/login/login.page"
import SetupPage from "./app/setup/setup.page"
import { AdminRoutes, ContentRoutes, GraphQLRoutes, UniversalEditorRoutes } from "./app/routes"
import { ROUTES } from "./app/routes.const"
import { ThemeProvider } from "./components/theme-provider"
import { Toaster } from "./components/ui/sonner"
import { ShioServiceProvider } from "./contexts/ShioServiceContext"
import { BreadcrumbProvider } from "./contexts/breadcrumb.context"

function App() {
  return (
    <div className="App">
      <ThemeProvider defaultTheme="dark" storageKey="shio-ui-theme">
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
