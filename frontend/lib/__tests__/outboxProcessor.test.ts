import { beforeEach, describe, expect, it, vi } from "vitest";
import { processOutbox, type OutboxApi, type OutboxStore } from "../offline/processor";
import type { ConflictEntry, OutboxEntry, OutboxOp } from "../offline/types";
import type { OrgListingDetail } from "../types";

class HttpError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

function memoryStore() {
  let seq = 0;
  const entries: OutboxEntry[] = [];
  const ids = new Map<string, number>();
  const conflicts: ConflictEntry[] = [];
  const store: OutboxStore = {
    pending: async (userId) => entries.filter((e) => e.userId === userId).sort((a, b) => a.seq! - b.seq!),
    update: async (e) => void entries.splice(entries.findIndex((x) => x.seq === e.seq), 1, e),
    remove: async (s) => void entries.splice(entries.findIndex((x) => x.seq === s), 1),
    mapId: async (c, id) => void ids.set(c, id),
    lookupId: async (c) => ids.get(c),
    addConflict: async (c) => void conflicts.push(c),
  };
  const add = (op: OutboxOp, userId = 1) =>
    entries.push({ seq: ++seq, userId, orgSlug: "estatex", op, createdAt: "", attempts: 0 });
  return { store, entries, conflicts, ids, add };
}

function detail(over: { titleType?: string; titleReference?: string; photos?: boolean; address?: boolean } = {}): OrgListingDetail {
  return {
    property: { id: 50, titleType: over.titleType ?? "TITLE_DEED" },
    compliance: { titleReference: over.titleReference ?? "DT 1", ownerConsentPhotos: over.photos ?? false, ownerConsentAddress: over.address ?? false },
    mandates: [],
  } as unknown as OrgListingDetail;
}

function fakeApi(overrides: Partial<OutboxApi> = {}): OutboxApi {
  return {
    createListing: vi.fn(async () => detail()),
    getListing: vi.fn(async () => detail()),
    setTitle: vi.fn(async () => ({})),
    setConsent: vi.fn(async () => ({})),
    createMandate: vi.fn(async () => ({ id: 77 }) as never),
    attachMandateDocument: vi.fn(async () => ({})),
    captureEnquiry: vi.fn(async () => ({})),
    ...overrides,
  };
}

const intake = (m: ReturnType<typeof memoryStore>) => {
  m.add({ kind: "createListing", clientId: "L1", payload: {} as never, label: "listing" });
  m.add({ kind: "setConsent", listing: { clientId: "L1" }, base: { photos: false, address: false }, mine: { photos: true, address: true }, label: "consent" });
  m.add({ kind: "createMandate", clientId: "M1", listing: { clientId: "L1" }, payload: {} as never, label: "mandate" });
  m.add({ kind: "attachMandateDocument", mandate: { clientId: "M1" }, file: new Blob(["x"]), fileName: "m.jpg", ownerSignedOn: "2026-11-17", label: "doc" });
};

describe("processOutbox", () => {
  let m: ReturnType<typeof memoryStore>;
  beforeEach(() => (m = memoryStore()));

  it("replays a full offline intake in order, resolving client ids to server ids", async () => {
    intake(m);
    const api = fakeApi();
    expect(await processOutbox(1, m.store, api)).toBe("done");
    expect(m.entries).toEqual([]);
    expect(api.setConsent).toHaveBeenCalledWith("estatex", 50, { photos: true, address: true });
    expect(api.createMandate).toHaveBeenCalledWith("estatex", 50, {}, "M1");
    expect(api.attachMandateDocument).toHaveBeenCalledWith("estatex", 77, expect.any(Blob), "m.jpg", "2026-11-17");
  });

  it("stops on a network failure and keeps everything queued, then resumes without duplicating", async () => {
    intake(m);
    let calls = 0;
    const api = fakeApi({
      setConsent: vi.fn(async () => {
        if (calls++ === 0) throw new TypeError("Failed to fetch");
        return {};
      }),
    });
    expect(await processOutbox(1, m.store, api)).toBe("offline");
    expect(m.entries.map((e) => e.op.label)).toEqual(["consent", "mandate", "doc"]);
    expect(await processOutbox(1, m.store, api)).toBe("done");
    expect(api.createListing).toHaveBeenCalledTimes(1);
    expect(m.entries).toEqual([]);
  });

  it("stops on 401 without dropping anything", async () => {
    intake(m);
    const api = fakeApi({ createListing: vi.fn(async () => { throw new HttpError(401, "expired"); }) });
    expect(await processOutbox(1, m.store, api)).toBe("auth");
    expect(m.entries).toHaveLength(4);
    expect(m.conflicts).toEqual([]);
  });

  it("backs off after a server error", async () => {
    intake(m);
    const api = fakeApi({ createListing: vi.fn(async () => { throw new HttpError(503, "down"); }) });
    expect(await processOutbox(1, m.store, api, 1_000)).toBe("backoff");
    expect(m.entries[0].attempts).toBe(1);
    expect(m.entries[0].nextAttemptAt).toBe(31_000);
    expect(await processOutbox(1, m.store, api, 2_000)).toBe("backoff");
    expect(api.createListing).toHaveBeenCalledTimes(1);
  });

  it("moves a refused write to the conflicts tray, and its dependants after it", async () => {
    intake(m);
    m.add({ kind: "captureEnquiry", clientId: "E1", payload: {} as never, label: "enquiry" });
    const api = fakeApi({ createListing: vi.fn(async () => { throw new HttpError(400, "suburb is required"); }) });
    expect(await processOutbox(1, m.store, api)).toBe("done");
    expect(m.conflicts.map((c) => c.label)).toEqual(["listing", "consent", "mandate", "doc"]);
    expect(m.conflicts[0].message).toBe("suburb is required");
    expect(api.captureEnquiry).toHaveBeenCalledTimes(1); // unrelated work still syncs
  });

  it("keeps the server's value when both sides changed a field (server wins)", async () => {
    m.add({
      kind: "setTitle",
      listing: { id: 50 },
      base: { titleType: "TITLE_DEED", titleReference: "DT 1" },
      mine: { titleType: "TITLE_DEED", titleReference: "MINE" },
      label: "title",
    });
    const api = fakeApi({ getListing: vi.fn(async () => detail({ titleReference: "THEIRS" })) });
    expect(await processOutbox(1, m.store, api)).toBe("done");
    expect(api.setTitle).not.toHaveBeenCalled();
    expect(m.conflicts[0]).toMatchObject({ kind: "field-conflict", fields: [{ field: "titleReference", mine: "MINE", theirs: "THEIRS" }] });
  });

  it("never replays another user's queued writes", async () => {
    m.add({ kind: "captureEnquiry", clientId: "E9", payload: {} as never, label: "theirs" }, 2);
    const api = fakeApi();
    expect(await processOutbox(1, m.store, api)).toBe("done");
    expect(api.captureEnquiry).not.toHaveBeenCalled();
    expect(m.entries).toHaveLength(1);
  });
});
