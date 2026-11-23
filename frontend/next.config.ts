import path from "path";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // NEXT_DIST_DIR lets a second build/dev instance (CI, smoke tests) use its own output folder
  // instead of sharing .next with a running `next dev`, which corrupts Turbopack's cache.
  distDir: process.env.NEXT_DIST_DIR || ".next",
  output: "standalone",
  turbopack: {
    root: path.join(__dirname),
  },
  // Hosting already sends HSTS, nosniff and X-Frame-Options. These add what was missing; the CSP
  // only covers directives that can't break scripts or styles (framing, plugins, <base>, forms).
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "Permissions-Policy", value: "camera=(self), microphone=(), geolocation=(self), payment=(self)" },
          {
            key: "Content-Security-Policy",
            value: "frame-ancestors 'none'; object-src 'none'; base-uri 'self'; form-action 'self'",
          },
        ],
      },
    ];
  },
  images: {
    // Resizing is delegated to the image CDN (see lib/imageLoader.ts) rather than done on our server.
    loader: "custom",
    loaderFile: "./lib/imageLoader.ts",
    // Fewer, smaller steps: phones on mobile data never pull more than ~1080px wide photos.
    deviceSizes: [640, 828, 1080, 1440, 1920],
    remotePatterns: [
      { protocol: "https", hostname: "images.unsplash.com" },
      { protocol: "http", hostname: "localhost" },
      { protocol: "https", hostname: "**" },
    ],
  },
};

export default nextConfig;