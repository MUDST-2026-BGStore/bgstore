<script setup lang="ts">
import {
  AppButton,
  AppDialog,
  AppDialogPanel,
  AppField,
  AppFormGrid,
} from '../../../../shared/ui';
import { useManagementWorkspace } from '../context';
const { isManager, showAddFoodModal, newFoodForm, submitAddNewFood } =
  useManagementWorkspace();
</script>
<template>
  <AppDialog
    v-if="showAddFoodModal && isManager"
    title-id="AddPantryItemDialog-title"
    @close="showAddFoodModal = false"
  >
    <AppDialogPanel
      title-id="AddPantryItemDialog-title"
      title="Add menu item"
      as="form"
      @submit.prevent="submitAddNewFood"
      @close="showAddFoodModal = false"
    >
      <AppFormGrid>
        <AppField
          class="field-full-width"
          input-id="AddPantryItemDialog-field-1"
          label="Item name (English)"
          ><input
            id="AddPantryItemDialog-field-1"
            v-model="newFoodForm.name"
            type="text"
            required
        /></AppField>
        <AppField
          class="field-full-width"
          input-id="AddPantryItemDialog-field-2"
          label="Item name (Thai)"
          ><input
            id="AddPantryItemDialog-field-2"
            v-model="newFoodForm.nameTh"
            type="text"
        /></AppField>
        <AppField input-id="AddPantryItemDialog-field-3" label="Category"
          ><select
            id="AddPantryItemDialog-field-3"
            v-model="newFoodForm.category"
          >
            <option value="beverages">Beverages</option>
            <option value="snacks">Snacks & Fries</option>
            <option value="tea_coffee">Tea & Coffee</option>
            <option value="bakery_desserts">Bakery & Desserts</option>
          </select></AppField
        >
        <AppField input-id="AddPantryItemDialog-field-4" label="Serving unit"
          ><select id="AddPantryItemDialog-field-4" v-model="newFoodForm.unit">
            <option value="cans">cans (กระป๋อง)</option>
            <option value="bottles">bottles (ขวด)</option>
            <option value="cups">cups (แก้ว)</option>
            <option value="portions">portions (ที่/จาน)</option>
            <option value="pieces">pieces (ชิ้น)</option>
            <option value="bags">bags (ซอง)</option>
          </select></AppField
        >
        <AppField
          input-id="AddPantryItemDialog-field-5"
          label="Selling price (฿)"
          ><input
            id="AddPantryItemDialog-field-5"
            v-model.number="newFoodForm.price"
            type="number"
            min="0"
        /></AppField>
        <AppField input-id="AddPantryItemDialog-field-6" label="Cost price (฿)"
          ><input
            id="AddPantryItemDialog-field-6"
            v-model.number="newFoodForm.costPrice"
            type="number"
            min="0"
        /></AppField>
        <AppField
          input-id="AddPantryItemDialog-field-7"
          label="Initial stock quantity"
          ><input
            id="AddPantryItemDialog-field-7"
            v-model.number="newFoodForm.initialStock"
            type="number"
            min="0"
            step="1"
        /></AppField>
        <AppField
          input-id="AddPantryItemDialog-field-8"
          label="Low stock threshold"
          ><input
            id="AddPantryItemDialog-field-8"
            v-model.number="newFoodForm.lowStockThreshold"
            type="number"
            min="1"
            step="1"
        /></AppField>
      </AppFormGrid>
      <template #footer
        ><AppButton @click="showAddFoodModal = false">Cancel</AppButton
        ><AppButton
          type="submit"
          appearance="primary"
          :disabled="!newFoodForm.name.trim()"
          >Add item</AppButton
        ></template
      >
    </AppDialogPanel>
  </AppDialog>
</template>
