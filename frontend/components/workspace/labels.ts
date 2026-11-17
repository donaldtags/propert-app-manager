import type { BuyingTimeline, ContactSource, ContactType, Financing, PipelineStage } from "@/lib/types";

export const CONTACT_TYPE_LABEL: Record<ContactType, string> = {
  OWNER: "Owner",
  BUYER: "Buyer",
  TENANT: "Tenant",
  LANDOWNER: "Landowner",
  INVESTOR: "Investor",
  REFERRAL_PARTNER: "Referral partner",
};

export const CONTACT_SOURCE_LABEL: Record<ContactSource, string> = {
  NETWORK: "Network",
  WALK_IN: "Walk-in",
  PORTAL: "Portal",
  INSTAGRAM: "Instagram",
  FACEBOOK: "Facebook",
  WHATSAPP: "WhatsApp",
  REFERRAL: "Referral",
  WEBSITE: "Website",
  SIGNBOARD: "Board",
  OTHER: "Other",
};

export const TIMELINE_LABEL: Record<BuyingTimeline, string> = {
  IMMEDIATELY: "Ready now",
  WITHIN_3_MONTHS: "Within 3 months",
  WITHIN_6_MONTHS: "Within 6 months",
  LATER: "Later",
  JUST_LOOKING: "Just looking",
};

export const FINANCING_LABEL: Record<Financing, string> = {
  CASH: "Cash",
  MORTGAGE_APPROVED: "Mortgage approved",
  MORTGAGE_APPLYING: "Applying for mortgage",
  DIASPORA_TRANSFER: "Diaspora transfer",
  PAYMENT_PLAN: "Payment plan",
  UNKNOWN: "Not sure yet",
};

export const STAGES: { stage: PipelineStage; label: string }[] = [
  { stage: "PROSPECT", label: "Prospect" },
  { stage: "MEETING", label: "Meeting" },
  { stage: "INSPECTION", label: "Inspection" },
  { stage: "MANDATE_SIGNED", label: "Mandate signed" },
  { stage: "MARKETING", label: "Marketing" },
  { stage: "VIEWINGS", label: "Viewings" },
  { stage: "OFFER", label: "Offer" },
  { stage: "CONVEYANCING", label: "With conveyancer" },
  { stage: "COMPLETED", label: "Completed" },
  { stage: "LOST", label: "Lost" },
];

export const STAGE_LABEL = Object.fromEntries(STAGES.map((s) => [s.stage, s.label])) as Record<PipelineStage, string>;
