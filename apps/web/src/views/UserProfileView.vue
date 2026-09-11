<script setup lang="ts">
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query';
import { computed, reactive, ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import BaseFormField from '../components/form/BaseFormField.vue';
import ProfileAvatar from '../components/profile/ProfileAvatar.vue';
import { completeClientProfile } from '../generated/api/sdk.gen';
import { currentUserQueryOptions } from '../queries/current-user';

const { t } = useI18n();
const queryClient = useQueryClient();
const currentUser = useQuery(currentUserQueryOptions());
const isEditing = ref(false);
const saveSucceeded = ref(false);
const accountApiRequired = ref(false);

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  phone: '',
  firstName: '',
  lastName: '',
  email: '',
});

const saved = reactive({
  username: '',
  phone: '',
  firstName: '',
  lastName: '',
  email: '',
});

const phoneChanged = computed(() => form.phone.trim() !== saved.phone);
const keycloakProfileChanged = computed(
  () =>
    form.username.trim() !== saved.username ||
    form.firstName.trim() !== saved.firstName ||
    form.lastName.trim() !== saved.lastName ||
    form.email.trim() !== saved.email,
);
const passwordChanged = computed(
  () => form.password.length > 0 || form.confirmPassword.length > 0,
);
const passwordMismatch = computed(
  () => passwordChanged.value && form.password !== form.confirmPassword,
);
const pendingApiChanges = computed(
  () => keycloakProfileChanged.value || passwordChanged.value,
);
const hasChanges = computed(
  () => phoneChanged.value || pendingApiChanges.value,
);

const restoreSavedValues = () => {
  form.username = saved.username;
  form.phone = saved.phone;
  form.firstName = saved.firstName;
  form.lastName = saved.lastName;
  form.email = saved.email;
  form.password = '';
  form.confirmPassword = '';
};

watch(
  () => currentUser.data.value,
  (user) => {
    if (!user) {
      return;
    }

    saved.username = user.username;
    saved.firstName = user.firstName;
    saved.lastName = user.lastName;
    saved.email = user.email;
    saved.phone = user.clientProfile?.phone ?? '';
    restoreSavedValues();
  },
  { immediate: true },
);

const updatePhone = useMutation({
  mutationFn: async () => {
    const { data } = await completeClientProfile({
      body: { countryCode: '+66', phoneNumber: form.phone },
      throwOnError: true,
    });
    return data;
  },
  onMutate: () => {
    saveSucceeded.value = false;
  },
  onSuccess: async (profile) => {
    saved.phone = profile.phone ?? form.phone;
    form.phone = saved.phone;
    saveSucceeded.value = true;
    isEditing.value = false;
    await queryClient.invalidateQueries({
      queryKey: ['current-user'],
      refetchType: 'none',
    });
  },
});

const startEditing = () => {
  saveSucceeded.value = false;
  accountApiRequired.value = false;
  restoreSavedValues();
  isEditing.value = true;
};

const cancelEditing = () => {
  restoreSavedValues();
  saveSucceeded.value = false;
  accountApiRequired.value = false;
  isEditing.value = false;
};

const saveProfile = () => {
  saveSucceeded.value = false;
  accountApiRequired.value = false;

  if (passwordMismatch.value) {
    return;
  }

  if (pendingApiChanges.value) {
    accountApiRequired.value = true;
    return;
  }

  if (phoneChanged.value && !updatePhone.isPending.value) {
    updatePhone.mutate();
    return;
  }

  isEditing.value = false;
};
</script>

<template>
  <section class="user-profile-page" aria-labelledby="user-profile-title">
    <h1 id="user-profile-title" class="visually-hidden">
      {{ t('userProfile.title') }}
    </h1>

    <article class="user-profile-card">
      <header class="profile-summary">
        <div class="profile-summary-inner">
          <ProfileAvatar
            class="user-profile-avatar"
            :label="t('userProfile.profilePhoto')"
            :change-label="t('userProfile.changePhoto')"
          />
        </div>
      </header>

      <form
        class="user-profile-form"
        :class="{ 'user-profile-form--editing': isEditing }"
        @submit.prevent="saveProfile"
      >
        <BaseFormField
          id="profile-username"
          v-model="form.username"
          class="field-username"
          name="username"
          autocomplete="username"
          :label="t('userProfile.username')"
          :readonly="!isEditing"
          :required="isEditing"
          :required-mark="isEditing"
        />
        <BaseFormField
          id="profile-email"
          v-model="form.email"
          class="field-email"
          name="email"
          type="email"
          inputmode="email"
          autocomplete="email"
          :label="t('userProfile.email')"
          :readonly="!isEditing"
          :required="isEditing"
          :required-mark="isEditing"
        />
        <BaseFormField
          id="profile-first-name"
          v-model="form.firstName"
          class="field-first-name"
          name="firstName"
          autocomplete="given-name"
          :label="t('userProfile.firstName')"
          :readonly="!isEditing"
          :required="isEditing"
          :required-mark="isEditing"
        />
        <BaseFormField
          id="profile-last-name"
          v-model="form.lastName"
          class="field-last-name"
          name="lastName"
          autocomplete="family-name"
          :label="t('userProfile.lastName')"
          :readonly="!isEditing"
          :required="isEditing"
          :required-mark="isEditing"
        />
        <BaseFormField
          id="profile-phone"
          v-model="form.phone"
          class="field-phone"
          name="phone"
          type="tel"
          inputmode="tel"
          autocomplete="tel"
          :label="t('userProfile.phone')"
          :readonly="!isEditing"
          :required="isEditing"
          :required-mark="isEditing"
        />
        <BaseFormField
          id="profile-password"
          v-model="form.password"
          class="field-password"
          name="newPassword"
          type="password"
          autocomplete="new-password"
          :label="t('userProfile.password')"
          :placeholder="
            isEditing
              ? t('userProfile.newPasswordPlaceholder')
              : t('userProfile.passwordHidden')
          "
          :readonly="!isEditing"
        />
        <BaseFormField
          v-if="isEditing"
          id="profile-confirm-password"
          v-model="form.confirmPassword"
          class="field-confirm-password"
          name="confirmPassword"
          type="password"
          autocomplete="new-password"
          :label="t('userProfile.confirmPassword')"
          :placeholder="t('userProfile.confirmPasswordPlaceholder')"
          :readonly="!isEditing"
        />

        <div class="form-feedback" aria-live="polite">
          <p v-if="passwordMismatch" class="status-error" role="alert">
            {{ t('userProfile.passwordMismatch') }}
          </p>
          <p v-else-if="accountApiRequired" class="status-pending" role="alert">
            {{ t('userProfile.accountApiRequired') }}
          </p>
          <p
            v-else-if="updatePhone.isError.value"
            class="status-error"
            role="alert"
          >
            {{ t('userProfile.saveError') }}
          </p>
          <p v-else-if="saveSucceeded" class="status-success">
            {{ t('userProfile.saveSuccess') }}
          </p>
        </div>

        <div class="form-actions">
          <button
            v-if="!isEditing"
            class="primary-button"
            type="button"
            @click="startEditing"
          >
            {{ t('userProfile.editProfile') }}
          </button>
          <template v-else>
            <button
              class="secondary-button"
              type="button"
              :disabled="updatePhone.isPending.value"
              @click="cancelEditing"
            >
              {{ t('userProfile.cancelChanges') }}
            </button>
            <button
              class="primary-button"
              type="submit"
              :disabled="
                !hasChanges || passwordMismatch || updatePhone.isPending.value
              "
            >
              {{
                updatePhone.isPending.value
                  ? t('userProfile.saving')
                  : t('userProfile.confirm')
              }}
            </button>
          </template>
        </div>
      </form>
    </article>
  </section>
</template>

<style scoped>
.user-profile-page {
  --profile-green: #497883;
  --profile-green-strong: #315b65;
  --profile-muted: #647080;
  --profile-border: #dce2e6;
  min-height: 100vh;
  min-height: 100dvh;
  color: #20252d;
  background: #fff;
}

.user-profile-card {
  background: #fff;
}

.profile-summary {
  height: 13rem;
  background: var(--profile-green);
}

.profile-summary-inner {
  position: relative;
  width: min(calc(100% - 4rem), 62rem);
  height: 100%;
  margin: 0 auto;
}

.user-profile-avatar {
  position: absolute;
  bottom: -5rem;
  left: 0;
  width: 10rem;
  height: 10rem;
  transform: translateX(-50%);
}

.user-profile-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-areas:
    'username first-name'
    'password last-name'
    'phone email'
    'feedback actions';
  gap: 1.15rem 1.5rem;
  width: min(calc(100% - 4rem), 62rem);
  margin: 0 auto;
  padding: 7rem 0 3rem;
}

.user-profile-form--editing {
  grid-template-areas:
    'username first-name'
    'password last-name'
    'confirm-password email'
    'phone empty'
    'feedback actions';
}

.user-profile-form :deep(.form-field) {
  gap: 0.35rem;
}

.user-profile-form :deep(label) {
  font-size: 0.82rem;
}

.user-profile-form :deep(input) {
  min-height: 2.65rem;
  padding: 0.5rem 0.7rem;
  font-size: 0.86rem;
}

.field-username {
  grid-area: username;
}

.field-first-name {
  grid-area: first-name;
}

.field-password {
  grid-area: password;
}

.field-last-name {
  grid-area: last-name;
}

.field-confirm-password {
  grid-area: confirm-password;
}

.field-email {
  grid-area: email;
}

.field-phone {
  grid-area: phone;
}

.form-feedback {
  grid-area: feedback;
  min-height: 1.35rem;
  color: #68747b;
  font-size: 0.78rem;
  line-height: 1.45;
}

.form-feedback p {
  margin: 0;
}

.status-error {
  color: #a3343b;
}

.status-pending {
  color: #775c1d;
}

.status-success {
  color: var(--profile-green);
  font-weight: 700;
}

.form-actions {
  display: flex;
  grid-area: actions;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 0.75rem;
}

button {
  min-width: 8rem;
  min-height: 2.5rem;
  padding: 0.55rem 1rem;
  border-radius: 0.6rem;
  font: inherit;
  font-size: 0.86rem;
  font-weight: 700;
  cursor: pointer;
  transition:
    background 150ms ease,
    box-shadow 150ms ease,
    transform 150ms ease;
}

.primary-button {
  border: 1px solid var(--profile-green);
  color: #fff;
  background: var(--profile-green);
}

.secondary-button {
  border: 1px solid var(--profile-green);
  color: var(--profile-green);
  background: #fff;
}

.primary-button:hover:not(:disabled),
.secondary-button:hover:not(:disabled) {
  box-shadow: 0 0.5rem 1rem rgb(73 120 131 / 18%);
}

.primary-button:hover:not(:disabled) {
  background: var(--profile-green-strong);
}

.secondary-button:hover:not(:disabled) {
  color: #fff;
  background: var(--profile-green);
}

button:active:not(:disabled) {
  transform: translateY(1px);
}

button:focus-visible {
  outline: 3px solid #20252d;
  outline-offset: 3px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}

@media (max-width: 1150px) {
  .user-profile-avatar {
    left: 0;
    transform: none;
  }
}

@media (max-width: 720px) {
  .profile-summary {
    height: 9rem;
  }

  .profile-summary-inner {
    width: min(calc(100% - 2rem), 34rem);
  }

  .user-profile-avatar {
    bottom: -3.5rem;
    left: 50%;
    width: 7rem;
    height: 7rem;
    transform: translateX(-50%);
  }

  .user-profile-form {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas:
      'username'
      'first-name'
      'last-name'
      'email'
      'phone'
      'password'
      'feedback'
      'actions';
    gap: 1rem;
    width: min(calc(100% - 2rem), 34rem);
    padding: 5.5rem 0 2rem;
  }

  .user-profile-form--editing {
    grid-template-areas:
      'username'
      'first-name'
      'last-name'
      'email'
      'phone'
      'password'
      'confirm-password'
      'feedback'
      'actions';
  }

  .form-actions {
    flex-direction: column-reverse;
  }

  button {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  button {
    transition: none;
  }
}
</style>
