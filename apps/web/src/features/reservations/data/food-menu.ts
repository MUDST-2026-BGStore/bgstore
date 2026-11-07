import type { FoodMenuItem } from '../types';

export const FOOD_MENU_ITEMS: FoodMenuItem[] = [
  // Beverages
  {
    id: 'bev-cold-brew',
    name: 'Slow-Drip Cold Brew',
    description: 'Steeped for 18 hours with single-origin beans. Smooth, chocolatey, low acidity.',
    category: 'beverages',
    price: 95,
    popular: true,
  },
  {
    id: 'bev-matcha-latte',
    name: 'Ceremonial Matcha Latte',
    description: 'Uji matcha whisked fresh with oat milk and light organic honey.',
    category: 'beverages',
    price: 110,
    dietary: 'Vegetarian',
    popular: true,
  },
  {
    id: 'bev-yuzu-fizz',
    name: 'Sparkling Yuzu Fizz',
    description: 'Refreshing Japanese yuzu juice, mint leaves, and bubbly soda water.',
    category: 'beverages',
    price: 85,
    dietary: 'Vegan',
  },
  {
    id: 'bev-earl-grey-pot',
    name: 'Earl Grey Artisan Tea Pot',
    description: 'Bergamot-infused whole leaf black tea. Serves 2 with free hot water refil.',
    category: 'beverages',
    price: 120,
    dietary: 'Vegan',
  },
  {
    id: 'bev-craft-soda',
    name: 'Old-Fashioned Craft Cola',
    description: 'Small-batch craft soda with natural botanicals, cane sugar, and citrus peel.',
    category: 'beverages',
    price: 75,
    dietary: 'Vegan',
  },

  // Snacks & Bites
  {
    id: 'snack-truffle-fries',
    name: 'Truffle Parmesan Fries',
    description: 'Hand-cut crispy potatoes tossed in white truffle oil, shaved parmesan, and garlic aioli.',
    category: 'snacks',
    price: 140,
    dietary: 'Vegetarian',
    popular: true,
  },
  {
    id: 'snack-nachos',
    name: 'Boardgamer’s Nacho Stack',
    description: 'Layered corn tortillas, warm queso, black beans, pico de gallo, and sour cream.',
    category: 'snacks',
    price: 180,
    dietary: 'Vegetarian',
    popular: true,
  },
  {
    id: 'snack-karaage',
    name: 'Japanese Crispy Chicken Karaage',
    description: 'Soy-ginger marinated bite-sized chicken thighs served with spicy Japanese mayo.',
    category: 'snacks',
    price: 135,
  },
  {
    id: 'snack-edamame',
    name: 'Smoked Sea Salt Edamame',
    description: 'Steamed soy beans seasoned with flaky smoked Maldon sea salt.',
    category: 'snacks',
    price: 70,
    dietary: 'Vegan',
  },

  // Platters for Groups
  {
    id: 'plat-party-spread',
    name: 'The Guildmaster Party Platter',
    description: 'Generous sharing platter: buffalo wings, onion rings, waffle fries, and 3 house dips (serves 3-4).',
    category: 'platters',
    price: 350,
    popular: true,
  },
  {
    id: 'plat-cheese-charcuterie',
    name: 'Artisan Cheese & Charcuterie Board',
    description: 'Aged white cheddar, brie, prosciutto, salami, crackers, fig jam, and fresh grapes.',
    category: 'platters',
    price: 390,
  },

  // Artisan Sandwiches
  {
    id: 'sand-ham-gruyere',
    name: 'Smoked Ham & Gruyère Melt',
    description: 'Artisan sourdough grilled with Dijon mustard, forest ham, and melted Swiss Gruyère.',
    category: 'sandwiches',
    price: 165,
  },
  {
    id: 'sand-truffle-mushroom',
    name: 'Wild Mushroom & Fontina Toastie',
    description: 'Sautéed forest mushrooms, melted fontina cheese, fresh thyme, and sourdough crust.',
    category: 'sandwiches',
    price: 155,
    dietary: 'Vegetarian',
  },

  // Sweets
  {
    id: 'sw-brownie-mode',
    name: 'Warm Fudge Brownie à la Mode',
    description: 'Decadent Belgian dark chocolate brownie topped with Madagascar vanilla ice cream.',
    category: 'sweets',
    price: 125,
    dietary: 'Vegetarian',
    popular: true,
  },
  {
    id: 'sw-belgian-waffle',
    name: 'Liege Sugar Waffle',
    description: 'Crispy caramelized pearl sugar waffle served with strawberries and whipped cream.',
    category: 'sweets',
    price: 110,
    dietary: 'Vegetarian',
  },
];

export const FOOD_CATEGORIES = [
  { id: 'all', label: 'All Items' },
  { id: 'beverages', label: 'Drinks & Coffee' },
  { id: 'snacks', label: 'Finger Food & Bites' },
  { id: 'platters', label: 'Sharing Platters' },
  { id: 'sandwiches', label: 'Grilled Sandwiches' },
  { id: 'sweets', label: 'Desserts' },
] as const;

