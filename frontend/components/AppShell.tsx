"use client";

import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth";
import Navbar from "@/components/Navbar";
import NavSidebar from "@/components/NavSidebar";

export default function AppShell({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuth();
  const pathname = usePathname();

  const showSidebar = !loading && !!user && !pathname.startsWith("/admin");

  if (!showSidebar) {
    return (
      <>
        <Navbar />
        <main className="flex-1 flex flex-col">{children}</main>
      </>
    );
  }

  return (
    <>
      <Navbar />
      <div className="flex flex-1 flex-col md:flex-row min-h-0">
        <NavSidebar roles={user.roles} />
        <main className="flex-1 flex flex-col min-w-0">{children}</main>
      </div>
    </>
  );
}
