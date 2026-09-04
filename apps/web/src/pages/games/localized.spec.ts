import { describe, expect, it } from 'vitest';
import { catalogueLocaleOf, resolveLocalized } from './localized';

describe('catalogueLocaleOf', () => {
  it('reads Thai as Thai and everything else as English', () => {
    expect(catalogueLocaleOf('th')).toBe('th');
    expect(catalogueLocaleOf('th-TH')).toBe('th');
    expect(catalogueLocaleOf('en')).toBe('en');
    expect(catalogueLocaleOf('en-GB')).toBe('en');
    // No German catalogue exists, so a German reader gets the entry every game
    // carries rather than nothing.
    expect(catalogueLocaleOf('de')).toBe('en');
  });
});

describe('resolveLocalized', () => {
  const title = { en: 'Ticket to Ride', th: 'ตั๋วรถไฟ' };

  it('shows the reader their own language when the catalogue has it', () => {
    expect(resolveLocalized(title, 'th')).toBe('ตั๋วรถไฟ');
    expect(resolveLocalized(title, 'en')).toBe('Ticket to Ride');
  });

  it('falls back to English when the translation is missing or blank', () => {
    expect(resolveLocalized({ en: 'Splendor', th: null }, 'th')).toBe(
      'Splendor',
    );
    expect(resolveLocalized({ en: 'Splendor' }, 'th')).toBe('Splendor');
    expect(resolveLocalized({ en: 'Splendor', th: '   ' }, 'th')).toBe(
      'Splendor',
    );
  });

  it('does not show one language in place of the other beyond the fallback', () => {
    // The rule falls back to English only. A description written in Thai alone
    // is shown to a Thai reader and to nobody else, which is what a catalogue
    // entry that was never translated means.
    expect(resolveLocalized({ en: null, th: 'สอนง่าย' }, 'th')).toBe('สอนง่าย');
    expect(resolveLocalized({ en: null, th: 'สอนง่าย' }, 'en')).toBe('');
  });

  it('resolves to nothing when there is nothing to show', () => {
    expect(resolveLocalized({ en: null, th: null }, 'th')).toBe('');
    expect(resolveLocalized(null, 'en')).toBe('');
    expect(resolveLocalized(undefined, 'en')).toBe('');
  });
});
