/**
 * The page to return to after sign-in or sign-up, taken from `?redirect=` (or the older `?next=`).
 * Only same-site paths are allowed: "https://…", "//host" and "/\host" would let a crafted link
 * send someone to another site straight after they type their password.
 */
export function safeRedirect(params: { get(name: string): string | null }): string | null {
  const raw = params.get("redirect") ?? params.get("next");
  if (!raw || !raw.startsWith("/") || raw.startsWith("//") || raw.startsWith("/\\")) return null;
  return raw;
}
