"use client";

import { ClipboardList, Flag, Gavel, History, Settings2, Users, type LucideIcon } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { ThemeToggle } from "@/components/theme/theme-toggle";
import { StepUpBanner } from "@/features/auth/components/step-up-banner";
import { UserMenu } from "@/features/auth/components/user-menu";
import { useMe } from "@/features/auth/hooks";
import { hasAuthority } from "@/features/auth/routing";
import { cn } from "@/lib/utils";

interface Item {
  href: string;
  label: string;
  icon: LucideIcon;
  authority: string;
}

const items: Item[] = [
  { href: "/admin/moderation", label: "Verification queue", icon: ClipboardList, authority: "VERIFICATION_REVIEW" },
  { href: "/admin/appeals", label: "Appeals", icon: Gavel, authority: "APPEAL_DECIDE" },
  { href: "/admin/reports", label: "Reports", icon: Flag, authority: "MODERATION_ACTION" },
  { href: "/admin/members", label: "Members", icon: Users, authority: "MEMBER_MANAGE" },
  { href: "/admin/settings", label: "Settings", icon: Settings2, authority: "SETTINGS_MANAGE" },
  { href: "/admin/audit", label: "Audit history", icon: History, authority: "AUDIT_READ" },
];

export function AdminShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const { data: me } = useMe();
  const visible = items.filter((item) => hasAuthority(me, item.authority));

  return (
    <div className="flex min-h-full flex-col">
      <header className="sticky top-0 z-40 border-b bg-background/95 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-7xl items-center justify-between gap-4 px-4">
          <div className="flex items-center gap-3">
            <Link href="/home" className="flex items-center gap-2" aria-label="Back to community">
              <Image src="/brand/csc-diu-logo.png" alt="" width={32} height={32} className="size-8 rounded-md" />
              <span className="font-semibold tracking-tight">CyberConnect DIU</span>
            </Link>
            <span className="rounded-md bg-navy px-2 py-0.5 text-xs font-medium text-navy-foreground">Moderation &amp; admin</span>
          </div>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <UserMenu />
          </div>
        </div>
      </header>

      <div className="mx-auto grid w-full max-w-7xl flex-1 gap-6 px-4 py-6 md:grid-cols-[220px_minmax(0,1fr)]">
        <nav aria-label="Administration">
          <ul className="flex gap-1 overflow-x-auto md:sticky md:top-20 md:flex-col">
            {visible.map((item) => {
              const active = pathname.startsWith(item.href);
              return (
                <li key={item.href}>
                  <Link
                    href={item.href}
                    aria-current={active ? "page" : undefined}
                    className={cn(
                      "flex items-center gap-3 whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                      active ? "bg-accent text-foreground" : "text-muted-foreground hover:bg-accent/60 hover:text-foreground",
                    )}
                  >
                    <item.icon className="size-5" aria-hidden="true" />
                    {item.label}
                  </Link>
                </li>
              );
            })}
          </ul>
        </nav>
        <main className="min-w-0 space-y-6">
          <StepUpBanner />
          {children}
        </main>
      </div>
    </div>
  );
}
