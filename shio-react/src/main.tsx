import '@/i18n';
import '@/lib/axios';
import { BackendStatusProvider, ErrorBoundary } from '@viglet/viglet-design-system';
import axios from 'axios';
import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App.tsx';
import { SHIO_LIGHT_BOOT_CLASS } from './theme';
import './index.css';

axios.defaults.baseURL = `${import.meta.env.VITE_API_URL}/api`;

axios.interceptors.request.use(async (config) => {
  if (!(config.data instanceof FormData)) {
    config.headers['Content-Type'] = 'application/json';
  }
  config.headers['X-Requested-With'] = 'XMLHttpRequest';
  return config;
});

// `?loading=1` holds the index.html boot loader for design/regression testing
// (see the guard in index.html). Bail out before React mounts — the static
// markup inside `#root` stays visible, animations keep playing.
if (!(globalThis as Record<string, unknown>).__SHIO_LOADING_TEST__) {
  /*
   * SH918 — `shio-light-boot` is a *boot* class, and after boot it is a claim about the theme
   * that nothing keeps true. The seed above makes it correct at first paint; a reader who then
   * switches to dark from the theme toggle leaves `<html class="dark">` over a `<body>` still
   * painting the light splash's near-white ground and salmon gradient, for the rest of the
   * session. Console pages hide that by painting `bg-background` over the viewport; a page
   * mounted outside a shell — SH887's, and the whole bento route tree after SH888 — does not.
   *
   * Removed here rather than in the seed script because the splash is still on screen until
   * React replaces `#root`. Under `?loading=1` this branch does not run at all, which is the
   * point: that mode holds the loader visible, and the class it is styled with has to stay.
   */
  document.body.classList.remove(SHIO_LIGHT_BOOT_CLASS)

  createRoot(document.getElementById('root')!).render(
    <React.StrictMode>
      <BrowserRouter>
        <ErrorBoundary>
          <BackendStatusProvider healthEndpoint="/api/v2/ping">
            <App />
          </BackendStatusProvider>
        </ErrorBoundary>
      </BrowserRouter>
    </React.StrictMode>
  )
}
