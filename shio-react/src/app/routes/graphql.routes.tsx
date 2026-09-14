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

/**
 * SH908 — the same lazy component under the bento shell, declared here rather than in App.tsx.
 *
 * The import above is the reason. Every other Block AR route names its component in App.tsx, and
 * doing that for this one would have imported the page eagerly and pulled **Monaco into the main
 * console bundle** — multi-megabytes on every route, to migrate a header. Nothing in `tsc`, in the
 * census or in any live probe would have said so; the console would simply have got slower to
 * start. So the bento route reuses this file's `lazy()` and its fallback, and the two chromes
 * share one code-split chunk.
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
