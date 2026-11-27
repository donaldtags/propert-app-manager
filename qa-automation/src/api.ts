import { expect, type APIRequestContext, type APIResponse } from "@playwright/test";
import { env, PASSWORD } from "./env";

/** A signed-in user the suite created. */
export interface Account {
  id: number;
  email: string;
  token: string;
  name: string;
}

let counter = 0;
/** Unique, valid Zimbabwean mobile number per call (phones are unique per platform). */
export function uniquePhone(): string {
  counter += 1;
  const seed = (Date.now() % 1_000_000) * 7 + counter + (process.pid % 997) * 1_000_000;
  return "+26377" + String(seed % 10_000_000).padStart(7, "0");
}

export function uniqueEmail(prefix: string): string {
  counter += 1;
  return `${prefix}-${env.runId}-${process.pid}-${counter}@qa.estatex.test`;
}

export function uniqueId(): string {
  return `${env.runId}-${process.pid}-${++counter}-${Math.random().toString(36).slice(2, 8)}`;
}

/**
 * Thin typed client over the REST API, used to set up state quickly (the UI is only exercised
 * where the UI is what's being tested). Every helper asserts the status it expects so a broken
 * precondition fails loudly at the line that caused it.
 */
export class Api {
  constructor(private readonly request: APIRequestContext) {}

  async call(method: string, path: string, opts: { token?: string; data?: unknown; multipart?: Record<string, unknown> } = {}): Promise<APIResponse> {
    return this.request.fetch(`${env.apiUrl}${path}`, {
      method,
      headers: opts.token ? { Authorization: `Bearer ${opts.token}` } : {},
      data: opts.data,
      multipart: opts.multipart as never,
      failOnStatusCode: false,
    });
  }

  async ok<T = any>(method: string, path: string, opts: { token?: string; data?: unknown; multipart?: Record<string, unknown> } = {}, expected = 200): Promise<T> {
    const res = await this.call(method, path, opts);
    const text = await res.text();
    expect(res.status(), `${method} ${path} → ${res.status()} ${text.slice(0, 300)}`).toBe(expected);
    return (text ? JSON.parse(text) : undefined) as T;
  }

  // -- accounts ---------------------------------------------------------------------------

  async register(name: string, role = "AGENT", email = uniqueEmail(role.toLowerCase()), password = PASSWORD): Promise<Account> {
    const r = await this.ok("POST", "/auth/register", {
      data: { fullName: name, email, phone: uniquePhone(), password, country: "Zimbabwe", roles: [role] },
    });
    return { id: r.user.id, email, token: r.token, name };
  }

  async login(email: string, password: string): Promise<{ status: number; account?: Account; roles?: string[] }> {
    const res = await this.call("POST", "/auth/login", { data: { identifier: email, password } });
    if (res.status() !== 200) return { status: res.status() };
    const r = await res.json();
    return { status: 200, account: { id: r.user.id, email, token: r.token, name: r.user.fullName }, roles: r.user.roles };
  }

  /**
   * The platform admin the suite works as. Logs in, or registers it on first run; the backend
   * must list this email in APP_BOOTSTRAP_ADMIN_EMAILS so it's granted ADMIN.
   */
  async platformAdmin(): Promise<Account> {
    let r = await this.login(env.adminEmail, env.adminPassword);
    if (r.status !== 200) {
      await this.call("POST", "/auth/register", {
        data: { fullName: "QA Platform Admin", email: env.adminEmail, phone: uniquePhone(), password: env.adminPassword, country: "Zimbabwe", roles: ["AGENT"] },
      });
      r = await this.login(env.adminEmail, env.adminPassword);
    }
    expect(r.status, `Can't sign in as ${env.adminEmail}. Set QA_ADMIN_EMAIL/QA_ADMIN_PASSWORD.`).toBe(200);
    expect(r.roles, `${env.adminEmail} isn't a platform admin. Add it to the backend's APP_BOOTSTRAP_ADMIN_EMAILS and restart it.`).toContain("ADMIN");
    return r.account!;
  }

  // -- organisation -----------------------------------------------------------------------

  async createAgency(admin: Account, slug: string, name: string) {
    return this.ok("POST", "/orgs", { token: admin.token, data: { slug, name, divisions: ["PROPERTIES", "MANAGEMENT"] } });
  }

  async addMember(slug: string, actor: Account, member: Account, roles: string[]) {
    return this.ok("POST", `/orgs/${slug}/members`, { token: actor.token, data: { email: member.email, roles } });
  }

  // -- listings ---------------------------------------------------------------------------

  async draftListing(slug: string, as: Account, over: Partial<{ title: string; suburb: string; titleType: string; priceCents: number; listingType: string }> = {}): Promise<number> {
    const r = await this.ok("POST", `/orgs/${slug}/listings`, {
      token: as.token,
      data: {
        title: over.title ?? `QA home ${uniqueId()}`,
        listingType: over.listingType ?? "SALE",
        city: "Harare",
        suburb: over.suburb ?? "Greendale",
        address: "1 QA Road",
        bedrooms: 3,
        bathrooms: 2,
        priceCents: over.priceCents ?? 25_000_000,
        currency: "USD",
        titleType: over.titleType ?? "TITLE_DEED",
      },
    });
    return r.property.id;
  }

  /** Everything except PREA approval + publish: the state a PREA sees "ready for approval". */
  async readyForApproval(slug: string, a: Agency, opts: { minimumCents?: number; titleType?: string } = {}): Promise<{ listingId: number; mandateId: number }> {
    const titleType = opts.titleType ?? "TITLE_DEED";
    const listingId = await this.draftListing(slug, a.negotiator, { titleType });
    await this.ok("PUT", `/orgs/${slug}/listings/${listingId}/title`, { token: a.negotiator.token, data: { titleType, titleReference: "DT 42/2001" } });
    const checklist = titleType === "CESSION"
      ? ["CESSION_AGREEMENT_SIGHTED", "DEVELOPER_CONFIRMATION", "CESSION_CHAIN", "OWNER_ID_MATCHES", "RATES_CLEARANCE", "NO_ENCUMBRANCES"]
      : ["TITLE_DEED_SIGHTED", "DEEDS_SEARCH", "OWNER_ID_MATCHES", "RATES_CLEARANCE", "NO_ENCUMBRANCES"];
    await this.ok("PUT", `/orgs/${slug}/listings/${listingId}/verification-checklist`, { token: a.negotiator.token, data: { checked: checklist } });
    await this.ok("POST", `/orgs/${slug}/listings/${listingId}/title-verification`, { token: a.prea.token, data: { status: "VERIFIED" } });
    const today = new Date();
    const iso = (d: Date) => d.toISOString().slice(0, 10);
    const mandate = await this.ok("POST", `/orgs/${slug}/listings/${listingId}/mandates`, {
      token: a.negotiator.token,
      data: {
        type: "SOLE",
        startDate: iso(new Date(today.getTime() - 86_400_000)),
        expiryDate: iso(new Date(today.getTime() + 60 * 86_400_000)),
        ownerName: "QA Owner",
        ownerPhone: "0772 000 111",
        ...(opts.minimumCents ? { minimumPriceCents: opts.minimumCents } : {}),
      },
    });
    await this.ok("POST", `/orgs/${slug}/mandates/${mandate.id}/signed-document`, {
      token: a.negotiator.token,
      multipart: { file: { name: "mandate.pdf", mimeType: "application/pdf", buffer: Buffer.from("%PDF-1.4 qa") } },
    });
    await this.ok("POST", `/orgs/${slug}/mandates/${mandate.id}/sign-off`, { token: a.prea.token });
    await this.ok("PUT", `/orgs/${slug}/listings/${listingId}/consent`, { token: a.negotiator.token, data: { photos: true, address: true } });
    return { listingId, mandateId: mandate.id };
  }

  async publishedListing(slug: string, a: Agency, opts: { minimumCents?: number; titleType?: string } = {}): Promise<number> {
    const { listingId } = await this.readyForApproval(slug, a, opts);
    await this.ok("POST", `/orgs/${slug}/listings/${listingId}/approve`, { token: a.prea.token });
    await this.ok("POST", `/orgs/${slug}/listings/${listingId}/publish`, { token: a.negotiator.token });
    return listingId;
  }

  /**
   * A private listing stays unpublished until its first month's fee is paid (since 2026-11-27).
   * Records the open bill as paid the way an admin does for money received offline; in production
   * Paynow/Stripe or the wallet completes it through the same success path.
   */
  async payListingFee(propertyId: number, owner: Account): Promise<void> {
    const billing = await this.ok<{ pendingPaymentId: number | null }>("GET", `/properties/${propertyId}/billing`, { token: owner.token });
    if (billing.pendingPaymentId == null) return;
    const admin = await this.platformAdmin();
    await this.ok("PATCH", `/payments/${billing.pendingPaymentId}/success`, { token: admin.token });
  }

  /** A published short-stay (BnB) listing owned by a fresh host, with its stay rules set. */
  async shortStay(opts: { nightly?: number; instantBook?: boolean; maxGuests?: number; minNights?: number; cleaningFee?: number } = {}): Promise<{ host: Account; id: number; title: string }> {
    const host = await this.register("QA Host " + uniqueId(), "LANDLORD");
    const title = `QA Stay ${uniqueId()}`;
    const created = await this.ok<{ id: number }>("POST", "/properties", {
      token: host.token,
      data: {
        title, description: "Garden cottage for QA", listingType: "SHORT_STAY", city: "Harare", suburb: "Avondale",
        bedrooms: 1, bathrooms: 1, price: opts.nightly ?? 60, currency: "USD", escrowRequired: false, landlordId: host.id,
        photoUrls: ["https://images.unsplash.com/photo-1600596542815-ffad4c1539a9?w=1200"],
      },
    });
    await this.payListingFee(created.id, host);
    await this.ok("PUT", `/stays/${created.id}/settings`, {
      token: host.token,
      data: {
        maxGuests: opts.maxGuests ?? 3, minNights: opts.minNights ?? 1, maxNights: 30, cleaningFee: opts.cleaningFee ?? 10,
        checkInTime: "14:00", checkOutTime: "10:00", instantBook: opts.instantBook ?? false, houseRules: "No smoking",
      },
    });
    return { host, id: created.id, title };
  }

  async contact(slug: string, as: Account, name: string, phone = uniquePhone()): Promise<number> {
    return (await this.ok("POST", `/orgs/${slug}/contacts`, { token: as.token, data: { fullName: name, phone, source: "NETWORK" } })).id;
  }
}

export interface Agency {
  slug: string;
  platformAdmin: Account;
  orgAdmin: Account;
  prea: Account;
  negotiator: Account;
  outsider: Account;
}
