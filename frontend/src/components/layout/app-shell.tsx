"use client";

import { Bell, Bookmark, Briefcase, FlaskConical, Home, MessagesSquare, Settings, Users, type LucideIcon } from "lucide-react";
import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { ThemeToggle } from "@/components/theme/theme-toggle";
import { UserMenu } from "@/features/auth/components/user-menu";
import { cn } from "@/lib/utils";

interface NavItem {
  href: string;
  label: string;
  icon: LucideIcon;
  /** Items for later milestones stay visible but inert so the shell's structure is already final. */
  available: boolean;
}

const items: NavItem[] = [
  { href: "/home", label: "Home", icon: Home, available: true },
  { href: "/members", label: "Members", icon: Users, available: false },
  { href: "/opportunities", label: "Opportunities", icon: Briefcase, available: false },
  { href: "/research", label: "Research", icon: FlaskConical, available: false },
  { href: "/messages", label: "Messages", icon: MessagesSquare, available: false },
  { href: "/notifications", label: "Notifications", icon: Bell, available: false },
  { href: "/saved", label: "Saved", icon: Bookmark, available: false },
  { href: "/settings/security", label: "Settings", icon: Settings, available: true },
];

function NavLink({ item, compact }: { item: NavItem; compact?: boolean }) {
  const pathname = usePathname();
  const active = pathname === item.href || pathname.startsWith(item.href + "/");
  const className = cn(
    "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
    compact && "flex-col gap-1 px-2 py-1.5 text-[11px]",
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
    <Link href={item.href} className={className} aria-current={active ? "page" : undefined}>
      <item.icon className="size-5" aria-hidden="true" />
      <span>{item.label}</span>
    </Link>
  );
}

/** Verified-member shell: left navigation, centre content, right rail on desktop; bottom navigation on mobile. */
export function AppShell({ children, rail }: { children: ReactNode; rail?: ReactNode }) {
  const mobileItems = items.filter((i) => ["/home", "/members", "/opportunities", "/messages", "/settings/security"].includes(i.href));
  return (
    <div className="flex min-h-full flex-col">
      <header className="sticky top-0 z-40 border-b bg-background/95 backdrop-blur">
        <div className="mx-auto flex h-14 max-w-7xl items-center justify-between gap-4 px-4">
          <Link href="/home" className="flex items-center gap-2" aria-label="CyberConnect DIU home">
            <Image src="/brand/csc-diu-logo.png" alt="" width={32} height={32} className="size-8 rounded-md" />
            <span className="font-semibold tracking-tight">CyberConnect DIU</span>
          </Link>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <UserMenu />
          </div>
        </div>
      </header>

      <div className="mx-auto grid w-full max-w-7xl flex-1 gap-6 px-4 py-6 lg:grid-cols-[220px_minmax(0,1fr)_280px]">
        <nav className="hidden lg:block" aria-label="Community">
          <ul className="sticky top-20 space-y-1">
            {items.map((item) => (
              <li key={item.href}>
                <NavLink item={item} />
              </li>
            ))}
          </ul>
        </nav>
        <main className="min-w-0 pb-20 lg:pb-0">{children}</main>
        <aside className="hidden lg:block">
          <div className="sticky top-20 space-y-4">{rail}</div>
        </aside>
      </div>

      <nav className="fixed inset-x-0 bottom-0 z-40 border-t bg-background lg:hidden" aria-label="Community (mobile)">
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
