"use client";

import { Loader2 } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef } from "react";
import { FormError, FormSuccess } from "@/components/forms/form-alert";
import { buttonVariants } from "@/components/ui/button";
import { useVerifyEmail } from "../hooks";

export function VerifyEmail({ token }: { token: string }) {
  const verify = useVerifyEmail();
  const started = useRef(false);

  useEffect(() => {
    if (token && !started.current) {
      started.current = true;
      verify.mutate(token);
    }
  }, [token, verify]);

  if (!token) {
    return <FormError error={new Error("This verification link is incomplete. Request a new one from your onboarding page.")} title="Invalid link" />;
  }
  if (verify.isPending || verify.isIdle) {
    return (
      <p className="flex items-center gap-2 text-sm text-muted-foreground" role="status">
        <Loader2 className="size-4 animate-spin" aria-hidden="true" /> Verifying your email…
      </p>
    );
  }
  if (verify.isError) {
    return (
      <div className="space-y-4">
        <FormError error={verify.error} title="Verification failed" />
        <p className="text-sm text-muted-foreground">Links expire after 24 hours. Sign in and request a new one from your onboarding page.</p>
        <Link href="/sign-in" className={buttonVariants({ variant: "outline" })}>Sign in</Link>
      </div>
    );
  }
  return (
    <div className="space-y-4">
      <FormSuccess title="Email verified">You can now sign in and upload your eligibility evidence.</FormSuccess>
      <Link href="/sign-in" className={buttonVariants({ className: "w-full" })}>Continue to sign in</Link>
    </div>
  );
}
