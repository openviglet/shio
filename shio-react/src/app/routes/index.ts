/*
 * SH910 — one export left, and the file stays for it.
 *
 * `ContentRoutes`, `AdminRoutes`, `GraphQLRoutes` and `UniversalEditorRoutes` were the console
 * route tree, and they went with the shell that rendered them. `BentoGraphQLRoute` is declared in
 * `graphql.routes.tsx` rather than in `App.tsx` because the page is `lazy()` — naming the
 * component in `App.tsx` would import Monaco eagerly and put multiple megabytes back into the main
 * bundle, which nothing in `tsc`, in the census or in any live probe would report (SH908).
 */
export { BentoGraphQLRoute } from "./graphql.routes";
