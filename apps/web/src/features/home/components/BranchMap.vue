<script setup lang="ts">
// Branch map rendered with MapLibre GL JS. Basemap: OpenFreeMap "positron"
// vector style (free, no API key; attribution is shown by MapLibre).
import { onMounted, onUnmounted, shallowRef, useTemplateRef, watch } from 'vue';
import {
  FullscreenControl,
  LngLatBounds,
  Map as MapLibreMap,
  Marker,
  NavigationControl,
  setWorkerUrl,
} from 'maplibre-gl';
import workerUrl from 'maplibre-gl/dist/maplibre-gl-worker.mjs?worker&url';
import 'maplibre-gl/dist/maplibre-gl.css';
import { useStoreText } from '../data/store-text';

setWorkerUrl(workerUrl);

const MAP_STYLE = 'https://tiles.openfreemap.org/styles/positron';
const EDGE = 60;

interface MapPoint {
  id: string;
  name: string;
  location: { lat: number; lng: number };
}
const props = defineProps<{
  points: MapPoint[];
  selectedId: string;
  locating?: boolean;
  /** Screen area covered by overlays (e.g. a floating list), kept clear of pins. */
  occluded?: { left?: number; bottom?: number };
}>();
const emit = defineEmits<{ select: [id: string]; locate: [] }>();
const { t } = useStoreText();

const container = useTemplateRef<HTMLDivElement>('container');
const map = shallowRef<MapLibreMap>();
let markers: Marker[] = [];
// Until the visitor moves the map, keep refitting as the layout settles.
let touched = false;

const reducedMotion = () =>
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const padding = () => ({
  top: EDGE,
  right: EDGE,
  bottom: EDGE + (props.occluded?.bottom ?? 0),
  left: EDGE + (props.occluded?.left ?? 0),
});

function fit() {
  const instance = map.value;
  if (!instance || !props.points.length) return;
  const bounds = new LngLatBounds();
  for (const point of props.points) bounds.extend(point.location);
  instance.fitBounds(bounds, {
    padding: padding(),
    maxZoom: 14,
    duration: reducedMotion() ? 0 : 400,
  });
}
function panTo(id: string) {
  const instance = map.value;
  const point = props.points.find((p) => p.id === id);
  if (!instance || !point) return;
  touched = true;
  const screen = instance.project(point.location);
  const { width, height } = instance.getContainer().getBoundingClientRect();
  const pad = padding();
  const visible =
    screen.x > pad.left &&
    screen.x < width - pad.right &&
    screen.y > pad.top &&
    screen.y < height - pad.bottom;
  if (!visible)
    instance.easeTo({
      center: point.location,
      padding: pad,
      duration: reducedMotion() ? 0 : 500,
    });
}

function markerElement(point: MapPoint, active: boolean) {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = active ? 'branch-pin is-active' : 'branch-pin';
  button.setAttribute('aria-label', t(point.name));
  button.setAttribute('aria-pressed', String(active));
  if (active)
    button.innerHTML = `<svg viewBox="0 0 32 42" aria-hidden="true"><path d="M16 41s14-13.6 14-25A14 14 0 0 0 2 16c0 11.4 14 25 14 25Z" fill="#244c38" stroke="white" stroke-width="2"/><rect x="10" y="10" width="12" height="12" rx="2" fill="white"/><path d="M13 14h6M13 18h6" stroke="#244c38" stroke-width="1.6" stroke-linecap="round"/></svg>`;
  const label = document.createElement('span');
  label.className = 'branch-pin-label';
  label.textContent = t(point.name);
  button.append(label);
  button.addEventListener('click', (event) => {
    event.stopPropagation();
    emit('select', point.id);
  });
  return button;
}
function renderMarkers() {
  const instance = map.value;
  if (!instance) return;
  for (const marker of markers) marker.remove();
  // Add the selected pin last so it sits on top.
  const ordered = [...props.points].sort(
    (a, b) =>
      Number(a.id === props.selectedId) - Number(b.id === props.selectedId),
  );
  markers = ordered.map((point) => {
    const active = point.id === props.selectedId;
    return new Marker({
      element: markerElement(point, active),
      anchor: active ? 'bottom' : 'center',
    })
      .setLngLat(point.location)
      .addTo(instance);
  });
}

function controlLocale() {
  return {
    'NavigationControl.ZoomIn': t('Zoom in'),
    'NavigationControl.ZoomOut': t('Zoom out'),
    'NavigationControl.ResetBearing': t('Reset map direction'),
    'FullscreenControl.Enter': t('Full screen'),
    'FullscreenControl.Exit': t('Exit full screen'),
  };
}

onMounted(() => {
  if (!container.value) return;
  const instance = new MapLibreMap({
    container: container.value,
    style: MAP_STYLE,
    center: props.points[0]?.location ?? { lat: 13.75, lng: 100.52 },
    zoom: 12,
    locale: controlLocale(),
    attributionControl: { compact: true },
    dragRotate: false,
    pitchWithRotate: false,
    touchPitch: false,
  });
  instance.touchZoomRotate.disableRotation();
  instance.addControl(new FullscreenControl(), 'top-right');
  instance.addControl(
    new NavigationControl({ showCompass: false }),
    'top-right',
  );
  // Only user gestures carry an originalEvent; programmatic moves do not.
  instance.on('movestart', (event) => {
    if ('originalEvent' in event && event.originalEvent) touched = true;
  });
  map.value = instance;
  renderMarkers();
  fit();
});
onUnmounted(() => {
  map.value?.remove();
  map.value = undefined;
  markers = [];
});

watch(
  () => [props.points, props.selectedId] as const,
  () => renderMarkers(),
);
watch(
  () => props.selectedId,
  (id) => panTo(id),
);
watch(
  () => props.occluded,
  () => {
    map.value?.resize();
    if (!touched) fit();
  },
);
defineExpose({ fit, panTo });
</script>

<template>
  <div class="branch-map">
    <div
      ref="container"
      class="canvas"
      role="region"
      :aria-label="t('Branch map')"
    ></div>
    <div class="actions">
      <button
        type="button"
        :aria-label="t('Find nearest store')"
        :title="t('Find nearest store')"
        :disabled="locating"
        @click="emit('locate')"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="12" r="3" />
          <circle cx="12" cy="12" r="7" />
          <path d="M12 2v3M12 19v3M2 12h3M19 12h3" />
        </svg>
      </button>
      <button
        type="button"
        :aria-label="t('Show all branches')"
        :title="t('Show all branches')"
        @click="fit"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <rect x="4" y="4" width="16" height="16" rx="1" />
          <circle cx="12" cy="12" r="2" />
        </svg>
      </button>
    </div>
  </div>
</template>

<style scoped>
.branch-map {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 320px;
  background: #eef0ec;
}
.canvas {
  position: absolute;
  inset: 0;
}
/* Below MapLibre's top-right zoom/fullscreen controls. */
.actions {
  position: absolute;
  z-index: 2;
  top: 124px;
  right: 10px;
  display: grid;
  gap: 8px;
}
.actions button {
  display: grid;
  place-items: center;
  width: 29px;
  height: 29px;
  border-radius: 4px;
  background: white;
  color: #202e27;
  box-shadow: 0 0 0 2px #0000001a;
  cursor: pointer;
  transition: background-color 140ms ease;
}
.actions button:hover {
  background: #f2f2f2;
}
.actions button:disabled {
  cursor: progress;
  opacity: 0.6;
}
.actions svg {
  width: 17px;
  height: 17px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
}
.actions button:focus-visible {
  outline: 2px solid #244c38;
  outline-offset: 2px;
}
/* Marker elements are created by MapLibre outside this component's scope. */
:deep(.branch-pin) {
  position: relative;
  width: 16px;
  height: 16px;
  border: 2px solid white;
  border-radius: 50%;
  background: #2f7a4f;
  box-shadow: 0 1px 4px #14241c55;
  cursor: pointer;
}
:deep(.branch-pin.is-active) {
  width: 34px;
  height: 44px;
  border: 0;
  border-radius: 0;
  background: none;
  box-shadow: none;
  filter: drop-shadow(0 2px 3px #14241c66);
}
:deep(.branch-pin svg) {
  display: block;
  width: 100%;
  height: 100%;
}
:deep(.branch-pin:focus-visible) {
  outline: 2px solid #244c38;
  outline-offset: 3px;
}
:deep(.branch-pin-label) {
  position: absolute;
  left: 50%;
  bottom: calc(100% + 4px);
  transform: translateX(-50%);
  padding: 3px 8px;
  border-radius: 3px;
  background: white;
  color: #202e27;
  font:
    700 12px/1.3 Arial,
    sans-serif;
  white-space: nowrap;
  box-shadow: 0 1px 4px #14241c33;
  opacity: 0;
  pointer-events: none;
  transition: opacity 140ms ease;
}
:deep(.branch-pin:hover .branch-pin-label),
:deep(.branch-pin:focus-visible .branch-pin-label),
:deep(.branch-pin.is-active .branch-pin-label) {
  opacity: 1;
}
@media (prefers-reduced-motion: reduce) {
  .actions button,
  :deep(.branch-pin-label) {
    transition: none;
  }
}
</style>
