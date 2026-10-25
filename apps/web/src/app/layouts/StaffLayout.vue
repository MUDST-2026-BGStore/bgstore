<script setup lang="ts">
import { RouterView } from 'vue-router';
</script>

<template>
  <div class="min-h-screen bg-slate-950 text-white">
    <header class="border-b border-white/10 p-5">Staff Portal</header>

    <main class="p-6">
      <RouterView />
    </main>
  </div>
</template>
