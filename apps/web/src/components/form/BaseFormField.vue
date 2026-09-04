<script setup lang="ts">
withDefaults(
  defineProps<{
    id: string;
    label: string;
    name: string;
    autocomplete?: string;
    inputmode?: 'email' | 'numeric' | 'search' | 'tel' | 'text' | 'url';
    placeholder?: string;
    readonly?: boolean;
    required?: boolean;
    requiredMark?: boolean;
    type?: 'email' | 'password' | 'tel' | 'text';
  }>(),
  {
    autocomplete: undefined,
    inputmode: 'text',
    placeholder: undefined,
    readonly: false,
    required: false,
    requiredMark: false,
    type: 'text',
  },
);

const model = defineModel<string>({ required: true });
</script>

<template>
  <div class="form-field">
    <label :for="id">
      {{ label }}
      <span
        v-if="required || requiredMark"
        class="required-mark"
        aria-hidden="true"
        >*</span
      >
    </label>
    <input
      :id="id"
      v-model="model"
      :name="name"
      :type="type"
      :autocomplete="autocomplete"
      :inputmode="inputmode"
      :placeholder="placeholder"
      :readonly="readonly"
      :required="required"
    />
  </div>
</template>

<style scoped>
.form-field {
  display: grid;
  gap: 0.125rem;
}

label {
  color: #20252d;
  font-size: 1rem;
  font-weight: 700;
  line-height: 1.35;
}

.required-mark {
  margin-left: 0.15rem;
  color: #8d1d24;
}

input {
  width: 100%;
  min-height: 3rem;
  padding: 0.55rem 0.75rem;
  border: 1px solid #dce2e6;
  border-radius: 0.55rem;
  color: #20252d;
  background: #fff;
  font: inherit;
  transition:
    border-color 150ms ease,
    box-shadow 150ms ease;
}

input:hover {
  border-color: #b9c7cb;
}

input:read-only {
  border-color: #e2e7e9;
  color: #53606b;
  background: #f5f7f7;
  cursor: default;
}

input:read-only:hover {
  border-color: #e2e7e9;
}

input::placeholder {
  color: #9aa4aa;
  opacity: 1;
}

input:focus {
  border-color: #497883;
  outline: none;
  box-shadow: 0 0 0 3px rgb(73 120 131 / 16%);
}

@media (max-width: 720px) {
  label {
    font-size: 1rem;
  }

  input {
    min-height: 2.85rem;
  }
}
</style>
