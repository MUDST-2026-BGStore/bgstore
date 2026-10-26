import { games } from './catalog';
export interface RetailVariant {
  id: string;
  gameId: string;
  language: 'en' | 'th';
  price: number;
}
// TODO(retail-api): Replace edition/SKU fixtures with authoritative variant IDs,
// price/currency, sale status, images and sellable stock per fulfillment location.
// Rental availability must never decide whether a sealed retail copy is sellable.
export const variants: RetailVariant[] = games.flatMap((game) =>
  game.retailPrice === null
    ? []
    : [
        {
          id: `${game.id}-en`,
          gameId: game.id,
          language: 'en' as const,
          price: game.retailPrice,
        },
        ...(['azul', 'codenames', 'catan'].includes(game.id)
          ? [
              {
                id: `${game.id}-th`,
                gameId: game.id,
                language: 'th' as const,
                price: game.retailPrice,
              },
            ]
          : []),
      ],
);
export const variantsFor = (gameId: string) =>
  variants.filter((variant) => variant.gameId === gameId);
