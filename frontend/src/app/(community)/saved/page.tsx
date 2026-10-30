import type { Metadata } from "next";
import { SavedPosts } from "@/features/community/components/saved";

export const metadata: Metadata = { title: "Saved posts" };

export default function SavedPage() {
  return <SavedPosts />;
}
