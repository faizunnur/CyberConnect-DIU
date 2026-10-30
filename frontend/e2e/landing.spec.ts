import { expect, test } from "@playwright/test";

test.describe("public landing page", () => {
  test("renders the value proposition and primary actions", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveTitle(/CyberConnect DIU/);
    await expect(page.getByRole("heading", { level: 1 })).toContainText(/cybersecurity community/i);
    await expect(page.getByRole("link", { name: /create an account/i }).first()).toBeVisible();
    await expect(page.getByRole("link", { name: /sign in/i }).first()).toBeVisible();
  });

  test("explains verification and eligibility", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { name: /how verification works/i })).toBeVisible();
    await expect(page.getByText(/Cybersecurity students/i).first()).toBeVisible();
    await expect(page.getByText(/alumni/i).first()).toBeVisible();
    await expect(page.getByText(/Cyber Security Center/i).first()).toBeVisible();
  });

  test("has no horizontal overflow on small screens", async ({ page }) => {
    await page.goto("/");
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    );
    expect(overflow).toBe(false);
  });

  test("exposes a liveness endpoint", async ({ request }) => {
    const response = await request.get("/healthz");
    expect(response.status()).toBe(200);
    expect(await response.json()).toMatchObject({ status: "ok" });
  });
});
