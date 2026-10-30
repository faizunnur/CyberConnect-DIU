import { expect, test, type APIRequestContext, type Browser, type Page } from "@playwright/test";

/**
 * Publish → apply → close against a running stack (E2E_BASE_URL) whose backend has E2E_FIXTURES_ENABLED=true
 * (dev only). Two verified members are created through the dev fixture endpoint; everything else goes through the UI.
 */
test.describe("opportunities", () => {
  test.skip(!process.env.E2E_BASE_URL, "needs the full stack (E2E_BASE_URL) with E2E_FIXTURES_ENABLED=true");

  test("a member publishes an opportunity, another applies, the author sees the applicant and closes it", async ({ browser, request }) => {
    const author = await fixture(request, "e2e-author");
    const applicant = await fixture(request, "e2e-applicant");
    const title = `SOC analyst intern ${Date.now()}`;

    // Author publishes through the form.
    const authorPage = await signedIn(browser, author);
    await authorPage.goto("/opportunities/new");
    await authorPage.getByLabel(/^Title/).fill(title);
    await authorPage.getByLabel(/^Organisation/).fill("ACME Security");
    await authorPage.getByLabel(/^Description/).fill("Help the blue team triage alerts and tune detections during a 3-month internship.");
    await authorPage.getByRole("button", { name: /^publish$/i }).click();
    await expect(authorPage).toHaveURL(/\/opportunities\/[0-9a-f-]{36}$/);
    await expect(authorPage.getByRole("heading", { name: title })).toBeVisible();
    const url = authorPage.url();

    // Applicant finds it in the list and applies with a cover note.
    const applicantPage = await signedIn(browser, applicant);
    await applicantPage.goto("/opportunities");
    await expect(applicantPage.getByRole("link", { name: title })).toBeVisible();
    await applicantPage.goto(url);
    await applicantPage.getByLabel(/cover note/i).fill("I run a home SOC lab and love detection engineering.");
    await applicantPage.getByRole("button", { name: /submit application/i }).click();
    await expect(applicantPage.getByText(/application submitted/i)).toBeVisible();

    // Author reviews the applicant, shortlists, then closes; the applicant can no longer apply.
    await authorPage.reload();
    await expect(authorPage.getByText("I run a home SOC lab and love detection engineering.")).toBeVisible();
    await authorPage.getByRole("button", { name: /^shortlisted$/i }).click();
    await expect(authorPage.getByText(/^Shortlisted$/).first()).toBeVisible();
    await authorPage.getByRole("button", { name: /^close$/i }).click();
    await expect(authorPage.getByText(/this opportunity is closed/i)).toBeVisible();
    await applicantPage.reload();
    await expect(applicantPage.getByText(/application shortlisted/i)).toBeVisible();
    await applicantPage.goto("/opportunities");
    await expect(applicantPage.getByRole("link", { name: title })).toHaveCount(0);

    await authorPage.context().close();
    await applicantPage.context().close();
  });
});

interface Fixture {
  userId: string;
  email: string;
  password: string;
}

async function fixture(request: APIRequestContext, prefix: string): Promise<Fixture> {
  const csrf = await request.get("/api/v1/auth/csrf");
  expect(csrf.ok()).toBeTruthy();
  const token = (await request.storageState()).cookies.find((c) => c.name === "XSRF-TOKEN")?.value;
  const response = await request.post("/api/v1/dev/fixtures/verified-member", {
    data: { prefix, affiliationType: "ALUMNI", fullName: `${prefix} member` },
    headers: token ? { "X-XSRF-TOKEN": token } : {},
  });
  expect(response.status(), "dev fixtures endpoint must be enabled (E2E_FIXTURES_ENABLED=true)").toBe(201);
  return (await response.json()) as Fixture;
}

async function signedIn(browser: Browser, user: Fixture): Promise<Page> {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto("/sign-in");
  await page.getByLabel(/^Email/).fill(user.email);
  await page.getByLabel(/^Password/).fill(user.password);
  await page.getByRole("button", { name: /^sign in$/i }).click();
  await expect(page).toHaveURL(/\/home/);
  return page;
}
