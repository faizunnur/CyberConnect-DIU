import Image from "next/image";
import Link from "next/link";
import { ThemeToggle } from "@/components/theme/theme-toggle";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";

const navigation = [
  { href: "/#features", label: "What members do" },
  { href: "/#how-it-works", label: "How it works" },
  { href: "/#eligibility", label: "Eligibility" },
];

export function SiteHeader() {
  return (
    <header className="sticky top-0 z-40 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
        <Link href="/" className="flex items-center gap-3" aria-label="CyberConnect DIU home">
          <Image
            src="/brand/csc-diu-logo.png"
            alt=""
            width={36}
            height={36}
            className="size-9 rounded-md"
            priority
          />
          <span className="flex flex-col leading-tight">
            <span className="text-base font-semibold tracking-tight">CyberConnect DIU</span>
            <span className="hidden text-xs text-muted-foreground sm:block">Verified cybersecurity network</span>
          </span>
        </Link>

        <nav className="hidden items-center gap-6 md:flex" aria-label="Primary">
          {navigation.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
            >
              {item.label}
            </Link>
          ))}
        </nav>

        <div className="flex items-center gap-2">
          <ThemeToggle />
          <Link href="/sign-in" className={cn(buttonVariants({ variant: "ghost", size: "sm" }), "hidden sm:inline-flex")}>
            Sign in
          </Link>
          <Link href="/register" className={buttonVariants({ size: "sm" })}>
            Create an account
          </Link>
        </div>
      </div>
    </header>
  );
}
