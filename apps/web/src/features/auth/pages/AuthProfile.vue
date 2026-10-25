<script setup lang="ts">
import { RouterLink } from 'vue-router';
import { ref, watch } from 'vue';
import { useQueryClient } from '@tanstack/vue-query';
import { getMeQueryKey, updateMe, useMe } from '@theparlor/api-client';
import { useAuth } from '@theparlor/auth';
import { updateAccountProfile } from '@theparlor/account';
import { PhoneNumberInput } from '@theparlor/ui';
import { useI18n } from 'vue-i18n';
import { useLocale, type Locale } from '../../../shared/lib/locale';

const { data: profile, error } = useMe();
const identity = useAuth();
const { locale, setLocale, saveStatus, retrySave } = useLocale();
const { t } = useI18n({ useScope: 'global' });
const client = useQueryClient();
const phoneNumber = ref('');
const phoneValid = ref(false);
const phoneSaving = ref(false);
const phoneResult = ref<'idle' | 'saved' | 'error'>('idle');
watch(
  () => profile.value?.id,
  () => {
    phoneNumber.value = profile.value?.phoneNumber ?? '';
    phoneResult.value = 'idle';
  },
  { immediate: true },
);

async function savePhone() {
  if (
    !phoneValid.value ||
    phoneSaving.value ||
    saveStatus.value === 'saving' ||
    !profile.value ||
    !identity.value.authenticated
  )
    return;
  const subject = identity.value.subject;
  const accountId = profile.value.id;
  const stillCurrent = () =>
    identity.value.authenticated &&
    identity.value.subject === subject &&
    profile.value?.id === accountId;
  phoneSaving.value = true;
  phoneResult.value = 'idle';
  try {
    const updated = await updateMe({ phoneNumber: phoneNumber.value });
    if (!stillCurrent()) return;
    await client.cancelQueries({ queryKey: getMeQueryKey() });
    if (!stillCurrent()) return;
    updateAccountProfile(updated);
    client.setQueryData(getMeQueryKey(), updated);
    phoneNumber.value = updated.phoneNumber;
    phoneResult.value = 'saved';
  } catch {
    if (stillCurrent()) phoneResult.value = 'error';
  } finally {
    phoneSaving.value = false;
  }
}
</script>
<template>
  <main>
    <h1>Your profile</h1>
    <label>
      {{ t('locale.label') }}
      <select
        :value="locale"
        :disabled="phoneSaving"
        @change="
          setLocale(($event.target as HTMLSelectElement).value as Locale)
        "
      >
        <option value="en-US">English</option>
        <option value="th-TH">ไทย</option>
      </select>
    </label>
    <p v-if="saveStatus === 'saving'" role="status">{{ t('locale.saving') }}</p>
    <p v-else-if="saveStatus === 'error'" role="alert">
      {{ t('locale.error') }}
      <button type="button" :disabled="phoneSaving" @click="retrySave">
        {{ t('locale.retry') }}
      </button>
    </p>
    <p v-if="error" role="alert">{{ error.message }}</p>
    <dl v-else-if="profile">
      <dt>Account ID</dt>
      <dd data-testid="profile-account-id">{{ profile.id }}</dd>
      <dt>Name</dt>
      <dd data-testid="profile-name">
        {{ [profile.firstName, profile.lastName].filter(Boolean).join(' ') }}
      </dd>
      <dt>Phone number</dt>
      <dd>
        <form class="max-w-lg space-y-2" @submit.prevent="savePhone">
          <PhoneNumberInput
            id="profile-phone"
            v-model="phoneNumber"
            required
            :disabled="phoneSaving"
            @validity="phoneValid = $event"
            @update:model-value="phoneResult = 'idle'"
          />
          <p v-if="phoneNumber && !phoneValid" role="alert">
            {{ t('phone.invalid') }}
          </p>
          <button
            type="submit"
            class="rounded bg-[#152c21] px-4 py-2 text-sm text-[#f3ead8] disabled:opacity-50"
            :disabled="
              phoneSaving ||
              !phoneValid ||
              phoneNumber === profile.phoneNumber ||
              saveStatus === 'saving'
            "
          >
            {{ t(phoneSaving ? 'phone.saving' : 'phone.save') }}
          </button>
          <p v-if="phoneResult === 'saved'" role="status">
            {{ t('phone.saved') }}
          </p>
          <p v-else-if="phoneResult === 'error'" role="alert">
            {{ t('phone.error') }}
          </p>
        </form>
      </dd>
      <dt>Preferred locale</dt>
      <dd>{{ profile.preferences.preferredLocale }}</dd>
      <dt>Identity subject</dt>
      <dd data-testid="profile-subject">{{ identity.subject }}</dd>
      <dt>Username</dt>
      <dd data-testid="profile-username">{{ identity.username }}</dd>
      <dt>Display name</dt>
      <dd>{{ identity.displayName || 'Not provided' }}</dd>
      <dt>Email</dt>
      <dd>{{ identity.email || 'Not provided' }}</dd>
      <dt>Email verified</dt>
      <dd>{{ identity.emailVerified ? 'Yes' : 'No' }}</dd>
      <dt>Business roles</dt>
      <dd>{{ identity.roles.join(', ') }}</dd>
    </dl>
    <p v-else>Loading profile…</p>
    <RouterLink :to="{ name: 'WorkspaceIndex' }">Back to workspace</RouterLink>
  </main>
</template>
