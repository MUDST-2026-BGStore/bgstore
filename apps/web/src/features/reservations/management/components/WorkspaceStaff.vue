<script setup lang="ts">
// Read-only list of staff assigned to the selected branch; administrators manage
// assignments on the Administration page.
import { computed } from 'vue';
import { useQuery } from '@tanstack/vue-query';
import {
  getListStaffAssignmentsQueryKey,
  listStaffAssignments,
} from '@theparlor/api-client';
import {
  AppDataTable,
  AppEmptyState,
  AppMessageBar,
  AppPage,
  AppPanel,
} from '../../../../shared/ui';
import { useManagementWorkspace } from '../context';

const { selectedBranchId } = useManagementWorkspace();
const columns = [{ label: 'Name' }, { label: 'Phone' }, { label: 'Granted' }];
const { data, error, isPending } = useQuery({
  queryKey: computed(() =>
    getListStaffAssignmentsQueryKey(selectedBranchId.value ?? ''),
  ),
  queryFn: ({ signal }) =>
    listStaffAssignments(selectedBranchId.value!, undefined, signal),
  enabled: computed(() => !!selectedBranchId.value),
});
const assignments = computed(() => data.value?.items ?? []);
const dateFormat = new Intl.DateTimeFormat('en-GB', {
  dateStyle: 'medium',
  timeZone: 'Asia/Bangkok',
});
</script>

<template>
  <AppPage title="Branch staff">
    <AppPanel title="Assigned staff" title-id="staff-roster-title"
      ><template #command
        ><span class="workspace-results"
          >{{ assignments.length }} assigned</span
        ></template
      >
      <AppMessageBar v-if="error" tone="error"
        >Could not load branch staff. {{ error.message }}</AppMessageBar
      >
      <p v-else-if="isPending && selectedBranchId" role="status">
        Loading branch staff…
      </p>
      <AppEmptyState
        v-else-if="!assignments.length"
        title="No staff assigned to this branch."
      />
      <AppDataTable
        v-else
        class="workspace-records"
        label="Branch staff table"
        caption="Staff assigned to this branch"
        :columns="columns"
      >
        <tr v-for="staff in assignments" :key="staff.staffSubject">
          <td>
            <strong class="record-primary">{{
              staff.name || staff.staffSubject
            }}</strong>
          </td>
          <td>{{ staff.phone || '—' }}</td>
          <td>{{ dateFormat.format(new Date(staff.grantedAt)) }}</td>
        </tr>
      </AppDataTable>
    </AppPanel>
  </AppPage>
</template>
