<template>
  <!--
    Keyed on the path so a route-param change (for example /games/a/edit to
    /games/b/edit) remounts the page instead of reusing a component whose
    form state was seeded from the previous game.
  -->
  <router-view v-slot="{ Component, route }">
    <component :is="Component" :key="route.path" />
  </router-view>
</template>
