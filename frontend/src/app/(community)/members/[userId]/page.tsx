import type { Metadata } from "next";
import { MemberProfile } from "@/features/profile/components/profile-view";

export const metadata: Metadata = { title: "Member profile" };

export default async function MemberPage({ params }: { params: Promise<{ userId: string }> }) {
  const { userId } = await params;
  return <MemberProfile userId={userId} />;
}
