import type { Metadata } from "next";

export const metadata: Metadata = { title: "Terms of use" };

export default function TermsPage() {
  return (
    <article className="mx-auto max-w-3xl px-4 py-12 sm:px-6">
      <h1 className="text-3xl font-semibold">Terms of use</h1>
      <p className="mt-2 text-sm text-muted-foreground">Draft for the pilot. The final terms must be approved by the platform owner.</p>

      <h2 className="mt-8 text-xl font-semibold">Eligibility and honesty</h2>
      <p className="mt-2 text-sm">
        Membership is limited to DIU Cybersecurity students, DIU Cybersecurity alumni and eligible members of the Cyber Security
        Center, DIU. You must provide truthful information and genuine evidence. Impersonation or falsified evidence leads to
        removal and may be reported to the institution.
      </p>

      <h2 className="mt-8 text-xl font-semibold">Professional conduct</h2>
      <p className="mt-2 text-sm">
        This is a professional community. Harassment, spam, discriminatory content, unauthorised disclosure of others&apos;
        information and posting of malicious code or exploit material targeting real systems are prohibited. Moderators may
        remove content, suspend accounts and act on reports; decisions are recorded and can be appealed.
      </p>

      <h2 className="mt-8 text-xl font-semibold">Opportunities and impact records</h2>
      <p className="mt-2 text-sm">
        Opportunities must be real and lawful. Impact records are recorded only when both members involved confirm them.
        Disputed records are reviewed by moderators.
      </p>

      <h2 className="mt-8 text-xl font-semibold">Availability</h2>
      <p className="mt-2 text-sm">The platform is provided as a community project without guarantees of availability during the pilot.</p>
    </article>
  );
}
