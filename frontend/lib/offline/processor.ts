import { threeWayMerge } from "./merge";
import type { ConflictEntry, OutboxEntry, Ref } from "./types";
import type { Mandate, OrgListingDetail } from "@/lib/types";

/** Storage the processor needs. IndexedDB in the app, an in-memory fake in tests. */
export interface OutboxStore {
  pending(userId: number): Promise<OutboxEntry[]>;
  update(entry: OutboxEntry): Promise<void>;
  remove(seq: number): Promise<void>;
  mapId(clientId: string, serverId: number): Promise<void>;
  lookupId(clientId: string): Promise<number | undefined>;
  addConflict(conflict: ConflictEntry): Promise<void>;
}

/** The API calls the processor makes. */
export interface OutboxApi {
  createListing(slug: string, payload: OutboxEntryPayload<"createListing">, clientId: string): Promise<OrgListingDetail>;
  getListing(slug: string, id: number): Promise<OrgListingDetail>;
  setTitle(slug: string, id: number, data: { titleType: NonNullable<OrgListingDetail["property"]["titleType"]>; titleReference?: string }): Promise<unknown>;
  setConsent(slug: string, id: number, data: { photos: boolean; address: boolean }): Promise<unknown>;
  createMandate(slug: string, listingId: number, payload: OutboxEntryPayload<"createMandate">, clientId: string): Promise<Mandate>;
  attachMandateDocument(slug: string, mandateId: number, file: Blob, fileName: string, ownerSignedOn: string): Promise<unknown>;
  captureEnquiry(slug: string, payload: OutboxEntryPayload<"captureEnquiry">, clientId: string): Promise<unknown>;
}

type OpOf<K extends OutboxEntry["op"]["kind"]> = Extract<OutboxEntry["op"], { kind: K }>;
type OutboxEntryPayload<K extends "createListing" | "createMandate" | "captureEnquiry"> = OpOf<K>["payload"];

export type ProcessOutcome = "done" | "offline" | "auth" | "backoff";

/** Retry delays after a server error: 30s, 2m, 10m, then hourly. */
const BACKOFF_MS = [30_000, 120_000, 600_000, 3_600_000];

class Blocked extends Error {}

function statusOf(e: unknown): number | undefined {
  return typeof e === "object" && e !== null && "status" in e ? (e as { status: number }).status : undefined;
}

function isNetwork(e: unknown): boolean {
  return e instanceof TypeError || (e instanceof Error && /Failed to fetch|NetworkError|Load failed|fetch failed/i.test(e.message));
}

/**
 * Replays the user's queued writes in order (FIFO across all kinds, so a listing is always created
 * before its title, consent and mandate are sent).
 *  - network failure  -> stop; everything stays queued for the next attempt
 *  - 401              -> stop; the session must be renewed, nothing is dropped
 *  - 5xx              -> stop with backoff on that entry
 *  - other 4xx        -> the server refused it (rule broken, record gone): move it to the
 *                        conflicts tray so a person sees why, and carry on with the rest
 */
export async function processOutbox(userId: number, store: OutboxStore, api: OutboxApi, now = Date.now()): Promise<ProcessOutcome> {
  const resolve = async (ref: Ref): Promise<number> => {
    if ("id" in ref) return ref.id;
    const id = await store.lookupId(ref.clientId);
    if (id === undefined) throw new Blocked("The record this change belongs to was never saved on the server.");
    return id;
  };

  for (const entry of await store.pending(userId)) {
    if (entry.nextAttemptAt && entry.nextAttemptAt > now) return "backoff";
    const { op, orgSlug: slug } = entry;
    try {
      switch (op.kind) {
        case "createListing": {
          const created = await api.createListing(slug, op.payload, op.clientId);
          await store.mapId(op.clientId, created.property.id);
          break;
        }
        case "setTitle":
        case "setConsent": {
          const id = await resolve(op.listing);
          const current = await api.getListing(slug, id);
          if (op.kind === "setTitle") {
            const theirs = { titleType: current.property.titleType ?? null, titleReference: current.compliance.titleReference ?? null };
            const { merged, applied, conflicts } = threeWayMerge(op.base, op.mine, theirs);
            if (conflicts.length) await store.addConflict(fieldConflict(entry, conflicts));
            if (applied.length && merged.titleType) {
              await api.setTitle(slug, id, { titleType: merged.titleType, titleReference: merged.titleReference ?? undefined });
            }
          } else {
            const theirs = { photos: current.compliance.ownerConsentPhotos, address: current.compliance.ownerConsentAddress };
            const { merged, applied, conflicts } = threeWayMerge(op.base, op.mine, theirs);
            if (conflicts.length) await store.addConflict(fieldConflict(entry, conflicts));
            if (applied.length) await api.setConsent(slug, id, merged);
          }
          break;
        }
        case "createMandate": {
          const listingId = await resolve(op.listing);
          const mandate = await api.createMandate(slug, listingId, op.payload, op.clientId);
          await store.mapId(op.clientId, mandate.id);
          break;
        }
        case "attachMandateDocument": {
          const mandateId = await resolve(op.mandate);
          await api.attachMandateDocument(slug, mandateId, op.file, op.fileName, op.ownerSignedOn);
          break;
        }
        case "captureEnquiry":
          await api.captureEnquiry(slug, op.payload, op.clientId);
          break;
      }
      await store.remove(entry.seq!);
    } catch (e) {
      if (isNetwork(e)) {
        await store.update({ ...entry, lastError: "Waiting for a connection" });
        return "offline";
      }
      const status = statusOf(e);
      if (status === 401) {
        await store.update({ ...entry, lastError: "Sign in again to sync" });
        return "auth";
      }
      if (status !== undefined && status >= 500) {
        const attempts = entry.attempts + 1;
        await store.update({
          ...entry,
          attempts,
          lastError: (e as Error).message,
          nextAttemptAt: now + BACKOFF_MS[Math.min(attempts - 1, BACKOFF_MS.length - 1)],
        });
        return "backoff";
      }
      // Refused by the server (4xx) or blocked by a parent that was refused: needs a person.
      await store.addConflict({
        userId: entry.userId,
        orgSlug: slug,
        label: op.label,
        kind: "rejected",
        message: e instanceof Error ? e.message : "The server refused this change",
        createdAt: new Date(now).toISOString(),
      });
      await store.remove(entry.seq!);
    }
  }
  return "done";
}

function fieldConflict(entry: OutboxEntry, conflicts: { field: string; mine: unknown; theirs: unknown }[]): ConflictEntry {
  return {
    userId: entry.userId,
    orgSlug: entry.orgSlug,
    label: entry.op.label,
    kind: "field-conflict",
    message: "Someone changed this on the server while you were offline. Their version was kept.",
    fields: conflicts.map(({ field, mine, theirs }) => ({ field, mine, theirs })),
    createdAt: new Date().toISOString(),
  };
}
