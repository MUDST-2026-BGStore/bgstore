<script setup lang="ts">
import ManagementInventory from '../../components/ManagementInventory.vue';
import WorkspaceSaleStock from './WorkspaceSaleStock.vue';
import WorkspacePantryStock from './WorkspacePantryStock.vue';
import { useManagementWorkspace } from '../context';
const { selectedBranchId, selectedBranch } = useManagementWorkspace();
</script>

<template>
  <ManagementInventory
    :key="selectedBranchId"
    :branch-name="selectedBranch?.name || 'Select a branch'"
  >
    <template #stock><WorkspaceSaleStock /><WorkspacePantryStock /></template>
  </ManagementInventory>
</template>
