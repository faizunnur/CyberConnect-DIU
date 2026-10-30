export const dynamic = "force-dynamic";

/** Liveness endpoint used by Docker healthchecks and nginx. Deliberately outside /api, which belongs to the backend. */
export function GET() {
  return Response.json(
    { status: "ok", service: "frontend", timestamp: new Date().toISOString() },
    { headers: { "Cache-Control": "no-store" } },
  );
}
