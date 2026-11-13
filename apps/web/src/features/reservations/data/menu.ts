import type { BranchMenuItem } from '@theparlor/api-client';
import type { FoodCategory, FoodMenuItem } from '../types';

/** A branch menu row as the food picker shows it; prices become whole THB. */
export function toFoodMenuItem({
  item,
  available,
}: BranchMenuItem): FoodMenuItem {
  return {
    id: item.id,
    name: item.names['en-US'] ?? item.sku,
    nameTh: item.names['th-TH'],
    description: item.descriptions?.['en-US'] ?? '',
    descriptionTh: item.descriptions?.['th-TH'],
    category: item.category.toLowerCase() as FoodCategory,
    price: item.price.amountMinor / 100,
    dietary: item.dietary,
    popular: item.popular,
    available,
  };
}
