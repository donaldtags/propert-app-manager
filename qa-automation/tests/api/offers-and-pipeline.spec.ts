import { test, expect } from "../../src/fixtures";
import { uniqueId } from "../../src/api";

test.describe("Offers within mandate authority", () => {
  test("below the owner's minimum needs the owner's written instruction; acceptance reserves the listing", async ({ api, agency: a }) => {
    const id = await api.publishedListing(a.slug, a, { minimumCents: 23_000_000 });
    const buyer = await api.contact(a.slug, a.negotiator, "QA Buyer");
    const offer = await api.ok("POST", `/orgs/${a.slug}/listings/${id}/offers`, {
      token: a.negotiator.token, data: { buyerContactId: buyer, amountCents: 22_000_000 },
    });
    expect(offer.withinAuthority).toBe(false);

    const refused = await api.call("POST", `/orgs/${a.slug}/offers/${offer.id}/owner-response`, { token: a.negotiator.token, data: { decision: "ACCEPT" } });
    expect(refused.status()).toBe(409);

    const accepted = await api.ok("POST", `/orgs/${a.slug}/offers/${offer.id}/owner-response`, {
      token: a.negotiator.token, data: { decision: "ACCEPT", ownerInstructionReference: "WhatsApp from owner (QA)" },
    });
    expect(accepted.status).toBe("ACCEPTED");
    expect((await api.ok("GET", `/properties/${id}`, { token: a.negotiator.token })).status).toBe("RESERVED");
  });

  test("no mandate in force, no offers", async ({ api, agency: a }) => {
    const draft = await api.draftListing(a.slug, a.negotiator);
    const buyer = await api.contact(a.slug, a.negotiator, "QA Buyer 2");
    const res = await api.call("POST", `/orgs/${a.slug}/listings/${draft}/offers`, { token: a.negotiator.token, data: { buyerContactId: buyer, amountCents: 1_000_000 } });
    expect(res.status()).toBe(409);
  });
});

test.describe("Pipeline", () => {
  test("stage moves need evidence; lost needs a reason", async ({ api, agency: a }) => {
    const owner = await api.contact(a.slug, a.negotiator, "QA Owner Prospect");
    const card = await api.ok("POST", `/orgs/${a.slug}/pipeline`, { token: a.negotiator.token, data: { title: `Deal ${uniqueId()}`, ownerContactId: owner } });
    const move = (stage: string, extra: Record<string, unknown> = {}) =>
      api.call("POST", `/orgs/${a.slug}/pipeline/${card.id}/move`, { token: a.negotiator.token, data: { stage, ...extra } });
    expect((await move("MEETING")).status()).toBe(200);
    expect((await move("MANDATE_SIGNED")).status()).toBe(409);
    expect((await move("COMPLETED")).status()).toBe(409);
    expect((await move("LOST")).status()).toBe(400);
    expect((await move("LOST", { lostReason: "Sold privately" })).status()).toBe(200);
  });
});
