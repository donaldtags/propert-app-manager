import { test, expect } from "../../src/fixtures";
import { uniqueId, uniquePhone } from "../../src/api";

/** Offline devices replay queued writes, sometimes twice. Each create must land exactly once. */
test.describe("Offline replay is idempotent", () => {
  test("enquiry, contact, listing, deal and viewing replayed twice create one record each", async ({ api, agency: a }) => {
    const t = a.negotiator.token;
    const enquiry = { clientId: uniqueId(), channel: "WHATSAPP", intent: "BUY", name: "Replay Buyer", phone: uniquePhone() };
    const e1 = await api.ok("POST", `/orgs/${a.slug}/enquiries`, { token: t, data: enquiry });
    const e2 = await api.ok("POST", `/orgs/${a.slug}/enquiries`, { token: t, data: enquiry });
    expect(e2.id).toBe(e1.id);

    const contact = { clientId: uniqueId(), fullName: "Replay Contact", phone: uniquePhone() };
    expect((await api.ok("POST", `/orgs/${a.slug}/contacts`, { token: t, data: contact })).id)
      .toBe((await api.ok("POST", `/orgs/${a.slug}/contacts`, { token: t, data: contact })).id);

    const listing = { clientId: uniqueId(), title: `Replay ${uniqueId()}`, listingType: "RENT", city: "Harare", suburb: "Hatfield", bedrooms: 2, bathrooms: 1, priceCents: 60_000 };
    expect((await api.ok("POST", `/orgs/${a.slug}/listings`, { token: t, data: listing })).property.id)
      .toBe((await api.ok("POST", `/orgs/${a.slug}/listings`, { token: t, data: listing })).property.id);

    const live = await api.publishedListing(a.slug, a);
    const viewing = { clientId: uniqueId(), contactId: e1.contactId, scheduledAt: new Date(Date.now() + 3_600_000).toISOString() };
    expect((await api.ok("POST", `/orgs/${a.slug}/listings/${live}/viewings`, { token: t, data: viewing })).id)
      .toBe((await api.ok("POST", `/orgs/${a.slug}/listings/${live}/viewings`, { token: t, data: viewing })).id);
  });

  test("an enquiry from a known number lands on the existing contact", async ({ api, agency: a }) => {
    const phone = uniquePhone();
    const contactId = await api.contact(a.slug, a.negotiator, "Known Person", phone);
    const local = "0" + phone.slice(4); // same number typed the Zimbabwean way
    const e = await api.ok("POST", `/orgs/${a.slug}/enquiries`, { token: a.negotiator.token, data: { channel: "PHONE", name: "Known", phone: local } });
    expect(e.contactId).toBe(contactId);
  });
});
