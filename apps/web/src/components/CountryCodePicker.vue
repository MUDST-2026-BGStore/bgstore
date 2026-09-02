<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useI18n } from 'vue-i18n';

type CountryOption = {
  code: string;
  flag: string;
  nameEn: string;
  nameTh: string;
};

const props = withDefaults(
  defineProps<{
    modelValue: string;
    id?: string;
    name?: string;
    label?: string;
  }>(),
  { id: 'countryCode', name: 'countryCode', label: '' },
);
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
const { locale, t } = useI18n();

const pickerRoot = ref<HTMLElement | null>(null);
const countryMenuOpen = ref(false);

const countryCodes: readonly CountryOption[] = [
  { code: '+66', flag: '🇹🇭', nameEn: 'Thailand', nameTh: 'ไทย' },
  {
    code: '+1',
    flag: '🇺🇸',
    nameEn: 'United States / Canada',
    nameTh: 'สหรัฐอเมริกา / แคนาดา',
  },
  {
    code: '+44',
    flag: '🇬🇧',
    nameEn: 'United Kingdom',
    nameTh: 'สหราชอาณาจักร',
  },
  { code: '+61', flag: '🇦🇺', nameEn: 'Australia', nameTh: 'ออสเตรเลีย' },
  { code: '+65', flag: '🇸🇬', nameEn: 'Singapore', nameTh: 'สิงคโปร์' },
  { code: '+60', flag: '🇲🇾', nameEn: 'Malaysia', nameTh: 'มาเลเซีย' },
  { code: '+81', flag: '🇯🇵', nameEn: 'Japan', nameTh: 'ญี่ปุ่น' },
  { code: '+82', flag: '🇰🇷', nameEn: 'South Korea', nameTh: 'เกาหลีใต้' },
  { code: '+86', flag: '🇨🇳', nameEn: 'China', nameTh: 'จีน' },
  { code: '+91', flag: '🇮🇳', nameEn: 'India', nameTh: 'อินเดีย' },
  { code: '+33', flag: '🇫🇷', nameEn: 'France', nameTh: 'ฝรั่งเศส' },
  { code: '+49', flag: '🇩🇪', nameEn: 'Germany', nameTh: 'เยอรมนี' },
];

const selectedCountry = computed(
  () =>
    countryCodes.find((country) => country.code === props.modelValue) ??
    countryCodes[0],
);
const pickerLabel = computed(() => props.label || t('onboarding.countryCode'));

const countryName = (country: CountryOption) =>
  locale.value === 'th' ? country.nameTh : country.nameEn;

function toggleCountryMenu() {
  countryMenuOpen.value = !countryMenuOpen.value;
}

function chooseCountry(code: string) {
  emit('update:modelValue', code);
  countryMenuOpen.value = false;
}

function handleCountryKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    countryMenuOpen.value = false;
  } else if (
    event.key === 'ArrowDown' ||
    event.key === 'Enter' ||
    event.key === ' '
  ) {
    event.preventDefault();
    countryMenuOpen.value = true;
  }
}

function handleHiddenInput(event: Event) {
  emit('update:modelValue', (event.target as HTMLInputElement).value);
}

function handleDocumentPointerDown(event: PointerEvent) {
  if (!countryMenuOpen.value) return;
  const target = event.target;
  if (
    target instanceof Node &&
    pickerRoot.value &&
    !pickerRoot.value.contains(target)
  ) {
    countryMenuOpen.value = false;
  }
}

onMounted(() =>
  document.addEventListener('pointerdown', handleDocumentPointerDown),
);
onBeforeUnmount(() =>
  document.removeEventListener('pointerdown', handleDocumentPointerDown),
);
</script>

<template>
  <div ref="pickerRoot" class="phone-country-picker">
    <button
      class="phone-country-trigger"
      type="button"
      data-testid="country-trigger"
      aria-haspopup="listbox"
      aria-controls="country-options"
      :aria-label="pickerLabel"
      :aria-expanded="countryMenuOpen"
      @click="toggleCountryMenu"
      @keydown="handleCountryKeydown"
    >
      <span class="phone-flag" aria-hidden="true">{{
        selectedCountry.flag
      }}</span>
      <span class="phone-country-code">{{ selectedCountry.code }}</span>
      <svg class="phone-chevron" viewBox="0 0 16 16" aria-hidden="true">
        <path d="m4 6 4 4 4-4" />
      </svg>
    </button>
    <div
      v-if="countryMenuOpen"
      id="country-options"
      class="phone-country-menu"
      role="listbox"
      :aria-label="pickerLabel"
    >
      <button
        v-for="country in countryCodes"
        :key="country.code"
        class="phone-country-option"
        type="button"
        role="option"
        :data-testid="`country-option-${country.code}`"
        :aria-selected="country.code === props.modelValue"
        @click="chooseCountry(country.code)"
      >
        <span class="phone-flag" aria-hidden="true">{{ country.flag }}</span>
        <span class="phone-country-name">{{ countryName(country) }}</span>
        <span class="phone-country-option-code">{{ country.code }}</span>
      </button>
    </div>
    <input
      :id="props.id"
      :name="props.name"
      type="hidden"
      :value="props.modelValue"
      @input="handleHiddenInput"
    />
  </div>
</template>

<style scoped>
.phone-country-picker {
  position: relative;
  flex: 0 0 auto;
}

.phone-country-trigger {
  display: flex;
  height: 100%;
  min-width: 7.8rem;
  align-items: center;
  gap: 0.5rem;
  padding: 0 0.8rem;
  border: 0;
  border-radius: 0.95rem 0 0 0.95rem;
  color: var(--ink);
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-size: 0.95rem;
  font-weight: 700;
  text-align: left;
}

.phone-country-trigger:hover,
.phone-country-trigger[aria-expanded='true'] {
  background: rgb(76 131 143 / 9%);
}

.phone-country-trigger:focus-visible,
.phone-country-option:focus-visible {
  outline: 3px solid rgb(76 131 143 / 25%);
  outline-offset: -3px;
}

.phone-flag {
  display: inline-grid;
  width: 1.55rem;
  height: 1.55rem;
  flex: 0 0 auto;
  place-items: center;
  border-radius: 0.5rem;
  font-size: 1.05rem;
  line-height: 1;
}

.phone-country-code {
  letter-spacing: 0.01em;
}

.phone-chevron {
  width: 1rem;
  height: 1rem;
  margin-left: auto;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
  opacity: 0.65;
  transition: transform 160ms ease;
}

.phone-country-trigger[aria-expanded='true'] .phone-chevron {
  transform: rotate(180deg);
}

.phone-country-menu {
  position: absolute;
  z-index: 10;
  top: calc(100% + 0.55rem);
  left: 0;
  width: min(18rem, 70vw);
  max-height: min(22rem, calc(100vh - 8rem));
  padding: 0.4rem;
  overflow-y: auto;
  overscroll-behavior: contain;
  border: 1px solid #dce8e9;
  border-radius: 0.9rem;
  background: rgb(255 255 255 / 98%);
  box-shadow: 0 1rem 2.25rem rgb(30 58 65 / 18%);
  animation: phone-menu-in 140ms ease-out;
  scrollbar-color: #b8ced1 transparent;
  scrollbar-width: thin;
}

.phone-country-menu::-webkit-scrollbar {
  width: 0.4rem;
}

.phone-country-menu::-webkit-scrollbar-thumb {
  border: 0.1rem solid transparent;
  border-radius: 999px;
  background: #b8ced1;
  background-clip: padding-box;
}

.phone-country-option {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 0.65rem;
  padding: 0.62rem 0.65rem;
  border: 0;
  border-radius: 0.65rem;
  color: var(--ink);
  background: transparent;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.phone-country-option:hover,
.phone-country-option[aria-selected='true'] {
  background: #eaf4f4;
}

.phone-country-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.86rem;
}

.phone-country-option-code {
  margin-left: auto;
  color: var(--muted);
  font-size: 0.8rem;
  font-variant-numeric: tabular-nums;
}

.phone-divider {
  width: 1px;
  margin: 0.75rem 0;
  background: #b9ced0;
}

@keyframes phone-menu-in {
  from {
    opacity: 0;
    transform: translateY(-0.25rem) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
