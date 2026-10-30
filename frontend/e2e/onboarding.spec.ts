import { expect, test, type APIRequestContext, type Page } from "@playwright/test";

/**
 * Applicant path against a running stack (E2E_BASE_URL) with Mailpit (E2E_MAILPIT_URL, default http://localhost:8025):
 * register → verify email from the captured mail → sign in → onboarding hub → upload fictional evidence → submit.
 * Skipped when no stack is configured, because the dev-only frontend has no backend to talk to.
 */
const MAILPIT = process.env.E2E_MAILPIT_URL ?? "http://localhost:8025";
const PASSWORD = "Correct-Horse-Battery-Staple-42";

test.describe("applicant onboarding", () => {
  test.skip(!process.env.E2E_BASE_URL, "needs the full stack (E2E_BASE_URL) and Mailpit");

  test("register, verify email, sign in, upload evidence and submit", async ({ page, request }) => {
    const email = `e2e-${Date.now()}@example.test`;

    await page.goto("/register");
    await page.getByLabel("Full name").fill("Playwright Applicant");
    await page.getByLabel(/^Email/).fill(email);
    await page.getByLabel(/^Password/).fill(PASSWORD);
    await page.getByLabel("Confirm password").fill(PASSWORD);
    await page.getByRole("button", { name: /continue/i }).click();

    await page.getByText("Cybersecurity student", { exact: true }).click();
    await page.getByRole("textbox", { name: /^Student ID/ }).fill("000-00-7777");
    await page.getByRole("textbox", { name: /^Batch/ }).fill("62");
    await page.getByRole("textbox", { name: /^Department/ }).fill("CSE");
    await page.getByRole("button", { name: /continue/i }).click();

    await page.getByRole("checkbox").check();
    await page.getByRole("button", { name: /create account/i }).click();
    await expect(page.getByRole("heading", { name: /check your inbox/i })).toBeVisible();

    const link = await verificationLink(request, email);
    await page.goto(link);
    await expect(page.getByText(/email verified/i)).toBeVisible();

    await signIn(page, email);
    await expect(page).toHaveURL(/\/onboarding/);
    await expect(page.getByRole("heading", { name: /your application/i })).toBeVisible();
    await expect(page.locator("main").getByText(/pending verification/i).first()).toBeVisible();

    await page.getByRole("button", { name: /start submission/i }).click();
    const fileInput = page.locator("#evidence-file");
    await fileInput.setInputFiles({ name: "fictional-card.png", mimeType: "image/png", buffer: fictionalPng() });
    await expect(page.getByText("fictional-card.png")).toBeVisible();
    await page.getByRole("button", { name: /submit for review/i }).click();
    await expect(page.getByText(/a moderator will review it/i)).toBeVisible();

    // Verified-only areas stay closed and route back to onboarding.
    for (const path of ["/home", "/members", "/connections", "/saved"]) {
      await page.goto(path).catch(() => undefined); // the guard may redirect mid-navigation
      await expect(page).toHaveURL(/\/onboarding/);
    }
  });

  test("anonymous visitors are sent to sign-in from protected areas", async ({ page }) => {
    for (const path of ["/home", "/members", "/connections", "/saved", "/settings/profile", "/admin/moderation", "/admin/reports"]) {
      await page.goto(path).catch(() => undefined); // the guard may redirect mid-navigation
      await expect(page).toHaveURL(/\/sign-in/);
    }
  });
});

async function signIn(page: Page, email: string) {
  await page.goto("/sign-in");
  await page.getByLabel(/^Email/).fill(email);
  await page.getByLabel(/^Password/).fill(PASSWORD);
  await page.getByRole("button", { name: /^sign in$/i }).click();
}

async function verificationLink(request: APIRequestContext, email: string): Promise<string> {
  for (let attempt = 0; attempt < 20; attempt++) {
    const search = await request.get(`${MAILPIT}/api/v1/search?query=${encodeURIComponent("to:" + email)}`);
    const body = await search.json();
    const message = body.messages?.[0];
    if (message) {
      const detail = await request.get(`${MAILPIT}/api/v1/message/${message.ID}`);
      const text: string = (await detail.json()).Text;
      const match = text.match(/https?:\/\/\S+\/verify-email\?token=[A-Za-z0-9_-]+/);
      if (match) return match[0].replace(/^https?:\/\/[^/]+/, "");
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error("verification email not received");
}

/** Tiny valid PNG (1×1, white) so the upload pipeline's magic-byte and decoder checks pass. */
function fictionalPng(): Buffer {
  return Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==",
    "base64",
  );
}
