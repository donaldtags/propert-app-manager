"use client";

import Link from "next/link";
import { useParams, usePathname } from "next/navigation";

/** Sub-navigation shared by every page of one listing. */
export default function ListingLayout({ children }: { children: React.ReactNode }) {
  const { slug, id } = useParams<{ slug: string; id: string }>();
  const pathname = usePathname();
  const base = `/orgs/${slug}/listings/${id}`;
  const tabs = [
    { href: base, label: "Compliance" },
    { href: `${base}/intake`, label: "Intake" },
    { href: `${base}/marketing`, label: "Marketing" },
    { href: `${base}/activity`, label: "Viewings & offers" },
  ];
  return (
    <div>
      <nav aria-label="Listing sections" className="mb-5 flex gap-1 overflow-x-auto border-b border-line">
        {tabs.map((t) => {
          const active = t.href === base ? pathname === base : pathname.startsWith(t.href);
          return (
            <Link key={t.href} href={t.href} aria-current={active ? "page" : undefined}
              className={`px-3 py-2.5 text-sm font-medium whitespace-nowrap border-b-2 -mb-px ${active ? "border-primary text-ink" : "border-transparent text-muted hover:text-ink"}`}>
              {t.label}
            </Link>
          );
        })}
      </nav>
      {children}
    </div>
  );
}
