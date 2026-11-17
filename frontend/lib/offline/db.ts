"use client";

import { openDB, type DBSchema, type IDBPDatabase } from "idb";
import type { OutboxStore } from "./processor";
import type { ConflictEntry, OutboxEntry } from "./types";

interface OfflineDB extends DBSchema {
  outbox: { key: number; value: OutboxEntry; indexes: { byUser: number } };
  idmap: { key: string; value: { clientId: string; serverId: number } };
  conflicts: { key: number; value: ConflictEntry; indexes: { byUser: number } };
  cache: { key: string; value: { key: string; data: unknown; savedAt: string } };
}

let dbPromise: Promise<IDBPDatabase<OfflineDB>> | null = null;

export function offlineDb(): Promise<IDBPDatabase<OfflineDB>> {
  if (!dbPromise) {
    dbPromise = openDB<OfflineDB>("estatex-offline", 1, {
      upgrade(db) {
        const outbox = db.createObjectStore("outbox", { keyPath: "seq", autoIncrement: true });
        outbox.createIndex("byUser", "userId");
        db.createObjectStore("idmap", { keyPath: "clientId" });
        const conflicts = db.createObjectStore("conflicts", { keyPath: "id", autoIncrement: true });
        conflicts.createIndex("byUser", "userId");
        db.createObjectStore("cache", { keyPath: "key" });
      },
    });
  }
  return dbPromise;
}

export const idbOutboxStore: OutboxStore = {
  async pending(userId) {
    const rows = await (await offlineDb()).getAllFromIndex("outbox", "byUser", userId);
    return rows.sort((a, b) => a.seq! - b.seq!);
  },
  async update(entry) {
    await (await offlineDb()).put("outbox", entry);
  },
  async remove(seq) {
    await (await offlineDb()).delete("outbox", seq);
  },
  async mapId(clientId, serverId) {
    await (await offlineDb()).put("idmap", { clientId, serverId });
  },
  async lookupId(clientId) {
    return (await (await offlineDb()).get("idmap", clientId))?.serverId;
  },
  async addConflict(conflict) {
    await (await offlineDb()).add("conflicts", conflict);
  },
};

export async function addToOutbox(entry: OutboxEntry): Promise<void> {
  await (await offlineDb()).add("outbox", entry);
}

export async function conflictsFor(userId: number): Promise<ConflictEntry[]> {
  return (await offlineDb()).getAllFromIndex("conflicts", "byUser", userId);
}

export async function dismissConflict(id: number): Promise<void> {
  await (await offlineDb()).delete("conflicts", id);
}

export async function discardOutboxEntry(seq: number): Promise<void> {
  await (await offlineDb()).delete("outbox", seq);
}

export async function cacheGet<T>(key: string): Promise<{ data: T; savedAt: string } | undefined> {
  try {
    const row = await (await offlineDb()).get("cache", key);
    return row ? { data: row.data as T, savedAt: row.savedAt } : undefined;
  } catch {
    return undefined;
  }
}

export async function cacheSet(key: string, data: unknown): Promise<void> {
  try {
    await (await offlineDb()).put("cache", { key, data, savedAt: new Date().toISOString() });
  } catch {
    // quota or private mode: reads just won't be available offline
  }
}
