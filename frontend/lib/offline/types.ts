import type {
  BuyerInterest,
  CreateContactRequest,
  CreateMandateRequest,
  CreateOrgListingRequest,
  CreatePipelineCardRequest,
  InspectionItem,
  PipelineStage,
  PriceOpinion,
  ShowingStatus,
  TitleType,
} from "@/lib/types";

/** A reference to a server record that may not exist on the server yet. */
export type Ref = { id: number } | { clientId: string };

export interface EnquiryPayload {
  channel: string;
  intent?: string;
  name: string;
  phone?: string;
  email?: string;
  message?: string;
  propertyId?: number;
  budgetCents?: number;
  capturedAt: string;
}

export type TitleFields = { titleType: TitleType | null; titleReference: string | null };
export type ConsentFields = { photos: boolean; address: boolean };

/** Every write that can be made offline. Approvals, publishing and sign-off are deliberately absent. */
export type OutboxOp =
  | { kind: "createListing"; clientId: string; payload: CreateOrgListingRequest; label: string }
  | { kind: "setTitle"; listing: Ref; base: TitleFields; mine: TitleFields; label: string }
  | { kind: "setConsent"; listing: Ref; base: ConsentFields; mine: ConsentFields; label: string }
  | { kind: "createMandate"; clientId: string; listing: Ref; payload: CreateMandateRequest; label: string }
  | { kind: "attachMandateDocument"; mandate: Ref; file: Blob; fileName: string; ownerSignedOn: string; label: string }
  | { kind: "captureEnquiry"; clientId: string; payload: EnquiryPayload; label: string }
  | { kind: "createContact"; clientId: string; payload: CreateContactRequest; label: string }
  | { kind: "createPipelineCard"; clientId: string; owner: Ref; payload: Omit<CreatePipelineCardRequest, "ownerContactId">; label: string }
  /** A command: the server re-checks the evidence; if it's refused it goes to the tray, never forced. */
  | { kind: "movePipelineStage"; card: Ref; stage: PipelineStage; lostReason?: string; label: string }
  | { kind: "scheduleViewing"; clientId: string; propertyId: number; contact: Ref; scheduledAt: string; durationMinutes?: number; label: string }
  | { kind: "viewingOutcome"; viewing: Ref; status: ShowingStatus; interest?: BuyerInterest; priceOpinion?: PriceOpinion; feedback?: string; label: string }
  | { kind: "setCampaignTask"; taskId: number; done: boolean; label: string }
  | { kind: "saveInspection"; listing: Ref; baseVersion?: number; items: InspectionItem[]; standSizeM2?: number; floorAreaM2?: number; notes?: string; label: string }
  | { kind: "uploadInspectionPhoto"; clientId: string; listing: Ref; file: Blob; fileName: string; itemKey?: string; caption?: string; label: string };

export interface OutboxEntry {
  seq?: number;
  /** Ops only replay for the user who queued them, never under someone else's session. */
  userId: number;
  orgSlug: string;
  op: OutboxOp;
  createdAt: string;
  attempts: number;
  nextAttemptAt?: number;
  lastError?: string;
}

/** Something that needs a person: a rejected write or a field the server changed first. */
export interface ConflictEntry {
  id?: number;
  userId: number;
  orgSlug: string;
  label: string;
  kind: "rejected" | "field-conflict";
  message: string;
  fields?: { field: string; mine: unknown; theirs: unknown }[];
  createdAt: string;
}
