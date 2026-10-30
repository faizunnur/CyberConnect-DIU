import { defineConfig, devices } from "@playwright/test";

/**
 * E2E_BASE_URL targets a running stack (for example the nginx origin http://localhost:8088).
 * Without it, Playwright starts `pnpm dev` and tests the frontend alone.
 */
const baseURL = process.env.E2E_BASE_URL ?? "http://localhost:3000";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 1,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL,
    trace: "on-first-retry",
  },
  projects: [
    { name: "desktop-chromium", use: { ...devices["Desktop Chrome"] } },
    { name: "mobile-chromium", use: { ...devices["Pixel 7"] } },
  ],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: "pnpm dev",
        url: "http://localhost:3000/healthz",
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
});
