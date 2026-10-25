import { mount } from '@vue/test-utils';
import { expect, it } from 'vitest';
import BranchSelect from './BranchSelect.vue';

it('searches a large branch directory without clearing or changing the selected branch', async () => {
  const options = Array.from({ length: 100 }, (_, index) => ({
    id: `branch-${index}`,
    name: `Branch ${String(index).padStart(3, '0')}`,
  }));
  const wrapper = mount(BranchSelect, {
    props: {
      modelValue: 'branch-0',
      options,
      name: 'store-branch',
      label: 'Branch',
    },
  });
  await wrapper.get('input[type=search]').setValue('Branch 090');
  expect(
    wrapper.findAll('option').map((option) => option.attributes('value')),
  ).toEqual(['', 'branch-0', 'branch-90']);
  expect((wrapper.get('select').element as HTMLSelectElement).value).toBe(
    'branch-0',
  );
  expect(wrapper.emitted('update:modelValue')).toBeUndefined();
  await wrapper.get('select').setValue('branch-90');
  expect(wrapper.emitted('update:modelValue')).toEqual([['branch-90']]);
  await wrapper.get('input[type=search]').setValue('missing place');
  expect(wrapper.get('[role=status]').text()).toContain('No matching branches');
  expect(wrapper.find('option[value=branch-0]').exists()).toBe(true);
  wrapper.unmount();
});
