"use client";

import { Bell } from "lucide-react";
import Link from "next/link";
import { useNotificationSummary } from "../api";

/** Header bell with the unread count; the list lives on /notifications. */
export function NotificationBell() {
  const { data } = useNotificationSummary();
  const unread = data?.unread ?? 0;
  return (
    <Link
      href="/notifications"
      className="relative inline-flex size-9 items-center justify-center rounded-lg text-muted-foreground hover:bg-accent hover:text-foreground"
      aria-label={unread > 0 ? `Notifications, ${unread} unread` : "Notifications"}
    >
      <Bell className="size-5" aria-hidden="true" />
      {unread > 0 ? (
        <span className="absolute -top-0.5 -right-0.5 min-w-4 rounded-full bg-destructive px-1 text-center text-[10px] font-semibold leading-4 text-white" aria-hidden="true">
          {unread > 99 ? "99+" : unread}
        </span>
      ) : null}
    </Link>
  );
}
