"use client";

import { Briefcase, GraduationCap, Lock, Mail, MapPin } from "lucide-react";
import Link from "next/link";
import { FormError } from "@/components/forms/form-alert";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { AFFILIATION_LABELS } from "@/features/auth/types";
import { AuthorPosts } from "@/features/community/components/author-posts";
import { ConnectButton } from "@/features/networking/components/connect-button";
import { RequestMentorshipButton } from "@/features/networking/components/mentorship";
import { isApiError } from "@/lib/api/client";
import { formatDate } from "@/lib/api/types";
import { useMemberProfile } from "../hooks";
import { EMPLOYMENT_LABELS, type ProfileView } from "../types";
import { MemberAvatar } from "./member-avatar";

export function MemberProfile({ userId }: { userId: string }) {
  const { data, isPending, isError, error } = useMemberProfile(userId);

  if (isPending) return <Skeleton className="h-96 w-full" />;
  if (isError) {
    if (isApiError(error) && error.status === 404) {
      return (
        <div className="space-y-2">
          <Link href="/members" className="text-sm text-muted-foreground hover:text-foreground">← Members</Link>
          <h1 className="text-2xl font-semibold">Member not found</h1>
          <p className="text-muted-foreground">This member does not exist or is not available to you.</p>
        </div>
      );
    }
    return <FormError error={error} title="Could not load the profile" />;
  }
  return <ProfileBody profile={data} />;
}

function ProfileBody({ profile }: { profile: ProfileView }) {
  const where = [profile.jobTitle, profile.organization].filter(Boolean).join(" at ");
  const place = [profile.location, profile.country].filter(Boolean).join(", ");
  return (
    <div className="space-y-6">
      <Link href="/members" className="text-sm text-muted-foreground hover:text-foreground">← Members</Link>
      <header className="flex flex-col gap-4 sm:flex-row sm:items-start">
        <MemberAvatar name={profile.displayName} photoUrl={profile.photoUrl} size="lg" className="size-24 text-2xl" />
        <div className="min-w-0 flex-1 space-y-2">
          <div>
            <h1 className="text-2xl font-semibold">{profile.displayName}</h1>
            {profile.headline ? <p className="text-muted-foreground">{profile.headline}</p> : null}
          </div>
          <div className="flex flex-wrap gap-1.5">
            {profile.affiliations.map((a) => <Badge key={a} variant="secondary">{AFFILIATION_LABELS[a]}</Badge>)}
            {profile.mentorAvailable ? <Badge variant="outline" className="border-success text-success"><GraduationCap aria-hidden="true" /> Open to mentoring</Badge> : null}
          </div>
          <dl className="space-y-1 text-sm text-muted-foreground">
            {where ? <div className="flex items-center gap-2"><Briefcase className="size-4" aria-hidden="true" /><dd>{where}</dd></div> : null}
            {place ? <div className="flex items-center gap-2"><MapPin className="size-4" aria-hidden="true" /><dd>{place}</dd></div> : null}
            {profile.email ? <div className="flex items-center gap-2"><Mail className="size-4" aria-hidden="true" /><dd><a href={`mailto:${profile.email}`} className="text-primary hover:underline">{profile.email}</a></dd></div> : null}
            <div><dd>Member since {formatDate(profile.memberSince)}</dd></div>
          </dl>
        </div>
        <div className="flex flex-col items-start gap-2">
          <ConnectButton userId={profile.userId} relationship={profile.relationship} />
          {profile.mentorAvailable && !profile.relationship.self ? <RequestMentorshipButton mentorId={profile.userId} mentorName={profile.displayName} /> : null}
        </div>
      </header>

      {profile.restricted ? (
        <Card>
          <CardContent className="flex items-center gap-3 text-sm text-muted-foreground">
            <Lock className="size-5 shrink-0" aria-hidden="true" />
            This member shares their full profile with connections only. Send a connection request to see more.
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
          <div className="space-y-6">
            {profile.bio || profile.specializations.length > 0 ? (
              <Card>
                <CardHeader><CardTitle>About</CardTitle></CardHeader>
                <CardContent className="space-y-3">
                  {profile.bio ? <p className="text-sm whitespace-pre-line">{profile.bio}</p> : null}
                  {profile.specializations.length > 0 ? (
                    <div className="flex flex-wrap gap-1.5">
                      {profile.specializations.map((s) => <Badge key={s} variant="outline">{s}</Badge>)}
                    </div>
                  ) : null}
                </CardContent>
              </Card>
            ) : null}
            <Card>
              <CardHeader><CardTitle>Experience</CardTitle></CardHeader>
              <CardContent>
                {profile.experiences.length === 0 ? <p className="text-sm text-muted-foreground">No experience listed.</p> : (
                  <ul className="space-y-4">
                    {profile.experiences.map((e) => (
                      <li key={e.id}>
                        <p className="font-medium">{e.title}</p>
                        <p className="text-sm text-muted-foreground">
                          {e.organization}{e.employmentType ? ` · ${EMPLOYMENT_LABELS[e.employmentType]}` : ""}{e.location ? ` · ${e.location}` : ""}
                        </p>
                        <p className="text-xs text-muted-foreground">{formatDate(e.startDate)} – {e.current ? "present" : formatDate(e.endDate)}</p>
                        {e.description ? <p className="mt-1 text-sm whitespace-pre-line">{e.description}</p> : null}
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>
            <Card>
              <CardHeader><CardTitle>Education</CardTitle></CardHeader>
              <CardContent>
                {profile.educations.length === 0 ? <p className="text-sm text-muted-foreground">No education listed.</p> : (
                  <ul className="space-y-4">
                    {profile.educations.map((e) => (
                      <li key={e.id}>
                        <p className="font-medium">{e.institution}</p>
                        <p className="text-sm text-muted-foreground">{[e.degree, e.fieldOfStudy].filter(Boolean).join(", ")}</p>
                        {e.startYear || e.endYear ? <p className="text-xs text-muted-foreground">{e.startYear ?? ""}{e.startYear && e.endYear ? " – " : ""}{e.endYear ?? ""}</p> : null}
                        {e.description ? <p className="mt-1 text-sm whitespace-pre-line">{e.description}</p> : null}
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>
          </div>
          <div className="space-y-6">
            <Card>
              <CardHeader><CardTitle>Skills</CardTitle></CardHeader>
              <CardContent>
                {profile.skills.length === 0 ? <p className="text-sm text-muted-foreground">No skills listed.</p> : (
                  <div className="flex flex-wrap gap-1.5">
                    {profile.skills.map((s) => <Badge key={s.id} variant="secondary">{s.name}</Badge>)}
                  </div>
                )}
              </CardContent>
            </Card>
            {profile.mentorAvailable && profile.mentorTopics ? (
              <Card>
                <CardHeader><CardTitle>Mentoring</CardTitle></CardHeader>
                <CardContent className="text-sm whitespace-pre-line">{profile.mentorTopics}</CardContent>
              </Card>
            ) : null}
          </div>
        </div>
      )}
      {!profile.restricted ? <AuthorPosts userId={profile.userId} /> : null}
    </div>
  );
}
