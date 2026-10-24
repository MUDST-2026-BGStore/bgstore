import { mount } from '@vue/test-utils';
import { nextTick } from 'vue';
import { createI18n } from 'vue-i18n';
import { afterEach, expect, it } from 'vitest';
import PhoneNumberInput from './PhoneInput.vue';

let wrapper: ReturnType<typeof mount>;
afterEach(() => {
  wrapper?.unmount();
  document.body.innerHTML = '';
});
async function searchCountries(page: ReturnType<typeof mount>) {
  await page.get('.country-trigger').trigger('click');
  return page.get('[role="combobox"]');
}
function render(value = '') {
  wrapper = mount(PhoneNumberInput, {
    attachTo: document.body,
    props: { id: 'phone', modelValue: value },
    global: {
      plugins: [
        createI18n({
          legacy: false,
          locale: 'en-US',
          messages: {
            'en-US': {
              phone: {
                label: 'Phone number',
                country: 'Country calling code',
                search: 'Search countries',
                noCountries: 'No countries found',
              },
            },
          },
        }),
      ],
    },
  });
  return wrapper;
}

it('loads an existing international number with its country and flag', () => {
  const page = render('+66812345678');
  expect(page.find('.country-trigger .fi-th').exists()).toBe(true);
  expect(page.get('.country-trigger').text()).toBe('+66');
  expect(page.get('#phone').attributes('placeholder')).toBe('81 234 5678');
  expect((page.get('#phone').element as HTMLInputElement).value).toContain(
    '81 234 5678',
  );
  expect(page.emitted('validity')?.at(-1)).toEqual([true]);
});

it('searches country names and saves a national number in international format', async () => {
  const page = render();
  const search = await searchCountries(page);
  await search.setValue('United Kingdom');
  expect(page.findAll('[role="option"]')).toHaveLength(1);
  expect(page.find('[role="option"] .fi-gb').exists()).toBe(true);
  await page.get('[role="option"] button').trigger('click');
  await page.get('#phone').setValue('020 7946 0018');
  expect(page.emitted('update:modelValue')?.at(-1)).toEqual(['+442079460018']);
  expect(page.emitted('validity')?.at(-1)).toEqual([true]);
});

it.each(['SG', '+65'])(
  'searches by country code or calling code: %s',
  async (query) => {
    const page = render();
    const search = await searchCountries(page);
    await search.setValue(query);
    expect(page.get('[role="option"]').text()).toContain('Singapore');
    await search.trigger('keydown', { key: 'Enter' });
    await page.get('#phone').setValue('8123 4567');
    expect(page.emitted('update:modelValue')?.at(-1)).toEqual(['+6581234567']);
  },
);

it('detects the country when a complete international number is pasted', async () => {
  const page = render();
  await page.get('#phone').setValue('+44 20 7946 0018');
  expect(page.find('.country-trigger .fi-gb').exists()).toBe(true);
  expect(page.emitted('update:modelValue')?.at(-1)).toEqual(['+442079460018']);
  expect(page.emitted('validity')?.at(-1)).toEqual([true]);
});

it('supports arrow keys, Enter, Escape and focus restoration', async () => {
  const page = render();
  const search = await searchCountries(page);
  await search.setValue('United');
  const first = search.attributes('aria-activedescendant');
  await search.trigger('keydown', { key: 'ArrowDown' });
  expect(search.attributes('aria-activedescendant')).not.toBe(first);
  await search.trigger('keydown', { key: 'Enter' });
  expect(page.get('.country-trigger').attributes('aria-expanded')).toBe(
    'false',
  );
  expect(document.activeElement).toBe(page.get('#phone').element);
  const reopened = await searchCountries(page);
  await reopened.trigger('keydown', { key: 'Escape' });
  expect(document.activeElement).toBe(page.get('.country-trigger').element);
  expect(page.find('[role="listbox"]').exists()).toBe(false);
});

it.each(['123', 'not a number', '+66812345678 ext. 123'])(
  'rejects incomplete, invalid, or extended numbers: %s',
  async (value) => {
    const page = render();
    await page.get('#phone').setValue(value);
    expect(page.emitted('validity')?.at(-1)).toEqual([false]);
  },
);

it('shows a no-results message and does not change the country on Enter', async () => {
  const page = render('+66812345678');
  const search = await searchCountries(page);
  await search.setValue('no such country');
  expect(page.text()).toContain('No countries found');
  await search.trigger('keydown', { key: 'Enter' });
  await search.trigger('keydown', { key: 'Escape' });
  expect(page.get('.country-trigger').text()).toBe('+66');
});

it('updates both inputs when the saved phone number changes', async () => {
  const page = render('+66812345678');
  await page.setProps({ modelValue: '+442079460018' });
  expect(page.get('.country-trigger').text()).toBe('+44');
  expect((page.get('#phone').element as HTMLInputElement).value).toContain(
    '20 7946 0018',
  );
});

it('closes the country search when focusing the phone number and keeps the number intact', async () => {
  const page = render('+66812345678');
  await searchCountries(page);
  const input = page.get('#phone').element as HTMLInputElement;
  input.focus();
  await nextTick();
  expect(page.find('[role="listbox"]').exists()).toBe(false);
  expect(page.get('.country-trigger').attributes('aria-expanded')).toBe(
    'false',
  );
  expect(document.activeElement).toBe(input);
  expect(input.value).toBe('81 234 5678');
  expect(page.emitted('update:modelValue')).toBeUndefined();
});

it.each([
  ['TH', '+66812345678', '0812345678', '81 234 5678'],
  ['GB', '+442079460018', '02079460018', '20 7946 0018'],
  ['US', '+12015550123', '2015550123', '201 555 0123'],
  ['CA', '+15062345678', '5062345678', '506 234 5678'],
  ['IT', '+390212345678', '0212345678', '02 1234 5678'],
  ['DE', '+4915123456789', '015123456789', '1512 3456789'],
  ['FR', '+33612345678', '0612345678', '6 12 34 56 78'],
  ['JP', '+819012345678', '09012345678', '90 1234 5678'],
  ['SG', '+6581234567', '81234567', '8123 4567'],
  ['AU', '+61412345678', '0412345678', '412 345 678'],
  ['IN', '+918123456789', '8123456789', '81234 56789'],
  ['BR', '+5511961234567', '11961234567', '11 96123 4567'],
])(
  'validates and spaces %s numbers without changing the saved international number',
  async (country, e164, input, display) => {
    const page = render(e164);
    expect(
      page.find(`.country-trigger .fi-${country.toLowerCase()}`).exists(),
    ).toBe(true);
    const phone = page.get('#phone');
    await phone.setValue(input);
    expect(page.emitted('validity')?.at(-1)).toEqual([true]);
    expect(page.emitted('update:modelValue')?.at(-1)).toEqual([e164]);
    await phone.trigger('blur');
    expect((phone.element as HTMLInputElement).value).toBe(display);
    // The displayed digits also work when typed without a domestic trunk prefix.
    await phone.setValue(display.replaceAll(' ', ''));
    expect(page.emitted('validity')?.at(-1)).toEqual([true]);
    expect(page.emitted('update:modelValue')?.at(-1)).toEqual([e164]);
  },
);

it.each([
  ['+6581234567', '12345678'], // Correct length, invalid Singapore prefix.
  ['+66812345678', '111111111'], // Correct length, invalid Thai prefix.
])(
  'rejects a number that does not match the selected country: %s / %s',
  async (existing, input) => {
    const page = render(existing);
    await page.get('#phone').setValue(input);
    expect(page.emitted('validity')?.at(-1)).toEqual([false]);
  },
);

it('does not reformat an invalid draft or lose it on blur', async () => {
  const page = render();
  await page.get('#phone').setValue('123');
  await page.get('#phone').trigger('blur');
  expect((page.get('#phone').element as HTMLInputElement).value).toBe('123');
  expect(page.emitted('validity')?.at(-1)).toEqual([false]);
});

it.each([
  ['US', '+12015550123', '4165550123', '+14165550123'],
  ['CA', '+15062345678', '2025550123', '+12025550123'],
  ['AX', '+358412345678', '412345678', '+358412345678'],
  ['BL', '+590690001234', '690001234', '+590690001234'],
  ['CC', '+61412345678', '412345678', '+61412345678'],
  ['CX', '+61412345678', '412345678', '+61412345678'],
  ['EH', '+212650123456', '650123456', '+212650123456'],
  ['IM', '+447924123456', '7924123456', '+447924123456'],
  ['MF', '+590690001234', '690001234', '+590690001234'],
  ['SJ', '+4741234567', '41234567', '+4741234567'],
  ['VA', '+393123456789', '3123456789', '+393123456789'],
])(
  'accepts shared numbering plans for %s without requiring an exact territory match',
  async (country, initial, input, expected) => {
    const page = render(initial);
    await searchCountries(page);
    await page
      .get(`.country-option:has(.fi-${country.toLowerCase()})`)
      .trigger('click');
    await page.get('#phone').setValue(input);
    expect(page.emitted('validity')?.at(-1)).toEqual([true]);
    expect(page.emitted('update:modelValue')?.at(-1)).toEqual([expected]);
  },
);

it('rejects a foreign international dialling prefix while another calling code is selected', async () => {
  const page = render('+12015550123');
  await page.get('#phone').setValue('011442079460018');
  expect(page.get('.country-trigger').text()).toBe('+1');
  expect(page.emitted('validity')?.at(-1)).toEqual([false]);
});
