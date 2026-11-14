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
    attachTo: document.body,
  });
  const choices = () =>
    wrapper
      .findAll('[role=option]')
      .map((option) => option.text().replace('✓', ''));
  await wrapper.get('input[type=search]').setValue('Branch 090');
  expect(choices()).toEqual(['Branch 000', 'Branch 090']);
  expect(wrapper.get('input[type=hidden]').element).toHaveProperty(
    'value',
    'branch-0',
  );
  expect(wrapper.emitted('update:modelValue')).toBeUndefined();
  await wrapper.findAll('[role=option]')[1].trigger('click');
  expect(wrapper.emitted('update:modelValue')).toEqual([['branch-90']]);
  await wrapper.get('input[type=search]').setValue('missing place');
  expect(wrapper.get('[role=status]').text()).toContain('No matching branches');
  expect(choices()).toEqual(['Branch 000']);
  wrapper.unmount();
});

it('matches branch addresses while keeping the selected branch visible', async () => {
  const options = Array.from({ length: 10 }, (_, index) => ({
    id: `branch-${index}`,
    name: `Branch ${index}`,
    address: index === 5 ? '991 Rama I Rd, Pathum Wan' : `${index} Silom Rd`,
  }));
  const wrapper = mount(BranchSelect, {
    props: {
      modelValue: 'branch-0',
      options,
      name: 'store-branch',
      label: 'Branch',
    },
    attachTo: document.body,
  });
  const choices = () =>
    wrapper
      .findAll('[role=option]')
      .map((option) => option.text().replace('✓', ''));
  await wrapper.get('input[type=search]').setValue('pathum wan');
  expect(choices()).toEqual(['Branch 0', 'Branch 5']);
  wrapper.unmount();
});
