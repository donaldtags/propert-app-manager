import { defineConfig, devices } from "@playwright/test";
import { env } from "./src/env";

/**
 * Projects:
 *  - api      business rules straight against the REST API (no browser, seconds)
 *  - desktop  critical user journeys in desktop Chrome
 *  - mobile   phone-sized checks: layout, offline field work, smoke
 *
 * Tags: put @smoke in a test title to include it in `npm run test:smoke` (safe against any
 * environment, read-only, no admin needed).
 *
 * PW_CHANNEL=chrome uses the installed Google Chrome instead of downloading Playwright's
 * Chromium (handy locally).
 */
const channel = process.env.PW_CHANNEL || undefined;

export default defineConfig({
  testDir: "./tests",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 4 : undefined,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: process.env.CI
    ? [["github"], ["list"], ["html", { outputFolder: "reports/html", open: "never" }], ["junit", { outputFile: "reports/junit.xml" }]]
    : [["list"], ["html", { outputFolder: "reports/html", open: "never" }]],
  use: {
    baseURL: env.baseUrl,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
    actionTimeout: 15_000,
    // Deployed free-plan services can be slow to answer after waking; CI raises this.
    navigationTimeout: Number(process.env.PW_NAV_TIMEOUT_MS ?? 30_000),
  },
  projects: [
    { name: "api", testMatch: /api\/.*\.spec\.ts/ },
    {
      name: "desktop",
      testMatch: /e2e\/.*\.spec\.ts/,
      testIgnore: /\.mobile\.spec\.ts/,
      use: { ...devices["Desktop Chrome"], channel },
    },
    {
      name: "mobile",
      testMatch: /e2e\/.*\.(mobile|smoke)\.spec\.ts/,
      use: { ...devices["Pixel 7"], channel },
    },
  ],
});
