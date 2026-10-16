import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import { RouterLink, createRouter, createWebHistory } from 'vue-router';
import UiButton from './UiButton.vue';

// Regression: every prop bound on the dynamic component is spread onto
// RouterLink's root <a> and overrides the router's own `href`. UiButton must
// therefore omit the `href` key entirely when it renders a router link, or
// every link in the app loses its `href` (breaking link semantics and
// middle-click / open-in-new-tab).
const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/games', component: { template: '<div />' } }],
});

describe('UiButton', () => {
  it('renders an internal destination as a router link with an href', async () => {
    router.push('/');
    await router.isReady();
    const wrapper = mount(UiButton, {
      props: { to: '/games' },
      slots: { default: 'Go' },
      global: { plugins: [router] },
    });
    const link = wrapper.find('a');
    expect(link.attributes('href')).toBe('/games');
    expect(wrapper.findComponent(RouterLink).exists()).toBe(true);
  });

  it('renders an external destination as a plain anchor', () => {
    const wrapper = mount(UiButton, {
      props: { href: 'https://example.test' },
      slots: { default: 'Go' },
    });
    expect(wrapper.find('a').attributes('href')).toBe('https://example.test');
  });

  it('renders an action as a button', () => {
    const wrapper = mount(UiButton, {
      props: { type: 'submit' },
      slots: { default: 'Save' },
    });
    const button = wrapper.find('button');
    expect(button.attributes('type')).toBe('submit');
    expect(button.attributes('href')).toBeUndefined();
  });
});
