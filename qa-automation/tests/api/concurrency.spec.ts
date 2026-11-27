import { test, expect } from "../../src/fixtures";
import { PASSWORD } from "../../src/env";

/**
 * QA finding F-2: the same account used from several places at once (two phones, a tab refresh
 * during login) must never get a server error. On MariaDB with snapshot isolation, concurrent
 * updates of the same user/device rows used to fail with 500 "Record has changed since last read".
 * Needs a real database (H2 in the backend tests can't reproduce it).
 */
test("the same account signing in and reading its profile from many places at once never gets a 5xx", async ({ api }) => {
  const user = await api.register("QA Concurrency", "LANDLORD");
  for (let round = 0; round < 3; round++) {
    const calls = [
      ...Array.from({ length: 8 }, () => api.call("POST", "/auth/login", { data: { identifier: user.email, password: PASSWORD } })),
      ...Array.from({ length: 8 }, () => api.call("GET", `/users/${user.id}/verification-level`, { token: user.token })),
      ...Array.from({ length: 4 }, () => api.call("GET", "/auth/me", { token: user.token })),
    ];
    const statuses = (await Promise.all(calls)).map((r) => r.status());
    expect(statuses.filter((s) => s >= 500), `round ${round + 1}: ${statuses.join(",")}`).toEqual([]);
  }
});
