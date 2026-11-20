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
  /** Who markets it: a company on EstateX, an independent agent, or the owner. */
  listedBy?: "AGENCY" | "AGENT" | "OWNER";
  /** Agent's Estate Agents Council licence approved by EstateX. */
  listerLicensed?: boolean | null;
  organisationName?: string | null;
  organisationSlug?: string | null;
  organisationLogoUrl?: string | null;
  organisationPhone?: string | null;
  /** The person marketing it has had their ID document checked by EstateX. */
  listerIdVerified?: boolean | null;
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

// ---------------------------------------------------------------------------------------------
// Wallet
// ---------------------------------------------------------------------------------------------

/** "wallet" pays from the user's EstateX balance; the others go through a gateway checkout. */
export type CheckoutProvider = "paynow" | "stripe" | "wallet";

export type WalletEntryType =
  | "TOP_UP"
  | "PAYMENT"
  | "PAYOUT"
  | "WITHDRAWAL"
  | "WITHDRAWAL_REVERSAL"
  | "FEE"
  | "ADJUSTMENT";

export interface WalletEntry {
  id: number;
  amount: number;
  balanceAfter: number | null;
  currency: string;
  type: WalletEntryType;
  description: string;
  relatedType: string | null;
  relatedId: number | null;
  createdAt: string;
}

export interface WalletSummary {
  currency: string;
  balance: number;
  pendingWithdrawals: number;
  entries: WalletEntry[];
  totalEntries: number;
  page: number;
  size: number;
}

export type WithdrawalMethod = "ECOCASH" | "BANK";
export type WithdrawalStatus = "REQUESTED" | "PAID" | "REJECTED";

export interface WalletWithdrawal {
  id: number;
  userId: number;
  userName: string | null;
  userEmail: string | null;
  amount: number;
  currency: string;
  method: WithdrawalMethod;
  ecocashPhone: string | null;
  bankAccountName: string | null;
  bankAccountNumber: string | null;
  bankName: string | null;
  bankBranch: string | null;
  status: WithdrawalStatus;
  requestedAt: string;
  decidedAt: string | null;
  payoutReference: string | null;
  rejectReason: string | null;
}

export interface WithdrawalRequest {
  amount: number;
  currency?: string;
  method: WithdrawalMethod;
  ecocashPhone?: string;
  bankAccountName?: string;
  bankAccountNumber?: string;
  bankName?: string;
  bankBranch?: string;
  password: string;
}

export interface User {
  id: number;
  fullName: string;
  /** Absent for accounts that signed up with only a phone number. */
  email?: string | null;
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

export type TwoFactorMethod = "TOTP" | "SMS" | "BACKUP";

/** Password accepted, but the account has two-step sign-in on: answer with a code. */
export interface TwoFactorChallenge {
  twoFactorRequired: true;
  challengeToken: string;
  /** Methods this account can use (BACKUP codes always work too). */
  methods: TwoFactorMethod[];
  maskedPhone?: string;
  /** True when a text was sent automatically (accounts using text messages only). */
  codeSent?: boolean;
}

export type LoginResponse = AuthResponse | TwoFactorChallenge;

export function isTwoFactorChallenge(r: LoginResponse): r is TwoFactorChallenge {
  return (r as TwoFactorChallenge).twoFactorRequired === true;
}

export interface TwoFactorStatus {
  enabled: boolean;
  authenticatorApp: boolean;
  sms: boolean;
  smsAvailable: boolean;
  maskedPhone?: string;
  backupCodesRemaining: number;
}

export interface TotpSetup {
  secret: string;
  otpauthUri: string;
  qrDataUrl: string;
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
  /** Reviewer-only automatic checks: ID_FORMAT, AGE, NAME, DUPLICATE_ID. */
  automatedChecks?: { key: string; label: string; result: "PASS" | "WARN" | "FAIL"; detail: string }[] | null;
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
  | "CONTACTS_MANAGE"
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
  verificationChecklist: { key: string; label: string; checked: boolean }[];
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
  contactId?: number;
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

// ---------------------------------------------------------------------------
// Phase 2: contacts, pipeline, viewings, offers, campaign, intake, reports
// ---------------------------------------------------------------------------

export type ContactType = "OWNER" | "BUYER" | "TENANT" | "LANDOWNER" | "INVESTOR" | "REFERRAL_PARTNER";
export type ContactSource =
  | "NETWORK" | "WALK_IN" | "PORTAL" | "INSTAGRAM" | "FACEBOOK" | "WHATSAPP" | "REFERRAL" | "WEBSITE" | "SIGNBOARD" | "OTHER";
export type BuyingTimeline = "IMMEDIATELY" | "WITHIN_3_MONTHS" | "WITHIN_6_MONTHS" | "LATER" | "JUST_LOOKING";
export type Financing = "CASH" | "MORTGAGE_APPROVED" | "MORTGAGE_APPLYING" | "DIASPORA_TRANSFER" | "PAYMENT_PLAN" | "UNKNOWN";

export interface Contact {
  id: number;
  clientId?: string;
  fullName: string;
  phone?: string;
  phoneNormalised?: string;
  whatsapp?: string;
  email?: string;
  types: ContactType[];
  source?: ContactSource;
  tags: string[];
  notes?: string;
  budgetCents?: number;
  budgetCurrency?: string;
  timeline?: BuyingTimeline;
  financing?: Financing;
  preferredAreas?: string;
  minBedrooms?: number;
  decisionMaker?: boolean;
  decisionMakerNote?: string;
  qualified: boolean;
  qualifiedAt?: string;
  createdAt: string;
  updatedAt?: string;
  version: number;
}

export interface ContactActivity {
  kind: "ENQUIRY" | "VIEWING" | "OFFER" | "DEAL";
  id: number;
  at?: string;
  summary: string;
  propertyId?: number;
  propertyTitle?: string;
}

export interface ContactDetail {
  contact: Contact;
  activity: ContactActivity[];
}

export interface CreateContactRequest {
  clientId?: string;
  fullName: string;
  phone?: string;
  email?: string;
  types?: ContactType[];
  source?: ContactSource;
  tags?: string[];
  notes?: string;
}

export type PipelineStage =
  | "PROSPECT" | "MEETING" | "INSPECTION" | "MANDATE_SIGNED" | "MARKETING" | "VIEWINGS" | "OFFER" | "CONVEYANCING" | "COMPLETED" | "LOST";

export interface PipelineCard {
  id: number;
  clientId?: string;
  title: string;
  stage: PipelineStage;
  ownerContactId: number;
  ownerName?: string;
  propertyId?: number;
  propertyTitle?: string;
  negotiatorUserId?: number;
  negotiatorName?: string;
  estimatedValueCents?: number;
  currency: string;
  lostReason?: string;
  nextActionOn?: string;
  nextAction?: string;
  notes?: string;
  stageChangedAt: string;
  daysInStage: number;
  createdAt: string;
  version: number;
}

export interface CreatePipelineCardRequest {
  clientId?: string;
  title: string;
  ownerContactId: number;
  propertyId?: number;
  estimatedValueCents?: number;
  nextActionOn?: string;
  nextAction?: string;
  notes?: string;
}

export type ShowingStatus = "SCHEDULED" | "COMPLETED" | "NO_SHOW" | "CANCELLED";
export type BuyerInterest = "HOT" | "WARM" | "COLD";
export type PriceOpinion = "TOO_HIGH" | "FAIR" | "GOOD_VALUE";

export interface Showing {
  id: number;
  clientId?: string;
  propertyId: number;
  propertyTitle?: string;
  propertySuburb?: string;
  contactId: number;
  contactName?: string;
  contactPhone?: string;
  negotiatorUserId?: number;
  scheduledAt: string;
  durationMinutes: number;
  status: ShowingStatus;
  interest?: BuyerInterest;
  priceOpinion?: PriceOpinion;
  feedback?: string;
  completedAt?: string;
  version: number;
}

export type OfferStatus = "RECEIVED" | "PRESENTED" | "COUNTERED" | "ACCEPTED" | "REJECTED" | "WITHDRAWN";

export interface Offer {
  id: number;
  propertyId: number;
  propertyTitle?: string;
  mandateId: number;
  buyerContactId: number;
  buyerName?: string;
  amountCents: number;
  currency: string;
  depositCents?: number;
  counterAmountCents?: number;
  financing?: Financing;
  conditions?: string;
  expiresOn?: string;
  status: OfferStatus;
  presentedVia?: string;
  presentedAt?: string;
  decidedAt?: string;
  ownerInstructionReference?: string;
  withinAuthority: boolean;
  mandateMinimumCents?: number;
  ownerName?: string;
  ownerWhatsapp?: string;
  createdAt: string;
  events: { type: string; amountCents?: number; note?: string; actorUserId?: number; at: string }[];
  version: number;
}

export interface CampaignTask {
  id: number;
  day: number;
  dueOn: string;
  channel: string;
  title: string;
  done: boolean;
  doneAt?: string;
  overdue: boolean;
  note?: string;
}

export interface Campaign {
  id: number;
  propertyId: number;
  startDate: string;
  endDate: string;
  today: number;
  stopped: boolean;
  tasksDone: number;
  tasksTotal: number;
  tasks: CampaignTask[];
  enquiriesByChannel: Record<string, number>;
  enquiriesTotal: number;
  viewingsTotal: number;
}

export type InspectionCondition = "GOOD" | "FAIR" | "POOR" | "NOT_APPLICABLE";

export interface InspectionItem {
  key: string;
  label?: string;
  condition?: InspectionCondition | null;
  note?: string;
}

export interface Inspection {
  items: InspectionItem[];
  standSizeM2?: number;
  floorAreaM2?: number;
  notes?: string;
  inspectedOn?: string;
  inspectedByUserId?: number;
  version?: number;
  photos: { id: number; clientId?: string; caption?: string; itemKey?: string; contentType?: string; createdAt: string }[];
  ratedItems: number;
  poorItems: number;
}

export interface PricingComparable {
  propertyId: number;
  title: string;
  suburb: string;
  city: string;
  bedrooms: number;
  bathrooms: number;
  priceCents: number;
  currency: string;
  status: string;
  titleType?: string;
  sameSuburb: boolean;
  source: string;
}

export interface PricingSuggestion {
  propertyId: number;
  askingCents: number;
  currency: string;
  comparableCount: number;
  lowCents?: number;
  medianCents?: number;
  highCents?: number;
  suggestedCents?: number;
  enoughData: boolean;
  basis: string;
  comparables: PricingComparable[];
  latest?: { id: number; recommendedCents: number; rationale?: string; createdAt: string; comparableCount: number };
}

export interface SellerReportSummary {
  id: number;
  weekEnding: string;
  shareToken: string;
  adviceKind: "REVIEW_DOWN" | "REFRESH" | "ABOVE_MARKET" | "HOLD";
  adviceText: string;
  generatedAt: string;
  expiresAt?: string;
}

// ---- Short stays (BnB) ----------------------------------------------------------------

export interface StaySettings {
  propertyId: number;
  nightlyRate: number;
  currency: string;
  maxGuests: number;
  minNights: number;
  maxNights: number;
  cleaningFee: number;
  checkInTime: string;
  checkOutTime: string;
  instantBook: boolean;
  houseRules: string | null;
}

/** Busy nights on the public calendar; `end` is exclusive (the checkout morning is free). */
export interface BusyRange {
  start: string;
  end: string;
}

export interface StayQuote {
  available: boolean;
  unavailableReason: string | null;
  nights: number;
  nightlyRate: number;
  accommodation: number;
  cleaningFee: number;
  serviceFee: number;
  total: number;
  currency: string;
  instantBook: boolean;
}

export type StayBookingStatus =
  | "PENDING"
  | "AWAITING_PAYMENT"
  | "CONFIRMED"
  | "COMPLETED"
  | "DECLINED"
  | "CANCELLED"
  | "EXPIRED";

export interface StayParty {
  id: number;
  fullName: string;
  phone: string | null;
  email: string | null;
}

export interface StayBooking {
  id: number;
  reference: string;
  status: StayBookingStatus;
  propertyId: number;
  propertyTitle: string;
  propertyCity: string;
  propertySuburb: string | null;
  coverPhotoUrl: string | null;
  checkIn: string;
  checkOut: string;
  nights: number;
  guests: number;
  nightlyRate: number;
  cleaningFee: number;
  serviceFee: number;
  total: number;
  currency: string;
  guestMessage: string | null;
  hostNote: string | null;
  holdExpiresAt: string | null;
  paidVia: string | null;
  createdAt: string;
  checkInTime: string;
  checkOutTime: string;
  guest: StayParty;
  host: StayParty;
}

export interface StayBlock {
  id: number;
  start: string;
  end: string;
  note: string | null;
}

export interface HostedStay {
  propertyId: number;
  title: string;
  city: string;
  nightlyRate: number;
  currency: string;
  settings: StaySettings;
  blocks: StayBlock[];
}

export interface HostingOverview {
  bookings: StayBooking[];
  stays: HostedStay[];
}
