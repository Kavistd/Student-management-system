import axios, { type InternalAxiosRequestConfig } from "axios";

/** Names must match Spring Security's cookie-based CSRF token repository. */
export const CSRF_COOKIE = "XSRF-TOKEN";
export const CSRF_HEADER = "X-XSRF-TOKEN";

/** Fired on `window` when the session can no longer be refreshed. */
export const SESSION_EXPIRED_EVENT = "studentms:session-expired";

const SAFE_METHODS = new Set(["get", "head", "options"]);

/** Endpoints whose 401s mean "bad credentials/session", never "refresh and retry". */
const AUTH_ENDPOINTS = [
  "/api/auth/login",
  "/api/auth/register",
  "/api/auth/refresh",
  "/api/auth/logout",
  "/api/auth/csrf",
];

const api = axios.create({
  // Requests stay same-origin: Next.js rewrites /api/* to Spring Boot, so the
  // HttpOnly auth cookies are first-party and the browser never calls Spring directly.
  baseURL: "",
  withCredentials: true,
  // Axios would otherwise copy the CSRF cookie into a header implicitly for
  // same-origin requests. That is done explicitly in the interceptor below.
  withXSRFToken: false,
  headers: {
    "Content-Type": "application/json",
  },
  timeout: 10000,
});

export function readCookie(name: string): string | null {
  if (typeof document === "undefined") return null;
  for (const part of document.cookie.split(";")) {
    const [key, ...rest] = part.trim().split("=");
    if (key === name) return decodeURIComponent(rest.join("="));
  }
  return null;
}

let csrfRequest: Promise<void> | null = null;

/** Makes sure the readable XSRF-TOKEN cookie exists before an unsafe request. */
export function ensureCsrfToken(force = false): Promise<void> {
  if (!force && readCookie(CSRF_COOKIE)) return Promise.resolve();
  csrfRequest ??= api
    .get("/api/auth/csrf")
    .then(() => undefined)
    .finally(() => {
      csrfRequest = null;
    });
  return csrfRequest;
}

api.interceptors.request.use(async (config) => {
  const method = (config.method ?? "get").toLowerCase();
  if (!SAFE_METHODS.has(method)) {
    await ensureCsrfToken();
    const token = readCookie(CSRF_COOKIE);
    if (token) config.headers.set(CSRF_HEADER, token);
  }
  return config;
});

type RetriableConfig = InternalAxiosRequestConfig & {
  _authRetried?: boolean;
  _csrfRetried?: boolean;
};

let refreshRequest: Promise<boolean> | null = null;

/** One refresh at a time; concurrent 401s share the same attempt. */
function refreshSession(): Promise<boolean> {
  refreshRequest ??= api
    .post("/api/auth/refresh")
    .then(
      () => true,
      () => false
    )
    .finally(() => {
      refreshRequest = null;
    });
  return refreshRequest;
}

function isCsrfFailure(data: unknown) {
  return (
    typeof data === "object" &&
    data !== null &&
    (data as Record<string, unknown>).code === "CSRF_TOKEN_INVALID"
  );
}

function notifySessionExpired() {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
  }
}

api.interceptors.response.use(undefined, async (error: unknown) => {
  if (!axios.isAxiosError(error) || !error.config || !error.response) throw error;

  const config = error.config as RetriableConfig;
  const url = config.url ?? "";
  const status = error.response.status;

  // A missing or stale CSRF cookie: fetch a fresh token and retry once.
  if (status === 403 && isCsrfFailure(error.response.data) && !config._csrfRetried) {
    config._csrfRetried = true;
    await ensureCsrfToken(true);
    return api(config);
  }

  // An expired access token: rotate the refresh token, then retry once.
  if (status === 401 && !config._authRetried && !AUTH_ENDPOINTS.some((path) => url.startsWith(path))) {
    config._authRetried = true;
    if (await refreshSession()) return api(config);

    // Another tab may have rotated the session a moment ago, updating the
    // shared cookies; retry once with them before declaring the session over.
    try {
      return await api(config);
    } catch (retryError) {
      if (axios.isAxiosError(retryError) && retryError.response?.status === 401) {
        notifySessionExpired();
      }
      throw retryError;
    }
  }

  throw error;
});

export default api;
