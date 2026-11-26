"use client";

import { useEffect } from "react";
import { useParams, useRouter } from "next/navigation";
import PageLoader from "@/components/PageLoader";
import { cleanReferralCode } from "@/lib/referralCode";

/** A friend's invitation link (/r/CODE): go to sign-up with the code filled in. */
export default function ReferralLinkPage() {
  const params = useParams<{ code: string }>();
  const router = useRouter();
  useEffect(() => {
    const code = cleanReferralCode(String(params.code ?? ""));
    router.replace(code ? `/register?ref=${code}` : "/register");
  }, [params.code, router]);
  return <PageLoader />;
}
