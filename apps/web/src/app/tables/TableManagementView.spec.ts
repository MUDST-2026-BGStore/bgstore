import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import { router } from '../../router';
import TableManagementView from './TableManagementView.vue';

describe('Owner table management screen', () => {
  it('renders the table management view on the default route', async () => {
    await router.push('/tables');
    await router.isReady();

    const wrapper = mount(TableManagementView, {
      global: {
        plugins: [router],
      },
    });

    expect(wrapper.text()).toContain('Table management');
    expect(wrapper.text()).toContain('Total tables');
    expect(wrapper.text()).toContain('+ Add table');
  });
});
