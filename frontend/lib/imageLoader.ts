"use client";

/**
 * next/image loader. Listing photos are mostly Unsplash URLs stored at ~1200px; Unsplash's CDN
 * resizes on the fly, so we ask it for exactly the width the layout needs (and modern formats)
 * instead of shipping the full-size original to every phone. Other sources (uploaded photos,
 * local assets, blob: previews) are returned unchanged — no work for our own small server.
 */
export default function imageLoader({ src, width, quality }: { src: string; width: number; quality?: number }) {
  if (src.startsWith("https://images.unsplash.com/")) {
    const url = new URL(src);
    url.searchParams.set("w", String(width));
    url.searchParams.set("q", String(quality ?? 60));
    url.searchParams.set("auto", "format");
    if (!url.searchParams.has("fit")) url.searchParams.set("fit", "crop");
    return url.toString();
  }
  return src;
}
