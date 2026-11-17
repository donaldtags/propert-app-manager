"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Briefcase, ChevronRight } from "lucide-react";
import { organisations } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import type { OrgContext } from "@/lib/types";
import PageLoader from "@/components/PageLoader";
import EmptyState from "@/components/EmptyState";
import { ROLE_LABELS } from "@/components/workspace/OrgWorkspace";

export default function WorkspacesPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();
  const [items, setItems] = useState<OrgContext[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (loading) return;
    if (!user || !token) {
      router.replace("/login?next=/orgs");
      return;
    }
    organisations.mine(token).then(setItems, (e: unknown) =>
      setError(e instanceof Error ? e.message : "Could not load your workspaces")
    );
  }, [loading, user, token, router]);

  if (error) return <p className="max-w-3xl mx-auto px-4 py-10 text-red-700">{error}</p>;
  if (!items) return <PageLoader />;

  const isAdmin = user?.roles.includes("ADMIN");

  return (
    <div className="max-w-3xl mx-auto w-full px-4 py-8">
      <h1 className="text-2xl font-bold text-brand-600">Workspaces</h1>
      <p className="text-sm text-gray-600 mt-1">Agencies and teams you work with on EstateX.</p>

      {items.length === 0 ? (
        <div className="mt-8">
          <EmptyState
            icon={Briefcase}
            title="You're not in any workspace yet"
            hint="An agency admin adds you to their workspace using the email on your account."
          />
        </div>
      ) : (
        <ul className="mt-6 space-y-3">
          {items.map(({ organisation: o, roles }) => (
            <li key={o.id}>
              <Link
                href={`/orgs/${o.slug}`}
                className="flex items-center gap-4 rounded-2xl border border-gray-200 bg-white p-4 hover:border-brand-300"
              >
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={o.logoOnLightUrl ?? "/orgs/estatex/logo-on-light.svg"} alt="" className="h-10 w-auto max-w-[140px]" />
                <div className="min-w-0 flex-1">
                  <p className="font-semibold text-gray-900 truncate">{o.name}</p>
                  <p className="text-xs text-gray-500 truncate">{roles.map((r) => ROLE_LABELS[r] ?? r).join(" · ")}</p>
                </div>
                <ChevronRight className="w-5 h-5 text-gray-400 shrink-0" />
              </Link>
            </li>
          ))}
        </ul>
      )}

      {isAdmin && (
        <p className="mt-8 text-sm text-gray-600">
          Platform admin: open{" "}
          <Link href="/orgs/estatex" className="font-medium text-brand-600 underline">
            the ESTATEX workspace
          </Link>{" "}
          to add its first members.
        </p>
      )}
    </div>
  );
}
