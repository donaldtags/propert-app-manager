// Builds the endpoint inventory straight from the Spring controllers and SecurityConfig, so the
// coverage matrix lists only routes that exist. Optionally reads backend logs (api_request lines)
// to mark which endpoints a test run actually called.
//
//   node tools/inventory.mjs [--backend-log mvn-test.log] [--qa-log backend.log] [--out reports/inventory.json] [--md matrix.md]
import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve(import.meta.dirname, "../..");
const SRC = path.join(ROOT, "src/main/java");
const args = Object.fromEntries(process.argv.slice(2).reduce((acc, a, i, all) => (a.startsWith("--") ? [...acc, [a.slice(2), all[i + 1]]] : acc), []));

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => (e.isDirectory() ? walk(path.join(dir, e.name)) : e.name.endsWith(".java") ? [path.join(dir, e.name)] : []));
}

/** Paths inside an annotation's argument list: "x", value = "x", path = {"a","b"}. */
function pathsOf(argText) {
  if (!argText) return [""];
  const named = argText.match(/(?:value|path)\s*=\s*(\{[^}]*\}|"[^"]*")/);
  const body = named ? named[1] : /^\s*(\{[^}]*\}|"[^"]*")/.test(argText) ? argText : null;
  if (!body) return [""];
  const out = [...body.matchAll(/"([^"]*)"/g)].map((m) => m[1]);
  return out.length ? out : [""];
}

const VERB = { Get: "GET", Post: "POST", Put: "PUT", Patch: "PATCH", Delete: "DELETE" };
const endpoints = [];
for (const file of walk(SRC)) {
  const src = fs.readFileSync(file, "utf8");
  if (!/@RestController/.test(src)) continue;
  const classIdx = src.search(/\b(class|record)\s+\w+/);
  const header = src.slice(0, classIdx);
  const classMap = header.match(/@RequestMapping\s*\(([^)]*)\)/);
  const bases = classMap ? pathsOf(classMap[1]) : [""];
  const controller = path.basename(file, ".java");
  const module = path.relative(SRC, path.dirname(file)).split(path.sep).slice(3).join("/") || "root";
  const body = src.slice(classIdx);
  const re = /@(Get|Post|Put|Patch|Delete)Mapping(?:\s*\(((?:[^()]|\([^)]*\))*)\))?[\s\S]*?\s(\w+)\s*\(/g;
  let m;
  while ((m = re.exec(body))) {
    for (const base of bases) for (const p of pathsOf(m[2])) {
      const full = (base + (p && !p.startsWith("/") ? "/" : "") + p).replace(/\/+/g, "/").replace(/(.)\/$/, "$1");
      const line = src.slice(0, classIdx + m.index).split("\n").length;
      endpoints.push({ method: VERB[m[1]], path: full, controller, handler: m[3], module, file: path.relative(ROOT, file) + ":" + line });
    }
  }
}

// ---- URL-level security rules (first match wins, as in Spring) ----
const sec = fs.readFileSync(path.join(SRC, "com/example/estatex/security/SecurityConfig.java"), "utf8");
const rules = [];
for (const m of sec.matchAll(/\.requestMatchers\(([\s\S]*?)\)\s*\.(permitAll|authenticated|hasAuthority|hasRole|hasAnyAuthority)\(([^)]*)\)/g)) {
  const verb = (m[1].match(/HttpMethod\.(\w+)/) || [])[1] || "*";
  const patterns = [...m[1].matchAll(/"([^"]*)"/g)].map((x) => x[1]);
  const access = m[2] === "permitAll" ? "public" : m[2] === "authenticated" ? "signed-in" : m[3].replace(/"/g, "");
  rules.push({ verb, patterns, access });
}
const toRegex = (p) => new RegExp("^" + p.replace(/\{[^}:]+:[^}]+\}/g, "[^/]+").replace(/\*\*/g, "§").replace(/\*/g, "[^/]+").replace(/\{[^}]+\}/g, "[^/]+").replace(/§/g, ".*") + "$");
const sample = (p) => p.replace(/\{[^}]+\}/g, "1");
for (const e of endpoints) {
  const rule = rules.find((r) => (r.verb === "*" || r.verb === e.method) && r.patterns.some((p) => toRegex(p).test(sample(e.path))));
  e.urlAccess = rule ? rule.access : "signed-in";
  const ctrl = fs.readFileSync(path.join(ROOT, e.file.split(":")[0]), "utf8");
  // In-code checks (permission/role/ownership) refine the URL rule; flag that they exist.
  e.inCodeChecks = /requirePermission|hasPermission|requireRole|assertCan|requireAdmin|forbidden|AccessDenied|ensure\w*Access|requireMember|requireStaff/.test(ctrl);
}

// ---- runtime coverage from backend logs ----
// --backend-log: output of `mvn test` (MockMvc requests are logged too); --qa-log: the log of the
// backend the Playwright suite ran against. Each endpoint records which source(s) called it.
function readHits(file) {
  const hits = new Map();
  if (!file || !fs.existsSync(file)) return hits;
  for (const m of fs.readFileSync(file, "utf8").matchAll(/api_request method=(\w+) path=(\S+) status=(\d+)/g)) {
    const [, verb, p, status] = m;
    const e = endpoints.find((x) => x.method === verb && toRegex(x.path.replace(/\{[^}]+\}/g, "*")).test(p));
    if (!e) continue;
    const k = e.method + " " + e.path;
    const h = hits.get(k) || { calls: 0, statuses: {} };
    h.calls++; h.statuses[status] = (h.statuses[status] || 0) + 1;
    hits.set(k, h);
  }
  return hits;
}
const sources = { backendTests: readHits(args["backend-log"]), qaSuite: readHits(args["qa-log"] || args.logs) };
for (const e of endpoints) {
  e.calledBy = {};
  for (const [name, hits] of Object.entries(sources)) if (hits.get(e.method + " " + e.path)) e.calledBy[name] = hits.get(e.method + " " + e.path);
  e.exercised = Object.keys(e.calledBy).length > 0;
}

const out = args.out || path.join(ROOT, "qa-automation/reports/inventory.json");
fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, JSON.stringify({ generatedAt: new Date().toISOString(), endpoints }, null, 2));
const byModule = {};
for (const e of endpoints) {
  const b = (byModule[e.module] ||= { total: 0, public: 0, backendTests: 0, qaSuite: 0, either: 0 });
  b.total++; if (e.succeeded) b.succeeded = (b.succeeded || 0) + 1; if (e.urlAccess === "public") b.public++; if (e.calledBy.backendTests) b.backendTests++; if (e.calledBy.qaSuite) b.qaSuite++; if (e.exercised) b.either++;
}
console.log(`${endpoints.length} endpoints in ${new Set(endpoints.map((e) => e.controller)).size} controllers → ${path.relative(process.cwd(), out)}`);
// "Called" includes the access sweep's rejected requests; "succeeded" (a 2xx answer) means the
// feature itself actually ran, which is the number that matters for functional coverage.
const ok = (h) => h && Object.keys(h.statuses).some((s) => s.startsWith("2"));
for (const e of endpoints) e.succeeded = Object.values(e.calledBy).some(ok);
for (const [name] of Object.entries(sources))
  console.log(`${name}: called ${endpoints.filter((e) => e.calledBy[name]).length}/${endpoints.length}, succeeded (2xx) ${endpoints.filter((e) => ok(e.calledBy[name])).length}/${endpoints.length}`);
console.log(`either: called ${endpoints.filter((e) => e.exercised).length}/${endpoints.length}, succeeded (2xx) ${endpoints.filter((e) => e.succeeded).length}/${endpoints.length}`);

// Markdown matrix, one row per endpoint, grouped by module.
if (args.md) {
  const rows = ["| Module | Method | Path | Handler | Access (URL rule) | Backend tests | QA suite |", "|---|---|---|---|---|---|---|"];
  for (const e of [...endpoints].sort((a, b) => (a.module + a.path).localeCompare(b.module + b.path))) {
    const mark = (h) => (!h ? "—" : ok(h) ? `✅ ${h.calls}` : `⛔ ${h.calls} (${Object.keys(h.statuses).join("/")})`);
    rows.push(`| ${e.module} | ${e.method} | \`${e.path}\` | ${e.controller}.${e.handler} | ${e.urlAccess} | ${mark(e.calledBy.backendTests)} | ${mark(e.calledBy.qaSuite)} |`);
  }
  fs.mkdirSync(path.dirname(args.md), { recursive: true });
  fs.writeFileSync(args.md, `# Endpoint coverage matrix\n\nGenerated ${new Date().toISOString()} by \`qa-automation/tools/inventory.mjs\` from the controllers and SecurityConfig. ✅ = at least one successful (2xx) call, ⛔ = only rejected/error answers (status codes shown), — = never called. Counts are the number of requests the last measured run actually sent to that endpoint (from the backend's api_request log).\n\n${rows.join("\n")}\n`);
  console.log("matrix → " + args.md);
}
console.table(byModule);

// Saved last so it includes the success flags computed above.
fs.writeFileSync(out, JSON.stringify({ generatedAt: new Date().toISOString(), endpoints }, null, 2));
