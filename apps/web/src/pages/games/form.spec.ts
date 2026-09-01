import { describe, expect, it } from 'vitest';
import {
  emptyForm,
  fieldErrorsOf,
  formValuesOf,
  hasStatus,
  toGameRequest,
} from './form';
import { branches, ticketToRide } from '../../test/fixtures';

describe('emptyForm', () => {
  it('gives every branch a row so copies can be entered against it', () => {
    const form = emptyForm(branches);

    expect(form.copies).toEqual([
      { branchId: branches[0].id, branchName: 'Big C Rama I', copies: '0' },
      { branchId: branches[1].id, branchName: 'Central Rama II', copies: '0' },
      { branchId: branches[2].id, branchName: 'Sukhumvit', copies: '0' },
    ]);
    expect(form.lifecycle).toBe('active');
    expect(form.category).toBe('');
  });
});

describe('formValuesOf', () => {
  it('fills the form from a loaded game, branch order first', () => {
    const form = formValuesOf(ticketToRide, branches);

    expect(form.title).toBe('Ticket to Ride');
    expect(form.category).toBe('family');
    expect(form.playTimeMinutes).toBe('60');
    expect(form.minPlayers).toBe('2');
    expect(form.maxPlayers).toBe('5');
    expect(form.difficulty).toBe('Easy to teach');
    expect(form.copies.map((row) => row.copies)).toEqual(['1', '2', '0']);
  });

  it('shows an absent optional value as an empty field, not "null"', () => {
    const form = formValuesOf(
      {
        ...ticketToRide,
        description: null,
        playTimeMinutes: null,
        difficulty: null,
      },
      branches,
    );

    expect(form.description).toBe('');
    expect(form.playTimeMinutes).toBe('');
    expect(form.difficulty).toBe('');
  });

  it('carries the lifecycle and tags the design gives no control for', () => {
    const retired = formValuesOf(
      { ...ticketToRide, lifecycle: 'retired' },
      branches,
    );

    expect(retired.lifecycle).toBe('retired');
    expect(retired.tags).toEqual(ticketToRide.tags);
  });
});

describe('toGameRequest', () => {
  it('converts the typed strings to the contract types', () => {
    const request = toGameRequest({
      ...formValuesOf(ticketToRide, branches),
      title: '  Catan  ',
      description: '  A route builder.  ',
      difficulty: '  Easy  ',
    });

    expect(request.title).toBe('Catan');
    expect(request.description).toBe('A route builder.');
    expect(request.difficulty).toBe('Easy');
    expect(request.minPlayers).toBe(2);
    expect(request.playTimeMinutes).toBe(60);
    expect(request.copies).toEqual([
      { branchId: branches[0].id, copies: 1 },
      { branchId: branches[1].id, copies: 2 },
      { branchId: branches[2].id, copies: 0 },
    ]);
  });

  it('sends blank optional text as nothing rather than an empty string', () => {
    const request = toGameRequest({
      ...emptyForm(branches),
      title: 'Catan',
      description: '   ',
      difficulty: '',
    });

    expect(request.description).toBeNull();
    expect(request.difficulty).toBeNull();
  });

  it('sends an unfilled or unparseable number as nothing, for the API to reject', () => {
    const request = toGameRequest({
      ...emptyForm(branches),
      title: 'Catan',
      minPlayers: '',
      maxPlayers: 'four',
      playTimeMinutes: '',
    });

    expect(request.minPlayers).toBeNull();
    expect(request.maxPlayers).toBeNull();
    expect(request.playTimeMinutes).toBeNull();
  });
});

describe('fieldErrorsOf', () => {
  it('maps each rejected field to its message key', () => {
    expect(
      fieldErrorsOf({
        status: 422,
        errors: [
          { field: 'title', message: 'required' },
          { field: 'copies[0].copies', message: 'belowInUse' },
        ],
      }),
    ).toEqual({ title: 'required', 'copies[0].copies': 'belowInUse' });
  });

  it('keeps the first message when a field is rejected twice', () => {
    expect(
      fieldErrorsOf({
        errors: [
          { field: 'title', message: 'required' },
          { field: 'title', message: 'invalid' },
        ],
      }),
    ).toEqual({ title: 'required' });
  });

  it('finds no fields in a failure that is not a validation problem', () => {
    expect(fieldErrorsOf({ status: 500, title: 'Server Error' })).toEqual({});
    expect(fieldErrorsOf(undefined)).toEqual({});
    expect(fieldErrorsOf(new Error('network'))).toEqual({});
  });
});

describe('hasStatus', () => {
  it('recognises the status the API reported', () => {
    expect(hasStatus({ status: 404 }, 404)).toBe(true);
    expect(hasStatus({ status: 500 }, 404)).toBe(false);
    expect(hasStatus(undefined, 404)).toBe(false);
  });
});
