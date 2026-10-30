import type { Metadata } from "next";

export const metadata: Metadata = { title: "Privacy notice" };

export default function PrivacyPage() {
  return (
    <article className="prose prose-slate mx-auto max-w-3xl px-4 py-12 dark:prose-invert sm:px-6">
      <h1 className="text-3xl font-semibold">Privacy notice</h1>
      <p className="mt-2 text-sm text-muted-foreground">Draft for the pilot. The final notice must be approved by the platform owner.</p>

      <h2 className="mt-8 text-xl font-semibold">What we collect</h2>
      <ul className="mt-2 list-disc space-y-1 pl-6 text-sm">
        <li>Account data: name, email address, password hash.</li>
        <li>Eligibility data: affiliation, student or member identifiers, batch, department, graduation year.</li>
        <li>Eligibility evidence: the document you upload for manual review.</li>
        <li>Profile and activity data you choose to add once verified.</li>
        <li>Technical data needed for security: IP address, user agent, timestamps of security-relevant actions.</li>
      </ul>

      <h2 className="mt-8 text-xl font-semibold">How eligibility evidence is handled</h2>
      <ul className="mt-2 list-disc space-y-1 pl-6 text-sm">
        <li>Uploaded only through the platform, scanned for malware and encrypted before storage.</li>
        <li>Visible only to authorised moderators, who must pass an additional authentication step to view it. Every view is logged.</li>
        <li>Never shown to other members and never shared outside the review process.</li>
        <li>Deleted 30 days after the final verification or appeal decision; a record that a review took place is retained.</li>
      </ul>

      <h2 className="mt-8 text-xl font-semibold">Your controls</h2>
      <ul className="mt-2 list-disc space-y-1 pl-6 text-sm">
        <li>Privacy settings decide who sees your profile fields and who can message you.</li>
        <li>You can deactivate your account at any time; it can be restored within 30 days, after which it is anonymised.</li>
        <li>Decisions about your application come with a reason and a way to resubmit or appeal.</li>
      </ul>

      <h2 className="mt-8 text-xl font-semibold">Contact</h2>
      <p className="mt-2 text-sm">Questions about this notice go to the platform moderators through the support contact shown on your onboarding page.</p>
    </article>
  );
}
