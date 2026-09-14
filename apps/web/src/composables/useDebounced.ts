import { onWatcherCleanup, shallowRef, watch, type Ref } from 'vue';

/**
 * Mirrors a source ref after it has stayed unchanged for the given delay.
 * The pending timer is tied to the watcher, so it is cancelled on a new
 * value and when the owning component scope is disposed.
 */
export function useDebounced<T>(source: Ref<T>, delay = 300): Ref<T> {
  const debounced = shallowRef<T>(source.value);

  watch(source, (value) => {
    const timer = setTimeout(() => {
      debounced.value = value;
    }, delay);

    onWatcherCleanup(() => clearTimeout(timer));
  });

  return debounced;
}
