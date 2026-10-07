import { defineConfig, devices } from '@playwright/test';
export default defineConfig({
  testDir: './tests/browser', fullyParallel: true, timeout: 30000,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: 'http://127.0.0.1:4173', trace: 'retain-on-failure' },
  webServer: { command: 'npm run preview', url: 'http://127.0.0.1:4173', reuseExistingServer: !process.env.CI },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 960 }, ...(process.env.CHAT_BROWSER_PATH ? { launchOptions: { executablePath: process.env.CHAT_BROWSER_PATH } } : {}) } },
    { name: 'mobile', use: { ...devices['Pixel 7'], ...(process.env.CHAT_BROWSER_PATH ? { launchOptions: { executablePath: process.env.CHAT_BROWSER_PATH } } : {}) } }
  ]
});
