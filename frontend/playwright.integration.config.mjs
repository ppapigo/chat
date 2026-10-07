import { defineConfig, devices } from '@playwright/test';
if (!process.env.CHAT_TEST_BASE_URL) throw new Error('Run npm run test:integration to start the isolated Spring Boot test server.');
const launch = process.env.CHAT_BROWSER_PATH ? { launchOptions: { executablePath: process.env.CHAT_BROWSER_PATH } } : {};
export default defineConfig({
  testDir: './tests/integration', workers: 1, fullyParallel: false, timeout: 60000,
  reporter: [['list']],
  use: { baseURL: process.env.CHAT_TEST_BASE_URL, trace: 'retain-on-failure' },
  outputDir: './test-results/integration',
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 960 }, ...launch } },
    { name: 'mobile', use: { ...devices['Pixel 7'], ...launch } }
  ]
});
