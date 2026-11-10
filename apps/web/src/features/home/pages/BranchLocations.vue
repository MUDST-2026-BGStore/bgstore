<script setup lang="ts">
// Branch directory with a map of every branch. Uses the frontend branch fixture
// until the branch API exposes addresses and coordinates. TODO(branch-api): phone
// numbers and per-branch hours when the backend provides them.
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import BranchMap from '../components/BranchMap.vue';
import { branches, openingHours } from '../data/branches';
import { useStoreText } from '../data/store-text';
import { useStoreContextStore } from '../stores/store-context';

const BRANCH_TIME_ZONE = 'Asia/Bangkok';
const { t, locale } = useStoreText();
const context = useStoreContextStore();

const query = ref('');
const openNowOnly = ref(false);
const filterOpen = ref(false);
const selectedId = ref(context.branch?.id ?? branches[0]?.id ?? '');
const expandedId = ref('');
const locating = ref(false);
const locateError = ref('');
const distances = ref<Record<string, number>>({});
const list = ref<HTMLElement>();
const directory = ref<HTMLElement>();
// Area of the map hidden by the floating list: left column on desktop,
// bottom sheet on phones. BranchMap keeps pins out from under it.
const occluded = ref({ left: 0, bottom: 0 });
let panelObserver: ResizeObserver | undefined;
onMounted(() => {
  panelObserver = new ResizeObserver(() => {
    const el = directory.value;
    if (!el) return;
    const sheet = window.matchMedia('(max-width: 700px)').matches;
    occluded.value = sheet
      ? { left: 0, bottom: el.offsetHeight }
      : { left: el.offsetLeft + el.offsetWidth, bottom: 0 };
  });
  if (directory.value) panelObserver.observe(directory.value);
});
onUnmounted(() => panelObserver?.disconnect());
const now = ref(new Date());
let timer: ReturnType<typeof setInterval> | undefined;
onMounted(() => (timer = setInterval(() => (now.value = new Date()), 60000)));
onUnmounted(() => clearInterval(timer));

// Current weekday (ISO, 1 = Monday) and minutes since midnight at the branches.
const branchClock = computed(() => {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: BRANCH_TIME_ZONE,
    weekday: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(now.value);
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  const day =
    ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].indexOf(get('weekday')) +
    1;
  return { day, minutes: Number(get('hour')) * 60 + Number(get('minute')) };
});
const toMinutes = (time: string) => {
  const [h = 0, m = 0] = time.split(':').map(Number);
  return h * 60 + m;
};
const timeFormatter = computed(
  () =>
    new Intl.DateTimeFormat(locale.value, {
      hour: 'numeric',
      minute: '2-digit',
      timeZone: 'UTC',
    }),
);
function formatTime(time: string) {
  const [h = 0, m = 0] = time.split(':').map(Number);
  return timeFormatter.value.format(new Date(Date.UTC(2024, 0, 1, h, m)));
}
const status = computed(() => {
  const { day, minutes } = branchClock.value;
  const today = openingHours.find((entry) => entry.day === day);
  if (today && minutes >= toMinutes(today.opens)) {
    if (minutes < toMinutes(today.closes))
      return {
        open: true,
        text: t('Open until {time}', { time: formatTime(today.closes) }),
      };
  } else if (today) {
    return {
      open: false,
      text: t('Closed · Opens at {time}', { time: formatTime(today.opens) }),
    };
  }
  const tomorrow = openingHours.find((entry) => entry.day === (day % 7) + 1);
  return {
    open: false,
    text: tomorrow
      ? t('Closed · Opens tomorrow at {time}', {
          time: formatTime(tomorrow.opens),
        })
      : t('Closed'),
  };
});

const dayFormatter = computed(
  () => new Intl.DateTimeFormat(locale.value, { weekday: 'long' }),
);
// 2024-01-01 is a Monday; ISO weekday 1..7 maps onto Jan 1..7.
const hours = computed(() =>
  openingHours.map((entry) => ({
    ...entry,
    label: dayFormatter.value.format(new Date(2024, 0, entry.day)),
    range: `${formatTime(entry.opens)} – ${formatTime(entry.closes)}`,
    today: entry.day === branchClock.value.day,
  })),
);

const filtered = computed(() => {
  const q = query.value.trim().toLocaleLowerCase();
  const matches = branches.filter(
    (branch) =>
      (!openNowOnly.value || status.value.open) &&
      (!q ||
        [branch.name, t(branch.name), branch.address].some((value) =>
          value.toLocaleLowerCase().includes(q),
        )),
  );
  const known = distances.value;
  return Object.keys(known).length
    ? [...matches].sort((a, b) => (known[a.id] ?? 0) - (known[b.id] ?? 0))
    : matches;
});

async function select(id: string) {
  selectedId.value = id;
  await nextTick();
  list.value
    ?.querySelector(`[data-branch="${id}"]`)
    ?.scrollIntoView({ block: 'nearest' });
}
function toggleInfo(id: string) {
  expandedId.value = expandedId.value === id ? '' : id;
  selectedId.value = id;
}
// The heart saves the branch as the visitor's browsing preference (same as the
// store selector). It is an explicit action; un-saving returns to browsing.
function toggleMyBranch(id: string) {
  context.select(context.branch?.id === id ? 'browse' : id);
}
const directionsUrl = (branch: (typeof branches)[number]) =>
  `https://www.google.com/maps/dir/?api=1&destination=${branch.location.lat},${branch.location.lng}`;

function distanceKm(
  a: { lat: number; lng: number },
  b: { lat: number; lng: number },
) {
  const rad = Math.PI / 180;
  const dLat = (b.lat - a.lat) * rad;
  const dLng = (b.lng - a.lng) * rad;
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.asin(Math.sqrt(h));
}
function findNearest() {
  locateError.value = '';
  if (!('geolocation' in navigator)) {
    locateError.value = t('Your browser cannot share your location.');
    return;
  }
  locating.value = true;
  navigator.geolocation.getCurrentPosition(
    ({ coords }) => {
      locating.value = false;
      const here = { lat: coords.latitude, lng: coords.longitude };
      const result: Record<string, number> = {};
      for (const branch of branches)
        result[branch.id] = distanceKm(here, branch.location);
      distances.value = result;
      query.value = '';
      const nearest = [...branches].sort(
        (a, b) => (result[a.id] ?? 0) - (result[b.id] ?? 0),
      )[0];
      if (nearest) void select(nearest.id);
    },
    () => {
      locating.value = false;
      locateError.value = t(
        'We couldn’t get your location. Choose a branch from the list.',
      );
    },
    { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 },
  );
}
watch(filterOpen, (open) => {
  if (!open) return;
  const close = (event: PointerEvent) => {
    if (!(event.target as Element).closest('.filter')) {
      filterOpen.value = false;
      document.removeEventListener('pointerdown', close);
    }
  };
  document.addEventListener('pointerdown', close);
});
</script>

<template>
  <section class="branches" aria-labelledby="branches-title">
    <h1 id="branches-title" class="visually-hidden">
      {{ t('Branch locations') }}
    </h1>

    <div class="layout">
      <aside ref="directory" class="directory">
        <div class="toolbar">
          <label class="search">
            <span class="visually-hidden">{{ t('Find a branch') }}</span>
            <input
              v-model="query"
              type="search"
              name="branch-location-search"
              :placeholder="t('Search by branch name or address')"
              autocomplete="off"
            />
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="11" cy="11" r="6.5" />
              <path d="m16 16 4.5 4.5" />
            </svg>
          </label>
          <div class="filter" @keydown.esc="filterOpen = false">
            <button
              type="button"
              class="filter-trigger"
              :class="{ 'is-active': openNowOnly }"
              :aria-expanded="filterOpen"
              aria-controls="branch-filter"
              @click="filterOpen = !filterOpen"
            >
              {{ t('Filter') }}
            </button>
            <div v-if="filterOpen" id="branch-filter" class="filter-panel">
              <label>
                <input v-model="openNowOnly" type="checkbox" />
                {{ t('Open now') }}
              </label>
            </div>
          </div>
        </div>
        <p v-if="locateError" class="notice" role="alert">{{ locateError }}</p>

        <ul ref="list" class="list" :aria-label="t('Branches')">
          <li
            v-for="branch in filtered"
            :key="branch.id"
            :data-branch="branch.id"
            :class="{ 'is-active': branch.id === selectedId }"
          >
            <div class="row">
              <button
                type="button"
                class="row-main"
                :aria-current="branch.id === selectedId ? 'true' : undefined"
                @click="select(branch.id)"
              >
                <strong>{{ t(branch.name) }}</strong>
                <span class="status" :class="{ closed: !status.open }">{{
                  status.text
                }}</span>
                <span
                  v-if="distances[branch.id] !== undefined"
                  class="distance"
                >
                  {{
                    t('{distance} km away', {
                      distance: (distances[branch.id] ?? 0).toFixed(1),
                    })
                  }}
                </span>
              </button>
              <button
                type="button"
                class="icon"
                :class="{ 'is-saved': context.branch?.id === branch.id }"
                :aria-pressed="context.branch?.id === branch.id"
                :aria-label="
                  t('Save {branch} as my branch', { branch: t(branch.name) })
                "
                :title="
                  context.branch?.id === branch.id
                    ? t('My branch')
                    : t('Save as my branch')
                "
                @click="toggleMyBranch(branch.id)"
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path
                    d="M12 20s-7.5-4.6-7.5-10A4.3 4.3 0 0 1 12 7.4 4.3 4.3 0 0 1 19.5 10c0 5.4-7.5 10-7.5 10Z"
                  />
                </svg>
              </button>
              <button
                type="button"
                class="icon"
                :aria-expanded="expandedId === branch.id"
                :aria-controls="`branch-info-${branch.id}`"
                :aria-label="t('{branch} details', { branch: t(branch.name) })"
                @click="toggleInfo(branch.id)"
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <circle cx="12" cy="12" r="8.5" />
                  <path d="M12 11v5M12 8v.01" />
                </svg>
              </button>
            </div>
            <div
              v-if="expandedId === branch.id"
              :id="`branch-info-${branch.id}`"
              class="details"
            >
              <p>{{ branch.address }}</p>
              <dl class="hours">
                <div
                  v-for="entry in hours"
                  :key="entry.day"
                  :class="{ today: entry.today }"
                >
                  <dt>{{ entry.label }}</dt>
                  <dd>{{ entry.range }}</dd>
                </div>
              </dl>
              <a
                :href="directionsUrl(branch)"
                target="_blank"
                rel="noopener noreferrer"
                >{{ t('Get directions') }} <span aria-hidden="true">↗</span></a
              >
            </div>
          </li>
        </ul>
        <p v-if="!filtered.length" class="notice" role="status">
          {{ t('No matching branches. Try another name.') }}
        </p>
      </aside>

      <div class="map-panel">
        <BranchMap
          :points="branches"
          :selected-id="selectedId"
          :locating="locating"
          :occluded="occluded"
          @select="select"
          @locate="findNearest"
        />
      </div>
    </div>
  </section>
</template>

<style scoped>
/* Fills the shell's content area (see map-shell in CustomerLayout). The map
   covers the whole page; the branch list floats over it at its natural height. */
.branches {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  color: #202e27;
}
.layout {
  position: relative;
  flex: 1;
  min-height: 480px;
  overflow: hidden;
}
.directory {
  position: absolute;
  z-index: 2;
  top: 16px;
  left: 16px;
  display: flex;
  flex-direction: column;
  width: min(380px, calc(100% - 32px));
  max-height: calc(100% - 32px);
  min-height: 0;
  border-radius: 6px;
  background: white;
  box-shadow: 0 4px 18px #14241c38;
  overflow: hidden;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 14px 12px 14px 16px;
  border-bottom: 1px solid #d9ddd6;
}
.search {
  position: relative;
  flex: 1;
  min-width: 0;
}
.search input {
  width: 100%;
  padding: 10px 36px 10px 2px;
  border: 0;
  border-bottom: 1px solid #829087;
  background: transparent;
  color: #202e27;
  font-size: 15px;
}
.search input::-webkit-search-cancel-button {
  margin-right: 4px;
}
.search svg {
  position: absolute;
  top: 50%;
  right: 6px;
  width: 20px;
  height: 20px;
  transform: translateY(-50%);
  fill: none;
  stroke: #4e5b53;
  stroke-width: 1.6;
  pointer-events: none;
}
.filter {
  position: relative;
}
.filter-trigger {
  padding: 8px 10px;
  border-radius: 3px;
  color: #4e5b53;
  font-size: 14px;
  cursor: pointer;
  transition: background-color 140ms ease;
}
.filter-trigger:hover {
  background: #f3f5f1;
}
.filter-trigger.is-active {
  color: #244c38;
  font-weight: 700;
}
.filter-panel {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  z-index: 5;
  min-width: 160px;
  padding: 12px;
  border: 1px solid #d9ddd6;
  border-radius: 4px;
  background: white;
  box-shadow: 0 6px 18px #14241c24;
  font-size: 14px;
}
.filter-panel label {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.notice {
  margin: 12px 16px 0;
  font-size: 13px;
  color: #4e5b53;
}
.list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.list li {
  border-bottom: 1px solid #eceee9;
  transition: background-color 140ms ease;
}
.list li:hover {
  background: #f7f8f6;
}
.list li.is-active {
  background: #eceeea;
}
.row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-right: 10px;
}
.row-main {
  display: grid;
  flex: 1;
  min-width: 0;
  gap: 3px;
  padding: 14px 8px 14px 16px;
  text-align: left;
  cursor: pointer;
}
.row-main strong {
  font-size: 14px;
  overflow-wrap: anywhere;
}
.status {
  font-size: 13px;
  color: #202e27;
}
.status.closed {
  color: #8c392b;
}
.distance {
  font-size: 12px;
  color: #4e5b53;
}
.icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: #4e5b53;
  cursor: pointer;
  transition:
    background-color 140ms ease,
    color 140ms ease;
}
.icon:hover {
  background: #e3e7e1;
  color: #202e27;
}
.icon svg {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
}
.icon.is-saved {
  color: #244c38;
}
.icon.is-saved svg {
  fill: currentColor;
}
.details {
  display: grid;
  gap: 10px;
  padding: 0 16px 16px;
  font-size: 13px;
  line-height: 1.5;
}
.hours div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.hours .today {
  font-weight: 700;
}
.details a {
  justify-self: start;
  color: #244c38;
  font-weight: 700;
  text-decoration: underline;
}
.map-panel {
  position: absolute;
  inset: 0;
}
button:focus-visible,
a:focus-visible,
input:focus-visible {
  outline: 2px solid #244c38;
  outline-offset: 2px;
}
.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
@media (max-width: 700px) {
  /* Bottom sheet over the map on phones. */
  .directory {
    top: auto;
    right: 0;
    bottom: 0;
    left: 0;
    width: auto;
    max-height: 55%;
    border-radius: 12px 12px 0 0;
  }
}
@media (prefers-reduced-motion: reduce) {
  .list li,
  .filter-trigger,
  .icon {
    transition: none;
  }
}
</style>
