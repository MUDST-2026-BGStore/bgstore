import { ref } from 'vue';

export interface Branch {
  id: string;
  name: string;
  code: string;
  status: 'Active' | 'Inactive';
  addressLine: string;
  city: string;
  postcode: string;
  district: string;
  phone: string;
  email: string;
  tables: number;
  stats: {
    total: number;
    available: number;
    reserved: number;
    occupied: number;
  };
  openingHours: {
    monFri: { open: string; close: string };
    saturday: { open: string; close: string };
    sunday: { open: string; close: string };
  };
  createdAt: string;
}

// State กลางระดับแอปพลิเคชัน
const branches = ref<Branch[]>([
  {
    id: '1',
    name: 'Sukhumvit',
    code: 'SKV',
    status: 'Active',
    addressLine: '88 Sukhumvit Rd',
    city: 'Bangkok',
    postcode: '10110',
    district: 'Watthana',
    phone: '02-111-2233',
    email: 'sukhumvit@reservation.co.th',
    tables: 20,
    stats: { total: 20, available: 12, reserved: 5, occupied: 3 },
    openingHours: {
      monFri: { open: '10:00', close: '22:00' },
      saturday: { open: '11:00', close: '23:00' },
      sunday: { open: '11:00', close: '22:00' },
    },
    createdAt: '12 January 2025',
  },
  {
    id: '2',
    name: 'Silom',
    code: 'SLM',
    status: 'Active',
    addressLine: '15 Silom Rd',
    city: 'Bangkok',
    postcode: '10500',
    district: 'Bang Rak',
    phone: '02-222-3344',
    email: 'silom@reservation.co.th',
    tables: 16,
    stats: { total: 16, available: 8, reserved: 4, occupied: 4 },
    openingHours: {
      monFri: { open: '11:00', close: '23:00' },
      saturday: { open: '11:00', close: '23:00' },
      sunday: { open: '11:00', close: '23:00' },
    },
    createdAt: '15 January 2025',
  },
  {
    id: '3',
    name: 'Thonglor',
    code: 'TGL',
    status: 'Inactive',
    addressLine: '9 Thonglor Soi 10',
    city: 'Bangkok',
    postcode: '10110',
    district: 'Watthana',
    phone: '02-333-4455',
    email: 'thonglor@reservation.co.th',
    tables: 12,
    stats: { total: 12, available: 0, reserved: 0, occupied: 0 },
    openingHours: {
      monFri: { open: '12:00', close: '24:00' },
      saturday: { open: '12:00', close: '24:00' },
      sunday: { open: '12:00', close: '24:00' },
    },
    createdAt: '20 January 2025',
  },
]);

export function useBranches() {
  const getBranchById = (id: string) => {
    return branches.value.find((b) => b.id === id);
  };

  const addBranch = (
    newBranch: Omit<Branch, 'id' | 'createdAt' | 'tables' | 'stats'>,
  ) => {
    const createdBranch: Branch = {
      ...newBranch,
      id: String(Date.now()),
      tables: 10,
      stats: { total: 10, available: 10, reserved: 0, occupied: 0 },
      createdAt: new Date().toLocaleDateString('en-GB', {
        day: 'numeric',
        month: 'long',
        year: 'numeric',
      }),
    };
    branches.value.push(createdBranch);
    return createdBranch;
  };

  const updateBranch = (id: string, updatedData: Partial<Branch>) => {
    const index = branches.value.findIndex((b) => b.id === id);
    if (index !== -1) {
      branches.value[index] = { ...branches.value[index], ...updatedData };
    }
  };

  const deleteBranch = (id: string) => {
    branches.value = branches.value.filter((b) => b.id !== id);
  };

  return {
    branches,
    getBranchById,
    addBranch,
    updateBranch,
    deleteBranch,
  };
}
