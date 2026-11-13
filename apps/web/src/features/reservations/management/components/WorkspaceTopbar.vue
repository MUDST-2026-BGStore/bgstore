<script setup lang="ts">
import { useManagementWorkspace } from '../context';
import { AppButton, AppField } from '../../../../shared/ui';
const {
  selectedBranchId,
  branches,
  branchesQuery,
  selectedBranch,
  activeViewLabel,
  refreshAll,
  reservationsQuery,
  currentTimeStr,
} = useManagementWorkspace();
</script>

<template>
  <header class="workspace-topbar">
    <nav class="workspace-breadcrumb" aria-label="Location">
      <span>Workspace</span><span aria-hidden="true">/</span
      ><strong>{{ activeViewLabel }}</strong>
    </nav>
    <div class="workspace-context">
      <AppField
        class="workspace-branch"
        label="Branch"
        input-id="workspace-branch"
        layout="inline"
        ><select
          id="workspace-branch"
          v-model="selectedBranchId"
          :disabled="branchesQuery.isPending.value || !branches.length"
          aria-label="Branch"
        >
          <option v-if="!branches.length" value="">
            {{
              branchesQuery.isPending.value
                ? 'Loading branches…'
                : 'No branches available'
            }}
          </option>
          <option
            v-for="branch in branches"
            :key="branch.id"
            :value="branch.id"
          >
            {{ branch.name }}
          </option>
        </select></AppField
      >
      <span class="workspace-refreshed" role="status">{{
        reservationsQuery.isFetching.value
          ? 'Refreshing…'
          : currentTimeStr
            ? `Refreshed ${currentTimeStr}`
            : 'Not refreshed'
      }}</span>
      <AppButton
        class="workspace-refresh"
        :disabled="reservationsQuery.isFetching.value || !selectedBranch"
        aria-label="Refresh reservations"
        title="Refresh reservations"
        @click="refreshAll"
      >
        <i class="fa-solid fa-rotate-right" aria-hidden="true"></i>
      </AppButton>
    </div>
  </header>
</template>

<style scoped>
.workspace-topbar {
  min-height: 64px;
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding: 12px 24px;
  background: var(--ui-surface);
  border-bottom: 1px solid var(--ui-stroke-subtle);
}
.workspace-breadcrumb {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 13px;
  color: var(--ui-muted);
}
.workspace-breadcrumb strong {
  color: var(--ui-text);
  font-weight: 600;
}
.workspace-context {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.workspace-branch select {
  max-width: 230px;
}
.workspace-refreshed {
  font-size: 12px;
  color: var(--ui-muted);
  white-space: nowrap;
}
.workspace-refresh {
  min-width: 34px;
  min-height: 34px;
  flex-shrink: 0;
}
@media (max-width: 1000px) {
  .workspace-refreshed {
    display: none;
  }
}
@media (max-width: 700px) {
  .workspace-topbar {
    padding: 12px;
    gap: 10px;
  }
  .workspace-context {
    width: 100%;
  }
  .workspace-branch {
    flex: 1;
  }
  .workspace-branch select {
    width: 100%;
    max-width: none;
  }
}
</style>
