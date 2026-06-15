export type ListingType = "RENT" | "SALE" | "SHORT_STAY";
export type PropertyStatus = "DRAFT" | "AVAILABLE" | "RENTED" | "SOLD";
export type VerificationStatus = "UNVERIFIED" | "VERIFIED" | "REJECTED";
export type UserRole = "TENANT" | "LANDLORD" | "AGENT" | "DIASPORA" | "INVESTOR" | "ADMIN" | "DEVELOPER" | "PRIVATE";
export type LeaseStatus = "DRAFT" | "ACTIVE" | "SIGNED" | "COMPLETED" | "ENDED";
export type EscrowStatus = "PENDING" | "FUNDED" | "RELEASED" | "DISPUTED";
export type PaymentStatus = "PENDING" | "SUCCESS" | "FAILED";
export type MaintenanceStatus = "OPEN" | "IN_PROGRESS" | "RESOLVED";
export type MessageType = "GENERAL" | "REFERENCE_REQUEST" | "MAINTENANCE" | "PAYMENT_QUERY";
export type InvestmentStatus = "ACTIVE" | "EXITED";
export type LeaseDocumentStatus = "SUBMITTED" | "APPROVED" | "REJECTED";
export type LeaseDocumentType = "PAYSLIP" | "PROOF_OF_EMPLOYMENT" | "BANK_STATEMENT";

export interface Property {
  id: number;
  title: string;
  description?: string;
  listingType: ListingType;
  status: PropertyStatus;
  verificationStatus: VerificationStatus;
  city: string;
  suburb: string;
  address?: string;
  country: string;
  bedrooms: number;
  bathrooms: number;
  price: number;
  currency: string;
  latitude?: number;
  longitude?: number;
  diasporaFriendly: boolean;
  escrowRequired: boolean;
  landlordId: number;
  agentId?: number;
  landlordName?: string;
  agentName?: string;
  agentPhone?: string;
  createdAt: string;
  verifiedAt?: string;
  photoUrls: string[];
  imageUrls: string[];
  photos: string[];
}

export interface User {
  id: number;
  fullName: string;
  email: string;
  phone?: string;
  country?: string;
  city?: string;
  preferredCurrency?: string;
  avatarUrl?: string;
  bio?: string;
  diasporaLocation?: string;
  occupation?: string;
  companyName?: string;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  emailNotifications?: boolean;
  smsNotifications?: boolean;
  twoFactorEnabled?: boolean;
  identityVerified?: boolean;
  verified?: boolean;
  trustScore?: number;
  primaryProfile?: string;
  profileCompletion?: number;
  roles: UserRole[];
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface Lease {
  id: number;
  propertyId: number;
  tenantId: number;
  landlordId: number;
  startDate: string;
  endDate: string;
  monthlyRent: number;
  depositAmount: number;
  currency: string;
  status: LeaseStatus;
  notes?: string;
  signedByTenant?: boolean;
  signedByLandlord?: boolean;
  createdAt: string;
}

export interface Escrow {
  id: number;
  propertyId: number;
  leaseId?: number;
  payerId: number;
  amount: number;
  currency: string;
  status: EscrowStatus;
  description?: string;
  createdAt: string;
}

export interface Payment {
  id: number;
  payerId: number;
  payeeId: number;
  propertyId: number;
  leaseId?: number;
  amount: number;
  currency: string;
  provider: string;
  description?: string;
  status: PaymentStatus;
  createdAt: string;
}

export interface MaintenanceRequest {
  id: number;
  propertyId: number;
  requestedById: number;
  category: string;
  priority: string;
  description?: string;
  status: MaintenanceStatus;
  createdAt: string;
}

export interface Rating {
  id: number;
  landlordId: number;
  tenantId: number;
  propertyId: number;
  leaseId?: number;
  rating: number;
  comment?: string;
  createdAt: string;
}

export interface Conversation {
  id: number;
  subject?: string;
  participants: number[];
  lastMessage?: string;
  unreadCount?: number;
  createdAt: string;
}

export interface ChatMessage {
  id: number;
  conversationId: number;
  senderId: number;
  content: string;
  messageType: MessageType;
  read: boolean;
  createdAt: string;
}

export interface Reit {
  id: number;
  name: string;
  description?: string;
  country: string;
  unitPrice: number;
  projectedYield: number;
  riskLevel: string;
  active: boolean;
}

export interface Investment {
  id: number;
  investorId: number;
  reitId: number;
  units: number;
  currency: string;
  status: InvestmentStatus;
  createdAt: string;
}

export interface TenantDashboard {
  tenant: User;
  activeLeases: Lease[];
  recentPayments: Payment[];
  maintenanceRequests: MaintenanceRequest[];
  escrows: Escrow[];
}

export interface LandlordDashboard {
  landlord: User;
  properties: Property[];
  activeLeases: Lease[];
  recentPayments: Payment[];
  maintenanceRequests: MaintenanceRequest[];
  totalRentIncome: number;
}

export interface PropertySearchParams {
  listingType?: ListingType;
  city?: string;
  suburb?: string;
  minPrice?: number;
  maxPrice?: number;
  bedrooms?: number;
  bathrooms?: number;
  diasporaFriendly?: boolean;
}

export interface LeaseDocument {
  id: number;
  leaseId: number;
  documentType: LeaseDocumentType;
  fileName: string;
  status: LeaseDocumentStatus;
  reviewNote?: string;
  uploadedAt: string;
}
