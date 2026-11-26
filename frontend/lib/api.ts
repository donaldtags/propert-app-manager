import type {
  StaySettings,
  BusyRange,
  StayQuote,
  StayBooking,
  StayBlock,
  HostingOverview,
  Property,
  PropertySearchParams,
  User,
  AuthResponse,
  LoginResponse,
  TwoFactorMethod,
  TwoFactorStatus,
  TotpSetup,
  Lease,
  Escrow,
  Payment,
  MaintenanceRequest,
  Rating,
  Conversation,
  ChatMessage,
  Reit,
  Investment,
  MarketSnapshot,
  TenantDashboard,
  LandlordDashboard,
  DiasporaDashboard,
  ConstructionProject,
  ConstructionMilestone,
  ConstructionUpdate,
  LeaseDocument,
  LeaseExtraction,
  PropertyInquiry,
  AdminRequest,
  KycSubmission,
  VerificationLevel,
  AdminDashboardOverview,
  AdminConversation,
  AdminMessage,
  MaintenancePhoto,
  LandlordProfile,
  PropertyPassport,
  TenantPassport,
  LandlordPassport,
  WaterSource,
  FraudSignal,
  NeighbourhoodProfile,
  Viewing,
  Vendor,
  VendorCategory,
  ServiceBooking,
  RentalApplication,
  ApplicationStatus,
  TimelineEvent,
  AppNotification,
  LeaseActionRequest,
  LeaseActionType,
  LeaseActionStatus,
  FeaturedListingSettings,
  CheckoutResponse,
  PlanSettings,
  SubscriptionInfo,
  SubscriptionPlan,
  PropertyBilling,
  GeocodeResult,
  Organisation,
  OrgContext,
  OrgMember,
  OrgAuditEntry,
  OrgRole,
  Division,
  UpdateOrganisationRequest,
  LocationNode,
  DensityTier,
  OrgListingSummary,
  OrgListingDetail,
  CreateOrgListingRequest,
  CreateMandateRequest,
  Mandate,
  TitleType,
  TitleVerificationStatus,
  Enquiry,
  EnquiryStatus,
  CaptureEnquiryRequest,
  Contact,
  ContactDetail,
  ContactType,
  CreateContactRequest,
  PipelineCard,
  PipelineStage,
  CreatePipelineCardRequest,
  Showing,
  ShowingStatus,
  BuyerInterest,
  PriceOpinion,
  Offer,
  Financing,
  Campaign,
  Inspection,
  InspectionItem,
  PricingSuggestion,
  SellerReportSummary,
  BuyingTimeline,
  CheckoutProvider,
  WalletSummary,
  WalletWithdrawal,
  WithdrawalRequest,
  WithdrawalStatus,
  PropertyCategory,
} from "./types";

export const BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081/api/v1";

/** An HTTP error response from the API. Network failures stay plain TypeErrors from fetch. */
export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = "ApiError";
  }
}

/** True when the request never reached the server (offline, DNS, server down, CORS preflight failed). */
export function isNetworkError(e: unknown): boolean {
  return e instanceof TypeError || (e instanceof Error && /Failed to fetch|NetworkError|Load failed|fetch failed/i.test(e.message));
}

export async function request<T>(
  path: string,
  options: RequestInit = {},
  token?: string
): Promise<T> {
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    ...(options.headers as Record<string, string>),
  };
  if (token) headers["Authorization"] = `Bearer ${token}`;

  const res = await fetch(`${BASE}${path}`, { ...options, headers });
  if (!res.ok) {
    let message = res.statusText;
    try {
      const body = await res.json();
      message = body.message ?? body.error ?? message;
    } catch {}
    throw new ApiError(res.status, message);
  }
  if (res.status === 204) return undefined as T;
  const contentLength = res.headers.get("content-length");
  if (contentLength === "0") return undefined as T;
  const text = await res.text();
  if (!text) return undefined as T;
  return JSON.parse(text) as T;
}

// Auth
export const auth = {
  register: (data: {
    fullName: string;
    /** Optional when a phone number is given. */
    email?: string;
    phone?: string;
    password: string;
    country?: string;
    roles?: string[];
    deviceId?: string;
    /** A friend's referral code: a discount on the first subscription payment. */
    referralCode?: string;
  }) => request<AuthResponse>("/auth/register", { method: "POST", body: JSON.stringify(data) }),

  /** A session, or a two-step challenge to answer with verifyTwoFactor. */
  login: (data: { identifier: string; password: string; deviceId?: string }) =>
    request<LoginResponse>("/auth/login", { method: "POST", body: JSON.stringify(data) }),

  verifyTwoFactor: (data: { challengeToken: string; code: string; method: TwoFactorMethod; deviceId?: string }) =>
    request<AuthResponse>("/auth/2fa/verify", { method: "POST", body: JSON.stringify(data) }),

  /** "Text me a code instead" while signing in. */
  sendLoginCode: (challengeToken: string) =>
    request<{ message: string }>("/auth/2fa/sms/send", { method: "POST", body: JSON.stringify({ challengeToken }) }),

  logout: (token: string) =>
    request<void>("/auth/logout", { method: "POST" }, token),

  /** Signs out every device, this one included. */
  logoutAll: (token: string) =>
    request<{ message: string }>("/auth/logout-all", { method: "POST" }, token),

  me: (token: string) => request<User>("/auth/me", {}, token),

  /** Email -> a reset link; phone number -> a texted code. */
  forgotPassword: (identifier: string) =>
    request<{ message: string }>("/auth/forgot-password", {
      method: "POST",
      body: JSON.stringify({ identifier }),
    }),

  /** With the emailed link's token, or with the phone number and texted code. */
  resetPassword: (data: { token?: string; identifier?: string; code?: string; password: string }) =>
    request<{ message: string }>("/auth/reset-password", { method: "POST", body: JSON.stringify(data) }),

  changePassword: (data: { currentPassword: string; newPassword: string }, token: string) =>
    request<{ message: string }>("/auth/change-password", { method: "POST", body: JSON.stringify(data) }, token),

  twoFactor: {
    status: (token: string) => request<TwoFactorStatus>("/auth/2fa", {}, token),
    startApp: (token: string) => request<TotpSetup>("/auth/2fa/totp/start", { method: "POST" }, token),
    confirmApp: (code: string, token: string) =>
      request<{ backupCodes: string[] }>("/auth/2fa/totp/confirm", { method: "POST", body: JSON.stringify({ code }) }, token),
    startSms: (token: string) => request<{ message: string }>("/auth/2fa/sms/start", { method: "POST" }, token),
    confirmSms: (code: string, token: string) =>
      request<{ backupCodes: string[] }>("/auth/2fa/sms/confirm", { method: "POST", body: JSON.stringify({ code }) }, token),
    sendDisableCode: (token: string) =>
      request<{ message: string }>("/auth/2fa/disable/code", { method: "POST" }, token),
    disable: (data: { password: string; code: string; method: TwoFactorMethod }, token: string) =>
      request<{ message: string }>("/auth/2fa/disable", { method: "POST", body: JSON.stringify(data) }, token),
  },
};

// Properties
export const properties = {
  list: (params: PropertySearchParams = {}) => {
    const q = new URLSearchParams();
    if (params.listingType) q.set("listingType", params.listingType);
    if (params.city) q.set("city", params.city);
    if (params.suburb) q.set("suburb", params.suburb);
    if (params.minPrice != null) q.set("minPrice", String(params.minPrice));
    if (params.maxPrice != null) q.set("maxPrice", String(params.maxPrice));
    if (params.bedrooms != null) q.set("bedrooms", String(params.bedrooms));
    if (params.bathrooms != null) q.set("bathrooms", String(params.bathrooms));
    if (params.diasporaFriendly) q.set("diasporaFriendly", "true");
    if (params.solarInstalled) q.set("solarInstalled", "true");
    if (params.backupPower) q.set("backupPower", "true");
    if (params.waterSource) q.set("waterSource", params.waterSource);
    if (params.furnished) q.set("furnished", "true");
    if (params.internetAvailable) q.set("internetAvailable", "true");
    if (params.securityFeatures) q.set("securityFeatures", "true");
    if (params.parkingAvailable) q.set("parkingAvailable", "true");
    if (params.petsAllowed) q.set("petsAllowed", "true");
    if (params.verifiedOnly) q.set("verifiedOnly", "true");
    if (params.escrowAvailable) q.set("escrowAvailable", "true");
    const qs = q.toString();
    return request<Property[]>(`/properties${qs ? `?${qs}` : ""}`);
  },

  // Paginated variant for the public browse/search page. Reads the total count from the
  // X-Total-Count response header so "Load more" knows when to stop, without changing the
  // response shape other callers of `list()` rely on.
  searchPaged: async (params: PropertySearchParams & { page: number; size: number }) => {
    const q = new URLSearchParams();
    if (params.listingType) q.set("listingType", params.listingType);
    if (params.city) q.set("city", params.city);
    if (params.suburb) q.set("suburb", params.suburb);
    if (params.minPrice != null) q.set("minPrice", String(params.minPrice));
    if (params.maxPrice != null) q.set("maxPrice", String(params.maxPrice));
    if (params.bedrooms != null) q.set("bedrooms", String(params.bedrooms));
    if (params.bathrooms != null) q.set("bathrooms", String(params.bathrooms));
    if (params.diasporaFriendly) q.set("diasporaFriendly", "true");
    if (params.solarInstalled) q.set("solarInstalled", "true");
    if (params.backupPower) q.set("backupPower", "true");
    if (params.waterSource) q.set("waterSource", params.waterSource);
    if (params.furnished) q.set("furnished", "true");
    if (params.internetAvailable) q.set("internetAvailable", "true");
    if (params.securityFeatures) q.set("securityFeatures", "true");
    if (params.parkingAvailable) q.set("parkingAvailable", "true");
    if (params.petsAllowed) q.set("petsAllowed", "true");
    if (params.verifiedOnly) q.set("verifiedOnly", "true");
    if (params.escrowAvailable) q.set("escrowAvailable", "true");
    q.set("page", String(params.page));
    q.set("size", String(params.size));
    const res = await fetch(`${BASE}/properties?${q.toString()}`);
    if (!res.ok) throw new Error("Failed to load properties");
    const items = (await res.json()) as Property[];
    const totalCount = Number(res.headers.get("X-Total-Count") ?? items.length);
    return { items, totalCount };
  },

  /** Pass the token when signed in: owners, agents and booked guests get details others don't (a short stay's address). */
  get: (id: number, token?: string | null) => request<Property>(`/properties/${id}`, {}, token ?? undefined),

  geocode: (params: { address?: string; suburb?: string; city?: string; country?: string }, token: string) => {
    const q = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => v && q.set(k, v));
    return request<GeocodeResult[]>(`/properties/geocode?${q}`, {}, token);
  },

  getPassport: (id: number) => request<PropertyPassport>(`/properties/${id}/passport`),

  create: (
    data: {
      title: string;
      description?: string;
      listingType: string;
      city: string;
      suburb: string;
      address?: string;
      country?: string;
      bedrooms: number;
      bathrooms: number;
      price: number;
      currency?: string;
      latitude?: number;
      longitude?: number;
      diasporaFriendly?: boolean;
      escrowRequired?: boolean;
      solarInstalled?: boolean;
      backupPower?: boolean;
      waterSource?: WaterSource;
      furnished?: boolean;
      internetAvailable?: boolean;
      securityFeatures?: boolean;
      parkingAvailable?: boolean;
      petsAllowed?: boolean;
      virtualTourUrl?: string;
      category?: PropertyCategory;
      floorAreaM2?: number;
      landlordId: number;
      agentId?: number;
      photoUrls?: string[];
    },
    token: string
  ) => request<Property>("/properties", { method: "POST", body: JSON.stringify(data) }, token),

  verify: (id: number, data: { approved?: boolean; note?: string }, token: string) =>
    request<Property>(`/properties/${id}/verify`, { method: "PATCH", body: JSON.stringify(data) }, token),

  update: (
    id: number,
    data: Partial<{
      title: string;
      description: string;
      listingType: string;
      city: string;
      suburb: string;
      address: string;
      country: string;
      bedrooms: number;
      bathrooms: number;
      price: number;
      currency: string;
      latitude: number;
      longitude: number;
      diasporaFriendly: boolean;
      escrowRequired: boolean;
      solarInstalled: boolean;
      backupPower: boolean;
      waterSource: WaterSource;
      furnished: boolean;
      internetAvailable: boolean;
      securityFeatures: boolean;
      parkingAvailable: boolean;
      petsAllowed: boolean;
      virtualTourUrl: string;
      status: string;
      category: PropertyCategory;
      floorAreaM2: number;
    }>,
    token: string
  ) => request<Property>(`/properties/${id}`, { method: "PATCH", body: JSON.stringify(data) }, token),

  deletePhoto: (propertyId: number, photoId: number, token: string) =>
    request<Property>(`/properties/${propertyId}/photos/${photoId}`, { method: "DELETE" }, token),

  billing: (id: number, token: string) =>
    request<PropertyBilling>(`/properties/${id}/billing`, {}, token),

  featureCheckout: (id: number, provider: string, token: string) =>
    request<CheckoutResponse>(`/properties/${id}/feature/checkout`, { method: "POST", body: JSON.stringify({ provider }) }, token),

  submitInquiry: (id: number, data: { name: string; email: string; phone?: string; message: string }) =>
    request<void>(`/properties/${id}/inquiries`, { method: "POST", body: JSON.stringify(data) }),

  myInquiries: (token: string) =>
    request<PropertyInquiry[]>("/property-inquiries", {}, token),

  uploadPhotos: async (propertyId: number, formData: FormData, token: string) => {
    const res = await fetch(`${BASE}/properties/${propertyId}/photos`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      body: formData,
    });
    if (!res.ok) {
      let message = res.statusText;
      try {
        const body = await res.json();
        message = body.message ?? body.error ?? message;
      } catch {}
      throw new Error(message);
    }
    return res.json() as Promise<Property>;
  },
};

// Users
export const users = {
  list: (role?: string, token?: string) => {
    const q = role ? `?role=${role}` : "";
    return request<User[]>(`/users${q}`, {}, token);
  },

  search: (params: { role?: string; q?: string }, token: string) => {
    const query = new URLSearchParams();
    if (params.role) query.set("role", params.role);
    if (params.q) query.set("q", params.q);
    const qs = query.toString();
    return request<{ id: number; fullName: string; primaryProfile: string }[]>(
      `/users/search${qs ? `?${qs}` : ""}`,
      {},
      token
    );
  },

  get: (id: number, token?: string) => request<User>(`/users/${id}`, {}, token),

  create: (data: {
    fullName: string;
    email: string;
    phone?: string;
    password?: string;
    country?: string;
    roles?: string[];
  }) => request<User>("/users", { method: "POST", body: JSON.stringify(data) }),

  updateProfile: (
    id: number,
    data: Partial<User>,
    token: string
  ) =>
    request<User>(`/users/${id}/profile`, {
      method: "PATCH",
      body: JSON.stringify(data),
    }, token),

  verify: (id: number, token: string) =>
    request<User>(`/users/${id}/verify`, { method: "PATCH" }, token),

  verifyBusiness: (id: number, token: string) =>
    request<User>(`/users/${id}/verify-business`, { method: "PATCH" }, token),

  addRole: (id: number, data: { role: string; password: string }, token: string) =>
    request<User>(`/users/${id}/roles`, { method: "POST", body: JSON.stringify(data) }, token),

  adminRequest: (id: number, token: string) =>
    request<void>(`/users/${id}/admin-request`, { method: "POST" }, token),

  listAdminRequests: (token: string, status?: string) =>
    request<AdminRequest[]>(`/users/admin-requests${status ? `?status=${status}` : ""}`, {}, token),

  decideAdminRequest: (id: number, approve: boolean, token: string) =>
    request<AdminRequest>(`/users/admin-requests/${id}`, { method: "PATCH", body: JSON.stringify({ approve }) }, token),

  verificationLevel: (id: number, token: string) =>
    request<VerificationLevel>(`/users/${id}/verification-level`, {}, token),

  landlordProfile: (id: number, token: string) =>
    request<LandlordProfile>(`/users/${id}/landlord-profile`, {}, token),

  /** Public only for people with listings; send the token to see your own before your first listing. */
  landlordPassport: (id: number, token?: string) =>
    request<LandlordPassport>(`/users/${id}/landlord-passport`, {}, token),

  tenantPassport: (id: number, token: string) =>
    request<TenantPassport>(`/users/${id}/tenant-passport`, {}, token),
};

// Leases
export const leases = {
  create: (
    data: {
      propertyId: number;
      tenantId: number;
      startDate: string;
      endDate: string;
      monthlyRent: number;
      depositAmount: number;
      currency?: string;
      terms?: string;
    },
    token: string
  ) => request<Lease>("/leases", { method: "POST", body: JSON.stringify(data) }, token),

  listByTenant: (tenantId: number, token: string) =>
    request<Lease[]>(`/leases?tenantId=${tenantId}`, {}, token),

  listByLandlord: (landlordId: number, token: string) =>
    request<Lease[]>(`/leases?landlordId=${landlordId}`, {}, token),

  listByAgent: (agentId: number, token: string) =>
    request<Lease[]>(`/leases?agentId=${agentId}`, {}, token),

  sign: (id: number, token: string) =>
    request<Lease>(`/leases/${id}/sign`, { method: "PATCH" }, token),

  uploadDocuments: async (leaseId: number, formData: FormData, token: string) => {
    const res = await fetch(`${BASE}/leases/${leaseId}/documents`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      body: formData,
    });
    if (!res.ok) {
      let message = res.statusText;
      try {
        const body = await res.json();
        message = body.message ?? body.error ?? message;
      } catch {}
      throw new Error(message);
    }
    return res.json() as Promise<LeaseDocument[]>;
  },

  listDocuments: (leaseId: number, token: string) =>
    request<LeaseDocument[]>(`/leases/${leaseId}/documents`, {}, token),

  extract: async (formData: FormData, token: string) => {
    const res = await fetch(`${BASE}/leases/extract`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      body: formData,
    });
    if (!res.ok) {
      let message = res.statusText;
      try {
        const body = await res.json();
        message = body.message ?? body.error ?? message;
      } catch {}
      throw new Error(message);
    }
    return res.json() as Promise<LeaseExtraction>;
  },

  downloadDocument: async (leaseId: number, documentId: number, token: string) => {
    const res = await fetch(`${BASE}/leases/${leaseId}/documents/${documentId}/download`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error("Failed to download document");
    return res.blob();
  },

  reviewDocument: (
    leaseId: number,
    documentId: number,
    data: { status: string; reviewNote?: string },
    token: string
  ) =>
    request<LeaseDocument>(
      `/leases/${leaseId}/documents/${documentId}/review`,
      { method: "PATCH", body: JSON.stringify(data) },
      token
    ),

  requestAction: (
    leaseId: number,
    data: { type: LeaseActionType; proposedEndDate?: string; note?: string },
    token: string
  ) => request<LeaseActionRequest>(`/leases/${leaseId}/actions`, { method: "POST", body: JSON.stringify(data) }, token),

  reviewAction: (id: number, data: { status: LeaseActionStatus; reviewNote?: string }, token: string) =>
    request<LeaseActionRequest>(`/leases/actions/${id}/review`, { method: "PATCH", body: JSON.stringify(data) }, token),

  myActions: (token: string) => request<LeaseActionRequest[]>("/leases/actions/mine", {}, token),

  receivedActions: (token: string) => request<LeaseActionRequest[]>("/leases/actions/received", {}, token),
};

// KYC / Identity Verification
export const kyc = {
  submit: async (formData: FormData, token: string) => {
    const res = await fetch(`${BASE}/kyc/submissions`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      body: formData,
    });
    if (!res.ok) {
      let message = res.statusText;
      try {
        const body = await res.json();
        message = body.message ?? body.error ?? message;
      } catch {}
      throw new Error(message);
    }
    return res.json() as Promise<KycSubmission>;
  },

  myList: (token: string) => request<KycSubmission[]>("/kyc/submissions/me", {}, token),

  get: (id: number, token: string) => request<KycSubmission>(`/kyc/submissions/${id}`, {}, token),

  listForAdmin: (token: string, status?: string) =>
    request<KycSubmission[]>(`/kyc/submissions${status ? `?status=${status}` : ""}`, {}, token),

  downloadDocument: async (submissionId: number, documentId: number, token: string) => {
    const res = await fetch(`${BASE}/kyc/submissions/${submissionId}/documents/${documentId}/download`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error("Failed to download document");
    return res.blob();
  },

  availableChecks: (token: string) =>
    request<{ zimbabweRegister: boolean; internationalPhotoId: boolean }>("/kyc/checks/available", {}, token),

  startDocumentCheck: (token: string) =>
    request<{ url: string; checkId: number }>("/kyc/document-check", { method: "POST" }, token),

  recheckRegistry: (id: number, token: string) =>
    request<KycSubmission>(`/kyc/submissions/${id}/registry-check`, { method: "POST" }, token),

  review: (id: number, data: { status: string; reviewNote?: string; confirmedChecks?: string[] }, token: string) =>
    request<KycSubmission>(`/kyc/submissions/${id}/review`, { method: "PATCH", body: JSON.stringify(data) }, token),
};

// Escrow
export const escrow = {
  create: (
    data: {
      propertyId: number;
      leaseId?: number;
      amount: number;
      currency?: string;
      purpose?: string;
    },
    token: string
  ) => request<Escrow>("/escrows", { method: "POST", body: JSON.stringify(data) }, token),

  list: (userId: number, token: string) =>
    request<Escrow[]>(`/escrows?userId=${userId}`, {}, token),

  listAllForAdmin: (token: string) =>
    request<Escrow[]>("/escrows/admin", {}, token),

  fund: (id: number, data: { method: string; provider?: string }, token: string) =>
    request<Escrow>(`/escrows/${id}/fund`, { method: "PATCH", body: JSON.stringify(data) }, token),

  checkout: (id: number, provider: string, token: string) =>
    request<CheckoutResponse>(`/escrows/${id}/checkout`, { method: "POST", body: JSON.stringify({ provider }) }, token),

  release: (id: number, token: string) =>
    request<Escrow>(`/escrows/${id}/release`, { method: "PATCH" }, token),

  dispute: (id: number, token: string) =>
    request<Escrow>(`/escrows/${id}/dispute`, { method: "PATCH" }, token),
};

// Payments
export const payments = {
  create: (
    data: {
      payeeId: number;
      propertyId: number;
      leaseId?: number;
      amount: number;
      currency?: string;
      provider: string;
      purpose?: string;
    },
    token: string
  ) => request<Payment>("/payments", { method: "POST", body: JSON.stringify(data) }, token),

  list: (userId: number, token: string) =>
    request<Payment[]>(`/payments?userId=${userId}`, {}, token),

  markSuccess: (id: number, token: string) =>
    request<Payment>(`/payments/${id}/success`, { method: "PATCH" }, token),

  checkout: (id: number, provider: string, token: string) =>
    request<CheckoutResponse>(`/payments/${id}/checkout`, { method: "POST", body: JSON.stringify({ provider }) }, token),

  confirmCheckout: (id: number, token: string) =>
    request<Payment>(`/payments/${id}/confirm-checkout`, {}, token),

  receipt: async (id: number, token: string) => {
    const res = await fetch(`${BASE}/payments/${id}/receipt`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error("Failed to load receipt");
    return res.blob();
  },

  statementCsv: async (userId: number, token: string) => {
    const res = await fetch(`${BASE}/payments/statement.csv?userId=${userId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error("Failed to load statement");
    return res.blob();
  },
};

// Maintenance
export const maintenance = {
  create: (
    data: {
      propertyId: number;
      category: string;
      priority?: string;
      description?: string;
    },
    token: string
  ) => request<MaintenanceRequest>("/maintenance", { method: "POST", body: JSON.stringify(data) }, token),

  list: (propertyId: number, token: string) =>
    request<MaintenanceRequest[]>(`/maintenance?propertyId=${propertyId}`, {}, token),

  updateStatus: (id: number, status: string, token: string) =>
    request<MaintenanceRequest>(`/maintenance/${id}/status?status=${status}`, { method: "PATCH" }, token),

  assignVendor: (id: number, vendorId: number, token: string) =>
    request<MaintenanceRequest>(`/maintenance/${id}/assign-vendor?vendorId=${vendorId}`, { method: "PATCH" }, token),

  uploadPhotos: async (requestId: number, formData: FormData, token: string) => {
    const res = await fetch(`${BASE}/maintenance/${requestId}/photos`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      body: formData,
    });
    if (!res.ok) {
      let message = res.statusText;
      try {
        const body = await res.json();
        message = body.message ?? body.error ?? message;
      } catch {}
      throw new Error(message);
    }
    return res.json() as Promise<MaintenancePhoto[]>;
  },

  listPhotos: (requestId: number, token: string) =>
    request<MaintenancePhoto[]>(`/maintenance/${requestId}/photos`, {}, token),
};

// Ratings
export const ratings = {
  create: (
    data: {
      landlordId: number;
      tenantId: number;
      propertyId: number;
      leaseId?: number;
      rating: number;
      comment?: string;
    },
    token: string
  ) => request<Rating>("/ratings", { method: "POST", body: JSON.stringify(data) }, token),

  listByLandlord: (landlordId: number) =>
    request<Rating[]>(`/ratings?landlordId=${landlordId}`),
};

// Messages
export const messages = {
  listConversations: (userId: number, token: string) =>
    request<Conversation[]>(`/messages/conversations?userId=${userId}`, {}, token),

  startConversation: (
    data: {
      recipientId: number;
      subject?: string;
      content: string;
      messageType: string;
      propertyId?: number;
    },
    token: string
  ) =>
    request<Conversation>("/messages/conversations", {
      method: "POST",
      body: JSON.stringify(data),
    }, token),

  getConversation: (conversationId: number, token: string) =>
    request<ChatMessage[]>(`/messages/conversations/${conversationId}`, {}, token),

  send: (
    data: { conversationId: number; content: string; messageType?: string },
    token: string
  ) => request<ChatMessage>("/messages", { method: "POST", body: JSON.stringify(data) }, token),

  markRead: (conversationId: number, token: string) =>
    request<void>(`/messages/conversations/${conversationId}/read`, { method: "PATCH" }, token),

  adminListConversations: (token: string) =>
    request<AdminConversation[]>("/messages/admin/conversations", {}, token),

  adminGetConversation: (conversationId: number, token: string) =>
    request<AdminMessage[]>(`/messages/admin/conversations/${conversationId}`, {}, token),
};

// Investments
export const investments = {
  listReits: () => request<Reit[]>("/investments/reits"),

  createReit: (
    data: {
      name: string;
      description?: string;
      market: string;
      unitPrice: number;
      projectedAnnualYield: number;
      riskLevel: string;
      vexEligible?: boolean;
      totalUnits?: number;
    },
    token: string
  ) => request<Reit>("/investments/reits", { method: "POST", body: JSON.stringify(data) }, token),

  checkout: (
    data: {
      reitId: number;
      units: number;
      currency?: string;
      provider: string;
    },
    token: string
  ) => request<CheckoutResponse>("/investments/checkout", { method: "POST", body: JSON.stringify(data) }, token),

  sell: (data: { reitId: number; units: number }, token: string) =>
    request<Investment[]>("/investments/sell", { method: "PATCH", body: JSON.stringify(data) }, token),

  listByInvestor: (investorId: number, token: string) =>
    request<Investment[]>(`/investments?investorId=${investorId}`, {}, token),
};

// Market data
export const market = {
  zwReits: () => request<MarketSnapshot>("/market/reits/zw"),
};

// Dashboards
export const dashboards = {
  tenant: (tenantId: number, token: string) =>
    request<TenantDashboard>(`/dashboards/tenants/${tenantId}`, {}, token),

  landlord: (landlordId: number, token: string) =>
    request<LandlordDashboard>(`/dashboards/landlords/${landlordId}`, {}, token),

  diaspora: (userId: number, token: string) =>
    request<DiasporaDashboard>(`/dashboards/diaspora/${userId}`, {}, token),

  adminOverview: (token: string) =>
    request<AdminDashboardOverview>("/dashboards/admin", {}, token),
};

// Admin
export const admin = {
  fraudSignals: (token: string) =>
    request<FraudSignal[]>("/admin/fraud-signals", {}, token),
};

// Landlord/agent subscription tiers: pricing and feature gates are admin-controlled
export const subscriptions = {
  plans: () => request<PlanSettings[]>("/subscriptions/plans"),

  updatePlan: (
    plan: SubscriptionPlan,
    data: {
      monthlyPrice: number;
      currency: string;
      maxProperties: number | null;
      escrowEnabled: boolean;
      digitalLeasesEnabled: boolean;
      maintenanceCoordinationEnabled: boolean;
      rentRemindersEnabled: boolean;
      aiPricingEnabled: boolean;
      tenantPassportEnabled: boolean;
      reportsEnabled: boolean;
    },
    token: string
  ) =>
    request<PlanSettings>(`/subscriptions/plans/${plan}`, { method: "PUT", body: JSON.stringify(data) }, token),

  mine: (token: string) => request<SubscriptionInfo>("/subscriptions/me", {}, token),

  subscribe: (plan: SubscriptionPlan, token: string) =>
    request<SubscriptionInfo>("/subscriptions/subscribe", { method: "POST", body: JSON.stringify({ plan }) }, token),

  checkout: (plan: SubscriptionPlan, provider: string, token: string) =>
    request<CheckoutResponse>("/subscriptions/checkout", { method: "POST", body: JSON.stringify({ plan, provider }) }, token),
};

// Featured listings: billing for landlords/agents to boost a listing, price set by admin
export const featuredListings = {
  settings: () => request<FeaturedListingSettings>("/featured-listings/settings"),

  updateSettings: (
    data: { price: number; currency: string; durationDays: number },
    token: string
  ) =>
    request<FeaturedListingSettings>(
      "/featured-listings/settings",
      { method: "PUT", body: JSON.stringify(data) },
      token
    ),
};

// Viewings
export const viewings = {
  create: (
    data: { propertyId: number; mode: string; preferredDate?: string; preferredTime?: string; notes?: string },
    token: string
  ) => request<Viewing>("/viewings", { method: "POST", body: JSON.stringify(data) }, token),

  listByRequester: (requesterId: number, token: string) =>
    request<Viewing[]>(`/viewings?requesterId=${requesterId}`, {}, token),

  listByLandlord: (landlordId: number, token: string) =>
    request<Viewing[]>(`/viewings?landlordId=${landlordId}`, {}, token),

  listByAgent: (agentId: number, token: string) =>
    request<Viewing[]>(`/viewings?agentId=${agentId}`, {}, token),

  confirm: (id: number, data: { videoCallLink?: string }, token: string) =>
    request<Viewing>(`/viewings/${id}/confirm`, { method: "PATCH", body: JSON.stringify(data) }, token),

  decline: (id: number, token: string) =>
    request<Viewing>(`/viewings/${id}/decline`, { method: "PATCH" }, token),

  cancel: (id: number, token: string) =>
    request<Viewing>(`/viewings/${id}/cancel`, { method: "PATCH" }, token),

  checkIn: (id: number, code: string, token: string) =>
    request<Viewing>(`/viewings/${id}/check-in`, { method: "PATCH", body: JSON.stringify({ code }) }, token),

  feedback: (id: number, data: { rating: number; comment?: string }, token: string) =>
    request<Viewing>(`/viewings/${id}/feedback`, { method: "POST", body: JSON.stringify(data) }, token),
};

// Construction monitoring
export const construction = {
  create: (
    data: {
      title: string;
      country?: string;
      city: string;
      address?: string;
      contractorName?: string;
      contractorPhone?: string;
      contractorCompany?: string;
      budgetTotal?: number;
      currency?: string;
      startDate?: string;
      expectedCompletionDate?: string;
      propertyId?: number;
    },
    token: string
  ) => request<ConstructionProject>("/construction-projects", { method: "POST", body: JSON.stringify(data) }, token),

  listByOwner: (ownerId: number, token: string) =>
    request<ConstructionProject[]>(`/construction-projects?ownerId=${ownerId}`, {}, token),

  get: (id: number, token: string) => request<ConstructionProject>(`/construction-projects/${id}`, {}, token),

  update: (
    id: number,
    data: Partial<{
      stage: string;
      progressPercent: number;
      status: string;
      budgetTotal: number;
      amountPaid: number;
      contractorName: string;
      contractorPhone: string;
      contractorCompany: string;
      startDate: string;
      expectedCompletionDate: string;
    }>,
    token: string
  ) => request<ConstructionProject>(`/construction-projects/${id}`, { method: "PATCH", body: JSON.stringify(data) }, token),

  addMilestone: (
    projectId: number,
    data: { title: string; description?: string; amountDue?: number; targetDate?: string },
    token: string
  ) =>
    request<ConstructionMilestone>(
      `/construction-projects/${projectId}/milestones`,
      { method: "POST", body: JSON.stringify(data) },
      token
    ),

  listMilestones: (projectId: number, token: string) =>
    request<ConstructionMilestone[]>(`/construction-projects/${projectId}/milestones`, {}, token),

  submitMilestone: (milestoneId: number, token: string) =>
    request<ConstructionMilestone>(`/construction-projects/milestones/${milestoneId}/submit`, { method: "PATCH" }, token),

  approveMilestone: (milestoneId: number, token: string) =>
    request<ConstructionMilestone>(`/construction-projects/milestones/${milestoneId}/approve`, { method: "PATCH" }, token),

  markMilestonePaid: (milestoneId: number, token: string) =>
    request<ConstructionMilestone>(`/construction-projects/milestones/${milestoneId}/mark-paid`, { method: "PATCH" }, token),

  postUpdate: (projectId: number, note: string, token: string) =>
    request<ConstructionUpdate>(
      `/construction-projects/${projectId}/updates`,
      { method: "POST", body: JSON.stringify({ note }) },
      token
    ),

  listUpdates: (projectId: number, token: string) =>
    request<ConstructionUpdate[]>(`/construction-projects/${projectId}/updates`, {}, token),
};

// Rental applications
export const applications = {
  create: (
    data: {
      propertyId: number;
      desiredMoveInDate?: string;
      monthlyIncome?: number;
      message?: string;
      saveAsDraft?: boolean;
    },
    token: string
  ) => request<RentalApplication>("/applications", { method: "POST", body: JSON.stringify(data) }, token),

  submit: (id: number, token: string) =>
    request<RentalApplication>(`/applications/${id}/submit`, { method: "PATCH" }, token),

  review: (id: number, data: { status: ApplicationStatus; reviewNote?: string }, token: string) =>
    request<RentalApplication>(`/applications/${id}/review`, { method: "PATCH", body: JSON.stringify(data) }, token),

  get: (id: number, token: string) => request<RentalApplication>(`/applications/${id}`, {}, token),

  mine: (token: string) => request<RentalApplication[]>("/applications/mine", {}, token),

  received: (token: string) => request<RentalApplication[]>("/applications/received", {}, token),
};

// Notifications
export const notifications = {
  mine: (token: string) => request<AppNotification[]>("/notifications/mine", {}, token),

  unreadCount: (token: string) =>
    request<{ unreadCount: number }>("/notifications/unread-count", {}, token),

  markRead: (id: number, token: string) =>
    request<AppNotification>(`/notifications/${id}/read`, { method: "PATCH" }, token),

  markAllRead: (token: string) =>
    request<void>("/notifications/read-all", { method: "PATCH" }, token),
};

// Unified home timeline
export const timeline = {
  mine: (token: string) => request<TimelineEvent[]>("/timeline/mine", {}, token),
};

// Vendors (services marketplace)
export const vendors = {
  list: (params: { category?: string; city?: string } = {}) => {
    const q = new URLSearchParams();
    if (params.category) q.set("category", params.category);
    if (params.city) q.set("city", params.city);
    const qs = q.toString();
    return request<Vendor[]>(`/vendors${qs ? `?${qs}` : ""}`);
  },

  create: (
    data: { businessName: string; category: string; description?: string; phone?: string; email?: string; city?: string },
    token: string
  ) => request<Vendor>("/vendors", { method: "POST", body: JSON.stringify(data) }, token),

  registerSelf: (
    data: { businessName: string; category: VendorCategory; description?: string; phone?: string; city?: string },
    token: string
  ) => request<Vendor>("/vendors/self", { method: "POST", body: JSON.stringify(data) }, token),

  mine: (token: string) => request<Vendor>("/vendors/mine", {}, token),

  updateMine: (
    data: Partial<{ businessName: string; category: VendorCategory; description: string; phone: string; city: string }>,
    token: string
  ) => request<Vendor>("/vendors/mine", { method: "PATCH", body: JSON.stringify(data) }, token),

  verify: (id: number, token: string) =>
    request<Vendor>(`/vendors/${id}/verify`, { method: "PATCH" }, token),

  deactivate: (id: number, token: string) =>
    request<Vendor>(`/vendors/${id}/deactivate`, { method: "PATCH" }, token),
};

// Service bookings
export const serviceBookings = {
  create: (
    data: { vendorId: number; propertyId?: number; preferredDate?: string; notes?: string },
    token: string
  ) => request<ServiceBooking>("/service-bookings", { method: "POST", body: JSON.stringify(data) }, token),

  listByRequester: (requesterId: number, token: string) =>
    request<ServiceBooking[]>(`/service-bookings?requesterId=${requesterId}`, {}, token),

  cancel: (id: number, token: string) =>
    request<ServiceBooking>(`/service-bookings/${id}/cancel`, { method: "PATCH" }, token),

  feedback: (id: number, data: { rating: number; comment?: string }, token: string) =>
    request<ServiceBooking>(`/service-bookings/${id}/feedback`, { method: "POST", body: JSON.stringify(data) }, token),
};

// Neighbourhood profiles
export const neighbourhoods = {
  get: async (city: string, suburb: string) => {
    const q = new URLSearchParams({ city, suburb });
    const res = await fetch(`${BASE}/neighbourhoods?${q.toString()}`);
    if (res.status === 404) return null;
    if (!res.ok) throw new Error("Failed to load neighbourhood profile");
    return res.json() as Promise<NeighbourhoodProfile>;
  },

  upsert: (
    data: {
      city: string;
      suburb: string;
      schoolsNote?: string;
      hospitalsNote?: string;
      transportNote?: string;
      shoppingNote?: string;
      generalNote?: string;
    },
    token: string
  ) => request<NeighbourhoodProfile>("/neighbourhoods", { method: "PUT", body: JSON.stringify(data) }, token),
};

// AI
export interface ConversationTurn {
  role: "user" | "assistant";
  content: string;
}

export const ai = {
  search: (query: string, history?: ConversationTurn[]) =>
    request<{ answer: string; matches: Property[]; aiPowered: boolean }>("/ai/property-search", {
      method: "POST",
      body: JSON.stringify({ query, history }),
    }),

  affordability: (data: { grossMonthlyIncome: number; existingMonthlyDebt?: number; propertyId?: number }) =>
    request<{
      maxByRentToIncomeRule: number;
      maxByDebtToIncomeRule: number;
      recommendedMaxRent: number;
      propertyRent?: number;
      fitsRecommendedBudget?: boolean;
      note: string;
    }>("/ai/affordability", { method: "POST", body: JSON.stringify(data) }),

  rentSuggestion: (params: { listingType: string; city: string; suburb?: string; bedrooms: number }, token: string) => {
    const q = new URLSearchParams();
    q.set("listingType", params.listingType);
    q.set("city", params.city);
    if (params.suburb) q.set("suburb", params.suburb);
    q.set("bedrooms", String(params.bedrooms));
    return request<{
      suggestedPrice: number | null;
      priceRangeLow: number | null;
      priceRangeHigh: number | null;
      comparableCount: number;
      basis: string;
    }>(`/ai/rent-suggestion?${q.toString()}`, {}, token);
  },

  homeAssistant: (message: string, token: string, history?: ConversationTurn[]) =>
    request<{ answer: string; aiPowered: boolean }>(
      "/ai/home-assistant",
      { method: "POST", body: JSON.stringify({ message, history }) },
      token
    ),

  leaseExplanation: (leaseId: number, token: string) =>
    request<{ answer: string; aiPowered: boolean }>(`/ai/lease-explanation/${leaseId}`, {}, token),
};

// Organisation workspaces
export const organisations = {
  mine: (token: string) => request<OrgContext[]>("/orgs/mine", {}, token),

  /** Public profile; includes internal fields when the caller is staff of the org. */
  get: (slug: string, token?: string | null) =>
    request<Organisation>(`/orgs/${encodeURIComponent(slug)}`, {}, token ?? undefined),

  me: (slug: string, token: string) => request<OrgContext>(`/orgs/${encodeURIComponent(slug)}/me`, {}, token),

  update: (slug: string, data: UpdateOrganisationRequest, token: string) =>
    request<Organisation>(`/orgs/${encodeURIComponent(slug)}`, { method: "PATCH", body: JSON.stringify(data) }, token),

  members: (slug: string, token: string) =>
    request<OrgMember[]>(`/orgs/${encodeURIComponent(slug)}/members`, {}, token),

  addMember: (slug: string, data: { email: string; roles: OrgRole[]; divisions?: Division[] }, token: string) =>
    request<OrgMember>(`/orgs/${encodeURIComponent(slug)}/members`, { method: "POST", body: JSON.stringify(data) }, token),

  updateMember: (
    slug: string,
    membershipId: number,
    data: { roles?: OrgRole[]; divisions?: Division[]; active?: boolean },
    token: string
  ) =>
    request<OrgMember>(
      `/orgs/${encodeURIComponent(slug)}/members/${membershipId}`,
      { method: "PATCH", body: JSON.stringify(data) },
      token
    ),

  audit: (slug: string, token: string, limit = 100) =>
    request<OrgAuditEntry[]>(`/orgs/${encodeURIComponent(slug)}/audit?limit=${limit}`, {}, token),
};

// Organisation listings, compliance gates and mandates
const orgPath = (slug: string) => `/orgs/${encodeURIComponent(slug)}`;

export const orgListings = {
  list: (slug: string, token: string) => request<OrgListingSummary[]>(`${orgPath(slug)}/listings`, {}, token),

  get: (slug: string, id: number, token: string) =>
    request<OrgListingDetail>(`${orgPath(slug)}/listings/${id}`, {}, token),

  create: (slug: string, data: CreateOrgListingRequest, token: string) =>
    request<OrgListingDetail>(`${orgPath(slug)}/listings`, { method: "POST", body: JSON.stringify(data) }, token),

  setTitle: (slug: string, id: number, data: { titleType: TitleType; titleReference?: string }, token: string) =>
    request<OrgListingDetail>(`${orgPath(slug)}/listings/${id}/title`, { method: "PUT", body: JSON.stringify(data) }, token),

  verifyTitle: (slug: string, id: number, data: { status: TitleVerificationStatus; notes?: string }, token: string) =>
    request<OrgListingDetail>(
      `${orgPath(slug)}/listings/${id}/title-verification`,
      { method: "POST", body: JSON.stringify(data) },
      token
    ),

  setConsent: (slug: string, id: number, data: { photos: boolean; address: boolean }, token: string) =>
    request<OrgListingDetail>(`${orgPath(slug)}/listings/${id}/consent`, { method: "PUT", body: JSON.stringify(data) }, token),

  approve: (slug: string, id: number, token: string) =>
    request<OrgListingDetail>(`${orgPath(slug)}/listings/${id}/approve`, { method: "POST" }, token),

  publish: (slug: string, id: number, token: string) =>
    request<OrgListingDetail>(`${orgPath(slug)}/listings/${id}/publish`, { method: "POST" }, token),

  unpublish: (slug: string, id: number, reason: string | undefined, token: string) =>
    request<OrgListingDetail>(
      `${orgPath(slug)}/listings/${id}/unpublish`,
      { method: "POST", body: JSON.stringify({ reason }) },
      token
    ),

  createMandate: (slug: string, id: number, data: CreateMandateRequest, token: string) =>
    request<Mandate>(`${orgPath(slug)}/listings/${id}/mandates`, { method: "POST", body: JSON.stringify(data) }, token),

  attachSignedMandate: async (slug: string, mandateId: number, file: File, ownerSignedOn: string, token: string) => {
    const form = new FormData();
    form.append("file", file);
    const q = ownerSignedOn ? `?ownerSignedOn=${encodeURIComponent(ownerSignedOn)}` : "";
    const res = await fetch(`${BASE}${orgPath(slug)}/mandates/${mandateId}/signed-document${q}`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      body: form,
    });
    if (!res.ok) {
      let message = res.statusText;
      try {
        message = (await res.json()).message ?? message;
      } catch {}
      throw new ApiError(res.status, message);
    }
    return (await res.json()) as Mandate;
  },

  signOffMandate: (slug: string, mandateId: number, token: string) =>
    request<Mandate>(`${orgPath(slug)}/mandates/${mandateId}/sign-off`, { method: "POST" }, token),

  cancelMandate: (slug: string, mandateId: number, reason: string | undefined, token: string) =>
    request<Mandate>(
      `${orgPath(slug)}/mandates/${mandateId}/cancel`,
      { method: "POST", body: JSON.stringify({ reason }) },
      token
    ),

  /** Opens the signed mandate in a new tab (fetched with auth, shown as a blob URL). */
  openSignedMandate: async (slug: string, mandateId: number, token: string) => {
    const res = await fetch(`${BASE}${orgPath(slug)}/mandates/${mandateId}/signed-document`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error("Could not open the signed mandate");
    const url = URL.createObjectURL(await res.blob());
    window.open(url, "_blank", "noopener");
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  },
};

// Enquiries captured from any channel
export const enquiries = {
  list: (slug: string, token: string) => request<Enquiry[]>(`${orgPath(slug)}/enquiries`, {}, token),

  capture: (slug: string, data: CaptureEnquiryRequest, token: string) =>
    request<Enquiry>(`${orgPath(slug)}/enquiries`, { method: "POST", body: JSON.stringify(data) }, token),

  setStatus: (slug: string, id: number, status: EnquiryStatus, token: string) =>
    request<Enquiry>(`${orgPath(slug)}/enquiries/${id}/status`, { method: "PATCH", body: JSON.stringify({ status }) }, token),
};

/** Fetches an authenticated binary (PDF/PNG/photo) and returns an object URL for it. */
async function authedBlobUrl(path: string, token: string): Promise<string> {
  const res = await fetch(`${BASE}${path}`, { headers: { Authorization: `Bearer ${token}` } });
  if (!res.ok) throw new ApiError(res.status, res.statusText);
  return URL.createObjectURL(await res.blob());
}

export const contacts = {
  search: (slug: string, token: string, q?: string, type?: ContactType) => {
    const p = new URLSearchParams();
    if (q) p.set("q", q);
    if (type) p.set("type", type);
    return request<Contact[]>(`${orgPath(slug)}/contacts?${p}`, {}, token);
  },
  get: (slug: string, id: number, token: string) => request<ContactDetail>(`${orgPath(slug)}/contacts/${id}`, {}, token),
  create: (slug: string, data: CreateContactRequest, token: string) =>
    request<Contact>(`${orgPath(slug)}/contacts`, { method: "POST", body: JSON.stringify(data) }, token),
  update: (slug: string, id: number, data: Partial<CreateContactRequest> & { version?: number }, token: string) =>
    request<Contact>(`${orgPath(slug)}/contacts/${id}`, { method: "PATCH", body: JSON.stringify(data) }, token),
  qualify: (
    slug: string,
    id: number,
    data: {
      budgetCents?: number;
      timeline?: BuyingTimeline;
      financing?: Financing;
      preferredAreas?: string;
      minBedrooms?: number;
      decisionMaker?: boolean;
      decisionMakerNote?: string;
    },
    token: string
  ) => request<Contact>(`${orgPath(slug)}/contacts/${id}/qualification`, { method: "PUT", body: JSON.stringify(data) }, token),
};

export const pipeline = {
  board: (slug: string, token: string) => request<PipelineCard[]>(`${orgPath(slug)}/pipeline`, {}, token),
  create: (slug: string, data: CreatePipelineCardRequest, token: string) =>
    request<PipelineCard>(`${orgPath(slug)}/pipeline`, { method: "POST", body: JSON.stringify(data) }, token),
  update: (slug: string, id: number, data: Partial<CreatePipelineCardRequest>, token: string) =>
    request<PipelineCard>(`${orgPath(slug)}/pipeline/${id}`, { method: "PATCH", body: JSON.stringify(data) }, token),
  move: (slug: string, id: number, stage: PipelineStage, lostReason: string | undefined, token: string) =>
    request<PipelineCard>(`${orgPath(slug)}/pipeline/${id}/move`, { method: "POST", body: JSON.stringify({ stage, lostReason }) }, token),
};

export const showings = {
  upcoming: (slug: string, token: string, days = 14) => request<Showing[]>(`${orgPath(slug)}/viewings?days=${days}`, {}, token),
  forListing: (slug: string, propertyId: number, token: string) =>
    request<Showing[]>(`${orgPath(slug)}/listings/${propertyId}/viewings`, {}, token),
  schedule: (
    slug: string,
    propertyId: number,
    data: { clientId?: string; contactId: number; scheduledAt: string; durationMinutes?: number },
    token: string
  ) => request<Showing>(`${orgPath(slug)}/listings/${propertyId}/viewings`, { method: "POST", body: JSON.stringify(data) }, token),
  outcome: (
    slug: string,
    id: number,
    data: { status: ShowingStatus; interest?: BuyerInterest; priceOpinion?: PriceOpinion; feedback?: string },
    token: string
  ) => request<Showing>(`${orgPath(slug)}/viewings/${id}/outcome`, { method: "PUT", body: JSON.stringify(data) }, token),
};

export const offers = {
  forListing: (slug: string, propertyId: number, token: string) =>
    request<Offer[]>(`${orgPath(slug)}/listings/${propertyId}/offers`, {}, token),
  record: (
    slug: string,
    propertyId: number,
    data: { clientId?: string; buyerContactId: number; amountCents: number; depositCents?: number; financing?: Financing; conditions?: string; expiresOn?: string },
    token: string
  ) => request<Offer>(`${orgPath(slug)}/listings/${propertyId}/offers`, { method: "POST", body: JSON.stringify(data) }, token),
  present: (slug: string, id: number, via: "WHATSAPP" | "EMAIL" | "IN_PERSON" | "PHONE", token: string) =>
    request<Offer>(`${orgPath(slug)}/offers/${id}/present`, { method: "POST", body: JSON.stringify({ via }) }, token),
  respond: (
    slug: string,
    id: number,
    data: { decision: "ACCEPT" | "REJECT" | "COUNTER"; counterAmountCents?: number; ownerInstructionReference?: string; note?: string },
    token: string
  ) => request<Offer>(`${orgPath(slug)}/offers/${id}/owner-response`, { method: "POST", body: JSON.stringify(data) }, token),
  withdraw: (slug: string, id: number, note: string | undefined, token: string) =>
    request<Offer>(`${orgPath(slug)}/offers/${id}/withdraw`, { method: "POST", body: JSON.stringify({ note }) }, token),
};

export const campaigns = {
  current: (slug: string, propertyId: number, token: string) =>
    request<Campaign | undefined>(`${orgPath(slug)}/listings/${propertyId}/campaign`, {}, token),
  start: (slug: string, propertyId: number, token: string) =>
    request<Campaign>(`${orgPath(slug)}/listings/${propertyId}/campaign`, { method: "POST" }, token),
  setTask: (slug: string, taskId: number, done: boolean, token: string) =>
    request<Campaign>(`${orgPath(slug)}/campaign-tasks/${taskId}`, { method: "PUT", body: JSON.stringify({ done }) }, token),
};

export const intake = {
  inspection: (slug: string, propertyId: number, token: string) =>
    request<Inspection>(`${orgPath(slug)}/listings/${propertyId}/inspection`, {}, token),
  saveInspection: (
    slug: string,
    propertyId: number,
    data: { items: InspectionItem[]; standSizeM2?: number; floorAreaM2?: number; notes?: string; inspectedOn?: string; baseVersion?: number },
    token: string
  ) => request<Inspection>(`${orgPath(slug)}/listings/${propertyId}/inspection`, { method: "PUT", body: JSON.stringify(data) }, token),
  uploadPhoto: async (slug: string, propertyId: number, file: Blob, fileName: string, meta: { clientId?: string; itemKey?: string; caption?: string }, token: string) => {
    const form = new FormData();
    form.append("file", new File([file], fileName, { type: file.type || "image/jpeg" }));
    const q = new URLSearchParams();
    if (meta.clientId) q.set("clientId", meta.clientId);
    if (meta.itemKey) q.set("itemKey", meta.itemKey);
    if (meta.caption) q.set("caption", meta.caption);
    const res = await fetch(`${BASE}${orgPath(slug)}/listings/${propertyId}/inspection/photos?${q}`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      body: form,
    });
    if (!res.ok) {
      let message = res.statusText;
      try {
        message = (await res.json()).message ?? message;
      } catch {}
      throw new ApiError(res.status, message);
    }
    return res.json();
  },
  photoUrl: (slug: string, photoId: number, token: string) => authedBlobUrl(`${orgPath(slug)}/inspection-photos/${photoId}`, token),
  pricing: (slug: string, propertyId: number, token: string) =>
    request<PricingSuggestion>(`${orgPath(slug)}/listings/${propertyId}/pricing`, {}, token),
  saveRecommendation: (slug: string, propertyId: number, recommendedCents: number, rationale: string | undefined, token: string) =>
    request<PricingSuggestion["latest"]>(`${orgPath(slug)}/listings/${propertyId}/pricing`, {
      method: "POST",
      body: JSON.stringify({ recommendedCents, rationale }),
    }, token),
  setChecklist: (slug: string, propertyId: number, checked: string[], token: string) =>
    request<OrgListingDetail>(`${orgPath(slug)}/listings/${propertyId}/verification-checklist`, {
      method: "PUT",
      body: JSON.stringify({ checked }),
    }, token),
};

export const sellerReports = {
  list: (slug: string, propertyId: number, token: string) =>
    request<SellerReportSummary[]>(`${orgPath(slug)}/listings/${propertyId}/seller-reports`, {}, token),
  generate: (slug: string, propertyId: number, token: string) =>
    request<SellerReportSummary>(`${orgPath(slug)}/listings/${propertyId}/seller-reports`, { method: "POST" }, token),
  /** Public link the owner opens (no login). */
  shareUrl: (shareToken: string) => `${BASE}/shared/seller-reports/${shareToken}`,
  shareImageUrl: (slug: string, propertyId: number, token: string) =>
    authedBlobUrl(`${orgPath(slug)}/listings/${propertyId}/share-image.png`, token),
};

// Location reference data (provinces, cities/towns, suburbs)
export const locations = {
  all: () => request<LocationNode[]>("/locations"),

  pending: (token: string) => request<LocationNode[]>("/locations/pending", {}, token),

  suggest: (cityId: number, name: string, token: string) =>
    request<LocationNode>("/locations/suggestions", { method: "POST", body: JSON.stringify({ cityId, name }) }, token),

  update: (
    id: number,
    data: { name?: string; densityTier?: DensityTier; aliases?: string[]; verified?: boolean },
    token: string
  ) => request<LocationNode>(`/locations/${id}`, { method: "PATCH", body: JSON.stringify(data) }, token),

  reject: (id: number, token: string) => request<void>(`/locations/${id}`, { method: "DELETE" }, token),
};

// Frontend logging
export async function logEvent(
  level: "info" | "warn" | "error",
  event: string,
  route: string,
  message: string,
  userId?: number,
  metadata?: Record<string, unknown>
) {
  try {
    await fetch(`${BASE}/logs/frontend`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ level, event, route, userId, message, metadata }),
    });
  } catch {}
}

// Short stays (BnB)
export interface StaySearchParams {
  city?: string;
  suburb?: string;
  checkIn?: string;
  checkOut?: string;
  guests?: number;
  minPrice?: number;
  maxPrice?: number;
  bedrooms?: number;
  page: number;
  size: number;
}

export const stays = {
  search: async (params: StaySearchParams) => {
    const q = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== "") q.set(k, String(v));
    });
    const res = await fetch(`${BASE}/stays/search?${q.toString()}`);
    if (!res.ok) throw new Error("Failed to load stays");
    const items = (await res.json()) as Property[];
    return { items, totalCount: Number(res.headers.get("X-Total-Count") ?? items.length) };
  },
  settings: (propertyId: number) => request<StaySettings>(`/stays/${propertyId}`),
  updateSettings: (propertyId: number, data: Omit<StaySettings, "propertyId" | "nightlyRate" | "currency">, token: string) =>
    request<StaySettings>(`/stays/${propertyId}/settings`, { method: "PUT", body: JSON.stringify(data) }, token),
  availability: (propertyId: number, from: string, to: string) =>
    request<BusyRange[]>(`/stays/${propertyId}/availability?from=${from}&to=${to}`),
  quote: (propertyId: number, checkIn: string, checkOut: string, guests: number) =>
    request<StayQuote>(`/stays/${propertyId}/quote?checkIn=${checkIn}&checkOut=${checkOut}&guests=${guests}`),
  book: (propertyId: number, data: { checkIn: string; checkOut: string; guests: number; message?: string }, token: string) =>
    request<StayBooking>(`/stays/${propertyId}/bookings`, { method: "POST", body: JSON.stringify(data) }, token),
  mine: (token: string) => request<StayBooking[]>("/stays/bookings/mine", {}, token),
  hosting: (token: string) => request<HostingOverview>("/stays/hosting", {}, token),
  get: (id: number, token: string) => request<StayBooking>(`/stays/bookings/${id}`, {}, token),
  accept: (id: number, note: string | undefined, token: string) =>
    request<StayBooking>(`/stays/bookings/${id}/accept`, { method: "POST", body: JSON.stringify({ note }) }, token),
  decline: (id: number, note: string | undefined, token: string) =>
    request<StayBooking>(`/stays/bookings/${id}/decline`, { method: "POST", body: JSON.stringify({ note }) }, token),
  cancel: (id: number, note: string | undefined, token: string) =>
    request<StayBooking>(`/stays/bookings/${id}/cancel`, { method: "POST", body: JSON.stringify({ note }) }, token),
  markPaid: (id: number, method: string, token: string) =>
    request<StayBooking>(`/stays/bookings/${id}/mark-paid`, { method: "POST", body: JSON.stringify({ method }) }, token),
  pay: (id: number, provider: CheckoutProvider, token: string) =>
    request<{ paymentId: number; redirectUrl: string }>(`/stays/bookings/${id}/pay`, { method: "POST", body: JSON.stringify({ provider }) }, token),
  block: (propertyId: number, data: { start: string; end: string; note?: string }, token: string) =>
    request<StayBlock>(`/stays/${propertyId}/blocks`, { method: "POST", body: JSON.stringify(data) }, token),
  unblock: (propertyId: number, blockId: number, token: string) =>
    request<void>(`/stays/${propertyId}/blocks/${blockId}`, { method: "DELETE" }, token),
};

export const wallet = {
  get: (token: string, page = 0, size = 20, currency = "USD") =>
    request<WalletSummary>(`/wallet?currency=${currency}&page=${page}&size=${size}`, {}, token),
  /** Starts a Paynow/card checkout; the balance goes up once the provider confirms it. */
  topUp: (amount: number, provider: "paynow" | "stripe", token: string, currency = "USD") =>
    request<CheckoutResponse>("/wallet/top-ups", { method: "POST", body: JSON.stringify({ amount, provider, currency }) }, token),
  withdraw: (data: WithdrawalRequest, token: string) =>
    request<WalletWithdrawal>("/wallet/withdrawals", { method: "POST", body: JSON.stringify(data) }, token),
  withdrawals: (token: string) => request<WalletWithdrawal[]>("/wallet/withdrawals", {}, token),
  admin: {
    withdrawals: (token: string, status?: WithdrawalStatus) =>
      request<WalletWithdrawal[]>(`/admin/wallet/withdrawals${status ? `?status=${status}` : ""}`, {}, token),
    decide: (id: number, data: { action: "PAID" | "REJECTED"; reference?: string; reason?: string }, token: string) =>
      request<WalletWithdrawal>(`/admin/wallet/withdrawals/${id}`, { method: "PATCH", body: JSON.stringify(data) }, token),
  },
};

// Analytics engine (admin). See docs/admin-analytics-plan.md §3.
export interface AnalyticsRunStep { name: string; status: "OK" | "FAILED" | "SKIPPED"; ms?: number; detail?: string }
export interface AnalyticsWarning { code: string; message: string; count?: number; sampleIds?: number[] }
export interface AnalyticsRun {
  id: number;
  job: string;
  status: "RUNNING" | "SUCCEEDED" | "FAILED" | "ABANDONED";
  triggeredBy?: string;
  windowStart: string;
  windowEnd: string;
  attempt: number;
  startedAt: string;
  finishedAt?: string;
  durationMs?: number;
  recordsRead: number;
  recordsRejected: number;
  metricsWritten: number;
  stepRetries: number;
  steps: AnalyticsRunStep[];
  warnings: AnalyticsWarning[];
  error?: string;
}
export interface AnalyticsEngineStatus {
  state: "UP_TO_DATE" | "RUNNING" | "STALE" | "FAILED" | "NEVER_RUN";
  stale: boolean;
  lastSuccessAt?: string;
  dataThrough?: string;
  nextScheduledAt?: string;
  historyStartsAt?: string;
  openDuplicatePairs: number;
  lastRun?: AnalyticsRun;
  lastSuccess?: AnalyticsRun;
}
export interface AnalyticsOutcome { status: "SUCCEEDED" | "FAILED" | "SKIPPED"; runId?: number; detail?: string }

export const analyticsEngine = {
  status: (token: string) => request<AnalyticsEngineStatus>("/admin/analytics/engine/status", {}, token),
  runs: (token: string, limit = 25) => request<AnalyticsRun[]>(`/admin/analytics/engine/runs?limit=${limit}`, {}, token),
  runNow: (token: string) => request<AnalyticsOutcome>("/admin/analytics/engine/run", { method: "POST" }, token),
};

// Executive overview, drill-down and staff access (admin). See docs/admin-analytics-plan.md Phase C.
export type OverviewGrain = "DAY" | "WEEK" | "MONTH" | "QUARTER" | "YEAR";
export interface OverviewPoint { start: string; value: number }
export interface OverviewKpi {
  key: string;
  label: string;
  help: string;
  metric?: string;
  listingType?: string;
  domain?: string;
  unit: "COUNT" | "USD" | "PERCENT";
  better: "UP" | "DOWN" | "NEUTRAL";
  value?: number | null;
  previous?: number | null;
  change?: number | null;
  changePercent?: number | null;
  direction: "UP" | "DOWN" | "FLAT" | "NEW" | "NONE";
  comparable: boolean;
  note?: string;
  sampleSize?: number;
  lowSample: boolean;
  live: boolean;
  available: boolean;
  locationApplies: boolean;
  drillable: boolean;
  trend: OverviewPoint[];
}
export interface OverviewSection { key: string; title: string; subtitle?: string; restricted: boolean; kpis: OverviewKpi[] }
export interface OverviewPeriod { grain: OverviewGrain; start: string; end: string; asOf?: string; complete: boolean; label: string }
export interface ExecutiveOverviewData {
  period: OverviewPeriod;
  comparison: OverviewPeriod;
  comparisonLabel: string;
  location: { city?: string; suburb?: string };
  freshness: AnalyticsEngineStatus;
  sections: OverviewSection[];
}
export interface DrillRecord {
  kind: string; id: number; title: string; detail?: string; amount?: number; currency?: string; at?: string; status?: string; href?: string;
}
export interface DrillRecords {
  metric: string; periodStart: string; periodEnd: string; asOf: string; total: number; truncated: boolean; note?: string; records: DrillRecord[];
}
export type StaffRole = "ADMIN" | "FINANCE_ADMIN" | "MARKET_ANALYST" | "OPERATIONS_ADMIN" | "VERIFICATION_OFFICER";
export interface StaffMember { id: number; fullName: string; email?: string; phone?: string; staffRoles: StaffRole[] }

function qs(params: Record<string, string | undefined>) {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => { if (v) q.set(k, v); });
  return q.toString();
}

export const analyticsOverview = {
  get: (token: string, p: { grain: OverviewGrain; compare: "TO_DATE" | "FULL"; city?: string; suburb?: string }) =>
    request<ExecutiveOverviewData>(`/admin/analytics/overview?${qs(p)}`, {}, token),
  records: (token: string, p: { metric: string; grain: OverviewGrain; periodStart: string; listingType?: string; city?: string; suburb?: string }) =>
    request<DrillRecords>(`/admin/analytics/records?${qs(p)}`, {}, token),
};

export const staffAccess = {
  list: (token: string) => request<StaffMember[]>("/admin/staff", {}, token),
  set: (token: string, userId: number, roles: StaffRole[]) =>
    request<StaffMember>(`/admin/staff/${userId}`, { method: "PUT", body: JSON.stringify({ roles }) }, token),
};

// Rental and sales market dashboards (admin). See docs/admin-analytics-plan.md Phase D.
export type MarketType = "rent" | "sale";
export type MarketGrain = "DAY" | "WEEK" | "MONTH" | "QUARTER" | "YEAR";
export interface MarketParams {
  from?: string; to?: string; grain?: MarketGrain; city?: string; suburb?: string; category?: string;
  bedrooms?: number; minPrice?: number; maxPrice?: number;
}
export interface MarketKpi {
  key: string; label: string; help: string; unit: "COUNT" | "USD" | "PERCENT" | "DAYS";
  value?: number | null; previous?: number | null; changePercent?: number | null; sampleSize?: number | null;
  lowSample: boolean; note?: string | null;
}
export interface MarketPoint { start: string; end: string; label: string; complete: boolean; snapshotDate?: string | null; values: Record<string, number> }
export interface MarketGroup {
  key: string; label: string; city?: string | null; active: number; askingMedian?: number | null; askingN: number; lowSample: boolean;
  newListings: number; demand?: number | null; demandPerListing?: number | null; transactions: number;
  transactionMedian?: number | null; transactionN: number;
}
export interface YieldRow {
  suburb?: string | null; city?: string | null; rentMedian?: number | null; rentN: number; saleMedian?: number | null; saleN: number;
  grossYield?: number | null; netYield?: number | null; note?: string | null;
}
export interface MarketDashboardData {
  type: "RENT" | "SALE"; from: string; to: string; grain: MarketGrain; historyStartsAt?: string; snapshotDate?: string | null;
  figuresCalculatedAt?: string; demandIncluded: boolean; kpis: MarketKpi[]; series: MarketPoint[];
  bySuburb: MarketGroup[]; byCategory: MarketGroup[]; byBedrooms: MarketGroup[]; byPriceBand: MarketGroup[];
  yields: { overall: YieldRow; bySuburb: YieldRow[]; costRatio: number; costRatioConfirmed: boolean; asOf?: string | null };
  notes: string[];
}
export interface MarketListing {
  id: number; title?: string; suburb?: string; city?: string; category?: string; bedrooms?: number; price?: number;
  currency?: string; status?: string; verification?: string;
}

function marketQs(p: MarketParams) {
  const q = new URLSearchParams();
  Object.entries(p).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== "") q.set(k, String(v)); });
  return q.toString();
}

export const marketDashboard = {
  get: (token: string, type: MarketType, p: MarketParams) =>
    request<MarketDashboardData>(`/admin/analytics/market/${type}?${marketQs(p)}`, {}, token),
  listings: (token: string, type: MarketType, p: MarketParams) =>
    request<MarketListing[]>(`/admin/analytics/market/${type}/listings?${marketQs(p)}`, {}, token),
  setCostRatio: (token: string, ratio: number) =>
    request<{ value: number; confirmed: boolean }>("/admin/analytics/market/settings/yield-cost-ratio",
      { method: "PUT", body: JSON.stringify({ ratio }) }, token),
};

// Market map and customer demand (admin). See docs/admin-analytics-plan.md Phase E.
export interface GeoSuburb {
  suburb: string; city: string; latitude?: number | null; longitude?: number | null; placedBy?: string | null;
  active: number; askingMedian?: number | null; askingMean?: number | null; askingN: number; confidence: "HIGH" | "MEDIUM" | "LOW";
  growthPercent?: number | null; newListings?: number | null; demand?: number | null; demandPerListing?: number | null;
  searches?: number | null; transactions: number; grossYield?: number | null;
}
export interface GeoData {
  type: "RENT" | "SALE"; snapshotDate?: string | null; comparedWith?: string | null; activityDays: number; demandIncluded: boolean;
  coverage: { liveListings: number; withSuburb: number; placedOnMap: number; withoutSuburb: number; inUnplacedSuburbs: number; withOwnPin: number };
  suburbs: GeoSuburb[]; points: { id: number; latitude: number; longitude: number; price?: number; suburb?: string }[]; notes: string[];
}
export interface DemandCount { key: string; label: string; city?: string | null; searches: number; noResults: number; liveListings?: number | null; searchesPerListing?: number | null }
export interface DemandStep { key: string; label: string; count: number; fromPrevious?: number | null }
export interface DemandListing { id: number; title: string; type?: string; suburb?: string; city?: string; price?: number; views: number; saves: number; enquiries: number }
export interface DemandGap {
  type: string; city?: string; category?: string; bedrooms?: number; searches: number; noResults: number; liveListings: number;
  searchesPerListing?: number | null; lift?: number | null; explanation: string;
}
export interface DemandData {
  from: string; to: string; figuresCalculatedAt?: string; searches: number; searchesAnyType: number; noResultsPercent?: number | null;
  ledToViewPercent?: number | null; views: number; saves: number; funnels: { type: "RENT" | "SALE"; steps: DemandStep[] }[];
  topSuburbs: DemandCount[]; topCategories: DemandCount[]; bedrooms: DemandCount[]; rentPriceBands: DemandCount[]; salePriceBands: DemandCount[];
  mostViewed: DemandListing[]; mostSaved: DemandListing[];
  responseTimes: { key: string; label: string; medianHours?: number | null; n: number; note?: string | null }[];
  gaps: DemandGap[]; rentSearchesPerListing?: number | null; saleSearchesPerListing?: number | null; notes: string[];
}

export const geoAnalytics = {
  get: (token: string, type: MarketType, p: MarketParams) => request<GeoData>(`/admin/analytics/geo/${type}?${marketQs(p)}`, {}, token),
};
export const demandAnalytics = {
  get: (token: string, p: { from?: string; to?: string; city?: string }) =>
    request<DemandData>(`/admin/analytics/demand?${marketQs(p)}`, {}, token),
};

// Referral programme. See docs/admin-analytics-plan.md Phase R.
export interface ReferralSettings { enabled: boolean; discountPercent: number; rewardCents: number; monthlyCap: number; currency: string }
export interface MyReferrals {
  code: string; programme: ReferralSettings; invited: number; subscribed: number; earnedCents: number; pending: number;
  referredBy?: { status: string; discountPercent?: number | null; discountAvailable: boolean } | null;
}
export interface ReferralAdminRow {
  id: number; code: string; referrerUserId: number; referrerName?: string; refereeUserId: number; refereeName?: string; status: string;
  blockReason?: string; discountCents?: number; rewardCents?: number; registeredAt: string; qualifiedAt?: string; rewardedAt?: string;
  reversalReason?: string;
}
export interface ReferralAdminView {
  settings: ReferralSettings; total: number; blocked: number; rewarded: number; rewardsPaidCents: number; discountsGivenCents: number;
  pendingReversals: number; recent: ReferralAdminRow[];
}
export const referralsApi = {
  code: (code: string) => request<{ valid: boolean; programmeActive: boolean; discountPercent?: number | null }>(
    `/referrals/code/${encodeURIComponent(code)}`),
  mine: (token: string) => request<MyReferrals>("/referrals/mine", {}, token),
  admin: (token: string) => request<ReferralAdminView>("/admin/referrals", {}, token),
  saveSettings: (token: string, s: Omit<ReferralSettings, "currency">) =>
    request<ReferralSettings>("/admin/referrals/settings", { method: "PUT", body: JSON.stringify(s) }, token),
  reverse: (token: string, id: number, reason: string) =>
    request<ReferralAdminView>(`/admin/referrals/${id}/reverse`, { method: "POST", body: JSON.stringify({ reason }) }, token),
};

// Revenue analytics (finance). See docs/admin-analytics-plan.md Phase F.
export interface RevenueKpi {
  key: string; group: "CASH" | "RECOGNISED" | "OUTSTANDING" | "ESTIMATED" | "UNIT"; label: string; help: string;
  unit: "USD" | "COUNT" | "PERCENT" | "MONTHS"; value?: number | null; note?: string | null;
}
export interface RevenueSlice { key: string; label: string; cash: number; recognised: number; payments: number }
export interface MarketingSpendRow { id: number; month: string; channel: string; amount: number; currency: string; note?: string | null }
export interface RevenueData {
  from: string; to: string; grain: MarketGrain; subscriptionHistoryStartsAt?: string | null; kpis: RevenueKpi[];
  series: { start: string; end: string; label: string; complete: boolean; values: Record<string, number> }[];
  byProduct: RevenueSlice[]; byCategory: RevenueSlice[]; byLocation: RevenueSlice[]; spend: MarketingSpendRow[];
  commissionSharePercent: number; notes: string[];
}
export const revenueAnalytics = {
  get: (token: string, p: { from?: string; to?: string; grain?: MarketGrain }) =>
    request<RevenueData>(`/admin/analytics/revenue?${marketQs(p)}`, {}, token),
  addSpend: (token: string, s: { month: string; channel: string; amount: number; note?: string }) =>
    request<MarketingSpendRow>("/admin/analytics/marketing-spend", { method: "POST", body: JSON.stringify(s) }, token),
  deleteSpend: (token: string, id: number) =>
    request<{ deleted: boolean }>(`/admin/analytics/marketing-spend/${id}`, { method: "DELETE" }, token),
  setCommissionShare: (token: string, percent: number) =>
    request<{ percent: number }>("/admin/analytics/revenue/commission-share", { method: "PUT", body: JSON.stringify({ percent }) }, token),
};

// Insights & alerts. See docs/admin-analytics-plan.md Phase G.
export type ObservationMethod = "POISSON_RATE" | "MEDIAN_SHIFT" | "SHARE_GAP" | "THRESHOLD" | "DESCRIPTIVE";
export interface MarketObservationView {
  id: number; kind: string; metricKey: string; domain: string; method: ObservationMethod; grain: "WEEK" | "MONTH";
  periodStart: string; periodEnd: string; comparisonStart?: string | null; comparisonEnd?: string | null;
  listingType?: string | null; city?: string | null; suburb?: string | null; category?: string | null; location: string;
  currentValue?: number | null; previousValue?: number | null; changePct?: number | null;
  sampleCurrent: number; samplePrevious: number; statistic?: number | null; pValue?: number | null; qValue?: number | null;
  confidence: "HIGH" | "MEDIUM" | "LOW"; statement: string; currency?: string | null; firstSeenAt: string;
}
export interface InsightCoverage {
  grain: string; periodStart?: string | null; periodEnd?: string | null; comparisonStart?: string | null; comparisonEnd?: string | null;
  tested: number; reported: number; belowMinimum: number; historyIncomplete: number; computedAt: string;
}
export interface AiInsightItem { section: "CHANGE" | "OPPORTUNITY" | "RISK"; title: string; interpretation: string; implication: string; observationIds: number[] }
export interface AiInsightView {
  status: "OK" | "OFF" | "UNAVAILABLE" | "NONE" | "FAILED" | "REFUSED"; enabled: boolean; available: boolean; model?: string | null;
  createdAt?: string | null; headline?: string | null; items: AiInsightItem[]; outdated: boolean; hiddenItems: number; note?: string | null;
}
export interface InsightQuestion { key: string; question: string; observationIds: number[]; answer?: string | null }
export interface InsightsData {
  grain: "WEEK" | "MONTH"; coverage?: InsightCoverage | null; observations: MarketObservationView[]; ai: AiInsightView;
  questions: InsightQuestion[]; minimums: { events: number; medianSample: number; stock: number; shareEvents: number; maxQValue: number };
}
export type AlertSeverity = "INFO" | "WARNING" | "CRITICAL";
export interface AnalyticsAlertView {
  id: number; ruleKey: string; ruleLabel: string; severity: AlertSeverity; domain: string; title: string; message: string;
  observationId?: number | null; createdAt: string; acknowledgedAt?: string | null; acknowledgedByUserId?: number | null; recipients: number;
}
export interface AlertRuleView {
  key: string; label: string; domain: string; unit: "PERCENT" | "COUNT" | "HOURS"; enabled: boolean; threshold: number; minSample: number;
  maxQValue: number; cooldownHours: number; severity: AlertSeverity; customised: boolean; marketRule: boolean;
}
export interface AlertPreferenceView { key: string; label: string; domain: string; inApp: boolean; email: boolean }
export const insightsApi = {
  get: (token: string, grain: "WEEK" | "MONTH") => request<InsightsData>(`/admin/analytics/insights?grain=${grain}`, {}, token),
  setAi: (token: string, enabled: boolean) =>
    request<{ enabled: boolean }>("/admin/analytics/insights/ai", { method: "PUT", body: JSON.stringify({ enabled }) }, token),
  alerts: (token: string, open: boolean) =>
    request<{ alerts: AnalyticsAlertView[]; openCount: number }>(`/admin/analytics/alerts?open=${open}`, {}, token),
  acknowledge: (token: string, id: number) =>
    request<AnalyticsAlertView>(`/admin/analytics/alerts/${id}/acknowledge`, { method: "POST" }, token),
  rules: (token: string) => request<{ rules: AlertRuleView[]; canEdit: boolean }>("/admin/analytics/alert-rules", {}, token),
  saveRule: (token: string, key: string, r: Pick<AlertRuleView, "enabled" | "threshold" | "minSample" | "maxQValue" | "cooldownHours" | "severity">) =>
    request<AlertRuleView>(`/admin/analytics/alert-rules/${key}`, { method: "PUT", body: JSON.stringify(r) }, token),
  preferences: (token: string) => request<AlertPreferenceView[]>("/admin/analytics/alert-preferences", {}, token),
  savePreference: (token: string, key: string, p: { inApp: boolean; email: boolean }) =>
    request<AlertPreferenceView>(`/admin/analytics/alert-preferences/${key}`, { method: "PUT", body: JSON.stringify(p) }, token),
};

// Reports, scheduled delivery and saved views. See docs/admin-analytics-plan.md Phase H.
export type ReportFormat = "PDF" | "XLSX" | "CSV";
export type ReportFrequency = "DAY" | "WEEK" | "MONTH" | "QUARTER" | "YEAR";
export interface ReportTypeView { key: string; label: string; description: string; grain: string; defaultFrom: string; defaultTo: string }
export interface ReportFilters { from?: string; to?: string; city?: string; suburb?: string; category?: string }
export interface ReportRunView {
  id: number; scheduleId?: number | null; reportType: string; reportLabel: string; format: ReportFormat; trigger: "DOWNLOAD" | "SEND_NOW" | "SCHEDULE";
  requestedByUserId?: number | null; requestedBy?: string | null; generatedAt: string; periodFrom?: string | null; periodTo?: string | null;
  status: "OK" | "FAILED" | "SKIPPED"; sizeBytes?: number | null; recipients: number; error?: string | null;
}
export interface ReportRecipient { id: number; name: string; email: string }
export interface ReportScheduleView {
  id: number; name: string; reportType: string; reportLabel: string; format: ReportFormat; frequency: ReportFrequency; city?: string | null;
  suburb?: string | null; category?: string | null; recipients: ReportRecipient[]; enabled: boolean; nextRunAt: string; lastRunAt?: string | null;
  lastStatus?: string | null; lastError?: string | null; ownerUserId: number; owner: string;
}
export interface ReportScheduleInput {
  name: string; reportType: string; format: ReportFormat; frequency: ReportFrequency; city?: string; suburb?: string; category?: string;
  recipientIds: number[]; enabled: boolean;
}
export type SavedViewFilters = Record<string, string | number | boolean | null>;
export interface SavedViewDto { id: number; name: string; page: string; filters: SavedViewFilters; shared: boolean; mine: boolean; owner: string; updatedAt: string }

function reportQs(format: ReportFormat, f: ReportFilters) {
  const q = new URLSearchParams({ format });
  for (const [k, v] of Object.entries(f)) if (v) q.set(k, v);
  return q.toString();
}

export const reportsApi = {
  catalogue: (token: string) => request<{ types: ReportTypeView[]; canSchedule: boolean; formats: ReportFormat[] }>("/admin/analytics/reports", {}, token),
  /** Downloads a report file; resolves with its name and content. */
  download: async (token: string, type: string, format: ReportFormat, f: ReportFilters) => {
    const res = await fetch(`${BASE}/admin/analytics/reports/${type}?${reportQs(format, f)}`, { headers: { Authorization: `Bearer ${token}` } });
    if (!res.ok) {
      let message = res.statusText;
      try { const body = await res.json(); message = body.message ?? body.error ?? message; } catch {}
      throw new ApiError(res.status, message);
    }
    const cd = res.headers.get("content-disposition") ?? "";
    const name = /filename\*=UTF-8''([^;]+)/i.exec(cd)?.[1] ?? /filename="?([^";]+)"?/i.exec(cd)?.[1] ?? `estatex-report.${format.toLowerCase()}`;
    return { name: decodeURIComponent(name), blob: await res.blob() };
  },
  runs: (token: string) => request<ReportRunView[]>("/admin/analytics/reports/runs", {}, token),
  schedules: (token: string) => request<ReportScheduleView[]>("/admin/analytics/report-schedules", {}, token),
  recipients: (token: string, type: string) => request<ReportRecipient[]>(`/admin/analytics/report-recipients?type=${type}`, {}, token),
  createSchedule: (token: string, s: ReportScheduleInput) =>
    request<ReportScheduleView>("/admin/analytics/report-schedules", { method: "POST", body: JSON.stringify(s) }, token),
  updateSchedule: (token: string, id: number, s: ReportScheduleInput) =>
    request<ReportScheduleView>(`/admin/analytics/report-schedules/${id}`, { method: "PUT", body: JSON.stringify(s) }, token),
  deleteSchedule: (token: string, id: number) =>
    request<{ deleted: boolean }>(`/admin/analytics/report-schedules/${id}`, { method: "DELETE" }, token),
  sendNow: (token: string, id: number) =>
    request<{ status: string; recipients: number; detail?: string | null }>(`/admin/analytics/report-schedules/${id}/send-now`, { method: "POST" }, token),
};

export const savedViewsApi = {
  list: (token: string, page: string) => request<SavedViewDto[]>(`/admin/analytics/saved-views?page=${page}`, {}, token),
  create: (token: string, v: { name: string; page: string; filters: SavedViewFilters; shared: boolean }) =>
    request<SavedViewDto>("/admin/analytics/saved-views", { method: "POST", body: JSON.stringify(v) }, token),
  update: (token: string, id: number, v: { name: string; page: string; filters: SavedViewFilters; shared: boolean }) =>
    request<SavedViewDto>(`/admin/analytics/saved-views/${id}`, { method: "PUT", body: JSON.stringify(v) }, token),
  remove: (token: string, id: number) => request<{ deleted: boolean }>(`/admin/analytics/saved-views/${id}`, { method: "DELETE" }, token),
};
