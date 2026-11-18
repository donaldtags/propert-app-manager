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