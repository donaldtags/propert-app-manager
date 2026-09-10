import type { Escrow, Investment, Property } from "./types";

export type JourneyStage =
  | "DISCOVER"
  | "VERIFY"
  | "ANALYSE"
  | "BUY_INVEST"
  | "LEGAL_PAYMENT"
  | "OWN"
  | "MONITOR"
  | "MANAGE"
  | "EARN"
  | "SELL_EXIT";

export const JOURNEY_STAGES: { stage: JourneyStage; label: string }[] = [
  { stage: "DISCOVER", label: "Discover" },
  { stage: "VERIFY", label: "Verify" },
  { stage: "ANALYSE", label: "Analyse" },
  { stage: "BUY_INVEST", label: "Buy / Invest" },
  { stage: "LEGAL_PAYMENT", label: "Legal & Payment" },
  { stage: "OWN", label: "Own" },
  { stage: "MONITOR", label: "Monitor" },
  { stage: "MANAGE", label: "Manage" },
  { stage: "EARN", label: "Earn" },
  { stage: "SELL_EXIT", label: "Sell / Exit" },
];

/**
 * Derives where one owned property sits in the diaspora journey from statuses that
 * already exist elsewhere in the system — there is no dedicated "stage" field on the
 * backend. This is the seam later phases (construction -> Monitor, an explicit sale
 * workflow -> Sell/Exit) extend rather than a page each new feature has to re-derive
 * its own progress logic for.
 */
export function stageForProperty(property: Property, escrow?: Escrow): JourneyStage {
  if (property.status === "SOLD") return "SELL_EXIT";
  if (property.status === "OCCUPIED") return "MANAGE";

  if (escrow) {
    if (escrow.status === "RELEASED") return "OWN";
    if (escrow.status === "CREATED" || escrow.status === "FUNDED" || escrow.status === "DISPUTED") return "LEGAL_PAYMENT";
  }

  if (property.verificationStatus !== "VERIFIED") return "VERIFY";
  return "ANALYSE";
}

export function stageForInvestment(investment: Investment): JourneyStage {
  return investment.status === "EXITED" ? "SELL_EXIT" : "EARN";
}
