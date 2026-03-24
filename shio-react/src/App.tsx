import { Navigate, Route, Routes } from "react-router-dom"
import ConsoleRootPage from "./app/console/console.root.page"
import HomePage from "./app/console/home/home.page"
import LoginPage from "./app/login/login.page"
import {
  AdminRoutes,
  ContentRoutes,
  ConfigRoutes,
} from "./app/routes"
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
              <Route path={ROUTES.LOGIN} element={<LoginPage />} />
              <Route path={ROUTES.CONSOLE} element={<ConsoleRootPage />}>
                <Route index element={<Navigate to={ROUTES.HOME} replace />} />
                <Route path="home" element={<HomePage />} />
                {ContentRoutes}
                {AdminRoutes}
                {ConfigRoutes}
              </Route>
            </Routes>
          </ShioServiceProvider>
        </BreadcrumbProvider>
      </ThemeProvider>
    </div>
  )
}

export default App
