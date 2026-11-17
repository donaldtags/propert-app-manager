export type ListingType = "RENT" | "SALE" | "SHORT_STAY";
export type TitleType = "TITLE_DEED" | "SECTIONAL_TITLE" | "CESSION";

export type PropertyStatus = "DRAFT" | "AVAILABLE" | "RESERVED" | "OCCUPIED" | "SOLD" | "INACTIVE";
export type VerificationStatus = "UNVERIFIED" | "PENDING" | "VERIFIED" | "REJECTED";
export type WaterSource = "MUNICIPAL" | "BOREHOLE" | "WELL" | "TANKER" | "OTHER";
export type UserRole = "TENANT" | "LANDLORD" | "AGENT" | "DIASPORA" | "INVESTOR" | "ADMIN" | "DEVELOPER" | "PRIVATE" | "SERVICE_PROVIDER";
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

/** precision is ArcGIS Addr_type: PointAddress (exact house), StreetAddress, StreetName, Locality, POI… */
export interface GeocodeResult {
  latitude: number;
  longitude: number;
  matchedAddress: string;
  precision: string;
  score: number;
}

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
  featured: boolean;
  featuredUntil?: string;
  photoUrls: string[];
  imageUrls: string[];
  photos: string[];
  photoDetails: { id: number; url: string }[];
  /** Set when an agency workspace holds the listing. */
  organisationId?: number;
  titleType?: TitleType;
  /** Buyer-facing risk markers, e.g. "CESSION". */
  riskFlags?: string[];
}

export interface FeaturedListingSettings {
  price: number;
  currency: string;
  durationDays: number;
  updatedAt: string;
}

export type SubscriptionPlan = "STARTER" | "GROWTH" | "PROFESSIONAL";
export type SubscriptionFeature =
  | "ESCROW"
  | "DIGITAL_LEASES"
  | "MAINTENANCE_REQUESTS"
  | "RENT_REMINDERS"
  | "AI_PRICING"
  | "TENANT_PASSPORT"
  | "REPORTS";

export interface PlanSettings {
  plan: SubscriptionPlan;
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
  updatedAt: string;
}

export interface SubscriptionInfo {
  plan: SubscriptionPlan;
  status: "ACTIVE" | "PAST_DUE" | "CANCELLED";
  currentPeriodEnd: string | null;
  maxProperties: number | null;
  activePropertyCount: number;
  enabledFeatures: SubscriptionFeature[];
}

export interface PropertyBilling {
  status: "ACTIVE" | "PAST_DUE";
  currentPeriodEnd: string | null;
  monthlyFee: number;
  currency: string;
}

export interface CheckoutResponse {
  paymentId: number;
  redirectUrl: string;
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
  tickerSymbol?: string;
  marketQuote?: MarketQuote;
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

export interface DiasporaDashboard {
  user: User;
  ownedProperties: Property[];
  investments: Investment[];
  escrows: Escrow[];
  pendingInspections: Viewing[];
  maintenanceRequests: MaintenanceRequest[];
  recentPayments: Payment[];
  constructionProjects: ConstructionProject[];
  portfolioValue: number;
  monthlyRentalIncome: number;
}

export type ConstructionStage =
  | "PLANNING"
  | "FOUNDATION"
  | "STRUCTURE"
  | "ROOFING"
  | "ELECTRICAL_PLUMBING"
  | "PLASTERING"
  | "FINISHES"
  | "COMPLETED";
export type ConstructionStatus = "ACTIVE" | "DELAYED" | "PAUSED" | "COMPLETED";
export type MilestoneStatus = "PENDING" | "SUBMITTED" | "APPROVED" | "PAID";

export interface ConstructionProject {
  id: number;
  ownerId: number;
  propertyId?: number;
  title: string;
  country: string;
  city: string;
  address?: string;
  contractorName?: string;
  contractorPhone?: string;
  contractorCompany?: string;
  stage: ConstructionStage;
  progressPercent: number;
  status: ConstructionStatus;
  budgetTotal?: number;
  amountPaid: number;
  amountRemaining?: number;
  currency: string;
  startDate?: string;
  expectedCompletionDate?: string;
  createdAt: string;
  updatedAt?: string;
  latestUpdateNote?: string;
  latestUpdateAt?: string;
}

export interface ConstructionMilestone {
  id: number;
  projectId: number;
  title: string;
  description?: string;
  amountDue?: number;
  targetDate?: string;
  status: MilestoneStatus;
  submittedAt?: string;
  approvedAt?: string;
  approvedByUserId?: number;
  createdAt: string;
}

export interface ConstructionUpdate {
  id: number;
  projectId: number;
  note: string;
  postedByUserId?: number;
  postedByName?: string;
  createdAt: string;
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
  active: boolean;
  ownerId?: number;
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

// ---------------------------------------------------------------------------
// Organisation workspaces (EstateX Phase 1a)
// ---------------------------------------------------------------------------

export type Division = "PROPERTIES" | "MANAGEMENT" | "INTELLIGENCE" | "DEVELOPMENTS" | "INVESTMENTS";

export type OrgRole =
  | "ORG_ADMIN"
  | "PREA"
  | "NEGOTIATOR"
  | "PROPERTY_MANAGER"
  | "ANALYST"
  | "OWNER"
  | "TENANT"
  | "BUYER"
  | "INVESTOR"
  | "LANDOWNER";

export type OrgPermission =
  | "ORG_VIEW"
  | "ORG_SETTINGS_EDIT"
  | "MEMBERS_VIEW"
  | "MEMBERS_MANAGE"
  | "AUDIT_VIEW"
  | "LISTING_CREATE"
  | "LISTING_EDIT"
  | "TITLE_VERIFY"
  | "ENQUIRY_CAPTURE"
  | "LISTING_APPROVE_PUBLISH"
  | "MANDATE_SIGN_OFF"
  | "TRUST_LEDGER_VIEW"
  | "TRUST_LEDGER_WRITE"
  | "COMMISSION_CONFIRM"
  | "MAINTENANCE_MANAGE"
  | "MARKET_DATA_WRITE"
  | "MARKET_DATA_ACHIEVED_VIEW"
  | "TARGETS_EDIT"
  | "OWNER_PORTAL_VIEW"
  | "TENANT_PORTAL_VIEW"
  | "BUYER_PORTAL_VIEW"
  | "INVESTOR_PORTAL_VIEW"
  | "LANDOWNER_PORTAL_VIEW";

export interface OrgThemeTokens {
  lightPrimary?: string;
  lightSurface?: string;
  lightText?: string;
  lightMuted?: string;
  lightAccent?: string;
  darkPrimary?: string;
  darkSurface?: string;
  darkText?: string;
  darkMuted?: string;
  darkAccent?: string;
  headingFont?: string;
  bodyFont?: string;
}

export interface Organisation {
  id: number;
  slug: string;
  name: string;
  slogan?: string;
  divisions: Division[];
  logoOnLightUrl?: string;
  logoOnDarkUrl?: string;
  theme?: OrgThemeTokens;
  phone?: string;
  whatsappNumber?: string;
  instagramHandle?: string;
  email?: string;
  address?: string;
  website?: string;
  preaName?: string;
  eacRegistrationNumber?: string;
  defaultCurrency: "USD" | "ZWG";
  timezone: string;
  // Staff-only fields (absent for everyone else)
  preaUserId?: number;
  trustAccountReference?: string;
  trustAccountBank?: string;
  saleCommissionBps?: number;
  managementFeeBps?: number;
  lettingFeeMonthsX100?: number;
  version?: number;
}

export interface OrgContext {
  organisation: Organisation;
  membershipId?: number;
  roles: OrgRole[];
  divisions: Division[];
  permissions: OrgPermission[];
  platformOverride: boolean;
}

export interface OrgMember {
  id: number;
  userId: number;
  fullName: string;
  email: string;
  phone?: string;
  roles: OrgRole[];
  divisions: Division[];
  active: boolean;
  createdAt: string;
  version: number;
}

export interface OrgAuditEntry {
  id: number;
  actorUserId?: number;
  actorName?: string;
  action: string;
  entityType: string;
  entityId?: number;
  fromState?: string;
  toState?: string;
  amountCents?: number;
  currency?: string;
  metadataJson?: string;
  createdAt: string;
}

export type UpdateOrganisationRequest = Partial<
  Pick<
    Organisation,
    | "name"
    | "slogan"
    | "divisions"
    | "logoOnLightUrl"
    | "logoOnDarkUrl"
    | "theme"
    | "phone"
    | "whatsappNumber"
    | "instagramHandle"
    | "email"
    | "address"
    | "website"
    | "eacRegistrationNumber"
    | "trustAccountReference"
    | "trustAccountBank"
    | "defaultCurrency"
    | "saleCommissionBps"
    | "managementFeeBps"
    | "lettingFeeMonthsX100"
    | "version"
  >
>;

// ---------------------------------------------------------------------------
// Location reference data
// ---------------------------------------------------------------------------

export type LocationType = "PROVINCE" | "CITY" | "TOWN" | "GROWTH_POINT" | "SUBURB";
export type DensityTier = "HIGH_DENSITY" | "MEDIUM_DENSITY" | "LOW_DENSITY" | "PERI_URBAN" | "INDUSTRIAL" | "CBD";

export interface LocationNode {
  id: number;
  type: LocationType;
  name: string;
  parentId?: number;
  province?: string;
  city?: string;
  densityTier?: DensityTier;
  aliases?: string[];
  latitude?: number;
  longitude?: number;
  verified: boolean;
}

// ---------------------------------------------------------------------------
// Organisation listings & compliance gates (EstateX Phase 1b)
// ---------------------------------------------------------------------------

export type TitleVerificationStatus = "NOT_STARTED" | "IN_PROGRESS" | "VERIFIED" | "FAILED";
export type MandateType = "SOLE" | "OPEN";
export type MandateStatus = "DRAFT" | "AWAITING_SIGN_OFF" | "ACTIVE" | "EXPIRED" | "CANCELLED" | "SUPERSEDED";
export type ComplianceCheckKey = "TITLE_TYPE" | "TITLE_VERIFIED" | "MANDATE" | "OWNER_CONSENT" | "PREA_APPROVAL";

export interface ComplianceCheck {
  key: ComplianceCheckKey;
  label: string;
  met: boolean;
  detail: string;
}

export interface ListingCompliance {
  titleVerificationStatus: TitleVerificationStatus;
  titleReference?: string;
  titleVerificationNotes?: string;
  titleVerifiedAt?: string;
  titleVerifiedByName?: string;
  ownerConsentPhotos: boolean;
  ownerConsentAddress: boolean;
  consentRecordedAt?: string;
  preaApprovedAt?: string;
  preaApprovedByName?: string;
  materialChangedAt?: string;
  activeMandateId?: number;
  publishedAt?: string;
  division?: Division;
  negotiatorUserId?: number;
  negotiatorName?: string;
  checks: ComplianceCheck[];
  publishable: boolean;
  readyForApproval: boolean;
  version: number;
}

export interface Mandate {
  id: number;
  propertyId: number;
  type: MandateType;
  status: MandateStatus;
  startDate: string;
  expiryDate: string;
  ownerName: string;
  ownerPhone?: string;
  ownerEmail?: string;
  askingPriceCents?: number;
  minimumPriceCents?: number;
  currency: string;
  commissionBps?: number;
  offerAuthority?: string;
  hasSignedDocument: boolean;
  documentFileName?: string;
  ownerSignedOn?: string;
  preaSignedOffAt?: string;
  preaSignedOffByName?: string;
  createdAt: string;
  version: number;
}

export interface OrgListingSummary {
  propertyId: number;
  title: string;
  listingType: ListingType;
  status: PropertyStatus;
  city: string;
  suburb: string;
  priceCents?: number;
  currency: string;
  titleType?: TitleType;
  riskFlags: string[];
  negotiatorName?: string;
  publishable: boolean;
  openChecks: number;
  createdAt: string;
}

export interface OrgListingDetail {
  property: Property;
  compliance: ListingCompliance;
  mandates: Mandate[];
}

export interface CreateOrgListingRequest {
  /** Client-generated id; replaying the same create never makes a second listing. */
  clientId?: string;
  title: string;
  description?: string;
  listingType: ListingType;
  city: string;
  suburb: string;
  address?: string;
  bedrooms: number;
  bathrooms: number;
  priceCents: number;
  currency?: "USD" | "ZWG";
  titleType?: TitleType;
}

export interface CreateMandateRequest {
  clientId?: string;
  type: MandateType;
  startDate: string;
  expiryDate: string;
  ownerName: string;
  ownerPhone?: string;
  ownerEmail?: string;
  askingPriceCents?: number;
  minimumPriceCents?: number;
  commissionBps?: number;
  offerAuthority?: string;
}

// ---------------------------------------------------------------------------
// Enquiries (EstateX Phase 1c)
// ---------------------------------------------------------------------------

export type EnquiryChannel =
  | "WHATSAPP" | "PHONE" | "WALK_IN" | "INSTAGRAM" | "FACEBOOK" | "PORTAL"
  | "WEBSITE" | "EMAIL" | "REFERRAL" | "SIGNBOARD" | "OTHER";
export type EnquiryIntent = "BUY" | "RENT" | "SELL" | "LET" | "INVEST" | "OTHER";
export type EnquiryStatus = "NEW" | "CONTACTED" | "QUALIFIED" | "CLOSED";

export interface Enquiry {
  id: number;
  clientId?: string;
  channel: EnquiryChannel;
  intent?: EnquiryIntent;
  status: EnquiryStatus;
  name: string;
  phone?: string;
  email?: string;
  message?: string;
  propertyId?: number;
  propertyTitle?: string;
  budgetCents?: number;
  currency?: string;
  capturedByUserId?: number;
  capturedAt: string;
  version: number;
}

export interface CaptureEnquiryRequest {
  clientId?: string;
  channel: EnquiryChannel | string;
  intent?: EnquiryIntent | string;
  name: string;
  phone?: string;
  email?: string;
  message?: string;
  propertyId?: number;
  budgetCents?: number;
  capturedAt?: string;
}
