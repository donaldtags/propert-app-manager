import Link from "next/link";
import { ShieldAlert } from "lucide-react";
import { settingsRoleUrl } from "@/lib/roleGate";
import type { UserRole } from "@/lib/types";

export default function RolePrompt({ roles, reason }: { roles: UserRole | UserRole[]; reason: string }) {
  const roleList = Array.isArray(roles) ? roles : [roles];
  return (
    <div className="bg-amber-50 border border-amber-200 rounded-xl p-3 text-sm text-amber-800 flex items-start gap-2">
      <ShieldAlert className="w-4 h-4 shrink-0 mt-0.5" />
      <div>
        <p>You need the {roleList.join(" or ")} role to do this.</p>
        <Link href={settingsRoleUrl(roleList, reason)} className="font-semibold underline">
          Add it in Settings →
        </Link>
      </div>
    </div>
  );
}
