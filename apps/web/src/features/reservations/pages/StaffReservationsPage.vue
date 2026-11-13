<script setup lang="ts">
import { ref } from 'vue';
import { AppButton, AppMessageBar } from '../../../shared/ui';
import { provideManagementWorkspace } from '../management/context';
import WorkspaceSidebar from '../management/components/WorkspaceSidebar.vue';
import WorkspaceTopbar from '../management/components/WorkspaceTopbar.vue';
import WorkspaceDashboard from '../management/components/WorkspaceDashboard.vue';
import WorkspaceFloor from '../management/components/WorkspaceFloor.vue';
import WorkspaceSessions from '../management/components/WorkspaceSessions.vue';
import WorkspaceBookings from '../management/components/WorkspaceBookings.vue';
import WorkspaceLibrary from '../management/components/WorkspaceLibrary.vue';
import WorkspaceInventory from '../management/components/WorkspaceInventory.vue';
import WorkspaceStaff from '../management/components/WorkspaceStaff.vue';
import WorkspaceAnalytics from '../management/components/WorkspaceAnalytics.vue';
import WorkspaceSettings from '../management/components/WorkspaceSettings.vue';
import WorkspacePolicies from '../management/components/WorkspacePolicies.vue';
import AssignGameDialog from '../management/components/AssignGameDialog.vue';
import WorkspaceBillDialog from '../management/components/WorkspaceBillDialog.vue';
import CancelBookingDialog from '../management/components/CancelBookingDialog.vue';
import WalkInDialog from '../management/components/WalkInDialog.vue';
import RefreshmentsDialog from '../management/components/RefreshmentsDialog.vue';

const {
  activeTab,
  isManager,
  isAdmin,
  branchesQuery,
  reservationsQuery,
  branchAccessVerified,
  branchAccessDenied,
  branches,
  selectedBranchId,
  actionError,
  refreshAll,
} = provideManagementWorkspace();

const contentScroll = ref<HTMLDivElement>();
function resetViewScroll() {
  if (contentScroll.value) contentScroll.value.scrollTop = 0;
}
</script>

<template>
  <div class="gl-admin-layout">
    <WorkspaceSidebar />
    <div class="gl-main-wrapper">
      <WorkspaceTopbar />
      <div
        ref="contentScroll"
        class="gl-content-scroll"
        tabindex="0"
        aria-label="Management workspace"
      >
        <main id="workspace-content" class="gl-content">
          <AppMessageBar v-if="branchAccessDenied" tone="error"
            >You do not have access to this branch.</AppMessageBar
          >
          <AppMessageBar
            v-else-if="branchesQuery.isSuccess.value && branches.length === 0"
            >No branches are available.</AppMessageBar
          >
          <AppMessageBar
            v-else-if="
              branchesQuery.isError.value || reservationsQuery.isError.value
            "
            tone="error"
            >Could not load branch data.<template #actions
              ><AppButton
                @click="
                  branchesQuery.refetch();
                  refreshAll();
                "
                >Retry</AppButton
              ></template
            ></AppMessageBar
          >
          <AppMessageBar
            v-else-if="
              branchesQuery.isPending.value ||
              (selectedBranchId && reservationsQuery.isPending.value)
            "
            >Loading…</AppMessageBar
          >
          <AppMessageBar v-if="actionError" tone="error">{{
            actionError
          }}</AppMessageBar>
          <!-- Views, sample records and actions appear only once the server has
               accepted this account as an operator of the selected branch. -->
          <Transition
            v-if="branchAccessVerified"
            name="workspace-view"
            mode="out-in"
            @before-enter="resetViewScroll"
            @before-leave="(element) => element.setAttribute('inert', '')"
          >
            <div :key="activeTab" class="workspace-view">
              <WorkspaceDashboard v-if="activeTab === 'dashboard'" />
              <WorkspaceFloor v-else-if="activeTab === 'floor'" />
              <WorkspaceSessions v-else-if="activeTab === 'sessions'" />
              <WorkspaceBookings v-else-if="activeTab === 'bookings'" />
              <WorkspaceLibrary v-else-if="activeTab === 'library'" />
              <WorkspaceInventory v-else-if="activeTab === 'inventory'" />
              <WorkspaceStaff
                v-else-if="activeTab === 'staff-roster' && isManager"
              />
              <WorkspaceAnalytics
                v-else-if="activeTab === 'analytics' && isManager"
              />
              <WorkspaceSettings
                v-else-if="activeTab === 'settings' && isAdmin"
              />
              <WorkspacePolicies
                v-else-if="activeTab === 'policies' && isAdmin"
              />
            </div>
          </Transition>
        </main>
      </div>
      <template v-if="branchAccessVerified">
        <AssignGameDialog />
        <WorkspaceBillDialog />
        <CancelBookingDialog />
        <WalkInDialog />
        <RefreshmentsDialog />
      </template>
    </div>
  </div>
</template>

<style src="../management/styles/workspace.css"></style>
