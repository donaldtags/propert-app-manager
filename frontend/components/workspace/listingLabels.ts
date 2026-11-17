import type { MandateStatus, TitleType, TitleVerificationStatus } from "@/lib/types";

export const STATUS_LABEL: Record<string, string> = {
  DRAFT: "Draft (not public)",
  AVAILABLE: "Live",
  RESERVED: "Reserved",
  OCCUPIED: "Let",
  SOLD: "Sold",
  INACTIVE: "Inactive",
};

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
