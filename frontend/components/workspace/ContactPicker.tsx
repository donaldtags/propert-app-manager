"use client";

import { useEffect, useState } from "react";
import { UserPlus } from "lucide-react";
import { contacts as contactsApi } from "@/lib/api";
import { useAuth } from "@/lib/auth";
import { newClientId, readThrough } from "@/lib/offline/sync";
import type { OutboxOp, Ref } from "@/lib/offline/types";
import type { Contact } from "@/lib/types";
import { ws } from "./OrgWorkspace";

export interface PickedContact {
  ref: Ref;
  name: string;
  /** Present when the contact is new: queue this BEFORE the op that uses `ref`. */
  createOp?: OutboxOp;
}

/**
 * Pick an existing contact (search online; offline, the last cached list) or add a new one by
 * name and phone. New contacts get a client id so they can be used offline straight away.
 */
export default function ContactPicker({ slug, label, onPick }: { slug: string; label: string; onPick: (c: PickedContact | null) => void }) {
  const { token, user } = useAuth();
  const [q, setQ] = useState("");
  const [all, setAll] = useState<Contact[]>([]);
  const [picked, setPicked] = useState<PickedContact | null>(null);
  const [adding, setAdding] = useState(false);
  const [newName, setNewName] = useState("");
  const [newPhone, setNewPhone] = useState("");

  useEffect(() => {
    if (!token) return;
    readThrough(`contacts:${slug}:${user?.id}`, () => contactsApi.search(slug, token)).then((r) => setAll(r.data), () => setAll([]));
  }, [slug, token, user?.id]);

  const term = q.trim().toLowerCase();
  const digits = term.replace(/\D/g, "");
  const matches = term
    ? all.filter((c) => c.fullName.toLowerCase().includes(term) || (digits.length >= 3 && (c.phoneNormalised ?? "").includes(digits.replace(/^0/, "")))).slice(0, 6)
    : [];

  const choose = (c: PickedContact | null) => {
    setPicked(c);
    onPick(c);
  };

  if (picked) {
    return (
      <div>
        <span className={ws.label}>{label}</span>
        <div className="flex items-center justify-between gap-2 rounded-lg border border-line px-3 min-h-11">
          <span className="text-sm font-medium truncate">
            {picked.name} {picked.createOp && <span className="text-xs text-muted">(new)</span>}
          </span>
          <button type="button" className="text-xs underline text-muted" onClick={() => choose(null)}>
            Change
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <label className="block">
        <span className={ws.label}>{label}</span>
        <input className={ws.input} placeholder="Search name or phone" value={q} onChange={(e) => setQ(e.target.value)} />
      </label>
      {matches.length > 0 && (
        <ul className="rounded-lg border border-line divide-y divide-[var(--ex-line)]">
          {matches.map((c) => (
            <li key={c.id}>
              <button type="button" className="w-full text-left px-3 py-2.5 text-sm hover:bg-surface"
                onClick={() => choose({ ref: { id: c.id }, name: c.fullName })}>
                <span className="font-medium">{c.fullName}</span> <span className="text-muted">{c.phone ?? c.email ?? ""}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
      {!adding ? (
        <button type="button" className={`${ws.buttonGhost} w-full`} onClick={() => { setAdding(true); setNewName(q); }}>
          <UserPlus className="w-4 h-4" /> New person
        </button>
      ) : (
        <div className="grid gap-2 sm:grid-cols-[1fr,1fr,auto] items-end rounded-lg border border-line p-2">
          <input className={ws.input} placeholder="Full name" value={newName} onChange={(e) => setNewName(e.target.value)} aria-label="New contact name" />
          <input className={ws.input} placeholder="Phone / WhatsApp" inputMode="tel" value={newPhone} onChange={(e) => setNewPhone(e.target.value)} aria-label="New contact phone" />
          <button
            type="button"
            className={ws.button}
            disabled={!newName.trim()}
            onClick={() => {
              const clientId = newClientId();
              choose({
                ref: { clientId },
                name: newName.trim(),
                createOp: {
                  kind: "createContact",
                  clientId,
                  label: `New contact: ${newName.trim()}`,
                  payload: { fullName: newName.trim(), phone: newPhone.trim() || undefined, source: "NETWORK" },
                },
              });
            }}
          >
            Use
          </button>
        </div>
      )}
    </div>
  );
}
