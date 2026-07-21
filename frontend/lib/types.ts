export type ListingType = "RENT" | "SALE" | "SHORT_STAY";
export type PropertyStatus = "DRAFT" | "AVAILABLE" | "RESERVED" | "OCCUPIED" | "SOLD" | "INACTIVE";
export type VerificationStatus = "UNVERIFIED" | "PENDING" | "VERIFIED" | "REJECTED";
export type WaterSource = "MUNICIPAL" | "BOREHOLE" | "WELL" | "TANKER" | "OTHER";
export type UserRole = "TENANT" | "LANDLORD" | "AGENT" | "DIASPORA" | "INVESTOR" | "ADMIN" | "DEVELOPER" | "PRIVATE";
export type LeaseStatus = "DRAFT" | "SENT" | "SIGNED" | "ACTIVE" | "ENDED" | "CANCELLED";
export type EscrowStatus = "CREATED" | "FUNDED" | "RELEASED" | "DISPUTED" | "REFUNDED" | "CANCELLED";
export type FundingMethod =
  | "BANK_TRANSFER"
  | "ECOCASH"
  | "ONEMONEY"
  | "INNBUCKS"
  | "PAYNOW"
  | "MPESA"
  | "MTN_MOMO"
  | "AIRTEL_MONEY"
  | "PAYFAST"
  | "CARD";
export type PaymentStatus = "INITIATED" | "SUCCESSFUL" | "FAILED" | "REFUNDED";
export type MaintenanceStatus = "OPEN" | "ASSIGNED" | "IN_PROGRESS" | "RESOLVED" | "CANCELLED";
export type MessageType = "GENERAL" | "REFERENCE_REQUEST" | "MAINTENANCE" | "PAYMENT_QUERY";
export type InvestmentStatus = "ACTIVE" | "EXITED";
export type LeaseDocumentStatus = "SUBMITTED" | "APPROVED" | "REJECTED";
export type LeaseDocumentType =
  | "PAYSLIP"
  | "PROOF_OF_EMPLOYMENT"
  | "BANK_STATEMENT"
  | "HAND_FILLED_APPLICATION"
  | "LEASE_FORM";
export type KycStatus = "PENDING" | "APPROVED" | "REJECTED";
export type KycDocumentType = "ID_FRONT" | "ID_BACK" | "PASSPORT" | "SELFIE" | "SELFIE_WITH_ID";
export type IdDocumentType = "NATIONAL_ID" | "PASSPORT" | "DRIVERS_LICENSE";

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
  solarInstalled: boolean;
  backupPower: boolean;
  waterSource?: WaterSource;
  furnished: boolean;
  internetAvailable: boolean;
  securityFeatures: boolean;
  parkingAvailable: boolean;
  petsAllowed: boolean;
  virtualTourUrl?: string;
  landlordId: number;
  agentId?: number;
  landlordName?: string;
  landlordCompanyName?: string;
  landlordTrustScore?: number;
  agentName?: string;
  agentPhone?: string;
  agentCompanyName?: string;
  agentTrustScore?: number;
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
  businessVerified?: boolean;
  emergencyContactName?: string;
  emergencyContactPhone?: string;
  emailNotifications?: boolean;
  smsNotifications?: boolean;
  twoFactorEnabled?: boolean;
  identityVerified?: boolean;
  faceVerified?: boolean;
  verified?: boolean;
  trustScore?: number;
  yearsOnPlatform?: number;
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
  terms?: string;
  signedByTenant?: boolean;
  signedByLandlord?: boolean;
  createdAt: string;
}

export interface LeaseExtraction {
  startDate?: string;
  endDate?: string;
  monthlyRent?: number;
  depositAmount?: number;
  currency?: string;
  tenantFullName?: string;
  propertyAddress?: string;
  notableTerms?: string;
}

export interface Escrow {
  id: number;
  propertyId: number;
  leaseId?: number;
  payerId: number;
  beneficiaryId: number;
  amount: number;
  currency: string;
  status: EscrowStatus;
  purpose?: string;
  createdAt: string;
  fundingMethod?: FundingMethod;
  fundingProvider?: string;
  releaseApprovals: number;
  releaseApprovalsRequired: number;
  releaseApprovedByUserIds: number[];
}

export interface Payment {
  id: number;
  payerId: number;
  payerName?: string;
  payeeId: number;
  payeeName?: string;
  propertyId: number;
  leaseId?: number;
  amount: number;
  currency: string;
  provider: string;
  reference?: string;
  purpose?: string;
  status: PaymentStatus;
  createdAt: string;
  paidAt?: string;
}

export interface MaintenancePhoto {
  id: number;
  photoUrl: string;
  uploadedAt: string;
}

export interface MaintenanceRequest {
  id: number;
  propertyId: number;
  requesterId: number;
  category: string;
  priority: string;
  description?: string;
  status: MaintenanceStatus;
  createdAt: string;
  assignedVendorId?: number;
  assignedVendorName?: string;
  photos: MaintenancePhoto[];
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

export interface ReitProperty {
  id: number;
  title: string;
  city: string;
  suburb: string;
  country: string;
  price: number;
  currency: string;
  listingType: ListingType;
  coverPhotoUrl?: string;
}

export interface FraudSignal {
  propertyId: number;
  propertyTitle: string;
  type: string;
  severity: "HIGH" | "MEDIUM" | "LOW";
  description: string;
}

export interface InvestmentScore {
  overall: number;
  yieldTier: string;
  riskTier: string;
  demandTier: string;
  sellThroughPercent?: number;
}

export interface Reit {
  id: number;
  name: string;
  description?: string;
  market: string;
  unitPrice: number;
  projectedAnnualYield: number;
  riskLevel: string;
  vexEligible?: boolean;
  active: boolean;
  totalUnits?: number;
  availableUnits?: number;
  propertyType: string;
  coverImageUrl?: string;
  investmentScore?: InvestmentScore;
  properties: ReitProperty[];
}

export interface MarketQuote {
  ticker: string;
  name: string;
  exchange: string;
  currency: string;
  price: number | null;
  changeAmount: number | null;
  changePercent: number | null;
  volume: number | null;
  asOf: string;
}

export interface MarketSnapshot {
  quotes: MarketQuote[];
  lastUpdated: string | null;
  stale: boolean;
  source: string;
}

export interface AdminRequest {
  id: number;
  userId: number;
  userFullName: string;
  userEmail: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  requestedAt: string;
}

export interface Investment {
  id: number;
  investorId: number;
  reitId: number;
  units: number;
  amount: number;
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
  paymentTrend: MonthlyAmount[];
}

export interface LandlordFinancialSummary {
  monthlyIncome: number;
  expectedMonthlyIncome: number;
  outstandingRent: number;
  overdueInvoiceCount: number;
  portfolioValue: number;
  currency: string;
}

export interface OccupancySummary {
  totalUnits: number;
  occupiedUnits: number;
  vacantUnits: number;
  occupancyRatePercent: number;
}

export interface LandlordEscrowSummary {
  balance: number;
  activeCount: number;
  currency: string;
}

export interface SatisfactionSummary {
  averageRating: number | null;
  ratingCount: number;
}

export interface MonthlyAmount {
  month: string;
  amount: number;
}

export interface LandlordDashboard {
  landlord: User;
  properties: Property[];
  activeLeases: Lease[];
  recentPayments: Payment[];
  maintenanceRequests: MaintenanceRequest[];
  totalRentIncome: number;
  financials: LandlordFinancialSummary;
  occupancy: OccupancySummary;
  escrow: LandlordEscrowSummary;
  satisfaction: SatisfactionSummary;
  incomeTrend: MonthlyAmount[];
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
  solarInstalled?: boolean;
  backupPower?: boolean;
  waterSource?: WaterSource;
  furnished?: boolean;
  internetAvailable?: boolean;
  securityFeatures?: boolean;
  parkingAvailable?: boolean;
  petsAllowed?: boolean;
  verifiedOnly?: boolean;
  escrowAvailable?: boolean;
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

export interface PropertyInquiry {
  id: number;
  propertyId: number;
  propertyTitle: string;
  name: string;
  email: string;
  phone?: string;
  message: string;
  createdAt: string;
}

export interface KycDocument {
  id: number;
  documentType: KycDocumentType;
  fileName: string;
  contentType: string;
  fileSize: number;
  uploadedAt: string;
}

export interface KycSubmission {
  id: number;
  userId: number;
  userFullName: string;
  userEmail: string;
  legalFullName: string;
  dateOfBirth: string;
  nationalIdNumber: string;
  idDocumentType: IdDocumentType;
  status: KycStatus;
  submittedAt: string;
  reviewedAt?: string;
  reviewedBy?: number;
  reviewNote?: string;
  documents: KycDocument[];
}

export interface VerificationLevel {
  level: number;
  label: string;
  emailVerified: boolean;
  phoneVerified: boolean;
  identityVerified: boolean;
  faceVerified: boolean;
  trustedUser: boolean;
}

export interface LandlordProfile {
  id: number;
  fullName: string;
  companyName?: string;
  avatarUrl?: string;
  verified: boolean;
  identityVerified: boolean;
  trustScore: number;
  propertyCount: number;
  averageRating: number | null;
  ratingCount: number;
}

export interface AdminConversation {
  id: number;
  participantIds: number[];
  participantNames: string[];
  propertyId?: number;
  subject?: string;
  lastMessage?: string;
  lastMessageAt: string;
}

export interface AdminMessage {
  id: number;
  conversationId: number;
  senderId: number;
  senderName: string;
  content: string;
  messageType: string;
  readAt?: string;
  createdAt: string;
}

export interface AdminDashboardOverview {
  revenue: { todayRevenue: number; monthRevenue: number; currency: string };
  escrow: { totalBalance: number; activeCount: number; disputedCount: number; currency: string };
  properties: { listed: number; sold: number; rented: number; total: number };
  leases: { active: number; occupancyRatePercent: number; total: number };
  maintenance: { open: number; total: number };
  verification: { pendingKyc: number; pendingAdminRequests: number };
  recentActivity: { type: string; description: string; occurredAt: string }[];
}

export interface PropertyHealthScore {
  overall: number;
  verification: number;
  maintenance: number;
  transactionSafety: number;
  solar: number | null;
  water: number | null;
  occupancy: number;
  neighbourhood: number | null;
}

export type VendorCategory =
  | "MOVING"
  | "CLEANING"
  | "PLUMBING"
  | "ELECTRICAL"
  | "INSURANCE"
  | "LEGAL"
  | "SOLAR"
  | "UTILITIES"
  | "FURNITURE"
  | "OTHER";

export type BookingStatus = "REQUESTED" | "CONFIRMED" | "COMPLETED" | "CANCELLED";

export interface Vendor {
  id: number;
  businessName: string;
  category: VendorCategory;
  description?: string;
  phone?: string;
  email?: string;
  city?: string;
  verified: boolean;
  averageRating?: number;
  ratingCount: number;
  createdAt: string;
}

export interface ServiceBooking {
  id: number;
  vendorId: number;
  vendorBusinessName: string;
  vendorCategory: VendorCategory;
  propertyId?: number;
  propertyTitle?: string;
  requesterId: number;
  requesterName: string;
  status: BookingStatus;
  preferredDate?: string;
  notes?: string;
  createdAt: string;
  completedAt?: string;
  feedbackRating?: number;
  feedbackComment?: string;
}

export type ViewingMode = "IN_PERSON" | "VIDEO_CALL";
export type ViewingStatus = "REQUESTED" | "CONFIRMED" | "COMPLETED" | "DECLINED" | "CANCELLED";

export type ApplicationStatus =
  | "DRAFT"
  | "SUBMITTED"
  | "VERIFICATION_REQUIRED"
  | "UNDER_REVIEW"
  | "APPROVED"
  | "REJECTED"
  | "LEASE_PREPARATION";

export type LeaseActionType = "RENEWAL" | "TERMINATION";
export type LeaseActionStatus = "PENDING" | "APPROVED" | "DECLINED";

export interface LeaseActionRequest {
  id: number;
  leaseId: number;
  propertyId: number;
  requestedById: number;
  requestedByName: string;
  type: LeaseActionType;
  status: LeaseActionStatus;
  proposedEndDate?: string;
  note?: string;
  reviewNote?: string;
  createdAt: string;
  resolvedAt?: string;
}

export type NotificationType = "RENT_REMINDER" | "LEASE_EXPIRY" | "MAINTENANCE_UPDATE" | "SECURITY_ALERT";

export interface AppNotification {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  relatedId?: number;
  read: boolean;
  createdAt: string;
}

export type TimelineEventType =
  | "LEASE"
  | "PAYMENT"
  | "MAINTENANCE"
  | "ESCROW"
  | "DOCUMENT"
  | "VIEWING"
  | "MESSAGE"
  | "APPLICATION";

export interface TimelineEvent {
  type: TimelineEventType;
  title: string;
  description?: string;
  status?: string;
  occurredAt: string;
  relatedId: number;
}

export interface RentalApplication {
  id: number;
  propertyId: number;
  propertyTitle: string;
  propertyPhotoUrl?: string;
  applicantId: number;
  applicantName: string;
  status: ApplicationStatus;
  desiredMoveInDate?: string;
  monthlyIncome?: number;
  message?: string;
  reviewNote?: string;
  identityVerified: boolean;
  createdAt: string;
  submittedAt?: string;
  reviewedAt?: string;
}

export interface Viewing {
  id: number;
  propertyId: number;
  propertyTitle: string;
  requesterId: number;
  requesterName: string;
  mode: ViewingMode;
  status: ViewingStatus;
  preferredDate?: string;
  preferredTime?: string;
  notes?: string;
  videoCallLink?: string;
  checkInCode?: string;
  qrCodeDataUri?: string;
  createdAt: string;
  confirmedAt?: string;
  completedAt?: string;
  feedbackRating?: number;
  feedbackComment?: string;
}

export interface NeighbourhoodProfile {
  id: number;
  city: string;
  suburb: string;
  schoolsNote?: string;
  hospitalsNote?: string;
  transportNote?: string;
  shoppingNote?: string;
  generalNote?: string;
  updatedAt: string;
}

export interface PropertyPassport {
  propertyId: number;
  title: string;
  createdAt: string;
  verificationStatus: VerificationStatus;
  verifiedAt?: string;
  solarInstalled: boolean;
  backupPower: boolean;
  waterSource?: WaterSource;
  healthScore: PropertyHealthScore;
  photoTimeline: { url: string; uploadedAt: string }[];
  leaseHistory: { status: LeaseStatus; startDate: string; endDate: string; monthlyRent: number; currency: string }[];
  maintenanceHistory: { category: string; status: MaintenanceStatus; createdAt: string; resolvedAt?: string }[];
  escrowHistory: { status: EscrowStatus; amount: number; currency: string; createdAt: string; releasedAt?: string }[];
  timeline: { occurredAt: string; type: string; label: string }[];
  neighbourhood?: NeighbourhoodProfile;
  averageRating?: number;
  ratingCount: number;
}

export interface TenantPassport {
  userId: number;
  fullName: string;
  identityVerified: boolean;
  trustScore: number;
  yearsOnPlatform: number;
  completedLeaseCount: number;
  activeLeaseCount: number;
  totalRentInvoices: number;
  onTimeRentInvoices: number;
  onTimePaymentRatePercent?: number;
  averageLandlordRatingGiven?: number;
  ratingsGivenCount: number;
}

export interface LandlordPassport {
  userId: number;
  fullName: string;
  companyName?: string;
  identityVerified: boolean;
  trustScore: number;
  yearsOnPlatform: number;
  propertyCount: number;
  completedLeaseCount: number;
  activeLeaseCount: number;
  resolvedMaintenanceCount: number;
  totalMaintenanceCount: number;
  maintenanceResolutionRatePercent?: number;
  escrowUsedCount: number;
  totalEscrowCount: number;
  escrowUsageRatePercent?: number;
  averageResponseHours?: number;
  responseRatePercent?: number;
  averageRating?: number;
  ratingCount: number;
}
