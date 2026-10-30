import type { NextConfig } from "next";

/**
 * In production nginx is the single origin and routes /api and /ws to the backend; Next.js never proxies.
 * For native development (`pnpm dev` without nginx) API_PROXY_TARGET enables an equivalent dev-only rewrite so
 * session cookies stay same-origin.
 */
const apiProxyTarget = process.env.API_PROXY_TARGET;

const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
];

const nextConfig: NextConfig = {
  output: "standalone",
  reactStrictMode: true,
  poweredByHeader: false,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async rewrites() {
    if (!apiProxyTarget) return [];
    return [
      { source: "/api/:path*", destination: `${apiProxyTarget}/api/:path*` },
      { source: "/ws/:path*", destination: `${apiProxyTarget}/ws/:path*` },
    ];
  },
  async headers() {
    return [{ source: "/(.*)", headers: securityHeaders }];
  },
};

export default nextConfig;
