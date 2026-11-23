import type { Metadata } from "next";

// A plain string here would stop the root "%s · EstateX" template reaching listing and compare
// pages, so pass the template on.
export const metadata: Metadata = { title: { absolute: "Properties · EstateX", template: "%s · EstateX" } };

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
