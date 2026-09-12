import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import type { ActiveSessionSnapshot } from './active-session-types';

const formatElapsedTime = (totalSeconds: number) => {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return [hours, minutes, seconds]
    .map((value) => String(value).padStart(2, '0'))
    .join(':');
};

/** Keeps the elapsed-time display moving between authoritative API refreshes. */
export const useActiveSession = (session: ActiveSessionSnapshot) => {
  const currentTime = ref(Date.now());
  let timer: ReturnType<typeof setInterval> | undefined;

  const elapsedSeconds = computed(() =>
    Math.max(
      0,
      Math.floor(
        (currentTime.value - new Date(session.startedAt).getTime()) / 1000,
      ),
    ),
  );
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
