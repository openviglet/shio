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
