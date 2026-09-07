import { describe, expect, it } from 'vitest';
import {
  emptyForm,
  fieldErrorsOf,
  formValuesOf,
  hasStatus,
  numericErrorsOf,
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
    expect(form.titleEn).toBe('');
    expect(form.titleTh).toBe('');
  });
});

describe('formValuesOf', () => {
  it('fills the form from a loaded game, branch order first', () => {
    const form = formValuesOf(ticketToRide, branches);

    // Each language is edited on its own, so the form holds both as stored
    // rather than the one a reader's locale would resolve to.
    expect(form.titleEn).toBe('Ticket to Ride');
    expect(form.titleTh).toBe('ตั๋วรถไฟ');
    expect(form.descriptionEn).toBe(
      'Build routes across the map — easy to teach.',
    );
    expect(form.descriptionTh).toBe('สร้างเส้นทางข้ามแผนที่ — สอนง่าย');
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
        title: { en: 'Ticket to Ride', th: null },
        description: null,
        playTimeMinutes: null,
        difficulty: null,
      },
      branches,
    );

    expect(form.titleTh).toBe('');
    expect(form.descriptionEn).toBe('');
    expect(form.descriptionTh).toBe('');
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
      titleEn: '  Catan  ',
      titleTh: '  คาทาน  ',
      descriptionEn: '  A route builder.  ',
      descriptionTh: '  เกมสร้างเส้นทาง  ',
      difficulty: '  Easy  ',
    });

    expect(request.title).toEqual({ en: 'Catan', th: 'คาทาน' });
    expect(request.description).toEqual({
      en: 'A route builder.',
      th: 'เกมสร้างเส้นทาง',
    });
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
      titleEn: 'Catan',
      titleTh: '   ',
      descriptionEn: '   ',
      descriptionTh: '',
      difficulty: '',
    });

    expect(request.title).toEqual({ en: 'Catan', th: null });
    // Neither language was filled in, so there is no description to send.
    expect(request.description).toBeNull();
    expect(request.difficulty).toBeNull();
  });

  it('sends a description written in only one language as that language alone', () => {
    const request = toGameRequest({
      ...emptyForm(branches),
      titleEn: 'Catan',
      descriptionTh: 'เกมสร้างเส้นทาง',
    });

    expect(request.description).toEqual({ en: null, th: 'เกมสร้างเส้นทาง' });
  });

  it('sends an unfilled number as nothing, for the API to report as missing', () => {
    const request = toGameRequest({
      ...emptyForm(branches),
      titleEn: 'Catan',
      minPlayers: '',
      playTimeMinutes: '',
    });

    expect(request.minPlayers).toBeNull();
    expect(request.playTimeMinutes).toBeNull();
  });
});

describe('numericErrorsOf', () => {
  it('calls out text that is not a number without calling it missing', () => {
    const errors = numericErrorsOf({
      ...emptyForm(branches),
      titleEn: 'Catan',
      minPlayers: 'four',
      maxPlayers: '',
      playTimeMinutes: '1.5',
    });

    // "four" and "1.5" are wrong values; a blank field is a missing one, and
    // the API is what reports that, so it is deliberately not listed here.
    expect(errors).toEqual({
      minPlayers: 'invalid',
      playTimeMinutes: 'invalid',
    });
  });

  it('names the branch row a bad copy count sits in', () => {
    const form = emptyForm(branches);
    form.copies[1].copies = 'two';

    expect(numericErrorsOf({ ...form, titleEn: 'Catan' })).toEqual({
      'copies[1].copies': 'invalid',
    });
  });

  it('finds nothing wrong with numbers that are whole and in range', () => {
    expect(numericErrorsOf(formValuesOf(ticketToRide, branches))).toEqual({});
  });

  it('treats a negative count as wrong rather than missing', () => {
    const errors = numericErrorsOf({
      ...emptyForm(branches),
      titleEn: 'Catan',
      minPlayers: '-2',
    });

    expect(errors).toEqual({ minPlayers: 'invalid' });
  });

  it('treats numbers outside JavaScript safe-integer range as wrong', () => {
    const errors = numericErrorsOf({
      ...emptyForm(branches),
      titleEn: 'Catan',
      minPlayers: '9007199254740992',
    });

    expect(errors).toEqual({ minPlayers: 'invalid' });
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
