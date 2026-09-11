import { describe, expect, it } from 'vitest';
import { branchLabel, formatDate, pageRange } from './display';
import { explodingKittens, splendor } from '../../test/fixtures';

describe('formatDate', () => {
  it('writes a timestamp the way the design writes dates', () => {
    expect(formatDate('2025-01-12T09:00:00Z', 'en')).toBe('12 Jan 2025');
  });

  it('has nothing to show for a missing or unparseable timestamp', () => {
    expect(formatDate(null, 'en')).toBeUndefined();
    expect(formatDate(undefined, 'en')).toBeUndefined();
    expect(formatDate('not a date', 'en')).toBeUndefined();
  });
});

describe('pageRange', () => {
  it('counts from one, not from the zero-based page index', () => {
    expect(pageRange(0, 20, 20)).toEqual({ from: 1, to: 20 });
    expect(pageRange(1, 20, 4)).toEqual({ from: 21, to: 24 });
  });
});

describe('branchLabel', () => {
  const many = (count: number) => count + ' branches';

  it('names the branch when the row covers exactly one', () => {
    expect(branchLabel(explodingKittens, many, '—')).toBe('Central Rama II');
  });

  it('counts the branches when the row covers several', () => {
    expect(branchLabel(splendor, many, '—')).toBe('2 branches');
  });

  it('falls back when the game is stocked nowhere', () => {
    expect(
      branchLabel({ ...splendor, branchCount: 0, copies: 0 }, many, '—'),
    ).toBe('—');
  });
});
