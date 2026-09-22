import { computed, onBeforeUnmount, onMounted, ref } from 'vue';

const formatElapsedTime = (totalSeconds: number) => {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return [hours, minutes, seconds]
    .map((value) => String(value).padStart(2, '0'))
    .join(':');
};

/**
 * Keeps the elapsed-time display moving between authoritative API refreshes.
 * The session is read through a getter so the clock survives the first load.
 */
export const useActiveSession = (
  session: () => { startedAt: string } | null,
) => {
  const currentTime = ref(Date.now());
  let timer: ReturnType<typeof setInterval> | undefined;

  const elapsedSeconds = computed(() => {
    const current = session();
    if (!current) {
      return 0;
    }
    return Math.max(
      0,
      Math.floor(
        (currentTime.value - new Date(current.startedAt).getTime()) / 1000,
      ),
    );
  });
  const elapsedTime = computed(() => formatElapsedTime(elapsedSeconds.value));

  onMounted(() => {
    timer = setInterval(() => {
      currentTime.value = Date.now();
    }, 1000);
  });

  onBeforeUnmount(() => {
    if (timer) {
      clearInterval(timer);
    }
  });

  return { elapsedTime };
};
