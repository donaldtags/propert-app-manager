import type {
  Property,
  PropertySearchParams,
  User,
  AuthResponse,
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
  ServiceBooking,
  RentalApplication,
  ApplicationStatus,
  TimelineEvent,
  AppNotification,
  LeaseActionRequest,
  LeaseActionType,
  LeaseActionStatus,
} from "./types";

const BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8081/api/v1";

async function request<T>(
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
    throw new Error(message);
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
    email: string;
    phone?: string;
    password: string;
    country?: string;
    roles?: string[];
  }) => request<AuthResponse>("/auth/register", { method: "POST", body: JSON.stringify(data) }),

  login: (data: { identifier: string; password: string }) =>
    request<AuthResponse>("/auth/login", { method: "POST", body: JSON.stringify(data) }),

  logout: (token: string) =>
    request<void>("/auth/logout", { method: "POST" }, token),

  me: (token: string) => request<User>("/auth/me", {}, token),

  forgotPassword: (email: string) =>
    request<void>("/auth/forgot-password", {
      method: "POST",
      body: JSON.stringify({ email }),
    }),

  resetPassword: (data: { token: string; password: string }) =>
    request<void>("/auth/reset-password", { method: "POST", body: JSON.stringify(data) }),
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

  get: (id: number) => request<Property>(`/properties/${id}`),

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
      landlordId: number;
      agentId?: number;
      photoUrls?: string[];
    },
    token: string
  ) => request<Property>("/properties", { method: "POST", body: JSON.stringify(data) }, token),

  verify: (id: number, data: { verifierId: number; note?: string }, token: string) =>
    request<Property>(`/properties/${id}/verify`, { method: "PATCH", body: JSON.stringify(data) }, token),

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

  landlordPassport: (id: number) =>
    request<LandlordPassport>(`/users/${id}/landlord-passport`),

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

  review: (id: number, data: { status: string; reviewNote?: string }, token: string) =>
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

  invest: (
    data: {
      reitId: number;
      units: number;
      currency?: string;
    },
    token: string
  ) => request<Investment>("/investments", { method: "POST", body: JSON.stringify(data) }, token),

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

  adminOverview: (token: string) =>
    request<AdminDashboardOverview>("/dashboards/admin", {}, token),
};

// Admin
export const admin = {
  fraudSignals: (token: string) =>
    request<FraudSignal[]>("/admin/fraud-signals", {}, token),
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
export const ai = {
  search: (query: string) =>
    request<{ answer: string; matches: Property[]; aiPowered: boolean }>("/ai/property-search", {
      method: "POST",
      body: JSON.stringify({ query }),
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

  rentSuggestion: (params: { listingType: string; city: string; suburb?: string; bedrooms: number }) => {
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
    }>(`/ai/rent-suggestion?${q.toString()}`);
  },

  homeAssistant: (message: string, token: string) =>
    request<{ answer: string; aiPowered: boolean }>(
      "/ai/home-assistant",
      { method: "POST", body: JSON.stringify({ message }) },
      token
    ),

  leaseExplanation: (leaseId: number, token: string) =>
    request<{ answer: string; aiPowered: boolean }>(`/ai/lease-explanation/${leaseId}`, {}, token),
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
