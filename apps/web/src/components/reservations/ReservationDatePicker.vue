<script setup lang="ts">
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue';
import { useI18n } from 'vue-i18n';

const props = defineProps<{
  minDate: string;
  maxDate: string;
}>();

const model = defineModel<string>({ required: true });
const { t, locale } = useI18n();
const root = ref<HTMLElement | null>(null);
const calendarPanel = ref<HTMLElement | null>(null);
const isOpen = ref(false);

const parseIsoDate = (value: string) => {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day, 12);
};

const toIsoDate = (value: Date) => {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const startOfMonth = (value: Date) =>
  new Date(value.getFullYear(), value.getMonth(), 1, 12);

const displayedMonth = ref(startOfMonth(parseIsoDate(model.value)));
const today = toIsoDate(new Date());

const fullDateFormatter = computed(
  () => new Intl.DateTimeFormat(locale.value, { dateStyle: 'full' }),
);
const monthFormatter = computed(
  () =>
    new Intl.DateTimeFormat(locale.value, {
      month: 'long',
      year: 'numeric',
    }),
);
const weekdayFormatter = computed(
  () => new Intl.DateTimeFormat(locale.value, { weekday: 'short' }),
);

const formattedDate = computed(() =>
  fullDateFormatter.value.format(parseIsoDate(model.value)),
);
const monthLabel = computed(() =>
  monthFormatter.value.format(displayedMonth.value),
);
const weekdayLabels = computed(() =>
  Array.from({ length: 7 }, (_, index) =>
    weekdayFormatter.value.format(new Date(2026, 0, 4 + index, 12)),
  ),
);

const calendarDays = computed(() => {
  const year = displayedMonth.value.getFullYear();
  const month = displayedMonth.value.getMonth();
  const firstDayOffset = new Date(year, month, 1, 12).getDay();

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(year, month, index - firstDayOffset + 1, 12);
    const isoDate = toIsoDate(date);
    return {
      date: isoDate,
      day: date.getDate(),
      inCurrentMonth: date.getMonth() === month,
      disabled: isoDate < props.minDate || isoDate > props.maxDate,
      label: fullDateFormatter.value.format(date),
    };
  });
});

const previousMonthDisabled = computed(() => {
  const firstDay = displayedMonth.value;
  const previousMonthLastDay = new Date(
    firstDay.getFullYear(),
    firstDay.getMonth(),
    0,
    12,
  );
  return toIsoDate(previousMonthLastDay) < props.minDate;
});

const nextMonthDisabled = computed(() => {
  const firstDay = displayedMonth.value;
  const nextMonthFirstDay = new Date(
    firstDay.getFullYear(),
    firstDay.getMonth() + 1,
    1,
    12,
  );
  return toIsoDate(nextMonthFirstDay) > props.maxDate;
});

const focusSelectedDate = async () => {
  await nextTick();
  const selected = calendarPanel.value?.querySelector<HTMLElement>(
    '.calendar-day[aria-pressed="true"]',
  );
  const firstAvailable = calendarPanel.value?.querySelector<HTMLElement>(
    '.calendar-day:not(:disabled)',
  );
  (selected ?? firstAvailable)?.focus();
};

const openCalendar = () => {
  displayedMonth.value = startOfMonth(parseIsoDate(model.value));
  isOpen.value = true;
  void focusSelectedDate();
};

const closeCalendar = () => {
  isOpen.value = false;
};

const toggleCalendar = () => {
  if (isOpen.value) {
    closeCalendar();
  } else {
    openCalendar();
  }
};

const changeMonth = (offset: number) => {
  const current = displayedMonth.value;
  displayedMonth.value = new Date(
    current.getFullYear(),
    current.getMonth() + offset,
    1,
    12,
  );
};

const selectDate = (date: string) => {
  model.value = date;
  closeCalendar();
};

const closeWhenClickingOutside = (event: PointerEvent) => {
  if (!root.value?.contains(event.target as Node)) {
    closeCalendar();
  }
};

watch(model, (value) => {
  if (!isOpen.value) {
    displayedMonth.value = startOfMonth(parseIsoDate(value));
  }
});

onMounted(() =>
  document.addEventListener('pointerdown', closeWhenClickingOutside),
);
onBeforeUnmount(() =>
  document.removeEventListener('pointerdown', closeWhenClickingOutside),
);
</script>

<template>
  <div ref="root" class="date-picker" @keydown.esc="closeCalendar">
    <label id="reservation-date-label">
      {{ t('reservation.date') }}
      <b aria-hidden="true">*</b>
    </label>

    <button
      type="button"
      class="date-picker-trigger"
      aria-haspopup="dialog"
      :aria-labelledby="'reservation-date-label'"
      :aria-expanded="isOpen"
      @click="toggleCalendar"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M6 3v3m12-3v3M4 9h16" />
        <rect x="4" y="5" width="16" height="16" rx="2" />
        <path d="M8 13h2m4 0h2m-8 4h2m4 0h2" />
      </svg>
      <span>{{ formattedDate }}</span>
      <svg class="trigger-chevron" viewBox="0 0 20 20" aria-hidden="true">
        <path d="m6 8 4 4 4-4" />
      </svg>
    </button>

    <small>{{ t('reservation.dateHint') }}</small>

    <div
      v-if="isOpen"
      ref="calendarPanel"
      class="calendar-panel"
      role="dialog"
      aria-modal="false"
      :aria-label="t('reservation.calendarDialog')"
    >
      <header class="calendar-header">
        <button
          type="button"
          class="month-button"
          :aria-label="t('reservation.previousMonth')"
          :disabled="previousMonthDisabled"
          @click="changeMonth(-1)"
        >
          <svg viewBox="0 0 20 20" aria-hidden="true">
            <path d="m12 5-5 5 5 5" />
          </svg>
        </button>
        <strong aria-live="polite">{{ monthLabel }}</strong>
        <button
          type="button"
          class="month-button"
          :aria-label="t('reservation.nextMonth')"
          :disabled="nextMonthDisabled"
          @click="changeMonth(1)"
        >
          <svg viewBox="0 0 20 20" aria-hidden="true">
            <path d="m8 5 5 5-5 5" />
          </svg>
        </button>
      </header>

      <div class="weekday-grid" aria-hidden="true">
        <span v-for="weekday in weekdayLabels" :key="weekday">
          {{ weekday }}
        </span>
      </div>

      <div class="calendar-grid">
        <button
          v-for="day in calendarDays"
          :key="day.date"
          type="button"
          class="calendar-day"
          :class="{
            'calendar-day--outside': !day.inCurrentMonth,
            'calendar-day--today': day.date === today,
            'calendar-day--selected': day.date === model,
          }"
          :data-date="day.date"
          :disabled="day.disabled"
          :aria-label="day.label"
          :aria-current="day.date === today ? 'date' : undefined"
          :aria-pressed="day.date === model"
          @click="selectDate(day.date)"
        >
          {{ day.day }}
        </button>
      </div>

      <footer>
        <span>{{ t('reservation.availableDateRange') }}</span>
        <strong>{{ formattedDate }}</strong>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.date-picker {
  position: relative;
  display: grid;
  gap: 0.55rem;
  min-width: 0;
  color: #20252d;
  font-size: 0.9rem;
}

label b {
  color: #8d1d24;
}

.date-picker-trigger {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 0.7rem;
  align-items: center;
  width: 100%;
  min-height: 2.9rem;
  padding: 0.65rem 0.85rem;
  border: 1px solid #dce2e6;
  border-radius: 0.55rem;
  color: #20252d;
  background: #fff;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 150ms ease,
    box-shadow 150ms ease;
}

.date-picker-trigger:hover,
.date-picker-trigger[aria-expanded='true'] {
  border-color: #497883;
}

.date-picker-trigger[aria-expanded='true'] {
  box-shadow: 0 0 0 3px rgb(73 120 131 / 14%);
}

.date-picker-trigger svg {
  width: 1.2rem;
  fill: none;
  stroke: #647080;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.date-picker-trigger .trigger-chevron {
  width: 1rem;
  transition: transform 150ms ease;
}

.date-picker-trigger[aria-expanded='true'] .trigger-chevron {
  transform: rotate(180deg);
}

small {
  color: #647080;
  font-size: 0.8rem;
}

.calendar-panel {
  position: absolute;
  z-index: 20;
  top: 4.85rem;
  left: 0;
  width: min(100%, 22rem);
  padding: 1rem;
  border: 1px solid #dce2e6;
  border-radius: 0.8rem;
  background: #fff;
  box-shadow: 0 1.25rem 3.5rem rgb(32 37 45 / 18%);
}

.calendar-header {
  display: grid;
  grid-template-columns: 2.35rem 1fr 2.35rem;
  align-items: center;
  margin-bottom: 0.8rem;
}

.calendar-header strong {
  text-align: center;
  text-transform: capitalize;
}

.month-button {
  display: grid;
  width: 2.35rem;
  height: 2.35rem;
  place-items: center;
  border: 0;
  border-radius: 50%;
  color: #315b65;
  background: transparent;
  cursor: pointer;
}

.month-button:hover:not(:disabled) {
  background: #edf4f4;
}

.month-button svg {
  width: 1.1rem;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.month-button:disabled {
  color: #b8c0c6;
  cursor: not-allowed;
}

.weekday-grid,
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 0.2rem;
}

.weekday-grid {
  margin-bottom: 0.3rem;
}

.weekday-grid span {
  padding: 0.25rem 0;
  color: #7a858f;
  font-size: 0.68rem;
  font-weight: 700;
  text-align: center;
  text-transform: uppercase;
}

.calendar-day {
  aspect-ratio: 1;
  min-width: 0;
  border: 0;
  border-radius: 50%;
  color: #2b333b;
  background: transparent;
  font: inherit;
  font-size: 0.78rem;
  cursor: pointer;
}

.calendar-day:hover:not(:disabled) {
  color: #315b65;
  background: #edf4f4;
}

.calendar-day--outside {
  color: #a7afb5;
}

.calendar-day--today:not(.calendar-day--selected) {
  box-shadow: inset 0 0 0 1px #497883;
}

.calendar-day--selected,
.calendar-day--selected:hover:not(:disabled) {
  color: #fff;
  background: #497883;
  font-weight: 700;
}

.calendar-day:disabled {
  color: #ccd1d5;
  cursor: not-allowed;
  text-decoration: line-through;
}

footer {
  display: grid;
  gap: 0.15rem;
  margin-top: 0.8rem;
  padding: 0.75rem 0.2rem 0;
  border-top: 1px solid #edf0f2;
}

footer span {
  color: #7a858f;
  font-size: 0.68rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

footer strong {
  color: #315b65;
  font-size: 0.78rem;
}

button:focus-visible {
  outline: 3px solid #20252d;
  outline-offset: 2px;
}

@media (max-width: 640px) {
  .calendar-panel {
    top: 4.75rem;
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .date-picker-trigger,
  .trigger-chevron {
    transition: none;
  }
}
</style>
