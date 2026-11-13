export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface FilterGroup {
  id: string;
  label: string;
  options: readonly SelectOption[];
}

export type FilterValues = Record<string, string[]>;
