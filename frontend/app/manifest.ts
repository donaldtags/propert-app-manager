import type { MetadataRoute } from "next";

/** Makes EstateX installable ("Add to Home Screen") so agents can open it like an app, offline. */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "EstateX",
    short_name: "EstateX",
    description: "Verified listings, escrow-protected deposits, digital leases and agency tools for Zimbabwe.",
    start_url: "/orgs",
    scope: "/",
    display: "standalone",
    orientation: "portrait",
    background_color: "#F5F2EA",
    theme_color: "#0B1D32",
    icons: [
      { src: "/icons/icon-192.png", sizes: "192x192", type: "image/png" },
      { src: "/icons/icon-512.png", sizes: "512x512", type: "image/png" },
      { src: "/icons/maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
