import { ref } from 'vue';
import { defineStore } from 'pinia';
// Keep discovery choices during product navigation, without persisting searches
// across visits or coupling them to purchase fulfillment.
export const useCatalogBrowseStore = defineStore('catalog-browse', () => {
  const query = ref('');
  const category = ref<string>('');
  const players = ref('');
  const time = ref('');
  const difficulty = ref('');
  const soldHere = ref(false);
  const inLibrary = ref(false);
  const sort = ref('shelf');
  const view = ref<'grid' | 'list'>('grid');
  return {
    query,
    category,
    players,
    time,
    difficulty,
    soldHere,
    inLibrary,
    sort,
    view,
  };
});
