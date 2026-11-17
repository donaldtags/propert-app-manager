import { NextResponse } from "next/server";

// Which commit this frontend is running. Render sets RENDER_GIT_COMMIT for every deploy; the
// deployed-app test workflow polls this to know the new version is live before testing it.
export const dynamic = "force-dynamic";

export function GET() {
  return NextResponse.json(
    { service: "frontend", commit: process.env.RENDER_GIT_COMMIT ?? process.env.GIT_COMMIT ?? "dev" },
    { headers: { "Cache-Control": "no-store" } }
  );
}
