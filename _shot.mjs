import { chromium } from "playwright";

const OUT = process.argv[2];
const THEME = process.argv[3] ?? "light";

const browser = await chromium.launch();
const page = await browser.newPage({
  viewport: { width: 1440, height: 1000 },
  deviceScaleFactor: 1,
  colorScheme: THEME === "dark" ? "dark" : "light",
});

const errors = [];
page.on("console", (m) => m.type() === "error" && errors.push(m.text()));
page.on("pageerror", (e) => errors.push(String(e)));
page.on("requestfailed", (r) => errors.push(`FAILED ${r.url()}`));

await page.goto("http://localhost:4319/", { waitUntil: "networkidle" });
await page.waitForTimeout(9000); // let the transcript reveal finish
await page.screenshot({ path: OUT, fullPage: true });

// Horizontal overflow check — the one defect a screenshot hides at the fold.
const overflow = await page.evaluate(
  () => document.documentElement.scrollWidth - document.documentElement.clientWidth
);
console.log(JSON.stringify({ overflow, errors }, null, 1));
await browser.close();
