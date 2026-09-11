/** Shared prop unions for the Figma design-system components. */
export type BadgeTone = 'success' | 'warning' | 'info' | 'neutral';
export type ButtonVariant = 'primary' | 'outline' | 'ghost';
export type ButtonSize = 'md' | 'sm';
export type StatTone = 'neutral' | 'success' | 'warning' | 'info';

/** One `<option>`: `value` is what the API sees, `label` what the reader sees. */
export interface SelectOption {
  value: string;
  label: string;
}
