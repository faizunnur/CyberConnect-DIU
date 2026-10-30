"use client";

import { LogOut, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Badge } from "@/components/ui/badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useLogout, useMe } from "../hooks";
import { homeFor, isPrivileged } from "../routing";
import { STATUS_LABELS } from "../types";

/** Header controls that adapt to the session: sign-in links for visitors, identity + sign-out for members. */
export function UserMenu() {
  const { data: me, isPending } = useMe();
  const logout = useLogout();
  const router = useRouter();

  if (isPending) return <span className="h-8 w-24" aria-hidden="true" />;

  if (!me) {
    return (
      <>
        <Link href="/sign-in" className={cn(buttonVariants({ variant: "ghost", size: "sm" }), "hidden sm:inline-flex")}>
          Sign in
        </Link>
        <Link href="/register" className={buttonVariants({ size: "sm" })}>
          Create an account
        </Link>
      </>
    );
  }

  return (
    <>
      <Link href={homeFor(me)} className="hidden items-center gap-2 text-sm md:flex">
        <span className="font-medium">{me.fullName}</span>
        <Badge variant={me.accountStatus === "VERIFIED" ? "default" : "secondary"}>{STATUS_LABELS[me.accountStatus]}</Badge>
      </Link>
      {isPrivileged(me) ? (
        <Link href="/admin/moderation" className={cn(buttonVariants({ variant: "outline", size: "sm" }), "hidden sm:inline-flex")}>
          <ShieldCheck aria-hidden="true" /> Moderation
        </Link>
      ) : null}
      <Button
        variant="ghost"
        size="sm"
        onClick={async () => {
          await logout.mutateAsync();
          router.replace("/");
        }}
        disabled={logout.isPending}
      >
        <LogOut aria-hidden="true" /> <span className="hidden sm:inline">Sign out</span>
      </Button>
    </>
  );
}
