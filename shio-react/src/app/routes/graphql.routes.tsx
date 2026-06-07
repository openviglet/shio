import { lazy, Suspense } from "react"
import { Route } from "react-router-dom"
import { IconBrandGraphql } from "@tabler/icons-react"

// GraphiQL pulls in Monaco (multi-MB). Load the page only when the route is hit
// so it never weighs down the main console bundle.
const GraphiQLPage = lazy(() => import("../console/graphql/graphql.page"))

const Fallback = (
    <div className="flex flex-1 items-center justify-center py-24 text-muted-foreground">
        <IconBrandGraphql className="h-8 w-8 animate-pulse" />
    </div>
)

export const GraphQLRoutes = (
    <Route
        path="graphql"
        element={
            <Suspense fallback={Fallback}>
                <GraphiQLPage />
            </Suspense>
        }
    />
)
