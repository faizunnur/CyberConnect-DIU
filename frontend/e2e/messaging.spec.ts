import { expect, test, type APIRequestContext, type Browser, type Page } from "@playwright/test";

/**
 * Message request flow against a running stack with E2E_FIXTURES_ENABLED=true: a member messages a stranger from
 * their profile, the stranger sees a request, accepts and replies, and both see the thread; the bell shows a
 * notification for the request.
 */
test.describe("messaging", () => {
  test.skip(!process.env.E2E_BASE_URL, "needs the full stack (E2E_BASE_URL) with E2E_FIXTURES_ENABLED=true");

  test("message request, accept, reply", async ({ browser, request }) => {
    const sender = await fixture(request, "e2e-sender");
    const recipient = await fixture(request, "e2e-recipient");
    const greeting = `Hello from Playwright ${Date.now()}`;

    const senderPage = await signedIn(browser, sender);
    await senderPage.goto(`/members/${recipient.userId}`);
    await senderPage.getByRole("button", { name: /^message$/i }).click();
    await senderPage.getByLabel(/^Message$/).fill(greeting);
    await senderPage.getByRole("button", { name: /^send$/i }).click();
    await expect(senderPage).toHaveURL(/\/messages\/[0-9a-f-]{36}/);
    await expect(thread(senderPage).getByText(greeting)).toBeVisible(); // the list preview is hidden on mobile
    await expect(senderPage.getByText(/wait for the member to accept/i)).toBeVisible();

    const recipientPage = await signedIn(browser, recipient);
    await recipientPage.goto("/messages?box=requests");
    await recipientPage.getByRole("link", { name: new RegExp(sender.email.split("@")[0] + "|e2e-sender member", "i") }).first().click();
    await expect(thread(recipientPage).getByText(greeting)).toBeVisible();
    await recipientPage.getByRole("button", { name: /^accept$/i }).click();
    await recipientPage.getByLabel(/^Message$/).fill("Accepted, nice to meet you");
    await recipientPage.getByRole("button", { name: /^send$/i }).click();
    await expect(thread(recipientPage).getByText("Accepted, nice to meet you")).toBeVisible();

    await senderPage.reload();
    await expect(thread(senderPage).getByText("Accepted, nice to meet you")).toBeVisible();
    await expect(senderPage.getByLabel(/^Message$/)).toBeEnabled();

    await recipientPage.goto("/notifications");
    await expect(recipientPage.getByText(/new message request/i).first()).toBeVisible();

    await senderPage.context().close();
    await recipientPage.context().close();
  });
});

/** The conversation pane (the list beside it also shows previews and is hidden on mobile). */
function thread(page: Page) {
  return page.locator("main section");
}

interface Fixture {
  userId: string;
  email: string;
  password: string;
}

async function fixture(request: APIRequestContext, prefix: string): Promise<Fixture> {
  await request.get("/api/v1/auth/csrf");
  const token = (await request.storageState()).cookies.find((c) => c.name === "XSRF-TOKEN")?.value;
  const response = await request.post("/api/v1/dev/fixtures/verified-member", {
    data: { prefix, affiliationType: "STUDENT", fullName: `${prefix} member` },
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
