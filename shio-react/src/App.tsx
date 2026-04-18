import { Navigate, Route, Routes } from "react-router-dom"
import ConsoleRootPage from "./app/console/console.root.page"
import HomePage from "./app/console/home/home.page"
import LoginPage from "./app/login/login.page"
import SetupPage from "./app/setup/setup.page"
import TenantGateway from "./app/tenant-gateway"
import {
  AdminRoutes,
  ContentRoutes,
  GraphqlRoutes,
  UniversalEditorRoutes,
} from "./app/routes"
import { ROUTES, ROUTE_PATTERNS } from "./app/routes.const"
import { ThemeProvider } from "./components/theme-provider"
import { Toaster } from "./components/ui/sonner"
import { ShioServiceProvider } from "./contexts/ShioServiceContext"
import { BreadcrumbProvider } from "./contexts/breadcrumb.context"
import { TenantProvider } from "./contexts/tenant.context"

function App() {
  return (
    <div className="App">
      <ThemeProvider defaultTheme="dark" storageKey="shio-ui-theme">
        <BreadcrumbProvider>
          <ShioServiceProvider>
            <Toaster />
            <Routes>
              <Route path={ROUTES.ROOT} element={<TenantGateway />} />
              <Route path={ROUTES.SETUP} element={<SetupPage />} />
              <Route path={ROUTES.LOGIN} element={<LoginPage />} />
              <Route
                path={ROUTE_PATTERNS.CONSOLE}
                element={
                  <TenantProvider>
                    <ConsoleRootPage />
                  </TenantProvider>
                }
              >
                <Route index element={<Navigate to={ROUTES.HOME} replace />} />
                <Route path="home" element={<HomePage />} />
                {ContentRoutes}
                {AdminRoutes}
                {GraphqlRoutes}
                {UniversalEditorRoutes}
              </Route>
            </Routes>
          </ShioServiceProvider>
        </BreadcrumbProvider>
      </ThemeProvider>
    </div>
  )
}

export default App
