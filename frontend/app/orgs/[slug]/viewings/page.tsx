"use client";

import { useEffect, useState } from "react";
import { showings } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { readThrough, useSyncStatus } from "@/lib/offline/sync";
import type { Showing } from "@/lib/types";
import { useOrgWorkspace } from "@/components/workspace/OrgWorkspace";
import ViewingCard from "@/components/workspace/ViewingCard";

const dayFmt = new Intl.DateTimeFormat("en-GB", { weekday: "long", day: "numeric", month: "long", timeZone: "Africa/Harare" });

export default function ViewingsPage() {
  const { slug, can } = useOrgWorkspace();
  const { token, user } = useAuth();
  const sync = useSyncStatus();
  const [items, setItems] = useState<Showing[] | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    if (!token) return;
    readThrough(`viewings:${slug}:${user?.id}`, () => showings.upcoming(slug, token, 14)).then((r) => setItems(r.data), () => setItems([]));
  }, [slug, token, user?.id, sync.pending, reload]);

  if (!can("CONTACTS_MANAGE")) return <p className="text-muted">Your role doesn&apos;t include viewings.</p>;

  const byDay = new Map<string, Showing[]>();
  for (const v of items ?? []) {
    const key = dayFmt.format(new Date(v.scheduledAt));
    byDay.set(key, [...(byDay.get(key) ?? []), v]);
  }

  return (
    <div className="max-w-3xl space-y-4">
      <div>
        <h1 className="text-xl font-semibold">Viewings</h1>
        <p className="text-sm text-muted mt-1">Today and the next two weeks. Book viewings from a listing&apos;s Viewings &amp; offers tab; record outcomes here or there, even offline.</p>
      </div>
      {message && <p role="status" className="text-sm text-success">{message}</p>}
      {!items ? <p className="text-muted">Loading…</p> : items.length === 0 ? <p className="text-muted">No viewings booked.</p> : (
        [...byDay.entries()].map(([day, list]) => (
          <section key={day}>
            <h2 className="text-sm font-semibold text-muted mb-2">{day}</h2>
            <ul className="space-y-2">
              {list.map((v) => <ViewingCard key={v.id} slug={slug} viewing={v} showListing onSaved={(m) => { setMessage(m); setReload((n) => n + 1); }} />)}
            </ul>
          </section>
        ))
      )}
    </div>
  );
}
