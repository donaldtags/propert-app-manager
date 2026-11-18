import type { Metadata, Viewport } from "next";
import { Inter, Montserrat } from "next/font/google";
import "./globals.css";
import { AuthProvider } from "@/lib/auth";
import AppShell from "@/components/AppShell";
import ReactDOM from "react-dom";
import { BASE } from "@/lib/api";

// Open the connection to the API while the page is still loading. On a far-away server this
// saves the DNS + TCP + TLS round trips (often more than half a second on mobile) that would
// otherwise happen only when the first data request fires. No credentials: requests use a
// bearer header, not cookies, so the anonymous connection is the one that gets reused.
const API_ORIGIN = (() => {
  try {
    return new URL(BASE).origin;
  } catch {
    return null;
  }
})();

// EstateX type: Montserrat for headings, Inter for body. Self-hosted by next/font.
const inter = Inter({
  variable: "--font-inter",
  subsets: ["latin"],
  display: "swap",
});

const montserrat = Montserrat({
  variable: "--font-montserrat",
  subsets: ["latin"],
  weight: ["500", "600", "700"],
  display: "swap",
});

export const metadata: Metadata = {
  title: "EstateX – More Than Property. Building Possibilities.",
  description:
    "Verified property listings, escrow-protected deposits, digital leases and agency tools for Zimbabwe and the diaspora.",
  applicationName: "EstateX",
  appleWebApp: { capable: true, title: "EstateX", statusBarStyle: "default" },
  icons: { apple: "/icons/apple-touch-icon.png" },
};

export const viewport: Viewport = {
  themeColor: "#0B1D32",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  if (API_ORIGIN) ReactDOM.preconnect(API_ORIGIN, { crossOrigin: "anonymous" });
  return (
    <html lang="en" className={`${inter.variable} ${montserrat.variable} h-full`}>
      <body className="min-h-full flex flex-col bg-background text-gray-900 antialiased">
        <AuthProvider>
          <AppShell>{children}</AppShell>
        </AuthProvider>
      </body>
    </html>
  );
}
