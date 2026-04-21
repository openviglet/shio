import '@/i18n';
import '@/lib/axios';
import { BackendStatusProvider, ErrorBoundary } from '@viglet/viglet-design-system';
import axios from 'axios';
import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App.tsx';
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
