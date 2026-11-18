import { test, expect } from "../../src/fixtures";

const day = (offset: number) => {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  return d.toISOString().slice(0, 10);
};

test.describe("Short stays (BnB)", () => {
  test("guest books, host accepts and records payment; the nights leave search", async ({ api }) => {
    const stay = await api.shortStay({ nightly: 70, cleaningFee: 15 });
    const guest = await api.register("QA Guest", "TENANT");
    const [inD, outD] = [day(30), day(33)];

    const quote = await api.ok("GET", `/stays/${stay.id}/quote?checkIn=${inD}&checkOut=${outD}&guests=2`);
    expect(quote).toMatchObject({ available: true, nights: 3, total: 225 });

    const booking = await api.ok("POST", `/stays/${stay.id}/bookings`, {
      token: guest.token, data: { checkIn: inD, checkOut: outD, guests: 2, message: "Arriving late" },
    });
    expect(booking.status).toBe("PENDING");

    const accepted = await api.ok("POST", `/stays/bookings/${booking.id}/accept`, { token: stay.host.token, data: {} });
    expect(accepted.status).toBe("AWAITING_PAYMENT");
    const paid = await api.ok("POST", `/stays/bookings/${booking.id}/mark-paid`, { token: stay.host.token, data: { method: "EcoCash" } });
    expect(paid).toMatchObject({ status: "CONFIRMED", paidVia: "EcoCash" });
    expect(paid.host.phone).toBeTruthy();

    const busySearch = await api.ok<{ id: number }[]>("GET", `/stays/search?checkIn=${day(31)}&checkOut=${day(32)}&size=50`);
    expect(busySearch.map((p) => p.id)).not.toContain(stay.id);
    const freeSearch = await api.ok<{ id: number }[]>("GET", `/stays/search?checkIn=${day(33)}&checkOut=${day(35)}&size=50`);
    expect(freeSearch.map((p) => p.id)).toContain(stay.id);
  });

  test("the same nights can't be sold twice, even under load", async ({ api }) => {
    const stay = await api.shortStay({ instantBook: true });
    const guests = await Promise.all(Array.from({ length: 5 }, (_, i) => api.register(`QA Racer ${i}`, "TENANT")));
    const results = await Promise.all(
      guests.map((g) => api.call("POST", `/stays/${stay.id}/bookings`, { token: g.token, data: { checkIn: day(50), checkOut: day(52), guests: 1 } }))
    );
    expect(results.filter((r) => r.status() === 200)).toHaveLength(1);
    expect(results.filter((r) => r.status() === 409)).toHaveLength(4);
  });

  test("strangers can't see or act on someone else's booking", async ({ api }) => {
    const stay = await api.shortStay();
    const guest = await api.register("QA Guest", "TENANT");
    const stranger = await api.register("QA Stranger", "TENANT");
    const b = await api.ok("POST", `/stays/${stay.id}/bookings`, { token: guest.token, data: { checkIn: day(60), checkOut: day(62), guests: 1 } });
    expect((await api.call("GET", `/stays/bookings/${b.id}`, { token: stranger.token })).status()).toBe(404);
    expect((await api.call("POST", `/stays/bookings/${b.id}/accept`, { token: guest.token, data: {} })).status()).toBe(404);
    expect((await api.call("POST", `/stays/${stay.id}/blocks`, { token: stranger.token, data: { start: day(70), end: day(72) } })).status()).toBe(403);
    expect((await api.call("GET", "/stays/hosting")).status()).toBe(401);
  });
});
