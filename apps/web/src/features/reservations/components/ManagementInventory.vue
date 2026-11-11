<script setup lang="ts">
import { computed, ref } from 'vue';
import { channelLabels, fulfillmentSamples, rentalCopySamples, retailStockSamples, type InventoryChannel, type RetailStockSample } from './management-inventory.samples';

defineProps<{ branchName: string }>();
const views = [
  { id: 'retail', label: 'Retail stock' },
  { id: 'rentals', label: 'Rental copies' },
  { id: 'food', label: 'Food & drinks' },
  { id: 'fulfillment', label: 'Fulfillment' },
] as const;
type View = typeof views[number]['id'];
const view = ref<View>('retail');
const search = ref('');
const status = ref('all');
const channel = ref<InventoryChannel | 'all'>('all');
const selectedSku = ref<string | null>(null);
const selectedItem = computed(() => retailStockSamples.find(item => item.sku === selectedSku.value));
const available = (item: RetailStockSample) => item.onHand - item.shippingHeld - item.pickupHeld;
function stockStatus(item: RetailStockSample) {
  if (item.onHand === 0) return 'Out of stock';
  if (available(item) === 0) return 'Fully held';
  if (available(item) <= item.reorderAt) return 'Low stock';
  return 'In stock';
}
const matches = (...values: string[]) => values.join(' ').toLowerCase().includes(search.value.trim().toLowerCase());
const retailRows = computed(() => retailStockSamples.filter(item => matches(item.sku, item.name, item.edition) && (status.value === 'all' || stockStatus(item) === status.value)));
const rentalRows = computed(() => rentalCopySamples.filter(item => matches(item.sku, item.assetCode, item.name) && (status.value === 'all' || item.state === status.value)));
const fulfillmentRows = computed(() => fulfillmentSamples.filter(item => matches(item.reference, item.sku) && (channel.value === 'all' || item.channel === channel.value)));
const rowCount = computed(() => (view.value === 'retail' ? retailRows.value.length : view.value === 'rentals' ? rentalRows.value.length : fulfillmentRows.value.length));
const filtersActive = computed(() => search.value.trim() !== '' || status.value !== 'all' || channel.value !== 'all');
function clearFilters() {
  search.value = '';
  status.value = 'all';
  channel.value = 'all';
}
const totals = computed(() => ({
  onHand: retailStockSamples.reduce((sum, item) => sum + item.onHand, 0),
  held: retailStockSamples.reduce((sum, item) => sum + item.shippingHeld + item.pickupHeld, 0),
  available: retailStockSamples.reduce((sum, item) => sum + available(item), 0),
}));
function changeView(next: View) {
  view.value = next;
  search.value = '';
  status.value = 'all';
  channel.value = 'all';
  selectedSku.value = null;
}
</script>

<template>
  <section class="inventory-workspace" aria-labelledby="inventory-title">
    <header class="inventory-heading">
      <div>
        <p class="context-label">{{ branchName }} branch</p>
        <h1 id="inventory-title">Inventory & fulfillment</h1>
      </div>
    </header>
    <nav class="inventory-tabs" aria-label="Inventory views">
      <button v-for="item in views" :key="item.id" type="button" :aria-current="view === item.id ? 'page' : undefined" @click="changeView(item.id)">{{ item.label }}</button>
    </nav>

    <template v-if="view === 'food'">
      <slot name="slot" />
    </template>
    <section v-else class="inventory-body" :aria-label="views.find(item => item.id === view)?.label">
      <template v-if="view === 'retail'">
        <dl class="stock-summary">
          <div><dt>On hand</dt><dd>{{ totals.onHand }}</dd></div>
          <div><dt>Held for orders</dt><dd>{{ totals.held }}</dd></div>
          <div><dt>Available to sell</dt><dd>{{ totals.available }}</dd></div>
          <div><dt>Retail SKUs</dt><dd>{{ retailStockSamples.length }}</dd></div>
        </dl>
      </template>
      <div v-else class="channel-rules">
        <article><h2>Delivery</h2><p>Supplied from the delivery branch</p></article>
        <article><h2>Store pickup</h2><p>Supplied from the pickup branch</p></article>
        <article><h2>In-store sale</h2><p>Deducted at the counter</p></article>
      </div>

      <div class="inventory-toolbar">
        <label class="search-field"><span>Search {{ view === 'fulfillment' ? 'orders or SKUs' : 'names or SKUs' }}</span><input v-model="search" type="search" :placeholder="view === 'rentals' ? 'Name, SKU or asset code' : 'Search inventory'" /></label>
        <label v-if="view !== 'fulfillment'" class="status-field"><span>Status</span><select v-model="status"><option value="all">All statuses</option><template v-if="view === 'retail'"><option>In stock</option><option>Low stock</option><option>Fully held</option><option>Out of stock</option></template><template v-else><option>On shelf</option><option>At table</option><option>Maintenance</option></template></select></label>
        <label v-else class="status-field"><span>Channel</span><select v-model="channel"><option value="all">All channels</option><option v-for="(label, key) in channelLabels" :key="key" :value="key">{{ label }}</option></select></label>
        <span class="results-count" role="status">{{ rowCount }} {{ view === 'retail' ? 'SKUs' : view === 'rentals' ? 'copies' : 'orders' }}</span>
      </div>

      <section v-if="selectedItem && view === 'retail'" class="stock-detail" aria-label="SKU stock breakdown">
        <div><h2>{{ selectedItem.name }} · {{ selectedItem.sku }}</h2><p>{{ selectedItem.edition }}</p></div>
        <button type="button" @click="selectedSku = null">Close breakdown</button>
        <dl><div><dt>Delivery holds</dt><dd>{{ selectedItem.shippingHeld }}</dd></div><div><dt>Pickup holds</dt><dd>{{ selectedItem.pickupHeld }}</dd></div><div><dt>Available</dt><dd>{{ available(selectedItem) }}</dd></div></dl>
      </section>

      <div class="inventory-table-scroll" tabindex="0" :aria-label="`${view} inventory table`">
        <table v-if="view === 'retail'">
          <caption class="sr-only">Retail stock by edition SKU</caption>
          <thead><tr><th scope="col">Product / SKU</th><th scope="col" class="numeric">On hand</th><th scope="col" class="numeric">Held</th><th scope="col" class="numeric">To sell</th><th scope="col">Status</th></tr></thead>
          <tbody><tr v-for="item in retailRows" :key="item.sku"><td><strong>{{ item.name }}</strong><span class="secondary">{{ item.edition }}</span><button type="button" class="sku-link" :aria-expanded="selectedSku === item.sku" @click="selectedSku = selectedSku === item.sku ? null : item.sku">{{ item.sku }}</button></td><td class="numeric">{{ item.onHand }}</td><td class="numeric">{{ item.shippingHeld + item.pickupHeld }}</td><td class="numeric"><strong>{{ available(item) }}</strong></td><td><span class="stock-state" :class="{ attention: available(item) <= item.reorderAt }">{{ stockStatus(item) }}</span></td></tr><tr v-if="!retailRows.length"><td colspan="5" class="empty-results">No results<button v-if="filtersActive" type="button" class="clear-filters" @click="clearFilters">Clear filters</button></td></tr></tbody>
        </table>
        <table v-else-if="view === 'rentals'">
          <caption class="sr-only">Rental copies</caption>
          <thead><tr><th scope="col">Game / rental SKU</th><th scope="col">Asset code</th><th scope="col">Status</th><th scope="col">Location / condition</th></tr></thead>
          <tbody><tr v-for="item in rentalRows" :key="item.assetCode"><td><strong>{{ item.name }}</strong><code class="secondary">{{ item.sku }}</code></td><td><code>{{ item.assetCode }}</code></td><td>{{ item.state }}</td><td>{{ item.location }}</td></tr><tr v-if="!rentalRows.length"><td colspan="4" class="empty-results">No results<button v-if="filtersActive" type="button" class="clear-filters" @click="clearFilters">Clear filters</button></td></tr></tbody>
        </table>
        <table v-else>
          <caption class="sr-only">Order fulfillment queue</caption>
          <thead><tr><th scope="col">Order</th><th scope="col">Channel</th><th scope="col">Stock source</th><th scope="col">Status</th></tr></thead>
          <tbody><tr v-for="item in fulfillmentRows" :key="item.reference"><td><strong>{{ item.reference }}</strong><code class="secondary">{{ item.sku }} × {{ item.quantity }}</code></td><td>{{ channelLabels[item.channel] }}</td><td>{{ item.channel === 'shipping' ? 'Delivery branch' : item.channel === 'pickup' ? 'Pickup branch' : 'Selling branch' }}</td><td>{{ item.state }}</td></tr><tr v-if="!fulfillmentRows.length"><td colspan="4" class="empty-results">No results<button v-if="filtersActive" type="button" class="clear-filters" @click="clearFilters">Clear filters</button></td></tr></tbody>
        </table>
      </div>
    </section>
  </section>
</template>

<style scoped>
.inventory-workspace { color: #202e27; font-size: 14px; min-width: 0; }
.inventory-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.context-label { color: #4e5b53; margin: 0 0 6px; font-size: 13px; }
h1 { font-size: clamp(24px, 3vw, 30px); font-weight: 600; letter-spacing: -.03em; line-height: 1.3; margin: 0 0 8px; }
h2 { margin: 0; font-size: 14px; font-weight: 600; }
p { line-height: 1.6; margin: 0; }
.inventory-heading p:last-child { color: #4e5b53; }
/* Sample data only. TODO: replace retailStockSamples/rentalCopySamples/fulfillmentSamples with live API data. */
.inventory-tabs { display: flex; gap: 24px; border-bottom: 1px solid #d3d9d4; margin-bottom: 24px; overflow-x: auto; }
.inventory-tabs button { background: transparent; padding: 12px 0; border: 0; border-bottom: 2px solid transparent; white-space: nowrap; color: #4e5b53; font: inherit; cursor: pointer; }
.inventory-tabs button[aria-current] { border-bottom-color: #202e27; color: #202e27; font-weight: 600; }
.inventory-body { background: #ffffff; border: 1px solid #d3d9d4; border-radius: 4px; padding: 24px; }
.stock-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 20px; margin: 0 0 18px; }
dt { font-size: 13px; color: #4e5b53; }
dd { margin: 6px 0 0; font-size: 24px; font-weight: 600; font-variant-numeric: tabular-nums; }
.inventory-toolbar { display: flex; align-items: end; flex-wrap: wrap; gap: 16px; margin: 24px 0 16px; }
.inventory-toolbar label { display: flex; flex-direction: column; gap: 6px; font-size: 12px; color: #4e5b53; }
.search-field { flex: 1; min-width: min(100%, 200px); }
.status-field { min-width: 160px; }
input, select { width: 100%; box-sizing: border-box; background: #fff; border: 1px solid #829087; border-radius: 3px; color: #202e27; padding: 9px 10px; font: inherit; font-size: 14px; min-height: 40px; }
input::placeholder { color: #4e5b53; }
.inventory-table-scroll { overflow-x: auto; max-width: 100%; }
table { width: 100%; border-collapse: collapse; white-space: nowrap; font-size: 13px; }
th { text-align: left; font-size: 12px; font-weight: 500; color: #4e5b53; padding: 12px; border-block: 1px solid #d3d9d4; background: #fafbf9; }
td { padding: 16px 12px; border-bottom: 1px solid #e4e8e4; vertical-align: middle; }
.numeric { text-align: right; font-variant-numeric: tabular-nums; }
.secondary { display: block; margin-top: 5px; color: #4e5b53; font-size: 12px; }
code { font-size: 12px; }
.sku-link { padding: 0; border: 0; margin-top: 6px; background: transparent; color: #244c38; font-family: monospace; font-size: 12px; text-decoration: underline; text-underline-offset: 3px; cursor: pointer; }
.stock-state { color: #244c38; }
.stock-state.attention { color: #823020; }
.results-count { align-self: end; padding-bottom: 10px; color: #4e5b53; font-size: 13px; white-space: nowrap; }
.clear-filters { display: inline-flex; margin-left: 14px; padding: 5px 10px; border: 1px solid #829087; border-radius: 3px; background: transparent; color: #202e27; font: inherit; font-size: 13px; cursor: pointer; transition: background-color 140ms ease, border-color 140ms ease; }
.clear-filters:hover { background: #e9ede9; }
.empty-results { text-align: center; white-space: normal; padding: 36px 12px; color: #4e5b53; }
.channel-rules { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 24px; padding-bottom: 20px; border-bottom: 1px solid #d3d9d4; }
.channel-rules p { margin-top: 8px; font-size: 13px; color: #4e5b53; }
.stock-detail { display: flex; align-items: start; flex-wrap: wrap; gap: 16px; background: #e9ede9; padding: 18px; margin-bottom: 16px; border-radius: 4px; }
.stock-detail > div { flex: 1; }
.stock-detail button { background: transparent; color: #202e27; border: 1px solid #829087; border-radius: 3px; font: inherit; font-size: 12px; padding: 6px 10px; cursor: pointer; }
.stock-detail p { font-size: 12px; color: #4e5b53; }
.stock-detail dl { display: flex; gap: 32px; width: 100%; margin: 0; }
.stock-detail dd { font-size: 20px; }
.inventory-workspace :is(button, input, select, .inventory-table-scroll):focus-visible { outline: 2px solid #244c38; outline-offset: 3px; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
@media (max-width: 700px) { .inventory-body { padding: 16px; } .inventory-heading { align-items: start; } .stock-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); } .channel-rules { grid-template-columns: 1fr; gap: 16px; } .inventory-tabs { gap: 18px; } .stock-detail dl { flex-wrap: wrap; gap: 16px; } .stock-detail h2 { overflow-wrap: anywhere; } }
</style>
