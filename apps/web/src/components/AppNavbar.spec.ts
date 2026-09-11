import { afterEach, describe, expect, it, vi } from 'vitest';
import AppNavbar from './AppNavbar.vue';
import { renderScreen, route, stubApi } from '../test/api-stub';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('application navbar', () => {
  it('shows public links and authentication actions to a guest', async () => {
    stubApi([route('/me', { status: 401 })]);
    const { wrapper } = await renderScreen(AppNavbar, '/');

    const navigation = wrapper.get('nav[aria-label="Primary navigation"]');
    expect(navigation.findAll('a').map((link) => link.text())).toEqual([
      'Home',
      'Game',
      'Branch',
    ]);
    expect(wrapper.get('a[href$="&signup"]').text()).toBe('Sign up');
  });

  it('shows client destinations without staff-only links', async () => {
    stubApi([
      route('/me', {
        body: {
          subject: 'client',
          username: 'client@example.test',
          email: 'client@example.test',
          firstName: 'Local',
          lastName: 'Client',
          roles: ['CLIENT'],
          clientProfile: { completed: true },
          onboardingRequired: false,
        },
      }),
    ]);
    const { wrapper } = await renderScreen(AppNavbar, '/history');

    const navigation = wrapper.get('nav[aria-label="Primary navigation"]');
    expect(navigation.findAll('a').map((link) => link.text())).toEqual([
      'Home',
      'Game',
      'Branch',
      'History',
      'Profile',
    ]);
    expect(
      navigation.find('a[href="/history"]').attributes('aria-current'),
    ).toBe('page');
    expect(navigation.find('a[href="/tables"]').exists()).toBe(false);
  });

  it('shows the operational navigation to staff', async () => {
    stubApi([
      route('/me', {
        body: {
          subject: 'staff',
          username: 'staff@example.test',
          email: 'staff@example.test',
          firstName: 'Local',
          lastName: 'Staff',
          roles: ['STAFF'],
          onboardingRequired: false,
        },
      }),
    ]);
    const { wrapper } = await renderScreen(AppNavbar, '/tables');

    const navigation = wrapper.get('nav[aria-label="Staff navigation"]');
    expect(navigation.findAll('a').map((link) => link.text())).toEqual([
      'Dashboard',
      'Branches',
      'Tables',
      'Games',
      'Profile',
    ]);
    expect(
      navigation.find('a[href="/tables"]').attributes('aria-current'),
    ).toBe('page');
    expect(wrapper.find('a[href$="&signup"]').exists()).toBe(false);
  });
});
