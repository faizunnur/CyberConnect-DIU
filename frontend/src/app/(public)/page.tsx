import {
  ArrowRight,
  BadgeCheck,
  Briefcase,
  FileCheck2,
  FlaskConical,
  GraduationCap,
  Lock,
  MessagesSquare,
  Send,
  ShieldCheck,
  UserCheck,
} from "lucide-react";
import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

const features = [
  {
    icon: Briefcase,
    title: "Jobs, internships and scholarships",
    description: "Opportunities shared by alumni and partners, with deadlines, requirements and a clear way to apply.",
  },
  {
    icon: FlaskConical,
    title: "Research collaboration",
    description: "Publish a project, list the skills you need and bring in collaborators from across batches.",
  },
  {
    icon: GraduationCap,
    title: "Mentorship",
    description: "Alumni mark themselves available; students request guidance on careers, certifications and projects.",
  },
  {
    icon: MessagesSquare,
    title: "Technical discussion",
    description: "SOC work, penetration testing, forensics, cloud security and governance, discussed among verified peers.",
  },
  {
    icon: Send,
    title: "Direct messaging",
    description: "Private conversations with message requests, blocking and reporting built in.",
  },
  {
    icon: BadgeCheck,
    title: "Confirmed impact",
    description: "Referrals, research and mentorship outcomes are recorded only when both members confirm them.",
  },
];

const steps = [
  {
    icon: UserCheck,
    title: "Register and verify your email",
    description: "Create an account with your name, email and primary affiliation. Confirm the email we send you.",
  },
  {
    icon: FileCheck2,
    title: "Submit your eligibility evidence",
    description:
      "Upload your student ID card, alumni evidence or CSC membership card through an encrypted, private upload.",
  },
  {
    icon: ShieldCheck,
    title: "A moderator reviews your submission",
    description:
      "Approved accounts join the community. If something is unclear you get a plain explanation, and you can resubmit or appeal.",
  },
];

const eligibility = [
  {
    title: "Cybersecurity students",
    description: "Current students of the Cybersecurity major at Daffodil International University.",
    evidence: "Student ID card",
  },
  {
    title: "Cybersecurity alumni",
    description: "Graduates of the DIU Cybersecurity program, wherever you work now.",
    evidence: "Alumni card, degree certificate or transcript",
  },
  {
    title: "Cyber Security Center members",
    description: "Eligible members of the Cyber Security Center (CSC), DIU.",
    evidence: "CSC membership card or CSC-issued letter",
  },
];

export default function LandingPage() {
  return (
    <>
      {/* Hero */}
      <section className="border-b bg-gradient-to-b from-card to-background">
        <div className="mx-auto grid max-w-6xl gap-10 px-4 py-16 sm:px-6 md:grid-cols-[1.2fr_1fr] md:items-center md:py-24">
          <div>
            <Badge variant="secondary" className="mb-4">
              Private · Verified · DIU cybersecurity
            </Badge>
            <h1 className="text-4xl font-semibold leading-tight sm:text-5xl">
              The professional network for DIU&apos;s cybersecurity community
            </h1>
            <p className="mt-5 max-w-xl text-lg text-muted-foreground">
              Connect with alumni working in security operations, penetration testing, forensics, cloud security and
              research. Find internships, research collaborators and mentors, in a community where every member is
              verified.
            </p>
            <div className="mt-8 flex flex-col gap-3 sm:flex-row">
              <Link href="/register" className={cn(buttonVariants({ size: "lg" }), "h-11 px-6 text-base")}>
                Create an account
                <ArrowRight aria-hidden="true" />
              </Link>
              <Link
                href="/sign-in"
                className={cn(buttonVariants({ variant: "outline", size: "lg" }), "h-11 px-6 text-base")}
              >
                Sign in
              </Link>
            </div>
            <p className="mt-4 text-sm text-muted-foreground">
              Membership is limited to Cybersecurity students, alumni and CSC DIU members.
            </p>
          </div>

          <Card className="border-navy/10 shadow-sm">
            <CardHeader>
              <div className="flex items-center gap-2">
                <Lock className="size-5 text-primary" aria-hidden="true" />
                <CardTitle>What verification means</CardTitle>
              </div>
              <CardDescription>Trust is the point of this network, so we check it carefully.</CardDescription>
            </CardHeader>
            <CardContent>
              <ul className="space-y-3 text-sm">
                <li className="flex gap-3">
                  <ShieldCheck className="mt-0.5 size-4 shrink-0 text-success" aria-hidden="true" />
                  <span>Every application is reviewed by a moderator, never auto-approved.</span>
                </li>
                <li className="flex gap-3">
                  <ShieldCheck className="mt-0.5 size-4 shrink-0 text-success" aria-hidden="true" />
                  <span>Identity documents are encrypted, visible only to authorised reviewers, and purged after review.</span>
                </li>
                <li className="flex gap-3">
                  <ShieldCheck className="mt-0.5 size-4 shrink-0 text-success" aria-hidden="true" />
                  <span>Until you are verified, nobody sees your profile and you see nothing private.</span>
                </li>
                <li className="flex gap-3">
                  <ShieldCheck className="mt-0.5 size-4 shrink-0 text-success" aria-hidden="true" />
                  <span>Decisions come with a clear reason and a path to resubmit or appeal.</span>
                </li>
              </ul>
            </CardContent>
          </Card>
        </div>
      </section>

      {/* Features */}
      <section id="features" className="mx-auto max-w-6xl scroll-mt-20 px-4 py-16 sm:px-6">
        <div className="max-w-2xl">
          <h2 className="text-3xl font-semibold">What members do here</h2>
          <p className="mt-3 text-muted-foreground">
            Not a generic social feed. Every feature exists to move a cybersecurity career or project forward.
          </p>
        </div>
        <div className="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {features.map((feature) => (
            <Card key={feature.title} className="h-full">
              <CardHeader>
                <div className="mb-2 flex size-10 items-center justify-center rounded-lg bg-accent text-primary">
                  <feature.icon className="size-5" aria-hidden="true" />
                </div>
                <CardTitle>{feature.title}</CardTitle>
                <CardDescription>{feature.description}</CardDescription>
              </CardHeader>
            </Card>
          ))}
        </div>
      </section>

      {/* How it works */}
      <section id="how-it-works" className="scroll-mt-20 border-y bg-card">
        <div className="mx-auto max-w-6xl px-4 py-16 sm:px-6">
          <div className="max-w-2xl">
            <h2 className="text-3xl font-semibold">How verification works</h2>
            <p className="mt-3 text-muted-foreground">
              Registration is public. Membership is not. Three steps stand between a new account and the community.
            </p>
          </div>
          <ol className="mt-10 grid gap-6 md:grid-cols-3">
            {steps.map((step, index) => (
              <li key={step.title} className="relative rounded-xl border bg-background p-6">
                <div className="flex items-center gap-3">
                  <span className="flex size-8 items-center justify-center rounded-full bg-navy text-sm font-semibold text-navy-foreground">
                    {index + 1}
                  </span>
                  <step.icon className="size-5 text-primary" aria-hidden="true" />
                </div>
                <h3 className="mt-4 text-lg font-semibold">{step.title}</h3>
                <p className="mt-2 text-sm text-muted-foreground">{step.description}</p>
              </li>
            ))}
          </ol>
        </div>
      </section>

      {/* Eligibility */}
      <section id="eligibility" className="mx-auto max-w-6xl scroll-mt-20 px-4 py-16 sm:px-6">
        <div className="max-w-2xl">
          <h2 className="text-3xl font-semibold">Who can join</h2>
          <p className="mt-3 text-muted-foreground">
            You may hold more than one affiliation. Each one is verified separately, and you choose a primary one when
            you register.
          </p>
        </div>
        <div className="mt-10 grid gap-5 md:grid-cols-3">
          {eligibility.map((group) => (
            <Card key={group.title} className="h-full">
              <CardHeader>
                <CardTitle>{group.title}</CardTitle>
                <CardDescription>{group.description}</CardDescription>
              </CardHeader>
              <CardContent>
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Accepted evidence</p>
                <p className="mt-1 text-sm">{group.evidence}</p>
              </CardContent>
            </Card>
          ))}
        </div>
      </section>

      {/* Call to action */}
      <section className="bg-navy text-navy-foreground">
        <div className="mx-auto flex max-w-6xl flex-col items-start gap-6 px-4 py-14 sm:px-6 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="text-2xl font-semibold">Ready to join the verified community?</h2>
            <p className="mt-2 max-w-xl text-sm text-navy-foreground/80">
              Registration takes a few minutes. Verification is handled by moderators from the DIU cybersecurity
              community.
            </p>
          </div>
          <Link
            href="/register"
            className={cn(buttonVariants({ variant: "secondary", size: "lg" }), "h-11 px-6 text-base")}
          >
            Create an account
            <ArrowRight aria-hidden="true" />
          </Link>
        </div>
      </section>
    </>
  );
}
