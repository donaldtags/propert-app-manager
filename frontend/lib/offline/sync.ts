"use client";

import { useSyncExternalStore } from "react";
import { campaigns, contacts, enquiries, intake, isNetworkError, orgListings, pipeline, showings } from "@/lib/api";
import { addToOutbox, cacheGet, cacheSet, conflictsFor, idbOutboxStore } from "./db";
import { processOutbox, type OutboxApi, type ProcessOutcome } from "./processor";
import type { OutboxEntry, OutboxOp } from "./types";

/**
 * Browser-side sync engine. One outbox for every offline-capable write; it replays whenever the
 * device comes online, every 30 s while there's work, and right after anything is queued.
 */
export interface SyncStatus {
  online: boolean;
  syncing: boolean;
  pending: number;
  conflicts: number;
  lastOutcome?: ProcessOutcome;
  lastSyncedAt?: string;
}

let status: SyncStatus = { online: true, syncing: false, pending: 0, conflicts: 0 };
const listeners = new Set<() => void>();
let session: { userId: number; token: string } | null = null;
let timer: ReturnType<typeof setInterval> | null = null;
let running: Promise<void> | null = null;

function setStatus(patch: Partial<SyncStatus>) {
  status = { ...status, ...patch };
  listeners.forEach((l) => l());
}

const SERVER_STATUS: SyncStatus = { online: true, syncing: false, pending: 0, conflicts: 0 };

export function useSyncStatus(): SyncStatus {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    () => status,
    () => SERVER_STATUS
  );
}

function api(token: string): OutboxApi {
  return {
    createListing: (slug, payload, clientId) => orgListings.create(slug, { ...payload, clientId }, token),
    getListing: (slug, id) => orgListings.get(slug, id, token),
    setTitle: (slug, id, data) => orgListings.setTitle(slug, id, data, token),
    setConsent: (slug, id, data) => orgListings.setConsent(slug, id, data, token),
    createMandate: (slug, listingId, payload, clientId) => orgListings.createMandate(slug, listingId, { ...payload, clientId }, token),
    attachMandateDocument: (slug, mandateId, file, fileName, ownerSignedOn) =>
      orgListings.attachSignedMandate(slug, mandateId, new File([file], fileName, { type: file.type }), ownerSignedOn, token),
    captureEnquiry: (slug, payload, clientId) => enquiries.capture(slug, { ...payload, clientId }, token),
    createContact: (slug, payload, clientId) => contacts.create(slug, { ...payload, clientId }, token),
    createPipelineCard: (slug, ownerContactId, payload, clientId) =>
      pipeline.create(slug, { ...payload, ownerContactId, clientId }, token),
    movePipelineStage: (slug, cardId, stage, lostReason) => pipeline.move(slug, cardId, stage, lostReason, token),
    scheduleViewing: (slug, propertyId, contactId, scheduledAt, durationMinutes, clientId) =>
      showings.schedule(slug, propertyId, { clientId, contactId, scheduledAt, durationMinutes }, token),
    viewingOutcome: (slug, viewingId, op) =>
      showings.outcome(slug, viewingId, { status: op.status, interest: op.interest, priceOpinion: op.priceOpinion, feedback: op.feedback }, token),
    setCampaignTask: (slug, taskId, done) => campaigns.setTask(slug, taskId, done, token),
    saveInspection: (slug, listingId, op) =>
      intake.saveInspection(slug, listingId, {
        items: op.items,
        standSizeM2: op.standSizeM2,
        floorAreaM2: op.floorAreaM2,
        notes: op.notes,
        baseVersion: op.baseVersion,
      }, token),
    uploadInspectionPhoto: (slug, listingId, op) =>
      intake.uploadPhoto(slug, listingId, op.file, op.fileName, { clientId: op.clientId, itemKey: op.itemKey, caption: op.caption }, token),
  };
}

/** Re-reads the pending/conflict counts (after a manual discard or dismiss). */
export async function refreshCounts() {
  if (!session) return;
  const [pending, conflicts] = await Promise.all([
    idbOutboxStore.pending(session.userId),
    conflictsFor(session.userId),
  ]);
  setStatus({ pending: pending.length, conflicts: conflicts.length });
}

/** Runs the outbox now (no-op if already running or there's no session). */
export function syncNow(): Promise<void> {
  if (!session || running) return running ?? Promise.resolve();
  const { userId, token } = session;
  running = (async () => {
    setStatus({ syncing: true });
    try {
      const pass = () => processOutbox(userId, idbOutboxStore, api(token));
      // Keep going while passes succeed and work remains (entries queued during a pass).
      const run = async () => {
        let outcome = await pass();
        for (let i = 0; i < 5 && outcome === "done" && (await idbOutboxStore.pending(userId)).length > 0; i++) {
          outcome = await pass();
        }
        return outcome;
      };
      // Web Locks keep two open tabs from replaying the same queue at once.
      const outcome = typeof navigator !== "undefined" && navigator.locks
        ? await navigator.locks.request("estatex-outbox", run)
        : await run();
      setStatus({
        lastOutcome: outcome,
        online: outcome === "offline" ? false : status.online,
        lastSyncedAt: outcome === "done" ? new Date().toISOString() : status.lastSyncedAt,
      });
    } catch {
      // storage unavailable; nothing to do
    } finally {
      await refreshCounts().catch(() => undefined);
      setStatus({ syncing: false });
      running = null;
    }
  })();
  return running;
}

/** Called by the workspace layout once the signed-in user is known. */
export function startSync(userId: number, token: string) {
  session = { userId, token };
  if (typeof window === "undefined") return;
  setStatus({ online: navigator.onLine });
  if (!timer) {
    window.addEventListener("online", () => {
      setStatus({ online: true });
      void syncNow();
    });
    window.addEventListener("offline", () => setStatus({ online: false }));
    timer = setInterval(() => {
      if (status.pending > 0 && navigator.onLine) void syncNow();
    }, 30_000);
    // Ask the browser not to evict queued work under storage pressure.
    void navigator.storage?.persist?.();
  }
  void refreshCounts().then(() => syncNow());
}

/** Queues a write and immediately tries to send it. Resolves once queued (not once synced). */
export async function enqueue(orgSlug: string, op: OutboxOp): Promise<void> {
  if (!session) throw new Error("Not signed in");
  const entry: OutboxEntry = { userId: session.userId, orgSlug, op, createdAt: new Date().toISOString(), attempts: 0 };
  await addToOutbox(entry);
  await refreshCounts();
  void syncNow();
}

/**
 * Queues several dependent writes (e.g. a whole intake: listing, title, consent, mandate, signed
 * copy) in order, then syncs once and waits. True when everything reached the server.
 */
export async function enqueueAllAndWait(orgSlug: string, ops: OutboxOp[]): Promise<boolean> {
  if (!session) throw new Error("Not signed in");
  for (const op of ops) {
    await addToOutbox({ userId: session.userId, orgSlug, op, createdAt: new Date().toISOString(), attempts: 0 });
  }
  await refreshCounts();
  if (running) await running;
  await syncNow();
  return status.pending === 0;
}

export function enqueueAndWait(orgSlug: string, op: OutboxOp): Promise<boolean> {
  return enqueueAllAndWait(orgSlug, [op]);
}

/** Queued writes for the signed-in user, oldest first (for "waiting to sync" UI). */
export async function pendingEntries(): Promise<OutboxEntry[]> {
  return session ? idbOutboxStore.pending(session.userId) : [];
}

export async function lookupServerId(clientId: string): Promise<number | undefined> {
  return idbOutboxStore.lookupId(clientId);
}

export function currentUserId(): number | undefined {
  return session?.userId;
}

/**
 * Network-first read with an offline fallback: returns fresh data and caches it, or the last
 * cached copy when the server can't be reached. Real API errors (403, 404, ...) still throw.
 */
export async function readThrough<T>(key: string, fetcher: () => Promise<T>): Promise<{ data: T; fromCache: boolean; savedAt?: string }> {
  try {
    const data = await fetcher();
    void cacheSet(key, data);
    return { data, fromCache: false };
  } catch (e) {
    if (!isNetworkError(e)) throw e;
    setStatus({ online: false });
    const cached = await cacheGet<T>(key);
    if (!cached) throw e;
    return { data: cached.data, fromCache: true, savedAt: cached.savedAt };
  }
}

export function newClientId(): string {
  return typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}
