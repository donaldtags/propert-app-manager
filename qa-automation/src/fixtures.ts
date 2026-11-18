import { test as base, expect, type Page } from "@playwright/test";
import { Api, type Account, type Agency } from "./api";
import { env } from "./env";

type TestFixtures = {
  api: Api;
  /** Opens the app already signed in as `account` (token injected, no login screen). */
  signIn: (page: Page, account: Account, opts?: { mode?: "light" | "dark" }) => Promise<void>;
};

type WorkerFixtures = {
  /**
   * A private agency per worker: its own workspace, ORG_ADMIN, PREA and negotiator, plus an
   * outsider with no membership. Tests in different workers never share data, so the suite
   * runs fully parallel with no cleanup.
   */
  agency: Agency;
};

export const test = base.extend<TestFixtures, WorkerFixtures>({
  api: async ({ request }, use) => {
    await use(new Api(request));
  },

  agency: [
    async ({ playwright }, use, workerInfo) => {
      const request = await playwright.request.newContext();
      const api = new Api(request);
      const platformAdmin = await api.platformAdmin();
      const slug = `qa-${env.runId}-w${workerInfo.parallelIndex}-${process.pid % 1000}`.toLowerCase().slice(0, 58);
      await api.createAgency(platformAdmin, slug, `QA Agency ${workerInfo.parallelIndex}`);
      const orgAdmin = await api.register("QA Org Admin");
      const prea = await api.register("QA Principal");
      const negotiator = await api.register("QA Negotiator");
      const outsider = await api.register("QA Outsider", "LANDLORD");
      await api.addMember(slug, platformAdmin, orgAdmin, ["ORG_ADMIN"]);
      await api.addMember(slug, orgAdmin, prea, ["PREA"]);
      await api.addMember(slug, orgAdmin, negotiator, ["NEGOTIATOR"]);
      await use({ slug, platformAdmin, orgAdmin, prea, negotiator, outsider });
      await request.dispose();
    },
    { scope: "worker" },
  ],

  signIn: async ({}, use) => {
    await use(async (page, account, opts = {}) => {
      await page.addInitScript(
        ([token, mode]) => {
          window.localStorage.setItem("estatex_token", token);
          window.localStorage.setItem("ex_color_mode", mode);
        },
        [account.token, opts.mode ?? "light"] as const
      );
    });
  },
});

export { expect };

/** Fails if the page is wider than the viewport (sideways scrolling on a phone). */
export async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow, `page scrolls sideways by ${overflow}px`).toBeLessThanOrEqual(0);
}
