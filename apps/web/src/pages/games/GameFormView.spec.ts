import { flushPromises } from '@vue/test-utils';
import { afterEach, describe, expect, it, vi } from 'vitest';
import GameFormView from './GameFormView.vue';
import { formValuesOf, type GameFormValues } from './form';
import { renderScreen, stubApi } from '../../test/api-stub';
import { branches, ticketToRide } from '../../test/fixtures';

/** The two pages above this view differ only in labels, so one set stands in. */
function props(values: GameFormValues, errors: Record<string, string> = {}) {
  return {
    breadcrumb: 'Edit game',
    pageTitle: 'Edit game — Ticket to Ride',
    secondaryLabel: 'Cancel',
    primaryLabel: 'Save changes',
    values,
    errors,
  };
}

async function renderForm(
  values: GameFormValues,
  errors: Record<string, string> = {},
) {
  stubApi([]);

  return renderScreen(GameFormView, '/games/x/edit', {
    props: props(values, errors),
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('game form view', () => {
  it('keeps what is being typed when the loaded game arrives again', async () => {
    const values = formValuesOf(ticketToRide, branches);
    const { wrapper } = await renderForm(values);

    await wrapper.get('#game-title').setValue('Half-typed title');
    await wrapper.get('#copies-0').setValue('7');

    // vue-query refetches on its own (window focus, an invalidation elsewhere)
    // and hands down a fresh object each time. That must not reach the form.
    await wrapper.setProps(props(formValuesOf(ticketToRide, branches)));
    await flushPromises();

    expect(wrapper.get<HTMLInputElement>('#game-title').element.value).toBe(
      'Half-typed title',
    );
    expect(wrapper.get<HTMLInputElement>('#copies-0').element.value).toBe('7');
  });

  it('never writes back into the object it was seeded from', async () => {
    const values = formValuesOf(ticketToRide, branches);
    const { wrapper } = await renderForm(values);

    await wrapper.get('#game-title').setValue('Something else');
    await wrapper.get('#copies-0').setValue('9');

    expect(values.title).toBe('Ticket to Ride');
    expect(values.copies[0].copies).toBe('1');
  });

  it('shows the submit button working and blocks a second submit', async () => {
    const values = formValuesOf(ticketToRide, branches);
    const { wrapper } = await renderScreen(GameFormView, '/games/x/edit', {
      props: { ...props(values), pending: true },
    });

    const submit = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Saving…');
    expect(submit?.attributes('disabled')).toBeDefined();
  });

  it('reports a failure that named no field', async () => {
    const { wrapper } = await renderScreen(GameFormView, '/games/x/edit', {
      props: { ...props(formValuesOf(ticketToRide, branches)), failed: true },
    });

    expect(wrapper.get('[data-testid="form-error"]').text()).toBe(
      'The game could not be saved.',
    );
  });

  it('emits the current values on submit', async () => {
    const { wrapper } = await renderForm(formValuesOf(ticketToRide, branches));

    await wrapper.get('#game-title').setValue('Ticket to Ride Europe');
    await wrapper.get('form').trigger('submit');

    const submitted = wrapper.emitted('submit');
    expect(submitted).toHaveLength(1);
    expect((submitted?.[0][0] as GameFormValues).title).toBe(
      'Ticket to Ride Europe',
    );
  });
});
