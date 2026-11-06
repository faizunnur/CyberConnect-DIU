import Image from "next/image";
import Link from "next/link";

export function SiteFooter() {
  return (
    <footer className="border-t bg-card">
      <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
        <div className="flex flex-col gap-8 md:flex-row md:items-start md:justify-between">
          <div className="flex items-center gap-5">
            <Image src="/brand/diu-logo.png" alt="Daffodil International University" width={136} height={36} className="h-9 w-auto" />
            <Image src="/brand/csc-diu-logo.png" alt="Cyber Security Center, DIU" width={36} height={36} className="size-9" />
          </div>
          <div className="max-w-md text-sm text-muted-foreground">
            <p className="font-medium text-foreground">CyberConnect DIU</p>
            <p className="mt-1">
              A verified professional community for Daffodil International University&apos;s cybersecurity students,
              alumni and Cyber Security Center members.
            </p>
          </div>
          <nav className="flex gap-6 text-sm" aria-label="Footer">
            <Link href="/#how-it-works" className="text-muted-foreground hover:text-foreground">
              Verification
            </Link>
            <Link href="/#eligibility" className="text-muted-foreground hover:text-foreground">
              Eligibility
            </Link>
          </nav>
        </div>
        <div className="mt-8 flex flex-col gap-2 border-t pt-6 text-xs text-muted-foreground sm:flex-row sm:justify-between">
          <p>
            &copy; {new Date().getFullYear()} CyberConnect DIU. Community project; institutional branding is used
            pending formal approval.
          </p>
          <p>Every member is manually verified. Identity documents are encrypted and purged after review.</p>
        </div>
      </div>
    </footer>
  );
}
