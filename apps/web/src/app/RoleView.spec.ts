import { afterEach, describe, expect, it, vi } from 'vitest';
import { h, markRaw, type Component } from 'vue';
import RoleView from './RoleView.vue';
import { hasStaffAccess } from '../queries/current-user';
import { renderScreen, route, stubApi } from '../test/api-stub';
import type { ApplicationRole } from '../generated/api/types.gen';

/** Stand-ins, so the assertion is about which screen was picked, not its contents. */
const screen = (text: string): Component =>
  markRaw({ render: () => h('p', text) });
const Staff = screen('staff screen');
const Client = screen('client screen');

afterEach(() => {
  vi.unstubAllGlobals();
});

async function renderAs(roles: ApplicationRole[]) {
  stubApi([
    route('/me', {
      body: {
        subject: 'someone',
        username: 'someone',
        email: 'someone@example.test',
        firstName: 'Some',
        lastName: 'One',
        roles,
        onboardingRequired: false,
      },
    }),
  ]);
  const { wrapper } = await renderScreen(RoleView, '/games', {
    props: { staff: Staff, client: Client },
  });

  return wrapper;
}

describe('role view', () => {
  it('shows a guest the client screen', async () => {
    expect((await renderAs(['CLIENT'])).text()).toBe('client screen');
  });

  it('shows staff and managers the staff screen, even alongside a client role', async () => {
    expect((await renderAs(['STAFF'])).text()).toBe('staff screen');
    expect((await renderAs(['CLIENT', 'MANAGER'])).text()).toBe('staff screen');
  });

  it('treats a user with no application role as a guest', () => {
    expect(hasStaffAccess([])).toBe(false);
  });
});
