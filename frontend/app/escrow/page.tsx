"use client";

import { useEffect, useMemo, useState, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { escrow as escrowApi, properties as propertiesApi } from "@/lib/api";
import type { Escrow, FundingMethod, Property } from "@/lib/types";
import {
  Shield,
  AlertCircle,
  CheckCircle,
  Plus,
  Clock,
  Lock,
  Unlock,
  Ban,
  RotateCcw,
  ArrowRight,
  Landmark,
  Smartphone,
  Globe2,
  CreditCard,
  X,
} from "lucide-react";
import EntityPicker from "@/components/EntityPicker";

const ZIMBABWE_BANKS = [
  "CBZ Bank",
  "Stanbic Bank Zimbabwe",
  "Steward Bank",
  "NMB Bank",
  "ZB Bank",
  "FBC Bank",
  "Standard Chartered Zimbabwe",
  "Ecobank Zimbabwe",
  "POSB",
  "BancABC Zimbabwe",
  "Nedbank Zimbabwe",
];

type ChannelOption = { value: FundingMethod; label: string; needsPhone?: boolean };
type ChannelGroup = { group: string; icon: typeof Landmark; options: ChannelOption[] };

const PAYMENT_CHANNELS: ChannelGroup[] = [
  {
    group: "Bank transfer",
    icon: Landmark,
    options: [{ value: "BANK_TRANSFER", label: "Bank transfer" }],
  },
  {
    group: "Mobile money · Zimbabwe",
    icon: Smartphone,
    options: [
      { value: "ECOCASH", label: "EcoCash", needsPhone: true },
      { value: "ONEMONEY", label: "OneMoney", needsPhone: true },
      { value: "INNBUCKS", label: "Innbucks", needsPhone: true },
    ],
  },
  {
    group: "Payment gateway",
    icon: CreditCard,
    options: [
      { value: "PAYNOW", label: "Paynow" },
      { value: "CARD", label: "Debit / credit card" },
    ],
  },
  {
    group: "SADC mobile money",
    icon: Globe2,
    options: [
      { value: "MPESA", label: "M-Pesa", needsPhone: true },
      { value: "MTN_MOMO", label: "MTN Mobile Money", needsPhone: true },
      { value: "AIRTEL_MONEY", label: "Airtel Money", needsPhone: true },
    ],
  },
  {
    group: "SADC gateway",
    icon: Globe2,
    options: [{ value: "PAYFAST", label: "PayFast (South Africa)" }],
  },
];

const STATUS_META: Record<string, { label: string; color: string; icon: typeof Clock }> = {
  CREATED: { label: "Awaiting funding", color: "bg-amber-100 text-amber-700", icon: Clock },
  FUNDED: { label: "Held in escrow", color: "bg-forest-100 text-forest-700", icon: Lock },
  RELEASED: { label: "Released", color: "bg-forest-100 text-forest-700", icon: Unlock },
  DISPUTED: { label: "Disputed", color: "bg-red-100 text-red-700", icon: AlertCircle },
  REFUNDED: { label: "Refunded", color: "bg-gray-100 text-gray-700", icon: RotateCcw },
  CANCELLED: { label: "Cancelled", color: "bg-gray-100 text-gray-700", icon: Ban },
};

const ACTIVE_STATUSES = new Set(["CREATED", "FUNDED", "DISPUTED"]);

function formatMoney(value: number, currency: string) {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: currency || "USD" }).format(value);
}

function StatusBadge({ status }: { status: string }) {
  const meta = STATUS_META[status] ?? { label: status, color: "bg-gray-100 text-gray-700", icon: Clock };
  const Icon = meta.icon;
  return (
    <span className={`inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-1 rounded-full ${meta.color}`}>
      <Icon className="w-3 h-3" /> {meta.label}
    </span>
  );
}

function EscrowContent() {
  const { user, token, loading } = useAuth();
  const router = useRouter();
  const searchParams = useSearchParams();
  const propertyIdParam = searchParams.get("propertyId");

  const [escrows, setEscrows] = useState<Escrow[]>([]);
  const [escrowLoading, setEscrowLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({
    propertyId: propertyIdParam ? Number(propertyIdParam) : (null as number | null),
    amount: "",
    description: "Security deposit",
  });

  const [allProperties, setAllProperties] = useState<Property[]>([]);
  const [pickerLoading, setPickerLoading] = useState(true);

  type FundStep = "channel" | "details" | "otp" | "processing";
  const [fundingFor, setFundingFor] = useState<number | null>(null);
  const [fundStep, setFundStep] = useState<FundStep>("channel");
  const [fundingMethod, setFundingMethod] = useState<FundingMethod | "">("");
  const [fundingBank, setFundingBank] = useState("");
  const [fundingAccount, setFundingAccount] = useState("");
  const [fundingBankAccount, setFundingBankAccount] = useState("");
  const [fundingBankAccountName, setFundingBankAccountName] = useState("");
  const [cardNumber, setCardNumber] = useState("");
  const [cardExpiry, setCardExpiry] = useState("");
  const [cardCvv, setCardCvv] = useState("");
  const [otpCode, setOtpCode] = useState("");
  const [otpInput, setOtpInput] = useState("");
  const [otpError, setOtpError] = useState("");
  const [processingLabel, setProcessingLabel] = useState("");
  const [funding, setFunding] = useState(false);

  useEffect(() => {
    if (!loading && !user) router.push("/login?redirect=/escrow");
  }, [user, loading, router]);

  useEffect(() => {
    if (user && token) {
      escrowApi.list(user.id, token)
        .then(setEscrows)
        .catch(() => {})
        .finally(() => setEscrowLoading(false));
    }
  }, [user, token]);

  useEffect(() => {
    setPickerLoading(true);
    propertiesApi.list()
      .then(setAllProperties)
      .catch(() => {})
      .finally(() => setPickerLoading(false));
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!user || !token || !form.propertyId) return;
    setError(""); setSuccess("");
    setCreating(true);
    try {
      await escrowApi.create({
        propertyId: form.propertyId,
        amount: Number(form.amount),
        currency: "USD",
        purpose: form.description,
      }, token);
      setSuccess("Escrow created! Your funds will be held securely until you receive the keys.");
      const updated = await escrowApi.list(user.id, token);
      setEscrows(updated);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to create escrow.");
    } finally {
      setCreating(false);
    }
  };

  const handleAction = async (id: number, action: "release" | "dispute") => {
    if (!token) return;
    setError(""); setSuccess("");
    try {
      if (action === "release") {
        const result = await escrowApi.release(id, token);
        setSuccess(
          result.status === "RELEASED"
            ? "Escrow released — second approval received."
            : `Approval recorded (${result.releaseApprovals}/${result.releaseApprovalsRequired}). Waiting on another admin.`
        );
      } else {
        await escrowApi.dispute(id, token);
        setSuccess("Escrow disputed successfully.");
      }
      const updated = await escrowApi.list(user!.id, token);
      setEscrows(updated);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : `Failed to ${action} escrow.`);
    }
  };

  const startFunding = (id: number) => {
    setFundingFor(id);
    setFundStep("channel");
    setFundingMethod("");
    setFundingBank("");
    setFundingAccount("");
    setFundingBankAccount("");
    setFundingBankAccountName("");
    setCardNumber("");
    setCardExpiry("");
    setCardCvv("");
    setOtpCode("");
    setOtpInput("");
    setOtpError("");
    setError("");
  };

  const cancelFunding = () => setFundingFor(null);

  const selectedChannel = PAYMENT_CHANNELS.flatMap((g) => g.options).find((o) => o.value === fundingMethod);

  const buildProviderLabel = (): string => {
    if (fundingMethod === "BANK_TRANSFER") {
      return `${fundingBank} •••${fundingBankAccount.slice(-4)}`;
    }
    if (selectedChannel?.needsPhone) {
      const digits = fundingAccount.replace(/\D/g, "");
      return `${selectedChannel.label} •••${digits.slice(-4)}`;
    }
    if (fundingMethod === "CARD") {
      const digits = cardNumber.replace(/\D/g, "");
      return `Card •••${digits.slice(-4)}`;
    }
    return selectedChannel?.label ?? "";
  };

  const handleFund = async (id: number) => {
    if (!token || !user || !fundingMethod) return;
    setError(""); setSuccess("");
    setFunding(true);
    try {
      const provider = buildProviderLabel();
      await escrowApi.fund(id, { method: fundingMethod, provider }, token);
      setSuccess("Escrow funded successfully — funds are now held securely.");
      setFundingFor(null);
      const updated = await escrowApi.list(user.id, token);
      setEscrows(updated);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to fund escrow.");
      setFundStep("details");
    } finally {
      setFunding(false);
    }
  };

  const proceedToProcessing = async (id: number, label: string, delayMs = 1400) => {
    setFundStep("processing");
    setProcessingLabel(label);
    await new Promise((resolve) => setTimeout(resolve, delayMs));
    await handleFund(id);
  };

  const handleContinueFromDetails = (id: number) => {
    setOtpError("");
    if (fundingMethod === "BANK_TRANSFER") {
      if (!fundingBank) { setError("Select a bank to continue."); return; }
      if (fundingBankAccount.trim().length < 6) { setError("Enter a valid account number."); return; }
      if (!fundingBankAccountName.trim()) { setError("Enter the account holder name."); return; }
      setError("");
      proceedToProcessing(id, `Verifying with ${fundingBank}…`);
      return;
    }
    if (selectedChannel?.needsPhone) {
      const digits = fundingAccount.replace(/\D/g, "");
      if (digits.length < 9) { setError("Enter a valid phone number."); return; }
      setError("");
      setOtpCode(String(Math.floor(100000 + Math.random() * 900000)));
      setOtpInput("");
      setFundStep("otp");
      return;
    }
    if (fundingMethod === "CARD") {
      const digits = cardNumber.replace(/\D/g, "");
      if (digits.length < 12) { setError("Enter a valid card number."); return; }
      if (!cardExpiry.trim() || !cardCvv.trim()) { setError("Enter card expiry and CVV."); return; }
      setError("");
      proceedToProcessing(id, "Authorizing card payment…");
      return;
    }
    setError("");
    proceedToProcessing(id, `Redirecting to ${selectedChannel?.label}…`);
  };

  const handleVerifyOtp = (id: number) => {
    if (otpInput.trim() !== otpCode) {
      setOtpError("Incorrect code. Try again.");
      return;
    }
    setOtpError("");
    proceedToProcessing(id, `Confirming with ${selectedChannel?.label}…`);
  };

  const resendOtp = () => {
    setOtpCode(String(Math.floor(100000 + Math.random() * 900000)));
    setOtpInput("");
    setOtpError("");
  };

  const propertyById = useMemo(
    () => new Map(allProperties.map((p) => [p.id, p] as const)),
    [allProperties]
  );

  const sortedEscrows = useMemo(
    () => [...escrows].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()),
    [escrows]
  );
  const activeEscrows = sortedEscrows.filter((e) => ACTIVE_STATUSES.has(e.status));
  const historyEscrows = sortedEscrows.filter((e) => !ACTIVE_STATUSES.has(e.status));
  const totalHeld = activeEscrows
    .filter((e) => e.status === "FUNDED")
    .reduce((sum, e) => sum + e.amount, 0);

  if (loading) return null;

  const renderEscrowCard = (e: Escrow) => {
    const property = propertyById.get(e.propertyId);
    const isFunding = fundingFor === e.id;
    return (
      <div key={e.id} className="bg-white border border-gray-200 rounded-2xl shadow-sm overflow-hidden">
        <div className="p-5 flex flex-wrap items-center justify-between gap-4">
          <div className="min-w-0">
            <div className="flex items-center gap-2 mb-1 flex-wrap">
              <p className="font-semibold text-gray-900">Escrow #{e.id}</p>
              <StatusBadge status={e.status} />
            </div>
            <p className="text-sm text-gray-700 font-medium truncate">
              {property ? property.title : `Property #${e.propertyId}`}
            </p>
            {property && <p className="text-xs text-gray-400">{property.suburb}, {property.city}</p>}
            <p className="text-sm text-gray-500 mt-1">{e.purpose || "Deposit"}</p>
            <p className="text-xl font-bold text-gray-900 mt-1">{formatMoney(e.amount, e.currency)}</p>
            <p className="text-xs text-gray-400">Created {new Date(e.createdAt).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" })}</p>
            {e.fundingProvider && (
              <p className="text-xs text-forest-600 font-medium mt-1">
                {e.status === "FUNDED" ? "Funded" : "Paid"} via {e.fundingProvider}
              </p>
            )}
          </div>
          <div className="flex gap-2 flex-wrap">
            {e.status === "CREATED" && !isFunding && (
              <button
                onClick={() => startFunding(e.id)}
                className="bg-forest-600 hover:bg-forest-700 text-white text-xs font-semibold px-4 py-2 rounded-xl transition-colors"
              >
                Fund now
              </button>
            )}
            {e.status === "FUNDED" && (
              <>
                {user?.roles?.includes("ADMIN") ? (
                  <button
                    onClick={() => handleAction(e.id, "release")}
                    className="bg-forest-600 hover:bg-forest-700 text-white text-xs font-semibold px-4 py-2 rounded-xl transition-colors"
                  >
                    Approve release {e.releaseApprovals > 0 ? `(${e.releaseApprovals}/${e.releaseApprovalsRequired})` : ""}
                  </button>
                ) : (
                  <span className="text-xs text-gray-400 self-center">
                    {e.releaseApprovals > 0
                      ? `Release pending — ${e.releaseApprovals}/${e.releaseApprovalsRequired} admin approvals`
                      : "Awaiting PrimeNest admin release approval"}
                  </span>
                )}
                <button
                  onClick={() => handleAction(e.id, "dispute")}
                  className="bg-red-50 hover:bg-red-100 text-red-700 text-xs font-semibold px-4 py-2 rounded-xl transition-colors"
                >
                  Raise dispute
                </button>
              </>
            )}
          </div>
        </div>

        {isFunding && (
          <div className="border-t border-gray-100 bg-gray-50 p-5">
            <div className="flex items-center justify-between mb-3">
              <p className="text-sm font-semibold text-gray-900">
                {fundStep === "channel" && "Choose how to fund this escrow"}
                {fundStep === "details" && `Enter your ${selectedChannel?.label ?? "payment"} details`}
                {fundStep === "otp" && "Verify it's you"}
                {fundStep === "processing" && "Processing payment"}
              </p>
              {fundStep !== "processing" && (
                <button onClick={cancelFunding} className="text-gray-400 hover:text-gray-600">
                  <X className="w-4 h-4" />
                </button>
              )}
            </div>

            {fundStep === "channel" && (
              <>
                <div className="space-y-3 mb-4">
                  {PAYMENT_CHANNELS.map((group) => (
                    <div key={group.group}>
                      <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wide mb-1.5 flex items-center gap-1.5">
                        <group.icon className="w-3 h-3" /> {group.group}
                      </p>
                      <div className="flex flex-wrap gap-2">
                        {group.options.map((opt) => (
                          <button
                            key={opt.value}
                            onClick={() => setFundingMethod(opt.value)}
                            className={`text-xs font-medium px-3 py-1.5 rounded-full border transition-colors ${
                              fundingMethod === opt.value
                                ? "bg-forest-600 border-forest-600 text-white"
                                : "bg-white border-gray-200 text-gray-600 hover:border-forest-300"
                            }`}
                          >
                            {opt.label}
                          </button>
                        ))}
                      </div>
                    </div>
                  ))}
                </div>
                <button
                  onClick={() => fundingMethod && setFundStep("details")}
                  disabled={!fundingMethod}
                  className="bg-forest-600 hover:bg-forest-700 disabled:opacity-50 text-white text-sm font-semibold px-5 py-2.5 rounded-xl transition-colors inline-flex items-center gap-2"
                >
                  Continue <ArrowRight className="w-4 h-4" />
                </button>
              </>
            )}

            {fundStep === "details" && (
              <div className="space-y-4">
                {fundingMethod === "BANK_TRANSFER" && (
                  <>
                    <div>
                      <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Select your bank</label>
                      <select
                        value={fundingBank}
                        onChange={(ev) => setFundingBank(ev.target.value)}
                        className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100 bg-white"
                      >
                        <option value="">Choose a bank…</option>
                        {ZIMBABWE_BANKS.map((bank) => (
                          <option key={bank} value={bank}>{bank}</option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Account number</label>
                      <input
                        value={fundingBankAccount}
                        onChange={(ev) => setFundingBankAccount(ev.target.value.replace(/\D/g, ""))}
                        placeholder="e.g. 0123456789"
                        className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100 bg-white"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Account holder name</label>
                      <input
                        value={fundingBankAccountName}
                        onChange={(ev) => setFundingBankAccountName(ev.target.value)}
                        placeholder="As it appears on your bank account"
                        className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100 bg-white"
                      />
                    </div>
                  </>
                )}

                {selectedChannel?.needsPhone && (
                  <div>
                    <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">
                      {selectedChannel.label} number
                    </label>
                    <input
                      type="tel"
                      value={fundingAccount}
                      onChange={(ev) => setFundingAccount(ev.target.value)}
                      placeholder="e.g. 0771 234 567"
                      className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100 bg-white"
                    />
                  </div>
                )}

                {fundingMethod === "CARD" && (
                  <>
                    <div>
                      <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Card number</label>
                      <input
                        value={cardNumber}
                        onChange={(ev) => setCardNumber(ev.target.value.replace(/[^\d\s]/g, "").slice(0, 19))}
                        placeholder="4242 4242 4242 4242"
                        className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100 bg-white"
                      />
                    </div>
                    <div className="flex gap-3">
                      <div className="flex-1">
                        <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Expiry</label>
                        <input
                          value={cardExpiry}
                          onChange={(ev) => setCardExpiry(ev.target.value.slice(0, 5))}
                          placeholder="MM/YY"
                          className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100 bg-white"
                        />
                      </div>
                      <div className="w-24">
                        <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">CVV</label>
                        <input
                          value={cardCvv}
                          onChange={(ev) => setCardCvv(ev.target.value.replace(/\D/g, "").slice(0, 4))}
                          placeholder="123"
                          className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100 bg-white"
                        />
                      </div>
                    </div>
                  </>
                )}

                {(fundingMethod === "PAYNOW" || fundingMethod === "PAYFAST") && (
                  <p className="text-sm text-gray-500 bg-white border border-gray-200 rounded-xl p-3">
                    You&apos;ll be redirected to {selectedChannel?.label} to complete this payment securely.
                  </p>
                )}

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setFundStep("channel")}
                    className="text-sm font-medium text-gray-500 hover:text-gray-700 px-3 py-2.5"
                  >
                    Back
                  </button>
                  <button
                    onClick={() => handleContinueFromDetails(e.id)}
                    className="bg-forest-600 hover:bg-forest-700 text-white text-sm font-semibold px-5 py-2.5 rounded-xl transition-colors inline-flex items-center gap-2"
                  >
                    Continue <ArrowRight className="w-4 h-4" />
                  </button>
                </div>
              </div>
            )}

            {fundStep === "otp" && (
              <div className="space-y-3">
                <p className="text-sm text-gray-600">
                  Enter the verification code sent to <strong>{fundingAccount}</strong>.
                </p>
                <div className="bg-amber-50 border border-amber-200 text-amber-800 text-xs rounded-lg px-3 py-2">
                  Demo mode — no real SMS is sent. Your code is <span className="font-mono font-semibold">{otpCode}</span>.
                </div>
                <input
                  value={otpInput}
                  onChange={(ev) => setOtpInput(ev.target.value.replace(/\D/g, "").slice(0, 6))}
                  placeholder="6-digit code"
                  className="w-40 text-center tracking-widest font-mono text-lg border border-gray-200 rounded-xl px-4 py-2.5 outline-none focus:border-forest-500 bg-white"
                />
                {otpError && <p className="text-xs text-red-600">{otpError}</p>}
                <div className="flex items-center gap-3">
                  <button
                    onClick={() => handleVerifyOtp(e.id)}
                    disabled={otpInput.length < 6}
                    className="bg-forest-600 hover:bg-forest-700 disabled:opacity-50 text-white text-sm font-semibold px-5 py-2.5 rounded-xl transition-colors"
                  >
                    Verify & continue
                  </button>
                  <button onClick={resendOtp} className="text-xs text-forest-600 hover:text-forest-700 font-medium">
                    Resend code
                  </button>
                  <button onClick={() => setFundStep("details")} className="text-sm text-gray-500 hover:text-gray-700">
                    Back
                  </button>
                </div>
              </div>
            )}

            {fundStep === "processing" && (
              <div className="flex flex-col items-center py-6 gap-3">
                <div className="w-8 h-8 border-2 border-forest-600 border-t-transparent rounded-full animate-spin" />
                <p className="text-sm text-gray-600">{processingLabel}</p>
                <p className="text-xs text-gray-400">Funding {formatMoney(e.amount, e.currency)}</p>
              </div>
            )}
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
          <Shield className="w-7 h-7 text-forest-600" /> Escrow Transactions
        </h1>
        <p className="text-gray-500 mt-1">Your deposits are held securely until both sides confirm the deal is done</p>
      </div>

      {/* How it works */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-6">
        {[
          { icon: Plus, title: "1. Create & fund", desc: "Start an escrow for a property and pay the agreed amount." },
          { icon: Lock, title: "2. Held securely", desc: "PrimeNest holds the funds — neither side can touch them alone." },
          { icon: Unlock, title: "3. Released on agreement", desc: "Funds are released once both parties confirm, or refunded if disputed." },
        ].map((step, i) => (
          <div key={i} className="bg-forest-50/60 border border-forest-100 rounded-xl p-4">
            <step.icon className="w-4 h-4 text-forest-600 mb-2" />
            <p className="text-sm font-semibold text-gray-900">{step.title}</p>
            <p className="text-xs text-gray-500 mt-0.5">{step.desc}</p>
          </div>
        ))}
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" /> {error}
        </div>
      )}
      {success && (
        <div className="bg-forest-50 border border-forest-200 text-forest-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <CheckCircle className="w-4 h-4 shrink-0" /> {success}
        </div>
      )}

      {/* Summary */}
      {!escrowLoading && escrows.length > 0 && (
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 mb-6">
          <div className="bg-white border border-gray-200 rounded-xl p-4">
            <p className="text-xs text-gray-500">Currently held</p>
            <p className="text-lg font-bold text-gray-900">{formatMoney(totalHeld, "USD")}</p>
          </div>
          <div className="bg-white border border-gray-200 rounded-xl p-4">
            <p className="text-xs text-gray-500">Active escrows</p>
            <p className="text-lg font-bold text-gray-900">{activeEscrows.length}</p>
          </div>
          <div className="bg-white border border-gray-200 rounded-xl p-4">
            <p className="text-xs text-gray-500">Completed</p>
            <p className="text-lg font-bold text-gray-900">{historyEscrows.length}</p>
          </div>
        </div>
      )}

      {/* Create form */}
      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm mb-8">
        <h2 className="font-bold text-gray-900 mb-1 flex items-center gap-2">
          <Plus className="w-5 h-5 text-forest-600" /> Create New Escrow
        </h2>
        <p className="text-xs text-gray-500 mb-4">Typically used for security deposits or purchase deposits before keys change hands.</p>
        <form onSubmit={handleCreate} className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <EntityPicker
            label="Property"
            loading={pickerLoading}
            options={allProperties.map((p) => ({ id: p.id, label: p.title, sublabel: `${p.suburb}, ${p.city}` }))}
            value={form.propertyId}
            onChange={(id) => setForm((f) => ({ ...f, propertyId: id }))}
            placeholder="Choose a property"
            emptyMessage="No properties found"
            required
          />
          <div>
            <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Amount (USD)</label>
            <input
              type="number"
              value={form.amount}
              onChange={(e) => setForm((f) => ({ ...f, amount: e.target.value }))}
              required
              min={1}
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100"
              placeholder="e.g. 550"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">Description</label>
            <input
              type="text"
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100"
            />
          </div>
          <div className="sm:col-span-3">
            <button
              type="submit"
              disabled={creating}
              className="bg-forest-600 hover:bg-forest-700 disabled:opacity-60 text-white font-semibold px-6 py-2.5 rounded-xl text-sm transition-colors inline-flex items-center gap-2"
            >
              {creating ? "Creating..." : "Create Escrow"} {!creating && <ArrowRight className="w-4 h-4" />}
            </button>
          </div>
        </form>
      </div>

      {/* Escrow list */}
      {escrowLoading ? (
        <div className="space-y-4">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="h-24 bg-gray-100 rounded-2xl animate-pulse" />
          ))}
        </div>
      ) : escrows.length === 0 ? (
        <div className="text-center py-16 text-gray-500 bg-white border border-gray-200 rounded-2xl">
          <Shield className="w-12 h-12 mx-auto mb-4 opacity-30" />
          <p className="text-lg font-medium text-gray-700">No escrow transactions yet</p>
          <p className="text-sm text-gray-400 mt-1">Create one above once you agree a deposit with a landlord, tenant, or buyer.</p>
        </div>
      ) : (
        <div className="space-y-8">
          {activeEscrows.length > 0 && (
            <div>
              <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-3">Active</h2>
              <div className="space-y-4">{activeEscrows.map(renderEscrowCard)}</div>
            </div>
          )}
          {historyEscrows.length > 0 && (
            <div>
              <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-3">History</h2>
              <div className="space-y-4">{historyEscrows.map(renderEscrowCard)}</div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default function EscrowPage() {
  return (
    <Suspense>
      <EscrowContent />
    </Suspense>
  );
}
