/**
 * Where the suite points. Defaults are a local dev stack; CI and post-deploy runs override them.
 *
 *  BASE_URL           frontend          (default http://127.0.0.1:3000)
 *  API_URL            backend /api/v1   (default http://127.0.0.1:8081/api/v1)
 *  QA_ADMIN_EMAIL     an email listed in the backend's APP_BOOTSTRAP_ADMIN_EMAILS
 *  QA_ADMIN_PASSWORD  its password (the suite registers it on first run)
 *  QA_ORG_SLUG        optional: a workspace to smoke-test read-only (default "estatex")
 */
export const env = {
  baseUrl: process.env.BASE_URL ?? "http://127.0.0.1:3000",
  apiUrl: (process.env.API_URL ?? "http://127.0.0.1:8081/api/v1").replace(/\/+$/, ""),
  adminEmail: process.env.QA_ADMIN_EMAIL ?? "qa-admin@estatex.test",
  adminPassword: process.env.QA_ADMIN_PASSWORD ?? "QaAdmin#2026!",
  orgSlug: process.env.QA_ORG_SLUG ?? "estatex",
  /** Unique per run so parallel CI jobs and reruns never collide. */
  runId: process.env.QA_RUN_ID ?? Date.now().toString(36),
};

export const PASSWORD = "QaUser#2026!";
