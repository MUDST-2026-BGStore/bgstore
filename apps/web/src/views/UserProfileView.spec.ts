import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query';
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createI18n } from 'vue-i18n';
import { messages } from '../i18n';
import UserProfileView from './UserProfileView.vue';
import { client } from '../generated/api/client.gen';

const currentUser = {
  subject: 'a9c7022e-a678-4d50-aa1b-69c917001234',
  username: 'client@example.test',
  email: 'client@example.test',
  firstName: 'Local',
  lastName: 'Client',
  roles: ['CLIENT'] as const,
  clientProfile: { phone: '+66812345678', completed: true },
  onboardingRequired: false,
};

const mountProfile = () => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  queryClient.setQueryData(['current-user'], currentUser);
  const i18n = createI18n({ legacy: false, locale: 'en', messages });

  return mount(UserProfileView, {
    global: { plugins: [[VueQueryPlugin, { queryClient }], i18n] },
  });
};

describe('user profile view', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders all profile fields in view mode', async () => {
    const wrapper = mountProfile();
    await flushPromises();

    expect(wrapper.get('h1').text()).toBe('User profile');
    expect(wrapper.findAll('input')).toHaveLength(6);
    expect(wrapper.get('#profile-username').attributes('readonly')).toBe('');
    expect(wrapper.get('#profile-first-name').attributes('readonly')).toBe('');
    expect(wrapper.get('#profile-last-name').attributes('readonly')).toBe('');
    expect(wrapper.get('#profile-email').attributes('readonly')).toBe('');
    expect(wrapper.get('#profile-phone').attributes('readonly')).toBe('');
    expect(wrapper.get('#profile-password').attributes('readonly')).toBe('');
    expect(wrapper.find('#profile-confirm-password').exists()).toBe(false);
    expect(wrapper.get('#profile-phone').element).toHaveProperty(
      'value',
      '+66812345678',
    );
    expect(wrapper.text()).not.toContain(
      'Phone and profile photo are owned by BGStore.',
    );
    expect(wrapper.find('button[type="submit"]').exists()).toBe(false);
    expect(wrapper.get('.primary-button').text()).toBe('Edit profile');
  });

  it('updates the BGStore-owned phone number in edit mode', async () => {
    client.setConfig({ baseUrl: 'http://localhost/api/v1' });
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({ phone: '+66987654321', completed: true }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          ),
        ),
    );
    const wrapper = mountProfile();
    await flushPromises();

    await wrapper.get('.primary-button').trigger('click');
    expect(wrapper.find('#profile-confirm-password').exists()).toBe(true);
    expect(wrapper.find('input[type="file"]').exists()).toBe(true);
    expect(
      wrapper.get('#profile-username').attributes('readonly'),
    ).toBeUndefined();
    expect(
      wrapper.get('#profile-phone').attributes('readonly'),
    ).toBeUndefined();
    expect(
      wrapper.get('#profile-first-name').attributes('readonly'),
    ).toBeUndefined();
    expect(
      wrapper.get('#profile-last-name').attributes('readonly'),
    ).toBeUndefined();
    expect(
      wrapper.get('#profile-email').attributes('readonly'),
    ).toBeUndefined();
    expect(
      wrapper.get('#profile-password').attributes('readonly'),
    ).toBeUndefined();
    expect(
      wrapper.get('#profile-confirm-password').attributes('readonly'),
    ).toBeUndefined();
    await wrapper.get('#profile-phone').setValue('098 765 4321');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    const request = vi.mocked(fetch).mock.calls[0]?.[0] as Request;
    expect(request.url).toBe('http://localhost/api/v1/me/client-profile');
    expect(await request.text()).toBe('{"phone":"098 765 4321"}');
    expect(wrapper.text()).toContain('Your phone number has been updated.');
    expect(wrapper.find('button[type="submit"]').exists()).toBe(false);
  });

  it('restores the saved phone number when editing is cancelled', async () => {
    const wrapper = mountProfile();
    await flushPromises();

    await wrapper.get('.primary-button').trigger('click');
    await wrapper.get('#profile-phone').setValue('081 111 1111');
    await wrapper.get('.secondary-button').trigger('click');

    expect(wrapper.get('#profile-phone').element).toHaveProperty(
      'value',
      '+66812345678',
    );
    expect(wrapper.get('#profile-phone').attributes('readonly')).toBe('');
  });

  it('keeps Keycloak-owned changes in edit mode until an account API exists', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const wrapper = mountProfile();
    await flushPromises();

    await wrapper.get('.primary-button').trigger('click');
    await wrapper.get('#profile-first-name').setValue('Updated');
    await wrapper.get('form').trigger('submit');

    expect(fetchMock).not.toHaveBeenCalled();
    expect(wrapper.get('.status-pending').text()).toContain(
      'needs the account/profile-image API',
    );
    expect(wrapper.find('button[type="submit"]').exists()).toBe(true);
  });
});
