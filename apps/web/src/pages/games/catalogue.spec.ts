import { describe, expect, it } from 'vitest';
import {
  cardMeta,
  freeCopies,
  playerCount,
  playTime,
  stockedBranches,
  wrapIndex,
} from './catalogue';
import { secondaryTitle } from './localized';
import { explodingKittensDetail } from '../../test/fixtures';

/** Echoes the key and its values, so assertions read the choice being made. */
const translate = (key: string, named: Record<string, unknown>) =>
  key + ' ' + JSON.stringify(named);

describe('catalogue display helpers', () => {
  it('writes a single-count range as one number', () => {
    expect(playerCount(4, 4, translate)).toBe(
      'catalogue.playersExact {"count":4}',
    );
    expect(playerCount(2, 5, translate)).toBe(
      'catalogue.players {"min":2,"max":5}',
    );
  });

  it('leaves an unknown play time out of the card line', () => {
    expect(playTime(null, translate)).toBe('');
    expect(cardMeta({ minPlayers: 2, maxPlayers: 5 }, translate)).toBe(
      'catalogue.players {"min":2,"max":5}',
    );
    expect(
      cardMeta(
        { minPlayers: 2, maxPlayers: 5, playTimeMinutes: 15 },
        translate,
      ),
    ).toBe(
      'catalogue.players {"min":2,"max":5} · catalogue.minutes {"minutes":15}',
    );
  });

  it('sums the free copies and lists only branches holding one', () => {
    expect(freeCopies(explodingKittensDetail.stock)).toBe(3);
    expect(
      stockedBranches(explodingKittensDetail.stock).map((b) => b.branchName),
    ).toEqual(['Big C Rama I', 'Central Rama II']);
  });

  it('wraps a gallery index past either end', () => {
    expect(wrapIndex(2, 2)).toBe(0);
    expect(wrapIndex(-1, 3)).toBe(2);
    expect(wrapIndex(1, 0)).toBe(0);
  });
});

describe('secondary title', () => {
  const title = { en: 'Exploding Kittens', th: 'เหมียวระเบิด' };

  it('shows the language the reader is not reading', () => {
    expect(secondaryTitle(title, 'th')).toBe('Exploding Kittens');
    expect(secondaryTitle(title, 'en')).toBe('เหมียวระเบิด');
  });

  it('shows nothing when the main title already fell back to English', () => {
    expect(secondaryTitle({ en: 'Splendor', th: '  ' }, 'th')).toBe('');
    expect(secondaryTitle({ en: 'Splendor', th: null }, 'en')).toBe('');
    expect(secondaryTitle(undefined, 'en')).toBe('');
  });
});
