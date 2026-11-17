import { test, expect } from '@playwright/test';

test('Property App Manager loads successfully', async ({ page }) => {
  const response = await page.goto('/');

  expect(response).not.toBeNull();
  expect(response!.status()).toBe(200);

  await expect(page.locator('body')).toBeVisible();

  console.log('Property App Manager loaded successfully');
});
