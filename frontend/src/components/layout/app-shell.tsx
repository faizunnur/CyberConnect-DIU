"use client";

import { Award, Bell, Bookmark, Briefcase, FlaskConical, Home, MessagesSquare, Settings, UserPlus, Users, type LucideIcon } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { ThemeToggle } from "@/components/theme/theme-toggle";
import { NotificationBell } from "@/features/notifications/components/notification-bell";
import { UserMenu } from "@/features/auth/components/user-menu";
import { cn } from "@/lib/utils";
import { shellContainer } from "./container";

interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
  /** Items for later milestones stay visible but inert so the shell's structure is already final. */
  available: boolean;
}

const items: NavItem[] = [
  { href: "/home", label: "Home", icon: Home, available: true },
  { href: "/members", label: "Members", icon: Users, available: true },
  { href: "/connections", label: "Connections", icon: UserPlus, available: true },
  { href: "/opportunities", label: "Opportunities", icon: Briefcase, available: true },
  { href: "/research", label: "Research", icon: FlaskConical, available: true },
  { href: "/messages", label: "Messages", icon: MessagesSquare, available: true },
  { href: "/notifications", label: "Notifications", icon: Bell, available: true },
  { href: "/saved", label: "Saved", icon: Bookmark, available: true },
  { href: "/impact", label: "Impact", icon: Award, available: true },
  { href: "/settings/profile", label: "Settings", icon: Settings, available: true },
];

/** `sidebar`: icon-only on tablets (label kept for screen readers and as a tooltip), icon + label from laptops up. */
function NavLink({ item, compact, sidebar }: { item: NavItem; compact?: boolean; sidebar?: boolean }) {
  const pathname = usePathname();
  const active = pathname === item.href || pathname.startsWith(item.href + "/")
    || (item.href.startsWith("/settings") && pathname.startsWith("/settings"));
  const className = cn(
    "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
    compact && "flex-col gap-1 px-2 py-1.5 text-[11px]",
    sidebar && "md:justify-center md:px-0 lg:justify-start lg:px-3",
    active ? "bg-accent text-foreground" : "text-muted-foreground hover:bg-accent/60 hover:text-foreground",
    !item.available && "cursor-not-allowed opacity-50",
  );
  if (!item.available) {
    return (
      <span className={className} aria-disabled="true" title="Coming in a later milestone">
        <item.icon className="size-5" aria-hidden="true" />
        <span>{item.label}</span>
      </span>
    );
  }
  return (
    <Link href={item.href} className={className} aria-current={active ? "page" : undefined} title={sidebar ? item.label : undefined}>
      <item.icon className="size-5 shrink-0" aria-hidden="true" />
      <span className={sidebar ? "md:sr-only lg:not-sr-only" : undefined}>{item.label}</span>
    </Link>
  );
}

/**
 * Verified-member shell, responsive by breakpoint:
 * phone (< md) bottom navigation · tablet (md) icon sidebar · laptop (lg) labelled sidebar ·
 * desktop (xl) sidebar + right rail · wide (2xl) wider columns. The container grows to 1760px.
 */
export function AppShell({ children, rail }: { children: ReactNode; rail?: ReactNode }) {
  const mobileItems = items.filter((i) => ["/home", "/members", "/opportunities", "/messages", "/settings/profile"].includes(i.href));
  return (
    <div className="flex min-h-full flex-col">
      <header className="sticky top-0 z-40 border-b bg-background/95 backdrop-blur">
        <div className={cn(shellContainer, "flex h-14 items-center justify-between gap-4")}>
          <Link href="/home" className="flex items-center gap-2" aria-label="CyberConnect DIU home">
            <Image src="/brand/csc-diu-logo.png" alt="" width={32} height={32} className="size-8 rounded-md" />
            <span className="font-semibold tracking-tight">CyberConnect DIU</span>
          </Link>
          <div className="flex items-center gap-2">
            <NotificationBell />
            <ThemeToggle />
            <UserMenu />
          </div>
        </div>
      </header>

      <div
        className={cn(
          shellContainer,
          "grid flex-1 gap-6 py-6 md:grid-cols-[56px_minmax(0,1fr)] lg:grid-cols-[220px_minmax(0,1fr)]",
          "xl:grid-cols-[230px_minmax(0,1fr)_300px] xl:gap-8 2xl:grid-cols-[260px_minmax(0,1fr)_360px]",
        )}
      >
        <nav className="hidden md:block" aria-label="Community">
          <ul className="sticky top-20 space-y-1">
            {items.map((item) => (
              <li key={item.href}>
                <NavLink item={item} sidebar />
              </li>
            ))}
          </ul>
        </nav>
        <main className="min-w-0 pb-20 md:pb-0">{children}</main>
        <aside className="hidden xl:block">
          <div className="sticky top-20 space-y-4">{rail}</div>
        </aside>
      </div>

      <nav className="fixed inset-x-0 bottom-0 z-40 border-t bg-background md:hidden" aria-label="Community (mobile)">
        <ul className="grid grid-cols-5">
          {mobileItems.map((item) => (
            <li key={item.href} className="flex justify-center py-1">
              <NavLink item={item} compact />
            </li>
          ))}
        </ul>
      </nav>
    </div>
  );
}
