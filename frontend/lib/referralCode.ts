/** Referral codes are letters and digits; normalised exactly as the server does. */
export function cleanReferralCode(code: string): string {
  return code.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 12);
}
