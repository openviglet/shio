import { lazy, Suspense } from "react"
import { Route } from "react-router-dom"
import { IconBrandGraphql } from "@tabler/icons-react"

// GraphiQL pulls in Monaco (multi-MB). Load the page only when the route is hit
// so it never weighs down the main console bundle.
const GraphiQLPage = lazy(() => import("../admin/graphql/graphql.page"))

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

/**
 * SH908 — the explorer's route, declared here rather than in App.tsx.
 *
 * The import above is the reason. Every other route names its component in App.tsx, and doing that
 * for this one would import the page eagerly and pull **Monaco into the main console bundle** —
 * multi-megabytes on every route. Nothing in `tsc` or in any live probe would say so; the console
 * would simply get slower to start.
 */
export const BentoGraphQLRoute = (
    <Route
        path="graphql"
        element={
            <Suspense fallback={Fallback}>
                <GraphiQLPage />
            </Suspense>
        }
    />
)
