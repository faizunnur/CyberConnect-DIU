import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "@playwright/test";

/** WCAG 2.2 AA smoke checks (axe-core) on the public pages; they need no backend. */
const pages = ["/", "/sign-in", "/register", "/privacy", "/terms"];

for (const path of pages) {
  test(`${path} has no serious or critical accessibility violations`, async ({ page }) => {
    await page.goto(path);
    await page.waitForLoadState("networkidle");
    const results = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"]).analyze();
    const blocking = results.violations.filter((v) => v.impact === "serious" || v.impact === "critical");
    expect(blocking, JSON.stringify(blocking.map((v) => ({ id: v.id, help: v.help, nodes: v.nodes.map((n) => n.target) })), null, 2)).toEqual([]);
  });
}
