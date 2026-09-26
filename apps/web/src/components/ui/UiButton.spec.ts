import { describe, expect, it } from 'vitest';
import UiButton from './UiButton.vue';
import { renderScreen } from '../../test/api-stub';

describe('UiButton', () => {
  it('renders a router link with a real href when `to` is set', async () => {
    const { wrapper } = await renderScreen(UiButton, '/', {
      props: { to: '/games' },
    });
    const anchor = wrapper.find('a');
    expect(anchor.exists()).toBe(true);
    expect(anchor.attributes('href')).toBe('/games');
  });

  it('renders a plain anchor with the given href when `href` is set', async () => {
    const { wrapper } = await renderScreen(UiButton, '/', {
      props: { href: '/auth/sign-in' },
    });
    const anchor = wrapper.find('a');
    expect(anchor.attributes('href')).toBe('/auth/sign-in');
  });

  it('renders a button with the default type when neither is set', async () => {
    const { wrapper } = await renderScreen(UiButton, '/', {
      props: {},
    });
    const button = wrapper.find('button');
    expect(button.exists()).toBe(true);
    expect(button.attributes('type')).toBe('button');
  });
});
