import type { MandateStatus, TitleType, TitleVerificationStatus } from "@/lib/types";

export const STATUS_LABEL: Record<string, string> = {
  DRAFT: "Draft (not public)",
  AVAILABLE: "Live",
  UNDER_OFFER: "Under offer",
  OCCUPIED: "Let",
  REPORTED_SOLD: "Sold (reported)",
  WITHDRAWN: "Withdrawn",
  EXPIRED: "Expired (mandate ended)",
  INACTIVE: "Inactive",
};

export const CATEGORY_OPTIONS: { value: import("@/lib/types").PropertyCategory; label: string }[] = [
  { value: "HOUSE", label: "House" },
  { value: "FLAT", label: "Flat / apartment" },
  { value: "TOWNHOUSE", label: "Townhouse / cluster" },
  { value: "COTTAGE", label: "Cottage" },
  { value: "ROOM", label: "Room" },
  { value: "STAND", label: "Stand / land" },
  { value: "COMMERCIAL", label: "Commercial" },
  { value: "INDUSTRIAL", label: "Industrial" },
  { value: "FARM", label: "Farm / plot" },
  { value: "OTHER", label: "Other" },
];

export const TITLE_TYPES: { value: TitleType; label: string }[] = [
  { value: "TITLE_DEED", label: "Title deed" },
  { value: "SECTIONAL_TITLE", label: "Sectional title" },
  { value: "CESSION", label: "Cession" },
];

export const TITLE_VERIFICATION_LABEL: Record<TitleVerificationStatus, string> = {
  NOT_STARTED: "Not started",
  IN_PROGRESS: "In progress",
  VERIFIED: "Verified",
  FAILED: "Failed",
};

export const MANDATE_STATUS_LABEL: Record<MandateStatus, string> = {
  DRAFT: "Awaiting owner signature",
  AWAITING_SIGN_OFF: "Awaiting PREA sign-off",
  ACTIVE: "In force",
  EXPIRED: "Expired",
  CANCELLED: "Cancelled",
  SUPERSEDED: "Superseded",
};
