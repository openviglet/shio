// Monaco web-worker setup for GraphiQL v5, kept in app source on purpose.
//
// GraphiQL ships an equivalent `@graphiql/react/setup-workers/vite` entry, but
// importing it makes Vite pre-bundle a *dependency* that contains `?worker`
// imports — which rolldown's dep optimizer can't load (fails the dev server on
// Windows). Declaring the same `?worker` imports here, in application source,
// lets Vite's worker plugin handle them natively at request time, so Monaco can
// be optimized normally (its CJS deps like nullthrows interop correctly).
import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker.js?worker"
import JsonWorker from "monaco-editor/esm/vs/language/json/json.worker.js?worker"
import GraphQLWorker from "monaco-graphql/esm/graphql.worker.js?worker"

;(globalThis as typeof globalThis & { MonacoEnvironment?: unknown }).MonacoEnvironment = {
  getWorker(_workerId: string, label: string) {
    if (label === "json") return new JsonWorker()
    if (label === "graphql") return new GraphQLWorker()
    return new EditorWorker()
  },
}
