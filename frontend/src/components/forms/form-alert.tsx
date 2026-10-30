import { AlertCircle, CheckCircle2 } from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { isApiError } from "@/lib/api/client";

export function errorMessage(error: unknown, fallback = "Something went wrong. Please try again."): string {
  if (isApiError(error)) {
    if (error.status === 429) return "Too many attempts. Please wait a moment and try again.";
    return error.detail || fallback;
  }
  if (error instanceof Error && error.message) return error.message;
  return fallback;
}

export function FormError({ error, title = "Request failed" }: { error: unknown; title?: string }) {
  if (!error) return null;
  return (
    <Alert variant="destructive" role="alert">
      <AlertCircle aria-hidden="true" />
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription>{errorMessage(error)}</AlertDescription>
    </Alert>
  );
}

export function FormSuccess({ title, children }: { title: string; children?: React.ReactNode }) {
  return (
    <Alert role="status">
      <CheckCircle2 aria-hidden="true" className="text-success" />
      <AlertTitle>{title}</AlertTitle>
      {children ? <AlertDescription>{children}</AlertDescription> : null}
    </Alert>
  );
}
