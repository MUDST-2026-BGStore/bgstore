<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import { useQuery, useQueryClient } from '@tanstack/vue-query';
import {
  getListStaffAssignmentsQueryKey,
  grantStaffAssignment,
  listBranches,
  listStaffAssignments,
  revokeStaffAssignment,
  searchAccounts,
  useWhoami,
  type AccountMatch,
  type StaffAssignment,
} from '@theparlor/api-client';
import { collectPages } from '../../../shared/lib/pagination';
import { problemMessage } from '../../../shared/lib/api-errors';
import {
  AppButton,
  AppConfirmDialog,
  AppDataTable,
  AppEmptyState,
  AppMessageBar,
  AppSearchField,
  AppSelect,
} from '../../../shared/ui';

const { data: identity, error, isPending } = useWhoami();
const status = computed(() => {
  if (isPending.value) return 'Checking server profile…';
  if (error.value) return error.value.message;
  return identity.value?.roles.includes('admin')
    ? 'Administrator role confirmed by the server profile.'
    : 'Administrator role required.';
});
const isAdmin = computed(() => !!identity.value?.roles.includes('admin'));

const client = useQueryClient();
const branchesQuery = useQuery({
  queryKey: ['admin', 'branches'],
  queryFn: () => collectPages((cursor) => listBranches({ limit: 100, cursor })),
  enabled: isAdmin,
});
const branchOptions = computed(() =>
  (branchesQuery.data.value ?? []).map((branch) => ({
    value: branch.id,
    label: branch.name,
  })),
);
const branchId = ref('');
watch(
  branchOptions,
  (options) => {
    if (!branchId.value && options.length) branchId.value = options[0].value;
  },
  { immediate: true },
);

const assignmentsQuery = useQuery({
  queryKey: computed(() => getListStaffAssignmentsQueryKey(branchId.value)),
  queryFn: ({ signal }) =>
    listStaffAssignments(branchId.value, undefined, signal),
  enabled: computed(() => isAdmin.value && !!branchId.value),
});
const assignments = computed(() => assignmentsQuery.data.value?.items ?? []);
const assignedSubjects = computed(
  () => new Set(assignments.value.map((item) => item.staffSubject)),
);

const query = ref('');
const matches = ref<AccountMatch[]>([]);
const searching = ref(false);
const searchError = ref<string | null>(null);
let searchTimer: ReturnType<typeof setTimeout> | undefined;
let searchSequence = 0;
watch(query, (value) => {
  clearTimeout(searchTimer);
  const text = value.trim();
  if (text.length < 2) {
    searchSequence++;
    matches.value = [];
    searching.value = false;
    searchError.value = null;
    return;
  }
  searchTimer = setTimeout(async () => {
    const sequence = ++searchSequence;
    searching.value = true;
    searchError.value = null;
    try {
      const result = await searchAccounts({ q: text });
      if (sequence === searchSequence) matches.value = result.items;
    } catch (err) {
      if (sequence === searchSequence)
        searchError.value = problemMessage(err) ?? 'Search failed. Try again.';
    } finally {
      if (sequence === searchSequence) searching.value = false;
    }
  }, 300);
});
onBeforeUnmount(() => clearTimeout(searchTimer));

const pending = ref(false);
const actionError = ref<string | null>(null);
const removing = ref<StaffAssignment | null>(null);

async function refreshAssignments() {
  await client.invalidateQueries({
    queryKey: getListStaffAssignmentsQueryKey(branchId.value),
  });
}

async function assign(match: AccountMatch) {
  if (pending.value || !branchId.value) return;
  pending.value = true;
  actionError.value = null;
  try {
    await grantStaffAssignment(branchId.value, match.subject);
    await refreshAssignments();
  } catch (err) {
    actionError.value =
      problemMessage(err) ?? 'Could not assign staff. Try again.';
  } finally {
    pending.value = false;
  }
}

async function confirmRemove() {
  const target = removing.value;
  removing.value = null;
  if (!target || pending.value) return;
  pending.value = true;
  actionError.value = null;
  try {
    await revokeStaffAssignment(branchId.value, target.staffSubject);
    await refreshAssignments();
  } catch (err) {
    actionError.value =
      problemMessage(err) ?? 'Could not remove staff. Try again.';
  } finally {
    pending.value = false;
  }
}

const columns = [
  { label: 'Name' },
  { label: 'Phone' },
  { label: 'Granted by' },
  { label: 'Granted' },
  { label: 'Actions', hidden: true },
];
const dateFormat = new Intl.DateTimeFormat('en-GB', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: 'Asia/Bangkok',
});
</script>
<template>
  <main class="administration">
    <h1>Administration</h1>
    <p data-testid="admin-status">{{ status }}</p>

    <section
      v-if="isAdmin"
      class="admin-section"
      aria-labelledby="branch-staff-title"
    >
      <h2 id="branch-staff-title">Branch staff</h2>
      <AppMessageBar v-if="branchesQuery.error.value" tone="error">
        Could not load branches. {{ branchesQuery.error.value.message }}
      </AppMessageBar>
      <AppSelect
        v-else
        v-model="branchId"
        input-id="admin-branch"
        label="Branch"
        :options="branchOptions"
        :disabled="pending"
      />

      <AppMessageBar v-if="actionError" tone="error">{{
        actionError
      }}</AppMessageBar>
      <AppMessageBar v-if="assignmentsQuery.error.value" tone="error">
        Could not load assignments.
        {{ assignmentsQuery.error.value.message }}
      </AppMessageBar>
      <p v-else-if="branchId && assignmentsQuery.isPending.value" role="status">
        Loading assignments…
      </p>
      <AppEmptyState
        v-else-if="branchId && !assignments.length"
        compact
        title="No staff assigned to this branch."
      />
      <AppDataTable
        v-else-if="assignments.length"
        label="Branch staff assignments"
        caption="Staff assigned to the selected branch"
        :columns="columns"
      >
        <tr v-for="item in assignments" :key="item.staffSubject">
          <td>{{ item.name || item.staffSubject }}</td>
          <td>{{ item.phone || '—' }}</td>
          <td>{{ item.grantedBy }}</td>
          <td>{{ dateFormat.format(new Date(item.grantedAt)) }}</td>
          <td>
            <AppButton
              :disabled="pending"
              :aria-label="`Remove ${item.name || item.staffSubject}`"
              @click="removing = item"
              >Remove</AppButton
            >
          </td>
        </tr>
      </AppDataTable>

      <div class="assign-staff">
        <h3>Assign staff</h3>
        <AppSearchField
          v-model="query"
          label="Search accounts by name or phone"
        />
        <p class="note">
          Branch access applies to accounts with the staff role.
        </p>
        <p v-if="searching" role="status">Searching…</p>
        <AppMessageBar v-else-if="searchError" tone="error">{{
          searchError
        }}</AppMessageBar>
        <p
          v-else-if="query.trim().length >= 2 && !matches.length"
          role="status"
        >
          No matching accounts.
        </p>
        <ul v-if="matches.length" class="matches">
          <li v-for="match in matches" :key="match.subject">
            <span
              >{{ match.name }} <small>{{ match.phone }}</small></span
            >
            <AppButton
              appearance="primary"
              :disabled="
                pending || !branchId || assignedSubjects.has(match.subject)
              "
              :aria-label="`Assign ${match.name}`"
              @click="assign(match)"
              >{{
                assignedSubjects.has(match.subject) ? 'Assigned' : 'Assign'
              }}</AppButton
            >
          </li>
        </ul>
      </div>
    </section>

    <RouterLink :to="{ name: 'WorkspaceIndex' }">Open workspace</RouterLink>

    <AppConfirmDialog
      v-if="removing"
      title-id="remove-staff-title"
      title="Remove branch access?"
      :description="`${removing.name || removing.staffSubject} will no longer be able to operate this branch.`"
      confirm-label="Remove"
      intent="danger"
      @close="removing = null"
      @confirm="confirmRemove"
    />
  </main>
</template>
<style scoped>
.administration {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 16px;
}
.admin-section {
  display: grid;
  gap: 12px;
  margin: 24px 0;
}
.assign-staff {
  display: grid;
  gap: 8px;
}
h3 {
  margin: 8px 0 0;
  font-size: 15px;
}
.note {
  margin: 0;
  font-size: 13px;
  opacity: 0.8;
}
.matches {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.matches li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
</style>
