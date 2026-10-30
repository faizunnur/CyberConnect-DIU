"use client";

import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { cn } from "@/lib/utils";
import { initials } from "../types";

/** Photo streamed from the backend (same-origin, session-authorised); initials when there is none. */
export function MemberAvatar({ name, photoUrl, className, size = "default" }: {
  name: string;
  photoUrl: string | null;
  className?: string;
  size?: "sm" | "default" | "lg";
}) {
  return (
    <Avatar size={size} className={cn(className)}>
      {photoUrl ? <AvatarImage src={photoUrl} alt="" /> : null}
      <AvatarFallback aria-hidden="true">{initials(name) || "?"}</AvatarFallback>
    </Avatar>
  );
}
