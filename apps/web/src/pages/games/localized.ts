import type {
  CatalogueLocale,
  LocalizedDescription,
  LocalizedTitle,
} from '../../generated/api/types.gen';

/**
 * Catalogue metadata is published in both languages the store serves, and every
 * screen renders one of them. This is the single place the choice is made.
 */

/**
 * The catalogue locale behind an active i18n locale. Anything that is not Thai
 * reads the catalogue in English, which is the language every game carries.
 */
export function catalogueLocaleOf(locale: string): CatalogueLocale {
  return locale.toLowerCase().startsWith('th') ? 'th' : 'en';
}

/**
 * The contract's fallback rule, as `LocalizedTitle` states it: take the asked-for
 * language when it holds text, and fall back to English otherwise.
 *
 * <p>A title always resolves, because English is required on one. A description
 * can resolve to nothing, which callers render as no description at all rather
 * than as an empty line.
 */
export function resolveLocalized(
  text: LocalizedTitle | LocalizedDescription | null | undefined,
  locale: string,
): string {
  if (!text) {
    return '';
  }

  const preferred = catalogueLocaleOf(locale) === 'th' ? text.th : text.en;

  return withText(preferred) ?? withText(text.en) ?? '';
}

/** Blank text carries no more meaning than an absent value, so both fall back. */
function withText(value: string | null | undefined): string | undefined {
  return value && value.trim() !== '' ? value : undefined;
}
