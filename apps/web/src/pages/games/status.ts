import type { BadgeTone } from '../../components/ui/types';
import type { GameAvailability } from '../../generated/api/types.gen';

/** Status filter options, in the order the design lists them. */
export const gameStatuses: readonly GameAvailability[] = [
  'available',
  'allCopiesOut',
  'retired',
  'notStocked',
];

/** Badge colour for a status. Values double as `games.status.*` message keys. */
export function statusTone(status: GameAvailability): BadgeTone {
  if (status === 'available') {
    return 'success';
  }

  return status === 'allCopiesOut' ? 'warning' : 'neutral';
}
