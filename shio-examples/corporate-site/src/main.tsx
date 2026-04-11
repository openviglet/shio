import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { BrowserRouter, Routes, Route } from "react-router-dom"
import { ShioProvider } from "@viglet/shio-react-sdk"
import { Layout } from "./components/Layout"
import { HomePage } from "./pages/HomePage"
import { AboutPage } from "./pages/AboutPage"

const API_BASE = import.meta.env.VITE_SHIO_API_URL || "/api"
const API_TOKEN = import.meta.env.VITE_SHIO_API_TOKEN || ""

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ShioProvider baseUrl={API_BASE} apiToken={API_TOKEN}>
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<HomePage />} />
            <Route path="about" element={<AboutPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </ShioProvider>
  </StrictMode>
)
