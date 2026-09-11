import { describe, expect, it } from 'vitest';
import { pageItems } from './pagination';

describe('pageItems', () => {
  it.each([
    [1, 9, [1, 2, 3, '…', 9]],
    [2, 9, [1, 2, 3, '…', 9]],
    [3, 9, [1, 2, 3, 4, '…', 9]],
    [5, 9, [1, '…', 4, 5, 6, '…', 9]],
    [8, 9, [1, '…', 7, 8, 9]],
    [9, 9, [1, '…', 7, 8, 9]],
  ])('on page %i of %i shows %j', (current, total, expected) => {
    expect(pageItems(current, total)).toEqual(expected);
  });

  it('shows every page when there are few enough to fit', () => {
    expect(pageItems(1, 4)).toEqual([1, 2, 3, 4]);
  });

  it('shows a lone hidden page instead of an ellipsis standing in for it', () => {
    expect(pageItems(4, 6)).toEqual([1, 2, 3, 4, 5, 6]);
  });

  it('has nothing to show without pages', () => {
    expect(pageItems(1, 0)).toEqual([]);
  });
});
