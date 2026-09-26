import { describe, expect, it } from 'vitest';
import { messages } from './i18n';

/** Flattens the nested message tree into dotted key paths. */
function keyPaths(node: unknown, prefix = ''): string[] {
  if (node === null || typeof node !== 'object') {
    return prefix ? [prefix] : [];
  }
  return Object.entries(node as Record<string, unknown>).flatMap(
    ([key, value]) => keyPaths(value, prefix ? `${prefix}.${key}` : key),
  );
}

describe('i18n message parity', () => {
  it('English and Thai define the same message keys', () => {
    const en = keyPaths(messages.en);
    const th = keyPaths(messages.th);

    expect(en.filter((key) => !th.includes(key))).toEqual([]);
    expect(th.filter((key) => !en.includes(key))).toEqual([]);
  });

  it('leaves no English or Thai message blank', () => {
    for (const locale of ['en', 'th'] as const) {
      for (const key of keyPaths(messages[locale])) {
        const value = key
          .split('.')
          .reduce<unknown>(
            (node, part) =>
              node !== null && typeof node === 'object'
                ? (node as Record<string, unknown>)[part]
                : undefined,
            messages[locale],
          );
        expect(
          typeof value === 'string' && value.trim().length > 0,
          `${locale}:${key} is blank`,
        ).toBe(true);
      }
    }
  });
});
