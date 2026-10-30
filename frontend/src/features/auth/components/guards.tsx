"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { buttonVariants } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useMe } from "../hooks";
import { homeFor, isPrivileged } from "../routing";
import type { Me } from "../types";

type Mode = "any" | "onboarding" | "verified" | "privileged";

function destination(mode: Mode, me: Me | null): string | null {
  if (!me) return "/sign-in";
  if (mode === "onboarding" && me.accountStatus === "VERIFIED") return "/home";
  if ((mode === "verified" || mode === "privileged") && me.accountStatus !== "VERIFIED") return homeFor(me);
  if (mode === "privileged" && !isPrivileged(me)) return "/home";
  return null;
}

/**
 * Navigation guard only: it routes people to the right area. Authorization is enforced by the backend on every
 * request regardless of what this component renders.
 */
export function RequireAuth({ mode, children }: { mode: Mode; children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { data: me, isPending, isError } = useMe();

  const target = isPending ? null : destination(mode, me ?? null);

  useEffect(() => {
    if (target) {
      router.replace(target === "/sign-in" ? `/sign-in?next=${encodeURIComponent(pathname)}` : target);
    }
  }, [target, router, pathname]);

  if (isPending || target) {
    return (
      <div className="mx-auto max-w-3xl space-y-4 px-4 py-12" aria-busy="true" aria-live="polite">
        <Skeleton className="h-8 w-1/3" />
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-32 w-full" />
      </div>
    );
  }
  if (isError) {
    return (
      <div className="mx-auto max-w-xl px-4 py-12">
        <Alert variant="destructive">
          <AlertTitle>Could not load your account</AlertTitle>
          <AlertDescription>The server did not respond. Check your connection and try again.</AlertDescription>
        </Alert>
      </div>
    );
  }
  if (mode === "privileged" && me && !me.mfaEnabled) {
    return (
      <div className="mx-auto max-w-xl px-4 py-12">
        <Alert>
          <AlertTitle>Multi-factor authentication required</AlertTitle>
          <AlertDescription>
            Moderators and administrators must set up an authenticator app before using privileged tools.
          </AlertDescription>
        </Alert>
        <Link href="/settings/security" className={buttonVariants({ className: "mt-4" })}>
          Set up MFA
        </Link>
      </div>
    );
  }
  return <>{children}</>;
}
