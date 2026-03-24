import { Navigate, Route } from "react-router-dom"
import ConfigRootPage from "../console/config/config.root.page"
import ConfigAuthProvidersPage from "../console/config/config.auth-providers.page"
import ConfigAuthProviderPage from "../console/config/config.auth-provider.page"
import ConfigExchangeProvidersPage from "../console/config/config.exchange-providers.page"
import ConfigExchangeProviderPage from "../console/config/config.exchange-provider.page"
import ConfigEmailPage from "../console/config/config.email.page"
import ConfigSearchPage from "../console/config/config.search.page"
import { ROUTES } from "../routes.const"

export const ConfigRoutes = (
  <Route path={ROUTES.CONFIG_ROOT} element={<ConfigRootPage />}>
    <Route index element={<Navigate to={`${ROUTES.CONFIG_ROOT}/auth-providers`} replace />} />
    <Route path="auth-providers" element={<ConfigAuthProvidersPage />} />
    <Route path="auth-providers/new" element={<ConfigAuthProviderPage />} />
    <Route path="auth-providers/:providerId" element={<ConfigAuthProviderPage />} />
    <Route path="exchange-providers" element={<ConfigExchangeProvidersPage />} />
    <Route path="exchange-providers/new" element={<ConfigExchangeProviderPage />} />
    <Route path="exchange-providers/:providerId" element={<ConfigExchangeProviderPage />} />
    <Route path="email" element={<ConfigEmailPage />} />
    <Route path="search" element={<ConfigSearchPage />} />
  </Route>
)
