import type { Metadata } from "next";

export const metadata: Metadata = { title: "Home services" };

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
