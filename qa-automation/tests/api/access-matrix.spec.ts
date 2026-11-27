import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { test, expect } from "../../src/fixtures";
import { env } from "../../src/env";

/**
 * Sweeps EVERY endpoint found in the Spring controllers (tools/inventory.mjs), so a new endpoint
 * is covered the day it's added:
 *  - signed-out callers get 401 from everything that isn't public,
 *  - ordinary users get 403 from everything the URL rules reserve for a staff permission,
 *  - nothing answers 5xx to a signed-out caller, or to a signed-in user sending an empty body
 *    or ids that don't exist.
 *
 * The last check sends junk writes as an ordinary user, so it only runs against a local or
 * dedicated test backend, never a shared environment (set QA_ALLOW_SWEEP=1 to override).
 */
type Endpoint = { method: string; path: string; controller: string; handler: string; file: string; urlAccess: string };

const inventoryFile = path.join(__dirname, "../../reports/inventory.json");
execFileSync(process.execPath, [path.join(__dirname, "../../tools/inventory.mjs"), "--out", inventoryFile], { stdio: "ignore" });
const endpoints: Endpoint[] = JSON.parse(fs.readFileSync(inventoryFile, "utf8")).endpoints;

const local = /^https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?\//.test(env.apiUrl + "/") || process.env.QA_ALLOW_SWEEP === "1";

/** Fill path variables with values that can't match a real record. */
function concrete(p: string): string {
  return p
    .replace(/^\/api\/v1/, "")
    .replace(/\{([^}:]+)(:[^}]+)?\}/g, (_, name: string, rx?: string) =>
      rx?.includes("\\d") || /id$|Id$|^id/i.test(name) ? "987654321" : name === "type" ? "WEEKLY_RENTAL" : `qa-missing-${name.toLowerCase()}`);
}
// Calls that would end the test user's own session or account part-way through the sweep.
const SESSION_ENDING = /\/auth\/(logout|sessions)|\/users\/me$|delete-account/;
// 503 is the app's deliberate "this integration isn't switched on" answer (Didit, SMS), not a crash.
const crashed = (s: number) => s >= 500 && s !== 503;
const describe = (e: Endpoint) => `${e.method} ${e.path}  (${e.controller}.${e.handler}, ${e.file})`;

test.describe("Access matrix: every endpoint", () => {
  test.setTimeout(300_000);

  test(`inventory found the controllers (${endpoints.length} endpoints)`, () => {
    expect(endpoints.length).toBeGreaterThan(300);
  });

  test("signed-out callers get 401 from every non-public endpoint, and nothing crashes", async ({ api }) => {
    const wrong: string[] = [];
    for (const e of endpoints) {
      const res = await api.call(e.method, concrete(e.path), { data: e.method === "GET" || e.method === "DELETE" ? undefined : {} });
      const s = res.status();
      if (crashed(s)) wrong.push(`${s} (crash)  ${describe(e)}`);
      else if (e.urlAccess !== "public" && s !== 401) wrong.push(`${s} (expected 401)  ${describe(e)}`);
    }
    expect(wrong, `\n${wrong.join("\n")}\n`).toEqual([]);
  });

  test("ordinary users get 403 from every staff-permission endpoint", async ({ api }) => {
    const user = await api.register("QA Sweep Tenant", "TENANT");
    const wrong: string[] = [];
    for (const e of endpoints.filter((x) => x.urlAccess.startsWith("PERM_") || x.urlAccess.startsWith("ROLE_"))) {
      const res = await api.call(e.method, concrete(e.path), { token: user.token, data: e.method === "GET" ? undefined : {} });
      if (res.status() !== 403) wrong.push(`${res.status()} (expected 403, needs ${e.urlAccess})  ${describe(e)}`);
    }
    expect(wrong, `\n${wrong.join("\n")}\n`).toEqual([]);
  });

  test("no endpoint crashes (5xx) for a signed-in user sending an empty body or unknown ids", async ({ api }) => {
    test.skip(!local, "Sends junk writes as an ordinary user: local/test backends only (QA_ALLOW_SWEEP=1 to force).");
    const user = await api.register("QA Sweep Robustness", "LANDLORD");
    const crashes: string[] = [];
    for (const e of endpoints.filter((x) => !SESSION_ENDING.test(x.path))) {
      const res = await api.call(e.method, concrete(e.path), { token: user.token, data: e.method === "GET" ? undefined : {} });
      if (crashed(res.status())) crashes.push(`${res.status()}  ${describe(e)}  → ${(await res.text()).slice(0, 160)}`);
    }
    expect(crashes, `\n${crashes.join("\n")}\n`).toEqual([]);
  });
});
