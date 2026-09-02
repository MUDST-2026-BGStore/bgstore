<script setup lang="ts">
import { RouterLink, RouterView, useRoute } from 'vue-router';

const route = useRoute();
const isBranchesActive = () => route.path.startsWith('/branches');
</script>

<template>
  <div
    class="h-screen w-screen flex flex-col bg-white text-gray-800 antialiased font-sans overflow-hidden"
  >
    <!-- Top Header Bar -->
    <header class="w-full border-b border-gray-200 bg-white shrink-0 h-16">
      <div class="w-full px-12 h-full flex justify-between items-center">
        <!-- Left: Circle Avatar -->
        <div class="w-9 h-9 rounded-full bg-[#3d6871]"></div>

        <!-- Right: Menu Items -->
        <nav class="flex items-center gap-2 text-[14px] font-medium">
          <RouterLink
            class="flex items-center gap-2 px-3.5 py-2 rounded-lg text-gray-600 hover:text-gray-900 transition-colors"
            to="/"
          >
            <svg
              class="w-4 h-4 text-gray-500"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"
              />
            </svg>
            Dashboard
          </RouterLink>

          <RouterLink
            :class="
              isBranchesActive()
                ? 'bg-[#e5eff1] text-[#2c5e69] font-semibold'
                : 'text-gray-600 hover:text-gray-900'
            "
            class="flex items-center gap-2 px-4 py-2 rounded-lg transition-colors"
            to="/branches"
          >
            <svg
              class="w-4 h-4"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"
              />
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M15 11a3 3 0 11-6 0 3 3 0 016 0z"
              />
            </svg>
            Branches
          </RouterLink>

          <a
            class="flex items-center gap-2 px-3.5 py-2 rounded-lg text-gray-600 hover:text-gray-900 transition-colors"
            href="#tables"
          >
            <svg
              class="w-4 h-4 text-gray-500"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M4 6h16M4 10h16M4 14h16M4 18h16"
              />
            </svg>
            Tables
          </a>

          <a
            class="flex items-center gap-2 px-3.5 py-2 rounded-lg text-gray-600 hover:text-gray-900 transition-colors"
            href="#games"
          >
            <svg
              class="w-4 h-4 text-gray-500"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M15 5v2m0 4v2m0 4v2M5 5a2 2 0 00-2 2v3a2 2 0 110 4v3a2 2 0 002 2h14a2 2 0 002-2v-3a2 2 0 110-4V7a2 2 0 00-2-2H5z"
              />
            </svg>
            Games
          </a>
        </nav>
      </div>
    </header>

    <!-- Main View Slot -->
    <div class="flex-1 flex flex-col overflow-hidden">
      <RouterView />
    </div>
  </div>
</template>
