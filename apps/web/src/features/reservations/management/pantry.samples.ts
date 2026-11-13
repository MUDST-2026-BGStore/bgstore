import type { BranchMenuItem } from '@theparlor/api-client';

export interface FoodDrinkStockItem {
  id: string;
  menuItemId?: string;
  name: string;
  nameTh: string;
  category: 'beverages' | 'snacks' | 'tea_coffee' | 'bakery_desserts';
  price: number;
  costPrice: number;
  unit: string;
  currentStock: number;
  lowStockThreshold: number;
}

const pantrySkuMeta: Record<
  string,
  { costPrice: number; unit: string; lowStockThreshold: number }
> = {
  'bev-coca-cola': { costPrice: 18, unit: 'cans', lowStockThreshold: 12 },
  'bev-coke-zero': { costPrice: 18, unit: 'cans', lowStockThreshold: 12 },
  'bev-sprite': { costPrice: 18, unit: 'cans', lowStockThreshold: 10 },
  'bev-singha-soda': { costPrice: 12, unit: 'bottles', lowStockThreshold: 10 },
  'bev-oishi-lemon': { costPrice: 16, unit: 'bottles', lowStockThreshold: 8 },
  'bev-oishi-genmai': { costPrice: 16, unit: 'bottles', lowStockThreshold: 8 },
  'bev-lipton-tea': { costPrice: 18, unit: 'cans', lowStockThreshold: 10 },
  'bev-mineral-water': {
    costPrice: 10,
    unit: 'bottles',
    lowStockThreshold: 12,
  },
  'snack-lays-classic': { costPrice: 20, unit: 'bags', lowStockThreshold: 8 },
  'snack-lays-seaweed': { costPrice: 20, unit: 'bags', lowStockThreshold: 8 },
  'snack-lays-bbq': { costPrice: 20, unit: 'bags', lowStockThreshold: 8 },
  'snack-pringles-sour-cream': {
    costPrice: 32,
    unit: 'cans',
    lowStockThreshold: 6,
  },
  'snack-pocky-chocolate': {
    costPrice: 14,
    unit: 'boxes',
    lowStockThreshold: 10,
  },
  'snack-taro-fish': { costPrice: 18, unit: 'packs', lowStockThreshold: 8 },
  'snack-bento-squid': { costPrice: 14, unit: 'packs', lowStockThreshold: 8 },
  'snack-tong-garden-nuts': {
    costPrice: 18,
    unit: 'packs',
    lowStockThreshold: 8,
  },
};

export function toPantryStockItem(
  { item, available }: BranchMenuItem,
  previous?: FoodDrinkStockItem,
): FoodDrinkStockItem {
  const id = item.sku.toLowerCase();
  const meta = pantrySkuMeta[id];
  const price = Math.round(item.price.amountMinor / 100);
  const category = item.category === 'SNACKS' ? 'snacks' : 'beverages';
  return {
    id,
    menuItemId: item.id,
    name: item.names['en-US'] ?? previous?.name ?? item.sku,
    nameTh:
      item.names['th-TH'] ??
      previous?.nameTh ??
      item.names['en-US'] ??
      item.sku,
    category: previous?.category ?? category,
    price,
    costPrice:
      previous?.costPrice ?? meta?.costPrice ?? Math.round(price * 0.5),
    unit:
      previous?.unit ??
      meta?.unit ??
      (category === 'snacks' ? 'packs' : 'cans'),
    currentStock: available,
    lowStockThreshold:
      previous?.lowStockThreshold ?? meta?.lowStockThreshold ?? 10,
  };
}

export function createPantrySamples(): FoodDrinkStockItem[] {
  return [
    {
      id: 'bev-coca-cola',
      name: 'Coca-Cola Original (Can 325ml)',
      nameTh: 'โคคา-โคล่า รสชาติต้นตำรับ (กระป๋อง)',
      category: 'beverages',
      price: 35,
      costPrice: 18,
      unit: 'cans',
      currentStock: 48,
      lowStockThreshold: 12,
    },
    {
      id: 'bev-coke-zero',
      name: 'Coca-Cola Zero Sugar (Can 325ml)',
      nameTh: 'โค้กไม่มีน้ำตาล ซีโร่ (กระป๋อง)',
      category: 'beverages',
      price: 35,
      costPrice: 18,
      unit: 'cans',
      currentStock: 36,
      lowStockThreshold: 12,
    },
    {
      id: 'bev-sprite',
      name: 'Sprite Lemon-Lime (Can 325ml)',
      nameTh: 'สไปรท์ เลมอน-ไลม์ (กระป๋อง)',
      category: 'beverages',
      price: 35,
      costPrice: 18,
      unit: 'cans',
      currentStock: 24,
      lowStockThreshold: 10,
    },
    {
      id: 'bev-singha-soda',
      name: 'Singha Soda Water (Bottle 325ml)',
      nameTh: 'สิงห์โซดา (ขวดแก้ว)',
      category: 'beverages',
      price: 25,
      costPrice: 12,
      unit: 'bottles',
      currentStock: 30,
      lowStockThreshold: 10,
    },
    {
      id: 'bev-oishi-lemon',
      name: 'Oishi Honey Lemon Green Tea (Bottle 350ml)',
      nameTh: 'ชาเขียวโออิชิ น้ำผึ้งมะนาว',
      category: 'beverages',
      price: 30,
      costPrice: 16,
      unit: 'bottles',
      currentStock: 18,
      lowStockThreshold: 8,
    },
    {
      id: 'bev-lipton-tea',
      name: 'Lipton Iced Lemon Tea (Can 325ml)',
      nameTh: 'ชามะนาวลิปตัน (กระป๋อง)',
      category: 'beverages',
      price: 35,
      costPrice: 18,
      unit: 'cans',
      currentStock: 5,
      lowStockThreshold: 10,
    },
    {
      id: 'snack-truffle-fries',
      name: 'Truffle & Parmesan Hand-Cut Fries',
      nameTh: 'เฟรนช์ฟรายส์ทรัฟเฟิลพาร์เมซาน',
      category: 'snacks',
      price: 120,
      costPrice: 45,
      unit: 'portions',
      currentStock: 14,
      lowStockThreshold: 8,
    },
    {
      id: 'snack-churros',
      name: 'Spanish Cinnamon Sugar Churros',
      nameTh: 'ชูร์โรส ซินนามอน ดิปช็อกโกแลต',
      category: 'snacks',
      price: 95,
      costPrice: 35,
      unit: 'portions',
      currentStock: 3,
      lowStockThreshold: 8,
    },
    {
      id: 'snack-popcorn-butter',
      name: 'Movie-Style Warm Butter Popcorn',
      nameTh: 'ป๊อปคอร์นเนยอุ่น สไตล์โรงหนัง',
      category: 'snacks',
      price: 65,
      costPrice: 20,
      unit: 'bags',
      currentStock: 26,
      lowStockThreshold: 10,
    },
    {
      id: 'snack-crispy-calamari',
      name: 'Salt & Pepper Crispy Baby Squid',
      nameTh: 'หมึกกรอบพริกเกลือ',
      category: 'snacks',
      price: 140,
      costPrice: 55,
      unit: 'portions',
      currentStock: 0,
      lowStockThreshold: 6,
    },
    {
      id: 'tc-thai-tea',
      name: 'Hand-Brewed Traditional Thai Iced Tea',
      nameTh: 'ชาไทยเย็นสูตรโบราณ',
      category: 'tea_coffee',
      price: 75,
      costPrice: 25,
      unit: 'cups',
      currentStock: 32,
      lowStockThreshold: 10,
    },
    {
      id: 'tc-matcha-latte',
      name: 'Ceremonial Uji Matcha Latte',
      nameTh: 'อุจิมัทฉะลาเต้แท้เข้มข้น',
      category: 'tea_coffee',
      price: 95,
      costPrice: 35,
      unit: 'cups',
      currentStock: 2,
      lowStockThreshold: 10,
    },
    {
      id: 'tc-iced-americano',
      name: 'Cold Brew Single-Origin Americano',
      nameTh: 'อเมริกาโน่โคลด์บรูว์',
      category: 'tea_coffee',
      price: 70,
      costPrice: 20,
      unit: 'cups',
      currentStock: 40,
      lowStockThreshold: 10,
    },
    {
      id: 'bakery-butter-croissant',
      name: 'French Pure Butter Croissant',
      nameTh: 'ครัวซองต์เนยสดฝรั่งเศส',
      category: 'bakery_desserts',
      price: 80,
      costPrice: 30,
      unit: 'pieces',
      currentStock: 8,
      lowStockThreshold: 10,
    },
    {
      id: 'bakery-choco-chip-cookie',
      name: 'Sea Salt Dark Chocolate Cookie',
      nameTh: 'คุกกี้ดาร์กช็อกโกแลต ซีซอลต์',
      category: 'bakery_desserts',
      price: 60,
      costPrice: 22,
      unit: 'pieces',
      currentStock: 15,
      lowStockThreshold: 8,
    },
  ];
}
