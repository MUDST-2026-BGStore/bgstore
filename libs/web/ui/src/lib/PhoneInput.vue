<script setup lang="ts">
import { computed, nextTick, ref, useId, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import 'flag-icons/css/flag-icons.min.css';
import examples from 'libphonenumber-js/mobile/examples';
import {
  getExampleNumber,
  getCountries,
  getCountryCallingCode,
  parsePhoneNumberFromString,
  type CountryCode,
  type PhoneNumber,
} from 'libphonenumber-js/max';

const props = defineProps<{
  modelValue: string;
  id: string;
  disabled?: boolean;
  required?: boolean;
}>();
const emit = defineEmits<{
  'update:modelValue': [value: string];
  validity: [valid: boolean];
  blur: [];
}>();
const { t, locale } = useI18n({ useScope: 'global' });
const uid = useId();
const root = ref<HTMLElement | null>(null);
const countryButton = ref<HTMLButtonElement | null>(null);
const searchInput = ref<HTMLInputElement | null>(null);
const countryList = ref<HTMLUListElement | null>(null);
const numberInput = ref<HTMLInputElement | null>(null);
const country = ref<CountryCode>('TH');
const national = ref('');
const open = ref(false);
const query = ref('');
const active = ref(0);
let lastEmitted: string | undefined;
const englishNames = new Intl.DisplayNames(['en'], { type: 'region' });
const countries = computed(() => {
  const names = new Intl.DisplayNames([locale.value, 'en'], { type: 'region' });
  return getCountries()
    .map((code) => ({
      code,
      name: names.of(code) ?? code,
      english: englishNames.of(code) ?? code,
      dial: getCountryCallingCode(code),
    }))
    .sort((a, b) => a.name.localeCompare(b.name, locale.value));
});
const filtered = computed(() => {
  const search = query.value.trim().toLowerCase();
  return countries.value.filter((item) =>
    `${item.name} ${item.english} ${item.code} +${item.dial}`
      .toLowerCase()
      .includes(search),
  );
});
const selected = computed(() =>
  countries.value.find((item) => item.code === country.value),
);
const placeholder = computed(() => {
  const example = getExampleNumber(country.value, examples);
  return example && displayNumber(example);
});
const optionId = (code: CountryCode) => `${uid}-${code}`;

function displayNumber(number: PhoneNumber) {
  // The calling code is already shown in the country selector.
  return number
    .formatInternational()
    .slice(number.countryCallingCode.length + 2);
}
function isValid(number: PhoneNumber | undefined) {
  return (
    !!number?.isValid() &&
    number.countryCallingCode === getCountryCallingCode(country.value) &&
    !number.ext
  );
}

watch(
  () => props.modelValue,
  (value) => {
    if (value === lastEmitted) return;
    const parsed = parsePhoneNumberFromString(value, {
      defaultCountry: country.value,
      extract: false,
    });
    if (parsed?.country) country.value = parsed.country;
    national.value = parsed ? displayNumber(parsed) : value;
    emit('validity', isValid(parsed));
  },
  { immediate: true },
);

function update(value: string) {
  national.value = value;
  const parsed = parsePhoneNumberFromString(value, {
    defaultCountry: country.value,
    extract: false,
  });
  if (value.trim().startsWith('+') && parsed?.country) {
    country.value = parsed.country;
    national.value = displayNumber(parsed);
  }
  lastEmitted = parsed?.number ?? value;
  emit('update:modelValue', lastEmitted);
  emit('validity', isValid(parsed));
}
function formatOnBlur() {
  const parsed = parsePhoneNumberFromString(national.value, {
    defaultCountry: country.value,
    extract: false,
  });
  if (parsed && isValid(parsed)) national.value = displayNumber(parsed);
  emit('blur');
}
function choose(code: CountryCode) {
  country.value = code;
  open.value = false;
  update(national.value);
  numberInput.value?.focus();
}
async function showCountries() {
  query.value = '';
  open.value = true;
  active.value = 0;
  await nextTick();
  if (open.value) searchInput.value?.focus({ preventScroll: true });
}
function closeCountries() {
  open.value = false;
  countryButton.value?.focus({ preventScroll: true });
}
function move(direction: number) {
  if (!open.value) showCountries();
  else
    active.value = Math.max(
      0,
      Math.min(filtered.value.length - 1, active.value + direction),
    );
}
function selectActive() {
  if (!open.value) showCountries();
  else {
    const item = filtered.value[active.value];
    if (item) choose(item.code);
  }
}
watch([active, open, filtered], async () => {
  await nextTick();
  const item = filtered.value[active.value];
  const list = countryList.value;
  const option = item && document.getElementById(optionId(item.code));
  if (!open.value || !list || !option) return;
  if (option.offsetTop < list.scrollTop) list.scrollTop = option.offsetTop;
  else if (
    option.offsetTop + option.offsetHeight >
    list.scrollTop + list.clientHeight
  ) {
    list.scrollTop = option.offsetTop + option.offsetHeight - list.clientHeight;
  }
});
function leave(event: FocusEvent) {
  if (!root.value?.contains(event.relatedTarget as Node | null))
    open.value = false;
}
</script>

<template>
  <div ref="root" class="phone-field" @focusout="leave">
    <div class="phone-control">
      <button
        ref="countryButton"
        type="button"
        class="country-trigger"
        :class="{ 'is-open': open }"
        :aria-label="`${t('phone.country')}: ${selected?.name} +${selected?.dial}`"
        aria-haspopup="listbox"
        :aria-expanded="open"
        :aria-controls="open ? `${uid}-countries` : undefined"
        :disabled="disabled"
        @pointerdown.prevent
        @click="open ? closeCountries() : showCountries()"
        @keydown.down.prevent="showCountries"
        @keydown.up.prevent="showCountries"
        @keydown.esc.prevent="closeCountries"
      >
        <span
          class="fi"
          :class="`fi-${country.toLowerCase()}`"
          aria-hidden="true"
        />
        <span>+{{ selected?.dial }}</span>
        <svg
          class="chevron"
          width="10"
          height="6"
          viewBox="0 0 10 6"
          fill="none"
          aria-hidden="true"
        >
          <path d="m1 1 4 4 4-4" stroke="currentColor" stroke-width="1.25" />
        </svg>
      </button>
      <span class="phone-divider" aria-hidden="true" />
      <input
        :id="id"
        ref="numberInput"
        class="number-input"
        type="tel"
        autocomplete="tel-national"
        inputmode="tel"
        :placeholder="placeholder"
        :value="national"
        :disabled="disabled"
        :required="required"
        :aria-label="t('phone.label')"
        @input="update(($event.target as HTMLInputElement).value)"
        @focus="open = false"
        @blur="formatOnBlur"
      />
    </div>
    <Transition name="country-menu">
      <div v-if="open" class="country-popup" :inert="!open">
        <div class="country-search">
          <input
            ref="searchInput"
            v-model="query"
            role="combobox"
            :aria-label="t('phone.search')"
            aria-autocomplete="list"
            aria-expanded="true"
            :aria-controls="`${uid}-countries`"
            :aria-activedescendant="
              filtered[active] ? optionId(filtered[active].code) : undefined
            "
            :placeholder="t('phone.search')"
            autocomplete="off"
            @input="active = 0"
            @keydown.down.prevent="move(1)"
            @keydown.up.prevent="move(-1)"
            @keydown.enter.prevent="selectActive"
            @keydown.esc.prevent="closeCountries"
            @keydown.tab="open = false"
          />
        </div>
        <ul
          :id="`${uid}-countries`"
          ref="countryList"
          role="listbox"
          :aria-label="t('phone.country')"
          class="country-list"
        >
          <li
            v-for="(item, index) in filtered"
            :id="optionId(item.code)"
            :key="item.code"
            role="option"
            :aria-selected="country === item.code"
          >
            <button
              type="button"
              tabindex="-1"
              class="country-option"
              :class="{ active: active === index }"
              @mousedown.prevent
              @click="choose(item.code)"
            >
              <span
                class="fi"
                :class="`fi-${item.code.toLowerCase()}`"
                aria-hidden="true"
              />
              <span class="country-name">{{ item.name }}</span>
              <span class="calling-code">+{{ item.dial }}</span>
            </button>
          </li>
          <li v-if="!filtered.length" role="presentation" class="no-countries">
            {{ t('phone.noCountries') }}
          </li>
        </ul>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.phone-field {
  position: relative;
  min-width: 0;
  width: 100%;
  color: #14241c;
  font-size: 15px;
  font-family:
    'Plus Jakarta Sans',
    -apple-system,
    BlinkMacSystemFont,
    sans-serif;
}
.phone-control {
  display: flex;
  align-items: center;
  height: 40px;
  border-bottom: 1px solid #bfb093;
}
.phone-control:focus-within {
  border-color: #152c21;
}
.country-trigger {
  position: relative;
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
  align-self: stretch;
  padding: 0 8px;
  border: 1px solid transparent;
  border-bottom: 0;
  border-radius: 4px 4px 0 0;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 14px;
  cursor: pointer;
  transition:
    background-color 150ms ease,
    border-color 150ms ease;
}
.country-trigger.is-open {
  z-index: 51;
  border-color: #d3c4a8;
  background: #fffaf1;
}
.country-trigger.is-open::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  background: #fffaf1;
  content: '';
}
.country-trigger:focus-visible {
  outline: 2px solid #c6ad7a;
  outline-offset: 2px;
}
.fi {
  width: 20px;
  height: 15px;
  flex-shrink: 0;
  border-radius: 1px;
}
.chevron {
  margin-left: 2px;
  color: #82745e;
  transition: transform 150ms ease;
}
.country-trigger.is-open .chevron {
  transform: rotate(180deg);
}
.phone-divider {
  width: 1px;
  height: 20px;
  margin: 0 12px;
  background: #cbbda4;
}
.number-input {
  flex: 1;
  min-width: 0;
  width: 100%;
  height: 100%;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  outline: none;
  box-shadow: none;
}
.number-input::placeholder {
  color: #b0a590;
}
.country-trigger:disabled,
.number-input:disabled {
  opacity: 0.55;
  cursor: default;
}
.country-popup {
  position: absolute;
  top: calc(100% - 1px);
  left: 0;
  right: 0;
  z-index: 50;
  overflow: hidden;
  border: 1px solid #d3c4a8;
  border-radius: 0 4px 4px 4px;
  background: #fffaf1;
  box-shadow: 0 8px 24px #14241c24;
  transform-origin: top left;
}
.country-menu-enter-active,
.country-menu-leave-active {
  transition:
    opacity 150ms ease,
    transform 150ms ease;
}
.country-menu-leave-active {
  pointer-events: none;
}
.country-menu-enter-from,
.country-menu-leave-to {
  opacity: 0;
  transform: scaleY(0.97);
}
@media (prefers-reduced-motion: reduce) {
  .country-trigger,
  .chevron,
  .country-menu-enter-active,
  .country-menu-leave-active {
    transition: none;
  }
}
.country-search {
  padding: 12px;
  border-bottom: 1px solid #e5dac6;
}
.country-search input {
  box-sizing: border-box;
  display: block;
  width: 100%;
  padding: 9px 10px;
  border: 1px solid #cbbda4;
  border-radius: 2px;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 13px;
  outline: none;
}
.country-search input:focus {
  border-color: #152c21;
}
.country-search input::placeholder {
  color: #82745e;
}
.country-list {
  position: relative;
  max-height: 216px;
  margin: 0;
  padding: 4px;
  overflow-y: auto;
  overscroll-behavior: contain;
  list-style: none;
}
.country-option {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px;
  border: 0;
  border-radius: 2px;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}
.country-option.active,
.country-option:hover {
  background: #eee4d1;
}
.country-name {
  flex: 1;
}
.calling-code {
  color: #6a6252;
  font-variant-numeric: tabular-nums;
}
.no-countries {
  padding: 12px 10px;
  font-size: 13px;
  color: #6a6252;
}
</style>
