import { Route } from "react-router-dom"
import GraphiQLPage from "../console/graphql/graphql.page"
import { ROUTES } from "../routes.const"

export const GraphqlRoutes = (
    <Route path={ROUTES.GRAPHQL_ROOT} element={<GraphiQLPage />} />
)
