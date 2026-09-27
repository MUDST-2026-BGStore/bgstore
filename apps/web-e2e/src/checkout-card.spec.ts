import { expect, test, type Page } from '@playwright/test';

const staffUser = {
  subject: 'staff-subject',
  username: 'staff@example.test',
  email: 'staff@example.test',
  firstName: 'Local',
  lastName: 'Staff',
  roles: ['STAFF'],
  onboardingRequired: false,
};

const checkedInSession = () => ({
  id: 'res-1',
  title: 'Ticket to Ride',
  date: '2026-09-20',
  timeSlot: '18:00–20:00',
  partySize: 4,
  tableId: 1,
  tableName: 'Map Room',
  seats: 6,
  ratePerHour: 20,
  status: 'CheckedIn',
  customerName: 'Somchai Jaidee',
  phoneNumber: '0812345678',
  checkInTime: '2026-09-20T18:05:00+07:00',
  actualCheckOut: '-',
  overtimeMinutes: 0,
  totalPrice: 120,
  canCancel: false,
});

const receipt = (overrides: Record<string, unknown> = {}) => ({
  reservationId: 'res-1',
  tableName: 'Map Room',
  partySize: 4,
  startedAt: '2026-09-20T18:05:00+07:00',
  endedAt: '2026-09-20T21:05:00+07:00',
  hours: 3,
  ratePerHour: 20,
  totalDue: 120,
  currency: 'THB',
  paymentMethod: 'Cash',
  paidAt: '2026-09-20T21:05:00+07:00',
  payment: null,
  ...overrides,
});

const problem = (status: number, code: string) => ({
  type: 'https://bgstore.example/problems/payment-declined',
  title: 'Payment declined',
  status,
  code,
});

/** Expiry helpers read the same Bangkok clock the card helpers do. */
const bangkokMonths = () => {
  const [year, month] = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Bangkok',
    year: 'numeric',
    month: '2-digit',
  })
    .format(new Date())
    .split('-')
    .map(Number);
  const print = (y: number, m: number) =>
    `${String(m).padStart(2, '0')}/${String(y).slice(2)}`;
  return {
    current: print(year, month),
    expired: month === 1 ? print(year - 1, 12) : print(year, month - 1),
  };
};

type CheckoutScript = (body: Record<string, unknown>) => {
  status: number;
  body: unknown;
};

/** Opens the check-out dialog for a checked-in session with a scripted gateway. */
async function openCheckout(page: Page, script: CheckoutScript) {
  const charges: Array<Record<string, unknown>> = [];
  await page.route('**/api/v1/me', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify(staffUser),
    }),
  );
  await page.route('**/api/v1/sessions', (route) =>
    route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ items: [checkedInSession()] }),
    }),
  );
  await page.route('**/api/v1/reservations/res-1/check-out', async (route) => {
    const body = route.request().postDataJSON() as Record<string, unknown>;
    charges.push(body);
    const outcome = script(body);
    await route.fulfill({
      status: outcome.status,
      contentType:
        outcome.status === 200
          ? 'application/json'
          : 'application/problem+json',
      body: JSON.stringify(outcome.body),
    });
  });

  await page.goto('/staff/sessions');
  const row = page.getByRole('row').filter({ hasText: 'Map Room' });
  await row.getByRole('button', { name: 'Check out' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toBeVisible();
  return { dialog, charges };
}

const decline = (code: string) => problem(402, code);

test.describe('session console card settlement', () => {
  test('keeps a Luhn-failing number out of the gateway', async ({ page }) => {
    const { dialog, charges } = await openCheckout(page, () => ({
      status: 200,
      body: receipt(),
    }));
    await dialog.getByLabel('Payment method').selectOption('Card');
    await dialog.getByLabel('Card number').fill('4242 4242 4242 4241');
    await dialog.getByLabel('Expiry (MM/YY)').fill('12/29');
    await dialog.getByLabel('Security code (CVV)').fill('555');

    // A disabled default button blocks the browser's implicit Enter submit, so
    // press Enter with the form alive: the validation path must explain itself.
    await dialog.getByLabel('Card number').press('Enter');
    await expect(
      dialog.getByText(
        'Enter a valid card number, expiry, and security code first.',
      ),
    ).toBeVisible();
    expect(charges).toHaveLength(0);
  });

  test('prints Amex grouping and wants a four-digit security code', async ({
    page,
  }) => {
    const { dialog, charges } = await openCheckout(page, () => ({
      status: 200,
      body: receipt({
        paymentMethod: 'Card',
        payment: {
          gateway: 'bogus',
          reference: 'bogus-amex-1',
          paidAt: '2026-09-20T21:05:00+07:00',
          card: { brand: 'Amex', last4: '0005' },
        },
      }),
    }));
    const confirm = dialog.getByRole('button', { name: 'Close session' });
    await dialog.getByLabel('Payment method').selectOption('Card');
    const number = dialog.getByLabel('Card number');
    await number.fill('378282246310005');
    await dialog.getByLabel('Expiry (MM/YY)').fill('12/29');

    // The read-out regroups into the Amex 4-6-5 print as the card is read.
    await expect(number).toHaveValue('3782 822463 10005');
    const cvv = dialog.getByLabel('Security code (CVV)');
    await cvv.fill('123');
    // Amex wants four digits; the confirmation explains the gap instead of
    // leaving a dead button.
    await confirm.click();
    await expect(
      dialog.getByText(
        'Enter a valid card number, expiry, and security code first.',
      ),
    ).toBeVisible();
    expect(charges).toHaveLength(0);

    await cvv.fill('1234');
    await confirm.click();
    await expect(
      dialog.getByRole('heading', { name: 'Session closed' }),
    ).toBeVisible();
    expect(charges[0].card).toEqual({
      number: '378282246310005',
      cvv: '1234',
      expiryMonth: 12,
      expiryYear: 2029,
    });
  });

  test('refuses an expired card locally instead of at the gateway', async ({
    page,
  }) => {
    const { dialog, charges } = await openCheckout(page, () => ({
      status: 200,
      body: receipt(),
    }));
    const confirm = dialog.getByRole('button', { name: 'Close session' });
    await dialog.getByLabel('Payment method').selectOption('Card');
    await dialog.getByLabel('Card number').fill('4242 4242 4242 4242');
    await dialog.getByLabel('Expiry (MM/YY)').fill(bangkokMonths().expired);
    await dialog.getByLabel('Security code (CVV)').fill('555');

    await expect(
      dialog.getByText('This card has already expired.'),
    ).toBeVisible();
    // Confirming anyway is answered by the form, never a silent dead button.
    await confirm.click();
    await expect(
      dialog.getByText(
        'Enter a valid card number, expiry, and security code first.',
      ),
    ).toBeVisible();
    expect(charges).toHaveLength(0);
  });

  test('keeps the current expiry month chargeable', async ({ page }) => {
    const { dialog, charges } = await openCheckout(page, () => ({
      status: 200,
      body: receipt({
        paymentMethod: 'Card',
        payment: {
          gateway: 'bogus',
          reference: 'bogus-ok',
          paidAt: '2026-09-20T21:05:00+07:00',
          card: { brand: 'Visa', last4: '4242' },
        },
      }),
    }));
    const confirm = dialog.getByRole('button', { name: 'Close session' });
    await dialog.getByLabel('Payment method').selectOption('Card');
    await dialog.getByLabel('Card number').fill('4242 4242 4242 4242');
    await dialog.getByLabel('Expiry (MM/YY)').fill(bangkokMonths().current);
    await dialog.getByLabel('Security code (CVV)').fill('555');
    await expect(confirm).toBeEnabled();
    await confirm.click();
    await expect(
      dialog.getByRole('heading', { name: 'Session closed' }),
    ).toBeVisible();
    expect(charges[0].card).toMatchObject(
      (() => {
        const [month, year] = bangkokMonths().current.split('/');
        return { expiryMonth: Number(month), expiryYear: 2000 + Number(year) };
      })(),
    );
  });

  test('scripts each decline reason from the security code and keeps the session open', async ({
    page,
  }) => {
    const { dialog, charges } = await openCheckout(page, (body) => {
      const cvv = (body.card as { cvv: string }).cvv;
      const map: Record<string, string> = {
        '111': 'insufficient_funds',
        '222': 'stolen_card',
        '333': 'cvv_mismatch',
      };
      return { status: 402, body: decline(map[cvv] ?? 'insufficient_funds') };
    });
    const confirm = dialog.getByRole('button', { name: 'Close session' });
    await dialog.getByLabel('Payment method').selectOption('Card');
    await dialog.getByLabel('Card number').fill('4242 4242 4242 4242');
    await dialog.getByLabel('Expiry (MM/YY)').fill('12/29');

    await dialog.getByLabel('Security code (CVV)').fill('111');
    await confirm.click();
    await expect(
      dialog.getByText(
        'The card network declined the charge: insufficient funds.',
      ),
    ).toBeVisible();

    await dialog.getByLabel('Security code (CVV)').fill('222');
    await confirm.click();
    await expect(
      dialog.getByText(
        'The card network declined the charge: the card is flagged and cannot be used.',
      ),
    ).toBeVisible();

    await dialog.getByLabel('Security code (CVV)').fill('333');
    await confirm.click();
    await expect(
      dialog.getByText(
        'The card was declined: the security code did not match.',
      ),
    ).toBeVisible();

    // Every decline left the dialog open and the session uncharged.
    expect(charges).toHaveLength(3);
    await expect(dialog).toBeVisible();
  });

  test('recovers from a gateway error and settles on retry', async ({
    page,
  }) => {
    const { dialog, charges } = await openCheckout(page, (body) => {
      const cvv = (body.card as { cvv: string }).cvv;
      return cvv === '999'
        ? { status: 502, body: problem(502, 'gateway_unavailable') }
        : {
            status: 200,
            body: receipt({
              paymentMethod: 'Card',
              payment: {
                gateway: 'bogus',
                reference: 'bogus-recovered',
                paidAt: '2026-09-20T21:05:00+07:00',
                card: { brand: 'Visa', last4: '4242' },
              },
            }),
          };
    });
    const confirm = dialog.getByRole('button', { name: 'Close session' });
    await dialog.getByLabel('Payment method').selectOption('Card');
    await dialog.getByLabel('Card number').fill('4242 4242 4242 4242');
    await dialog.getByLabel('Expiry (MM/YY)').fill('12/29');

    await dialog.getByLabel('Security code (CVV)').fill('999');
    await confirm.click();
    await expect(
      dialog.getByText(
        'The payment gateway is temporarily unavailable. Please try again.',
      ),
    ).toBeVisible();

    await dialog.getByLabel('Security code (CVV)').fill('444');
    await confirm.click();
    await expect(
      dialog.getByRole('heading', { name: 'Session closed' }),
    ).toBeVisible();
    await expect(dialog.getByText('bogus', { exact: true })).toBeVisible();
    await expect(dialog.getByText(/bogus-/).first()).toBeVisible();
    await expect(dialog.getByText('•••• 4242')).toBeVisible();
    expect(charges).toHaveLength(2);
    expect(charges[1].card).toMatchObject({ cvv: '444' });
  });

  test('waives the fee without charging', async ({ page }) => {
    const { dialog, charges } = await openCheckout(page, () => ({
      status: 200,
      body: receipt({ paymentMethod: 'Waived', payment: null }),
    }));
    const amount = dialog.getByLabel('Final amount (THB)');
    await dialog.getByLabel('Payment method').selectOption('Waived');
    await expect(amount).toBeDisabled();

    await dialog.getByRole('button', { name: 'Close session' }).click();
    await expect(
      dialog.getByRole('heading', { name: 'Session closed' }),
    ).toBeVisible();
    await expect(
      dialog.getByText('A waived fee closes the session without a charge.'),
    ).toBeVisible();
    expect(charges).toHaveLength(1);
    expect(charges[0]).toEqual({ finalAmount: 0, paymentMethod: 'Waived' });
  });

  test('shows the PromptPay QR for a PromptPay settlement', async ({
    page,
  }) => {
    const { dialog } = await openCheckout(page, () => ({
      status: 200,
      body: receipt({ paymentMethod: 'PromptPay' }),
    }));
    await dialog.getByLabel('Payment method').selectOption('PromptPay');
    await expect(dialog.locator('img[src*="promptpay"]')).toBeVisible();
    await expect(
      dialog.getByText('Demo QR from the bogus gateway', { exact: false }),
    ).toBeVisible();
  });

  test('demands a positive confirmed amount', async ({ page }) => {
    const { dialog, charges } = await openCheckout(page, () => ({
      status: 200,
      body: receipt(),
    }));
    const confirm = dialog.getByRole('button', { name: 'Close session' });
    const amount = dialog.getByLabel('Final amount (THB)');
    await amount.fill('');
    await confirm.click();
    await expect(dialog.getByText('Enter the confirmed amount.')).toBeVisible();

    await amount.fill('0');
    await confirm.click();
    await expect(
      dialog.getByText('A settled amount must be greater than 0.'),
    ).toBeVisible();
    expect(charges).toHaveLength(0);
  });
});
