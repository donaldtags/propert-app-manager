"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { dashboards } from "@/lib/api";
import type { DiasporaDashboard, Escrow, MaintenanceRequest, Property, Viewing } from "@/lib/types";
import { settingsRoleUrl } from "@/lib/roleGate";
import { stageForProperty } from "@/lib/journeyStage";
import StatusBadge from "@/components/StatusBadge";
import EmptyState from "@/components/EmptyState";
import StatTile from "@/components/StatTile";
import AlertBanner from "@/components/AlertBanner";
import JourneyStepper from "@/components/JourneyStepper";
import ProgressBar from "@/components/ProgressBar";
import {
  Home,
  DollarSign,
  Building2,
  TrendingUp,
  Globe,
  ShieldCheck,
  Clock,
  AlertCircle,
  BadgeCheck,
  HardHat,
  MapPin,
} from "lucide-react";

interface Task {
  key: string;
  label: string;
  sub?: string;
  status: string;
  href: string;
}

export default function DiasporaDashboardPage() {
  const { user, token, loading } = useAuth();
  const router = useRouter();
  const [dashboard, setDashboard] = useState<DiasporaDashboard | null>(null);
  const [dashLoading, setDashLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!loading && (!user || !user.roles?.includes("DIASPORA"))) {
      router.push(user ? settingsRoleUrl("DIASPORA", "the Diaspora Dashboard needs the Diaspora role") : "/login?redirect=/diaspora");
    }
  }, [user, loading, router]);

  useEffect(() => {
    if (user && token && user.roles?.includes("DIASPORA")) {
      dashboards
        .diaspora(user.id, token)
        .then(setDashboard)
        .catch(() => setError("Failed to load dashboard."))
        .finally(() => setDashLoading(false));
    }
  }, [user, token]);

  const ownedProperties = useMemo(() => dashboard?.ownedProperties ?? [], [dashboard]);
  const investments = useMemo(() => dashboard?.investments ?? [], [dashboard]);
  const escrows = useMemo(() => dashboard?.escrows ?? [], [dashboard]);
  const maintenanceRequests = useMemo(() => dashboard?.maintenanceRequests ?? [], [dashboard]);
  const pendingInspections = useMemo(() => dashboard?.pendingInspections ?? [], [dashboard]);
  const constructionProjects = useMemo(() => dashboard?.constructionProjects ?? [], [dashboard]);
  const recentPayments = dashboard?.recentPayments ?? [];

  const escrowForProperty = (property: Property): Escrow | undefined =>
    escrows.find((e) => e.propertyId === property.id);

  const activeInvestmentCount = useMemo(
    () => investments.filter((i) => i.status === "ACTIVE").length,
    [investments]
  );

  const unverifiedProperties = useMemo(
    () => ownedProperties.filter((p) => p.verificationStatus !== "VERIFIED"),
    [ownedProperties]
  );

  const tasks = useMemo<Task[]>(() => {
    const items: Task[] = [];
    unverifiedProperties.forEach((p) =>
      items.push({
        key: `verify-${p.id}`,
        label: p.title,
        sub: "Verification in progress",
        status: p.verificationStatus,
        href: `/properties/${p.id}`,
      })
    );
    escrows
      .filter((e) => e.status === "DISPUTED")
      .forEach((e) =>
        items.push({
          key: `escrow-${e.id}`,
          label: `Escrow #${e.id} disputed`,
          sub: `$${e.amount} ${e.currency}`,
          status: e.status,
          href: "/escrow",
        })
      );
    maintenanceRequests
      .filter((m: MaintenanceRequest) => m.status === "OPEN")
      .forEach((m) =>
        items.push({
          key: `maint-${m.id}`,
          label: m.category,
          sub: m.description,
          status: m.status,
          href: "/maintenance",
        })
      );
    pendingInspections
      .filter((v: Viewing) => v.status === "CONFIRMED")
      .forEach((v) =>
        items.push({
          key: `viewing-${v.id}`,
          label: v.propertyTitle,
          sub: "Inspection confirmed",
          status: v.status,
          href: "/properties",
        })
      );
    constructionProjects
      .filter((p) => p.status === "DELAYED")
      .forEach((p) =>
        items.push({
          key: `construction-${p.id}`,
          label: `${p.title} is delayed`,
          sub: `${p.stage.replaceAll("_", " ")} · ${p.progressPercent}% complete`,
          status: p.status,
          href: `/construction/${p.id}`,
        })
      );
    return items;
  }, [unverifiedProperties, escrows, maintenanceRequests, pendingInspections, constructionProjects]);

  if (loading || dashLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 py-8">
        <div className="h-8 w-48 bg-gray-100 rounded-lg animate-pulse mb-8" />
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-28 bg-gray-100 rounded-2xl animate-pulse" />
          ))}
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="h-52 bg-gray-100 rounded-2xl animate-pulse" />
          ))}
        </div>
      </div>
    );
  }

  if (error && !dashboard) {
    return (
      <div className="text-center py-24 text-gray-500">
        <AlertCircle className="w-12 h-12 mx-auto mb-4 text-red-400" />
        <p className="text-lg font-semibold">{error}</p>
        <Link href="/" className="mt-4 inline-block text-forest-600 hover:underline text-sm">← Home</Link>
      </div>
    );
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">My Diaspora Dashboard</h1>
        <p className="text-gray-500 mt-1 flex items-center gap-1.5">
          <Globe className="w-4 h-4 text-forest-600" />
          Managing property from {user?.diasporaLocation || "abroad"}
        </p>
      </div>

      {error && <AlertBanner variant="error" className="mb-5">{error}</AlertBanner>}

      {/* Stat cards */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
        <StatTile
          icon={DollarSign}
          label="Portfolio Value"
          value={`$${dashboard?.portfolioValue.toLocaleString() ?? 0}`}
          tone="forest"
          emphasis
        />
        <StatTile
          icon={TrendingUp}
          label="Monthly Rental Income"
          value={`$${dashboard?.monthlyRentalIncome.toLocaleString() ?? 0}`}
          tone="gold"
          emphasis
        />
        <StatTile icon={Home} label="Properties Owned" value={ownedProperties.length} tone="forest" />
        <StatTile icon={Building2} label="Active Investments" value={activeInvestmentCount} tone="gold" />
      </div>

      {/* Journey overview */}
      <div className="bg-white border border-gray-200 rounded-2xl shadow-sm p-6 mb-8">
        <h2 className="font-bold text-gray-900 mb-4">My Property Journey</h2>
        {ownedProperties.length === 0 ? (
          <EmptyState
            icon={Home}
            title="No properties yet"
            hint="Find a verified property back home to start your journey"
            action={{ label: "Find Property", href: "/properties" }}
            compact
          />
        ) : (
          <div className="space-y-5">
            {ownedProperties.map((p) => (
              <JourneyStepper key={p.id} label={p.title} stage={stageForProperty(p, escrowForProperty(p))} />
            ))}
          </div>
        )}
      </div>

      {/* Construction */}
      <div className="bg-white border border-gray-200 rounded-2xl shadow-sm p-6 mb-8">
        <div className="flex items-center justify-between mb-4">
          <h2 className="font-bold text-gray-900 flex items-center gap-2">
            <HardHat className="w-5 h-5 text-forest-600" /> Construction
          </h2>
          <Link href="/construction" className="text-xs text-forest-600 hover:underline">
            {constructionProjects.length === 0 ? "Start a project" : "View all"}
          </Link>
        </div>
        {constructionProjects.length === 0 ? (
          <EmptyState icon={HardHat} title="No active construction projects" compact />
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {constructionProjects.map((p) => (
              <Link
                key={p.id}
                href={`/construction/${p.id}`}
                className="block p-4 bg-gray-50 hover:bg-gray-100 rounded-xl transition-colors"
              >
                <div className="flex items-center justify-between mb-1">
                  <p className="text-sm font-semibold text-gray-900">{p.title}</p>
                  <StatusBadge status={p.status} />
                </div>
                <p className="text-xs text-gray-500 flex items-center gap-1 mb-2">
                  <MapPin className="w-3 h-3" /> {p.city}, {p.country}
                </p>
                <div className="flex items-center justify-between text-xs text-gray-500 mb-1">
                  <span>{p.stage.replaceAll("_", " ")}</span>
                  <span className="font-semibold text-gray-900">{p.progressPercent}%</span>
                </div>
                <ProgressBar percent={p.progressPercent} />
                {p.latestUpdateNote && (
                  <p className="text-xs text-gray-500 mt-2 italic truncate">&ldquo;{p.latestUpdateNote}&rdquo;</p>
                )}
              </Link>
            ))}
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
        {/* Tasks requiring attention */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
            <Clock className="w-5 h-5 text-amber-500" /> Tasks Requiring My Attention
          </h2>
          {tasks.length === 0 ? (
            <EmptyState icon={ShieldCheck} title="Nothing needs your attention right now" compact />
          ) : (
            <div className="space-y-3">
              {tasks.map((t) => (
                <Link key={t.key} href={t.href} className="block p-3 bg-gray-50 hover:bg-gray-100 rounded-xl transition-colors">
                  <div className="flex items-center justify-between mb-0.5">
                    <p className="text-sm font-medium text-gray-900">{t.label}</p>
                    <StatusBadge status={t.status} />
                  </div>
                  {t.sub && <p className="text-xs text-gray-500">{t.sub}</p>}
                </Link>
              ))}
            </div>
          )}
        </div>

        {/* Verification */}
        <div className="bg-white border border-gray-200 rounded-2xl p-6 shadow-sm">
          <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
            <ShieldCheck className="w-5 h-5 text-forest-600" /> Verification
          </h2>
          <div className="flex items-center justify-between mb-3 p-3 bg-gray-50 rounded-xl">
            <span className="text-sm text-gray-700 flex items-center gap-1.5">
              {user?.identityVerified && <BadgeCheck className="w-4 h-4 text-forest-600" />}
              My identity
            </span>
            <StatusBadge status={user?.identityVerified ? "VERIFIED" : "PENDING"} />
          </div>
          {unverifiedProperties.length === 0 ? (
            <p className="text-sm text-gray-400">All your properties are fully verified.</p>
          ) : (
            <div className="space-y-2">
              {unverifiedProperties.map((p) => (
                <div key={p.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-xl">
                  <span className="text-sm text-gray-700 truncate">{p.title}</span>
                  <StatusBadge status={p.verificationStatus} />
                </div>
              ))}
            </div>
          )}
          {!user?.identityVerified && (
            <Link href="/verification" className="mt-3 inline-block text-sm text-forest-600 hover:underline">
              Verify your identity →
            </Link>
          )}
        </div>
      </div>

      {/* Recent activity */}
      <div className="bg-white border border-gray-200 rounded-2xl shadow-sm p-6 mb-8">
        <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
          <DollarSign className="w-5 h-5 text-forest-600" /> Recent Activity
        </h2>
        {recentPayments.length === 0 ? (
          <EmptyState icon={DollarSign} title="No payments recorded yet" compact />
        ) : (
          <div className="space-y-3">
            {recentPayments.slice(0, 6).map((p) => (
              <div key={p.id} className="flex items-center justify-between p-3 bg-gray-50 rounded-xl">
                <div>
                  <p className="text-sm font-medium text-gray-900">{p.purpose || "Payment"}</p>
                  <p className="text-xs text-gray-500">${p.amount} {p.currency}</p>
                </div>
                <StatusBadge status={p.status} />
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Quick actions */}
      <div className="bg-forest-50 border border-forest-100 rounded-2xl p-6">
        <h2 className="font-bold text-gray-900 mb-4">Quick Actions</h2>
        <div className="flex flex-wrap gap-3">
          <Link href="/properties" className="bg-forest-600 text-white px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-forest-700 transition-colors">
            Find Property
          </Link>
          <Link href="/investments" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            Invest
          </Link>
          <Link href="/construction" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            Build My Home
          </Link>
          <Link href="/verification" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            Verify My Identity
          </Link>
          <Link href="/services?category=LEGAL" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            Get Legal Help
          </Link>
          <Link href="/ai" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            Ask Assistant
          </Link>
          <Link href="/messages" className="bg-white text-gray-700 border border-gray-200 px-5 py-2.5 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors">
            Messages
          </Link>
        </div>
      </div>
    </div>
  );
}
