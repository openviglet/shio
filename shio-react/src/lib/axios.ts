import axios, { type AxiosRequestConfig } from "axios";

const CSRF_HEADER = "X-XSRF-TOKEN";
const CSRF_ENDPOINT = "/csrf";
let csrfToken: string | null = null;

const csrfClient = axios.create({ withCredentials: true });

function isMutatingMethod(method?: string): boolean {
  return ["post", "put", "delete", "patch"].includes(
    method?.toLowerCase() || "",
  );
}

function readCsrfTokenFromHeaders(headers: unknown): string | null {
  if (!headers) return null;

  if (
    typeof headers === "object" &&
    headers !== null &&
    "get" in headers &&
    typeof (headers as { get: (name: string) => string | undefined }).get ===
      "function"
  ) {
    const value = (
      headers as { get: (name: string) => string | undefined }
    ).get(CSRF_HEADER);
    return value || null;
  }

  if (typeof headers === "object" && headers !== null) {
    const value = (headers as Record<string, string | undefined>)[
      CSRF_HEADER.toLowerCase()
    ];
    return value || null;
  }

  return null;
}

function setRequestHeader(config: { headers?: unknown }, token: string): void {
  if (
    typeof config.headers === "object" &&
    config.headers !== null &&
    "set" in config.headers &&
    typeof (config.headers as { set: (name: string, value: string) => void })
      .set === "function"
  ) {
    (config.headers as { set: (name: string, value: string) => void }).set(
      CSRF_HEADER,
      token,
    );
    return;
  }

  const headers = (config.headers as Record<string, string | undefined>) || {};
  headers[CSRF_HEADER] = token;
  config.headers = headers;
}

function clearCsrfToken(): void {
  csrfToken = null;
}

function readCsrfTokenFromCookie(): string | null {
  const regex = /(?:^|;\s*)XSRF-TOKEN=([^;]+)/;
  const match = regex.exec(globalThis.document.cookie);
  return match ? decodeURIComponent(match[1]) : null;
}

async function ensureCsrfToken(): Promise<void> {
  if (csrfToken) return;

  csrfClient.defaults.baseURL = axios.defaults.baseURL;
  const response = await csrfClient.get(CSRF_ENDPOINT);
  const headerToken = readCsrfTokenFromHeaders(response.headers);
  const bodyToken =
    typeof response.data === "object" && response.data !== null
      ? (response.data as { token?: string }).token
      : undefined;
  const cookieToken = readCsrfTokenFromCookie();

  csrfToken = headerToken || bodyToken || cookieToken || null;
}

// Configure axios defaults
axios.defaults.withCredentials = true;
axios.defaults.xsrfCookieName = "XSRF-TOKEN";
axios.defaults.xsrfHeaderName = CSRF_HEADER;

// Add request interceptor to include CSRF token
axios.interceptors.request.use(
  async (config) => {
    const requestUrl = config.url || "";
    const requestBaseUrl = config.baseURL || axios.defaults.baseURL;

    csrfClient.defaults.baseURL = requestBaseUrl;

    if (
      requestUrl.includes(CSRF_ENDPOINT) ||
      !isMutatingMethod(config.method)
    ) {
      return config;
    }

    if (!csrfToken) {
      await ensureCsrfToken();
    }

    if (!csrfToken) {
      throw new Error("Unable to obtain CSRF token for mutating request.");
    }

    setRequestHeader(config, csrfToken);

    return config;
  },
  (error) => {
    return Promise.reject(error);
  },
);

// Add response interceptor to handle errors
axios.interceptors.response.use(
  (response) => {
    const tokenFromHeader = readCsrfTokenFromHeaders(response.headers);
    if (tokenFromHeader) {
      csrfToken = tokenFromHeader;
    }
    return response;
  },
  async (error) => {
    const tokenFromHeader = readCsrfTokenFromHeaders(error.response?.headers);
    if (tokenFromHeader) {
      csrfToken = tokenFromHeader;
    }

    const requestConfig = error.config as
      | (AxiosRequestConfig & { _csrfRetried?: boolean })
      | undefined;

    if (
      error.response?.status === 403 &&
      requestConfig &&
      !requestConfig._csrfRetried &&
      isMutatingMethod(requestConfig.method)
    ) {
      requestConfig._csrfRetried = true;
      clearCsrfToken();
      csrfClient.defaults.baseURL =
        requestConfig.baseURL || axios.defaults.baseURL;
      await ensureCsrfToken();

      if (!csrfToken) {
        throw error;
      }

      setRequestHeader(requestConfig, csrfToken);

      return axios.request(requestConfig);
    }

    if (error.response?.status === 401) {
      // Handle unauthorized - redirect to login preserving the current URL
      if (!globalThis.location.pathname.startsWith("/login")) {
        const returnUrl = globalThis.location.pathname + globalThis.location.search;
        globalThis.location.href = `/login?returnUrl=${encodeURIComponent(returnUrl)}`;
      }
    }
    throw error;
  },
);

export default axios;
