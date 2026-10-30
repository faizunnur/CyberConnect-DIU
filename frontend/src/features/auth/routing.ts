import type { Me } from "./types";

/** Where a signed-in user belongs. Backend authorization is the real gate; this only drives navigation. */
export function homeFor(user: Me | null | undefined): string {
  if (!user) return "/sign-in";
  if (user.accountStatus === "VERIFIED") return "/home";
  return "/onboarding";
}

export function isPrivileged(user: Me | null | undefined): boolean {
  return !!user && user.roles.some((r) => r !== "MEMBER");
}

export function hasAuthority(user: Me | null | undefined, authority: string): boolean {
  return !!user && user.authorities.includes(authority);
}

export function stepUpIsFresh(user: Me | null | undefined): boolean {
  if (!user?.stepUpValidUntil) return false;
  return new Date(user.stepUpValidUntil).getTime() > Date.now();
}
