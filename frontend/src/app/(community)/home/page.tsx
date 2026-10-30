import type { Metadata } from "next";
import { Briefcase, FlaskConical, MessagesSquare, Users } from "lucide-react";
import Link from "next/link";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";

export const metadata: Metadata = { title: "Home" };

const upcoming = [
  { icon: Users, title: "Member directory", text: "Find alumni by specialization, skills and organisation.", href: "/members" },
  { icon: MessagesSquare, title: "Community feed", text: "Technical discussion, achievements and events." },
  { icon: Briefcase, title: "Opportunities", text: "Jobs, internships, scholarships and research assistantships." },
  { icon: FlaskConical, title: "Research hub", text: "Publish projects and find collaborators." },
];

export default function HomePage() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Welcome to the community</h1>
        <p className="mt-1 text-muted-foreground">
          Your eligibility is verified. Complete your profile, find members and build your network; more features arrive milestone by milestone.
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        {upcoming.map((item) => (
          <Card key={item.title}>
            <CardHeader>
              <div className="mb-1 flex size-9 items-center justify-center rounded-lg bg-accent text-primary">
                <item.icon className="size-5" aria-hidden="true" />
              </div>
              <CardTitle>{item.title}</CardTitle>
              <CardDescription>{item.text}</CardDescription>
            </CardHeader>
            <CardContent className="text-xs text-muted-foreground">
              {"href" in item && item.href ? (
                <Link href={item.href} className="text-sm font-medium text-primary underline-offset-4 hover:underline">Open</Link>
              ) : (
                "Coming soon"
              )}
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
