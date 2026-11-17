"use client";

import Link from "next/link";
import { useParams, usePathname, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { ArrowLeft, CloudOff, Monitor, Moon, Sun } from "lucide-react";
import { organisations } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { themeStyle, useColorMode, type ColorMode } from "@/lib/orgTheme";
import type { OrgContext, OrgPermission } from "@/lib/types";
import PageLoader from "@/components/PageLoader";
import { OrgWorkspaceProvider, ws } from "@/components/workspace/OrgWorkspace";
import SyncIndicator from "@/components/workspace/SyncIndicator";
import EnquirySheet from "@/components/workspace/EnquirySheet";
import { readThrough, startSync, useSyncStatus } from "@/lib/offline/sync";
import { registerServiceWorker } from "@/lib/offline/serviceWorker";

const MODES: { mode: ColorMode; icon: typeof Sun; label: string }[] = [
  { mode: "light", icon: Sun, label: "Light" },
  { mode: "dark", icon: Moon, label: "Dark" },
  { mode: "system", icon: Monitor, label: "System" },
];

export default function OrgWorkspaceLayout({ children }: { children: React.ReactNode }) {
  const { slug } = useParams<{ slug: string }>();
  const pathname = usePathname();
  const router = useRouter();
  const { user, token, loading } = useAuth();
  const [mode, setMode] = useColorMode();
  const [context, setContext] = useState<OrgContext | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cachedAt, setCachedAt] = useState<string | undefined>();
  const sync = useSyncStatus();
  const userId = user?.id;

  // Network first; offline, fall back to the last copy of this workspace saved on the device.
  const fetchContext = useCallback(
    () =>
      readThrough(`orgctx:${slug}:${userId}`, () => organisations.me(slug, token ?? "")).then(
        (r) => {
          setContext(r.data);
          setCachedAt(r.fromCache ? r.savedAt : undefined);
          setError(null);
        },
        (e: unknown) =>
          setError(
            e instanceof TypeError
              ? "You're offline and this workspace hasn't been opened on this device before."
              : e instanceof Error
                ? e.message
                : "Could not open this workspace"
          )
      ),
    [slug, token, userId]
  );

  useEffect(() => {
    if (loading) return;
    if (!user || !token) {
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
      return;
    }
    startSync(user.id, token);
    registerServiceWorker([
      `/orgs/${slug}`, `/orgs/${slug}/listings`, `/orgs/${slug}/listings/new`, `/orgs/${slug}/enquiries`,
      `/orgs/${slug}/sync`, `/orgs/${slug}/contacts`, `/orgs/${slug}/pipeline`, `/orgs/${slug}/viewings`,
    ]);
    void fetchContext();
  }, [loading, user, token, fetchContext, router, pathname, slug]);

  const base = `/orgs/${slug}`;
  const org = context?.organisation;
  const can = (p: OrgPermission) => !!context?.permissions.includes(p);
  const tabs = [
    { href: base, label: "Home", show: true },
    { href: `${base}/pipeline`, label: "Pipeline", show: can("LISTING_EDIT") },
    { href: `${base}/listings`, label: "Listings", show: can("LISTING_CREATE") || can("LISTING_EDIT") || can("ORG_SETTINGS_EDIT") },
    { href: `${base}/contacts`, label: "Contacts", show: can("CONTACTS_MANAGE") },
    { href: `${base}/enquiries`, label: "Enquiries", show: can("ENQUIRY_CAPTURE") },
    { href: `${base}/viewings`, label: "Viewings", show: can("CONTACTS_MANAGE") },
    { href: `${base}/sync`, label: "Sync", show: sync.pending > 0 || sync.conflicts > 0 || pathname.startsWith(`${base}/sync`) },
    { href: `${base}/settings`, label: "Settings", show: can("MEMBERS_VIEW") || can("ORG_SETTINGS_EDIT") },
    { href: `${base}/audit`, label: "Audit log", show: can("AUDIT_VIEW") },
  ].filter((t) => t.show);

  return (
    <div className="ex-theme flex-1 flex flex-col min-w-0" data-mode={mode} style={themeStyle(org?.theme)}>
      {error ? (
        <div className="max-w-xl mx-auto w-full px-4 py-16">
          <div className={`${ws.card} p-6`}>
            <h1 className="text-xl font-semibold">Workspace unavailable</h1>
            <p className="mt-2 text-sm text-muted">{error}</p>
            <Link href="/orgs" className={`${ws.buttonGhost} mt-6`}>
              <ArrowLeft className="w-4 h-4" /> Your workspaces
            </Link>
          </div>
        </div>
      ) : !context || !org ? (
        <PageLoader />
      ) : (
        <OrgWorkspaceProvider value={{ slug, context, can, refresh: fetchContext }}>
          <header className="border-b border-line bg-raised">
            <div className="max-w-6xl mx-auto w-full px-4 pt-4 flex flex-wrap items-center gap-3 justify-between">
              <div className="flex items-center gap-3 min-w-0">
                <picture className="shrink-0">
                  {org.logoOnDarkUrl && (
                    <source
                      srcSet={org.logoOnDarkUrl}
                      media={mode === "dark" ? "all" : mode === "light" ? "not all" : "(prefers-color-scheme: dark)"}
                    />
                  )}
                  <img src={org.logoOnLightUrl ?? "/orgs/estatex/logo-on-light.svg"} alt={org.name} className="h-9 w-auto max-w-[180px]" />
                </picture>
                {context.platformOverride && (
                  <span className={`${ws.chip} text-warning`} title="You are viewing as a platform admin, not a member">
                    Admin view
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2">
              <SyncIndicator slug={slug} />
              <div role="radiogroup" aria-label="Colour mode" className="flex rounded-lg border border-line overflow-hidden">
                {MODES.map(({ mode: m, icon: Icon, label }) => (
                  <button
                    key={m}
                    role="radio"
                    aria-checked={mode === m}
                    aria-label={label}
                    title={label}
                    onClick={() => setMode(m)}
                    className={`p-2.5 min-w-11 flex items-center justify-center ${
                      mode === m ? "bg-primary text-on-primary" : "text-muted hover:text-ink"
                    }`}
                  >
                    <Icon className="w-4 h-4" />
                  </button>
                ))}
              </div>
              </div>
            </div>
            <nav className="max-w-6xl mx-auto w-full px-4 mt-3 flex gap-1 overflow-x-auto" aria-label="Workspace">
              {tabs.map((t) => {
                const active = t.href === base ? pathname === base : pathname.startsWith(t.href);
                return (
                  <Link
                    key={t.href}
                    href={t.href}
                    className={`px-3 py-2.5 text-sm font-medium whitespace-nowrap border-b-2 ${
                      active ? "border-primary text-ink" : "border-transparent text-muted hover:text-ink"
                    }`}
                  >
                    {t.label}
                  </Link>
                );
              })}
            </nav>
          </header>
          {(!sync.online || cachedAt) && (
            <div role="status" className="border-b border-line bg-surface">
              <p className="max-w-6xl mx-auto px-4 py-2 text-xs flex items-center gap-2 text-warning">
                <CloudOff className="w-4 h-4 shrink-0" aria-hidden />
                You&apos;re offline. New listings, intake details and enquiries are saved on this device and sync
                automatically. Approving and publishing need a connection.
              </p>
            </div>
          )}
          <div className="max-w-6xl mx-auto w-full px-4 py-6 pb-28 flex-1">{children}</div>
          {can("ENQUIRY_CAPTURE") && <EnquirySheet slug={slug} />}
        </OrgWorkspaceProvider>
      )}
    </div>
  );
}
