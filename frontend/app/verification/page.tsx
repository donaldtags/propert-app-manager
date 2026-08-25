"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { kyc } from "@/lib/api";
import {
  ShieldCheck,
  Upload,
  Camera,
  User as UserIcon,
  CheckCircle,
  XCircle,
  Clock,
  AlertCircle,
  ArrowRight,
  ArrowLeft,
} from "lucide-react";
import type { IdDocumentType, KycDocumentType, KycSubmission } from "@/lib/types";

const ID_TYPE_LABELS: Record<IdDocumentType, string> = {
  NATIONAL_ID: "National ID",
  PASSPORT: "Passport",
  DRIVERS_LICENSE: "Driver's Licence",
};

type UploadSlot = { type: KycDocumentType; label: string; hint: string; file: File | null };

export default function VerificationPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();

  const [submissions, setSubmissions] = useState<KycSubmission[] | null>(null);
  const [loadingSubmissions, setLoadingSubmissions] = useState(true);
  const [showWizard, setShowWizard] = useState(false);

  const [step, setStep] = useState(0);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const [form, setForm] = useState({
    legalFullName: "",
    dateOfBirth: "",
    nationalIdNumber: "",
    idDocumentType: "NATIONAL_ID" as IdDocumentType,
  });

  const [idSlots, setIdSlots] = useState<UploadSlot[]>([
    { type: "ID_FRONT", label: "ID / Licence Front", hint: "Clear photo of the front side", file: null },
    { type: "ID_BACK", label: "ID / Licence Back", hint: "Clear photo of the back side", file: null },
  ]);
  const [selfieSlots, setSelfieSlots] = useState<UploadSlot[]>([
    { type: "SELFIE", label: "Live Selfie", hint: "A clear photo of your face, no sunglasses or masks", file: null },
    { type: "SELFIE_WITH_ID", label: "Selfie Holding Your ID", hint: "Hold your ID document next to your face", file: null },
  ]);

  useEffect(() => {
    if (!loading && !user) router.push("/login?redirect=/verification");
  }, [user, loading, router]);

  useEffect(() => {
    if (!user || !token) return;
    kyc
      .myList(token)
      .then(setSubmissions)
      .catch(() => setSubmissions([]))
      .finally(() => setLoadingSubmissions(false));
  }, [user, token]);

  useEffect(() => {
    if (form.idDocumentType === "PASSPORT") {
      setIdSlots([{ type: "PASSPORT", label: "Passport Photo Page", hint: "Clear photo of the main passport page", file: null }]);
    } else {
      setIdSlots([
        { type: "ID_FRONT", label: "ID / Licence Front", hint: "Clear photo of the front side", file: null },
        { type: "ID_BACK", label: "ID / Licence Back", hint: "Clear photo of the back side", file: null },
      ]);
    }
  }, [form.idDocumentType]);

  const latest = submissions && submissions.length > 0 ? submissions[0] : null;

  const handleSubmit = async () => {
    if (!user || !token) return;
    setError("");
    const allSlots = [...idSlots, ...selfieSlots];
    if (allSlots.some((s) => !s.file)) {
      setError("Please upload all required documents before submitting.");
      return;
    }
    setSubmitting(true);
    try {
      const formData = new FormData();
      formData.append("legalFullName", form.legalFullName);
      formData.append("dateOfBirth", form.dateOfBirth);
      formData.append("nationalIdNumber", form.nationalIdNumber);
      formData.append("idDocumentType", form.idDocumentType);
      allSlots.forEach((slot) => {
        formData.append("files", slot.file as File);
        formData.append("documentTypes", slot.type);
      });
      const created = await kyc.submit(formData, token);
      setSubmissions([created, ...(submissions ?? [])]);
      setShowWizard(false);
      setStep(0);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to submit verification request.");
    } finally {
      setSubmitting(false);
    }
  };

  if (loading || !user || loadingSubmissions) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-12 space-y-4">
        <div className="h-24 bg-gray-100 rounded-2xl animate-pulse" />
        <div className="h-64 bg-gray-100 rounded-2xl animate-pulse" />
      </div>
    );
  }

  if (latest && !showWizard) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-12">
        <div className="text-center mb-6">
          <ShieldCheck className="w-10 h-10 text-forest-600 mx-auto mb-2" />
          <h1 className="text-2xl font-bold text-gray-900">Identity Verification</h1>
        </div>

        {latest.status === "PENDING" && (
          <div className="bg-amber-50 border border-amber-200 rounded-2xl p-6 text-center">
            <Clock className="w-8 h-8 text-amber-600 mx-auto mb-2" />
            <p className="font-semibold text-amber-800">Your verification is under review</p>
            <p className="text-sm text-amber-700 mt-1">
              Submitted on {new Date(latest.submittedAt).toLocaleDateString()}. We&apos;ll notify you once an admin
              has reviewed your documents.
            </p>
          </div>
        )}

        {latest.status === "APPROVED" && (
          <div className="bg-forest-50 border border-forest-200 rounded-2xl p-6 text-center">
            <CheckCircle className="w-8 h-8 text-forest-600 mx-auto mb-2" />
            <p className="font-semibold text-forest-800">You&apos;re verified!</p>
            <p className="text-sm text-forest-700 mt-1">
              Your identity and face were verified on {latest.reviewedAt && new Date(latest.reviewedAt).toLocaleDateString()}.
            </p>
            <div className="flex justify-center gap-2 mt-4">
              <span className="flex items-center gap-1 bg-white text-forest-700 text-xs px-3 py-1 rounded-full border border-forest-200">
                <ShieldCheck className="w-3 h-3" /> Identity Verified
              </span>
              <span className="flex items-center gap-1 bg-white text-forest-700 text-xs px-3 py-1 rounded-full border border-forest-200">
                <Camera className="w-3 h-3" /> Face Verified
              </span>
            </div>
          </div>
        )}

        {latest.status === "REJECTED" && (
          <div className="bg-red-50 border border-red-200 rounded-2xl p-6 text-center">
            <XCircle className="w-8 h-8 text-red-600 mx-auto mb-2" />
            <p className="font-semibold text-red-800">Your submission was rejected</p>
            {latest.reviewNote && <p className="text-sm text-red-700 mt-1">Reason: {latest.reviewNote}</p>}
            <button
              onClick={() => setShowWizard(true)}
              className="mt-4 bg-forest-600 hover:bg-forest-700 text-white text-sm font-semibold px-5 py-2.5 rounded-xl"
            >
              Resubmit Verification
            </button>
          </div>
        )}
      </div>
    );
  }

  const steps = ["Personal Info", "Identity Document", "Selfie", "Selfie with ID", "Review & Submit"];

  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      <div className="text-center mb-6">
        <ShieldCheck className="w-10 h-10 text-forest-600 mx-auto mb-2" />
        <h1 className="text-2xl font-bold text-gray-900">Verify Your Identity</h1>
        <p className="text-gray-500 text-sm mt-1">
          Complete this guided process to unlock listing, applying, and receiving escrow funds on PrimeNest.
        </p>
      </div>

      {/* Step indicator */}
      <div className="flex items-center justify-between mb-8">
        {steps.map((label, index) => (
          <div key={label} className="flex-1 flex items-center">
            <div className="flex flex-col items-center flex-1">
              <div
                className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold ${
                  index < step
                    ? "bg-forest-600 text-white"
                    : index === step
                    ? "bg-forest-600 text-white"
                    : "bg-gray-100 text-gray-400"
                }`}
              >
                {index < step ? <CheckCircle className="w-4 h-4" /> : index + 1}
              </div>
              <span className="text-[10px] text-gray-500 mt-1 text-center hidden sm:block">{label}</span>
            </div>
            {index < steps.length - 1 && <div className={`h-0.5 flex-1 ${index < step ? "bg-forest-600" : "bg-gray-100"}`} />}
          </div>
        ))}
      </div>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 text-sm px-4 py-3 rounded-xl mb-5 flex items-center gap-2">
          <AlertCircle className="w-4 h-4 shrink-0" /> {error}
        </div>
      )}

      <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
        {step === 0 && (
          <div className="space-y-4">
            <h2 className="font-bold text-gray-900 flex items-center gap-2">
              <UserIcon className="w-5 h-5 text-forest-600" /> Personal Information
            </h2>
            <div>
              <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">
                Full Legal Name
              </label>
              <input
                type="text"
                value={form.legalFullName}
                onChange={(e) => setForm((f) => ({ ...f, legalFullName: e.target.value }))}
                className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">
                Date of Birth
              </label>
              <input
                type="date"
                value={form.dateOfBirth}
                onChange={(e) => setForm((f) => ({ ...f, dateOfBirth: e.target.value }))}
                className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">
                National ID Number
              </label>
              <input
                type="text"
                value={form.nationalIdNumber}
                onChange={(e) => setForm((f) => ({ ...f, nationalIdNumber: e.target.value }))}
                className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-500 uppercase tracking-wide mb-1.5">
                Document Type
              </label>
              <select
                value={form.idDocumentType}
                onChange={(e) => setForm((f) => ({ ...f, idDocumentType: e.target.value as IdDocumentType }))}
                className="w-full border border-gray-200 rounded-xl px-4 py-2.5 text-sm outline-none focus:border-forest-500 focus:ring-2 focus:ring-forest-100"
              >
                {Object.entries(ID_TYPE_LABELS).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
          </div>
        )}

        {step === 1 && (
          <UploadStep
            title="Identity Document"
            icon={<Upload className="w-5 h-5 text-forest-600" />}
            slots={idSlots}
            onFileChange={(type, file) =>
              setIdSlots((slots) => slots.map((s) => (s.type === type ? { ...s, file } : s)))
            }
          />
        )}

        {step === 2 && (
          <UploadStep
            title="Live Selfie"
            icon={<Camera className="w-5 h-5 text-forest-600" />}
            slots={selfieSlots.filter((s) => s.type === "SELFIE")}
            onFileChange={(type, file) =>
              setSelfieSlots((slots) => slots.map((s) => (s.type === type ? { ...s, file } : s)))
            }
          />
        )}

        {step === 3 && (
          <UploadStep
            title="Selfie Holding Your ID"
            icon={<Camera className="w-5 h-5 text-forest-600" />}
            slots={selfieSlots.filter((s) => s.type === "SELFIE_WITH_ID")}
            onFileChange={(type, file) =>
              setSelfieSlots((slots) => slots.map((s) => (s.type === type ? { ...s, file } : s)))
            }
          />
        )}

        {step === 4 && (
          <div className="space-y-4">
            <h2 className="font-bold text-gray-900 flex items-center gap-2">
              <CheckCircle className="w-5 h-5 text-forest-600" /> Review &amp; Submit
            </h2>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between py-2 border-b border-gray-100">
                <span className="text-gray-500">Full Legal Name</span>
                <span className="text-gray-900 font-medium">{form.legalFullName || "—"}</span>
              </div>
              <div className="flex justify-between py-2 border-b border-gray-100">
                <span className="text-gray-500">Date of Birth</span>
                <span className="text-gray-900 font-medium">{form.dateOfBirth || "—"}</span>
              </div>
              <div className="flex justify-between py-2 border-b border-gray-100">
                <span className="text-gray-500">National ID Number</span>
                <span className="text-gray-900 font-medium">{form.nationalIdNumber || "—"}</span>
              </div>
              <div className="flex justify-between py-2 border-b border-gray-100">
                <span className="text-gray-500">Document Type</span>
                <span className="text-gray-900 font-medium">{ID_TYPE_LABELS[form.idDocumentType]}</span>
              </div>
              {[...idSlots, ...selfieSlots].map((slot) => (
                <div key={slot.type} className="flex justify-between py-2 border-b border-gray-100">
                  <span className="text-gray-500">{slot.label}</span>
                  <span className={slot.file ? "text-forest-600 font-medium" : "text-red-500 font-medium"}>
                    {slot.file ? slot.file.name : "Missing"}
                  </span>
                </div>
              ))}
            </div>
            <p className="text-xs text-gray-500">
              Your documents are reviewed manually by a PrimeNest administrator and are only accessible to
              authorized reviewers.
            </p>
          </div>
        )}

        {/* Navigation */}
        <div className="flex justify-between mt-6 pt-6 border-t border-gray-100">
          <button
            onClick={() => setStep((s) => Math.max(0, s - 1))}
            disabled={step === 0}
            className="flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 disabled:opacity-40 px-3 py-2"
          >
            <ArrowLeft className="w-4 h-4" /> Back
          </button>
          {step < steps.length - 1 ? (
            <button
              onClick={() => setStep((s) => Math.min(steps.length - 1, s + 1))}
              className="flex items-center gap-1 text-sm bg-forest-600 hover:bg-forest-700 text-white px-4 py-2 rounded-xl font-semibold"
            >
              Next <ArrowRight className="w-4 h-4" />
            </button>
          ) : (
            <button
              onClick={handleSubmit}
              disabled={submitting}
              className="flex items-center gap-1 text-sm bg-forest-600 hover:bg-forest-700 disabled:opacity-60 text-white px-4 py-2 rounded-xl font-semibold"
            >
              {submitting ? "Submitting..." : "Submit for Review"}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

function UploadStep({
  title,
  icon,
  slots,
  onFileChange,
}: {
  title: string;
  icon: React.ReactNode;
  slots: UploadSlot[];
  onFileChange: (type: KycDocumentType, file: File | null) => void;
}) {
  return (
    <div className="space-y-4">
      <h2 className="font-bold text-gray-900 flex items-center gap-2">
        {icon} {title}
      </h2>
      {slots.map((slot) => (
        <div key={slot.type} className="border border-dashed border-gray-300 rounded-xl p-4">
          <p className="text-sm font-medium text-gray-900">{slot.label}</p>
          <p className="text-xs text-gray-500 mb-3">{slot.hint}</p>
          <input
            type="file"
            accept="image/png,image/jpeg,application/pdf"
            onChange={(e) => onFileChange(slot.type, e.target.files?.[0] ?? null)}
            className="text-sm text-gray-600"
          />
          {slot.file && <p className="text-xs text-forest-600 mt-2">Selected: {slot.file.name}</p>}
        </div>
      ))}
    </div>
  );
}
