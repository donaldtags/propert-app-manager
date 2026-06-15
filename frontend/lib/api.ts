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
  TenantDashboard,
  LandlordDashboard,
  LeaseDocument,
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

  login: (data: { email: string; password: string }) =>
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
    const qs = q.toString();
    return request<Property[]>(`/properties${qs ? `?${qs}` : ""}`);
  },

  get: (id: number) => request<Property>(`/properties/${id}`),

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
};

// Users
export const users = {
  list: (role?: string) => {
    const q = role ? `?role=${role}` : "";
    return request<User[]>(`/users${q}`);
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

  addRole: (id: number, data: { role: string; password: string }, token: string) =>
    request<User>(`/users/${id}/roles`, { method: "POST", body: JSON.stringify(data) }, token),

  adminRequest: (id: number, token: string) =>
    request<void>(`/users/${id}/admin-request`, { method: "POST" }, token),
};

// Leases
export const leases = {
  create: (
    data: {
      propertyId: number;
      tenantId: number;
      landlordId: number;
      startDate: string;
      endDate: string;
      monthlyRent: number;
      depositAmount: number;
      currency?: string;
      notes?: string;
    },
    token: string
  ) => request<Lease>("/leases", { method: "POST", body: JSON.stringify(data) }, token),

  listByTenant: (tenantId: number, token: string) =>
    request<Lease[]>(`/leases?tenantId=${tenantId}`, {}, token),

  listByLandlord: (landlordId: number, token: string) =>
    request<Lease[]>(`/leases?landlordId=${landlordId}`, {}, token),

  sign: (id: number, userId: number, token: string) =>
    request<Lease>(`/leases/${id}/sign`, {
      method: "PATCH",
      body: JSON.stringify({ userId }),
    }, token),

  uploadDocuments: (leaseId: number, formData: FormData, token: string) => {
    return fetch(`${BASE}/leases/${leaseId}/documents`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      body: formData,
    }).then((r) => r.json() as Promise<LeaseDocument[]>);
  },

  listDocuments: (leaseId: number, token: string) =>
    request<LeaseDocument[]>(`/leases/${leaseId}/documents`, {}, token),

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
};

// Escrow
export const escrow = {
  create: (
    data: {
      propertyId: number;
      leaseId?: number;
      payerId: number;
      amount: number;
      currency?: string;
      description?: string;
    },
    token: string
  ) => request<Escrow>("/escrows", { method: "POST", body: JSON.stringify(data) }, token),

  list: (userId: number, token: string) =>
    request<Escrow[]>(`/escrows?userId=${userId}`, {}, token),

  fund: (id: number, token: string) =>
    request<Escrow>(`/escrows/${id}/fund`, { method: "PATCH" }, token),

  release: (id: number, token: string) =>
    request<Escrow>(`/escrows/${id}/release`, { method: "PATCH" }, token),

  dispute: (id: number, token: string) =>
    request<Escrow>(`/escrows/${id}/dispute`, { method: "PATCH" }, token),
};

// Payments
export const payments = {
  create: (
    data: {
      payerId: number;
      payeeId: number;
      propertyId: number;
      leaseId?: number;
      amount: number;
      currency?: string;
      provider: string;
      description?: string;
    },
    token: string
  ) => request<Payment>("/payments", { method: "POST", body: JSON.stringify(data) }, token),

  list: (userId: number, token: string) =>
    request<Payment[]>(`/payments?userId=${userId}`, {}, token),

  markSuccess: (id: number, token: string) =>
    request<Payment>(`/payments/${id}/success`, { method: "PATCH" }, token),
};

// Maintenance
export const maintenance = {
  create: (
    data: {
      propertyId: number;
      requestedById: number;
      category: string;
      priority: string;
      description?: string;
    },
    token: string
  ) => request<MaintenanceRequest>("/maintenance", { method: "POST", body: JSON.stringify(data) }, token),

  list: (propertyId: number, token: string) =>
    request<MaintenanceRequest[]>(`/maintenance?propertyId=${propertyId}`, {}, token),

  updateStatus: (id: number, status: string, token: string) =>
    request<MaintenanceRequest>(`/maintenance/${id}/status?status=${status}`, { method: "PATCH" }, token),
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
};

// Investments
export const investments = {
  listReits: () => request<Reit[]>("/investments/reits"),

  createReit: (
    data: {
      name: string;
      description?: string;
      country: string;
      unitPrice: number;
      projectedYield: number;
      riskLevel: string;
      active?: boolean;
    },
    token: string
  ) => request<Reit>("/investments/reits", { method: "POST", body: JSON.stringify(data) }, token),

  invest: (
    data: {
      investorId: number;
      reitId: number;
      units: number;
      currency?: string;
    },
    token: string
  ) => request<Investment>("/investments", { method: "POST", body: JSON.stringify(data) }, token),

  listByInvestor: (investorId: number, token: string) =>
    request<Investment[]>(`/investments?investorId=${investorId}`, {}, token),
};

// Dashboards
export const dashboards = {
  tenant: (tenantId: number, token: string) =>
    request<TenantDashboard>(`/dashboards/tenants/${tenantId}`, {}, token),

  landlord: (landlordId: number, token: string) =>
    request<LandlordDashboard>(`/dashboards/landlords/${landlordId}`, {}, token),
};

// AI
export const ai = {
  search: (query: string) =>
    request<{ result: string }>("/ai/property-search", {
      method: "POST",
      body: JSON.stringify({ query }),
    }),
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
