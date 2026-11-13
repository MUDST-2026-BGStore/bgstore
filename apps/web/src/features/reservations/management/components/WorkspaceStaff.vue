<script setup lang="ts">
// TODO(staff-admin-api): Staff accounts and branch assignments cannot be read or
// changed through the API yet. The table below is local sample data; the server
// enforces branch access from assignments this screen does not manage.
import { ref } from 'vue';
import {
  AppBadge,
  AppDataTable,
  AppPage,
  AppPanel,
} from '../../../../shared/ui';
const columns = [
  { label: 'Team member' },
  { label: 'Role' },
  { label: 'Shift' },
  { label: 'Assigned area' },
  { label: 'Status' },
];
interface StaffMember {
  id: string;
  name: string;
  role: 'Floor Lead' | 'Game Master' | 'Counter & POS' | 'Branch Manager';
  shift: string;
  status: 'ON_DUTY' | 'ON_BREAK' | 'OFF_DUTY';
  assignedZone: string;
}

const branchStaffRoster = ref<StaffMember[]>([
  {
    id: 's1',
    name: 'Krit Somchai',
    role: 'Floor Lead',
    shift: 'Evening (16:00 - 23:00)',
    status: 'ON_DUTY',
    assignedZone: 'Tables 1 - 3',
  },
  {
    id: 's2',
    name: 'Aom Pornpawee',
    role: 'Game Master',
    shift: 'Evening (16:00 - 23:00)',
    status: 'ON_DUTY',
    assignedZone: 'Tables 4 - 6 & Recommendations',
  },
  {
    id: 's3',
    name: 'Natt Worapat',
    role: 'Counter & POS',
    shift: 'Evening (16:00 - 23:00)',
    status: 'ON_DUTY',
    assignedZone: 'Counter, Bar & Bill Settlement',
  },
  {
    id: 's4',
    name: 'Thana Meechai',
    role: 'Branch Manager',
    shift: 'Full Day (10:00 - 20:00)',
    status: 'ON_DUTY',
    assignedZone: 'Branch Operations & Supervision',
  },
]);
</script>

<template>
  <AppPage title="Staff & shifts">
    <AppPanel title="Branch team" title-id="staff-roster-title"
      ><template #command
        ><span class="workspace-results"
          >{{ branchStaffRoster.length }} team members</span
        ></template
      >
      <AppDataTable
        class="workspace-records"
        label="Staff shifts table"
        caption="Team members, shifts, and assigned areas"
        :columns="columns"
      >
        <tr v-for="staff in branchStaffRoster" :key="staff.id">
          <td>
            <strong class="record-primary">{{ staff.name }}</strong>
          </td>
          <td>{{ staff.role }}</td>
          <td>{{ staff.shift }}</td>
          <td>{{ staff.assignedZone }}</td>
          <td>
            <AppBadge
              :tone="staff.status === 'ON_DUTY' ? 'success' : 'neutral'"
              >{{
                staff.status === 'ON_DUTY'
                  ? 'On duty'
                  : staff.status === 'ON_BREAK'
                    ? 'On break'
                    : 'Off duty'
              }}</AppBadge
            >
          </td>
        </tr>
      </AppDataTable>
    </AppPanel>
  </AppPage>
</template>
