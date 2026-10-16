import type { NextConfig } from "next";

/**
 * Where the Spring Boot API runs, as seen from the Next.js server (never the
 * browser). Server-only on purpose: the browser always calls same-origin /api/*
 * so the HttpOnly auth cookies stay first-party. Read when `next build`/`next dev`
 * loads this config, so set it before building for production.
 */
const backendUrl = (process.env.BACKEND_URL ?? "http://localhost:8080").replace(/\/+$/, "");

const nextConfig: NextConfig = {
  /* config options here */
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async redirects() {
    return [
      // Legacy duplicate of the admin dashboard; /admin/dashboard is canonical.
      { source: "/dashboard", destination: "/admin/dashboard", permanent: false },
      // Guard against the singular form of the course catalog route.
      { source: "/course", destination: "/courses", permanent: false },
      // Account settings now live in My Profile; keep old links and bookmarks working.
      { source: "/student/settings", destination: "/student/profile", permanent: false },
    ];
  },
  async rewrites() {
    return [
      // Thin same-origin proxy to Spring Boot; no auth logic lives here.
      { source: "/api/:path*", destination: `${backendUrl}/api/:path*` },
    ];
  },
};

export default nextConfig;
