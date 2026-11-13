<script setup lang="ts">
import { RouterLink } from 'vue-router';
import { computed, ref, watch } from 'vue';
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

const fullName = computed(
  () =>
    [profile.value?.firstName, profile.value?.lastName]
      .filter(Boolean)
      .join(' ') ||
    identity.value.displayName ||
    identity.value.username ||
    copy.value.notProvided,
);
const initials = computed(() =>
  fullName.value
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => Array.from(part)[0])
    .join('')
    .toLocaleUpperCase(),
);
const copy = computed(() =>
  locale.value === 'th-TH'
    ? {
        back: 'กลับไปยังพื้นที่ทำงาน',
        account: 'บัญชีของคุณ',
        title: 'โปรไฟล์ของคุณ',
        description: 'จัดการข้อมูลส่วนตัว ข้อมูลติดต่อ และภาษาที่คุณต้องการ',
        loading: 'กำลังโหลดโปรไฟล์…',
        personal: 'ข้อมูลส่วนตัว',
        personalHint: 'ข้อมูลพื้นฐานและอีเมลของคุณ',
        displayName: 'ชื่อที่แสดง',
        email: 'อีเมล',
        verified: 'ยืนยันแล้ว',
        unverified: 'ยังไม่ยืนยัน',
        contact: 'ข้อมูลติดต่อ',
        contactHint: 'ตรวจสอบและอัปเดตเบอร์โทรศัพท์ของคุณ',
        phone: 'เบอร์โทรศัพท์',
        language: 'ภาษาและการแสดงผล',
        languageHint: 'เลือกภาษาที่คุณต้องการใช้ในแอป',
        savedLanguage: 'ภาษาที่บันทึกไว้',
        accountDetails: 'ข้อมูลบัญชี',
        accountHint: 'รหัสอ้างอิงและสิทธิ์การใช้งานของคุณ',
        accountId: 'รหัสบัญชี',
        subject: 'รหัสผู้ใช้ในระบบ',
        roles: 'บทบาท',
        notProvided: 'ไม่ได้ระบุ',
      }
    : {
        back: 'Back to workspace',
        account: 'Your account',
        title: 'Your profile',
        description:
          'Manage your personal details, contact information, and preferences.',
        loading: 'Loading profile…',
        personal: 'Personal information',
        personalHint: 'Your basic details and email address.',
        displayName: 'Display name',
        email: 'Email',
        verified: 'Verified',
        unverified: 'Not verified',
        contact: 'Contact information',
        contactHint: 'Review and update your phone number.',
        phone: 'Phone number',
        language: 'Language & preferences',
        languageHint: 'Choose the language you prefer to use.',
        savedLanguage: 'Saved language',
        accountDetails: 'Account details',
        accountHint: 'Your account references and access roles.',
        accountId: 'Account ID',
        subject: 'Identity subject',
        roles: 'Business roles',
        notProvided: 'Not provided',
      },
);
</script>
<template>
  <main class="profile-page">
    <div class="profile-shell">
      <RouterLink class="back-link" :to="{ name: 'WorkspaceIndex' }">
        <span aria-hidden="true">←</span> {{ copy.back }}
      </RouterLink>

      <header class="page-heading">
        <h1>{{ copy.title }}</h1>
        <p class="page-description">{{ copy.description }}</p>
      </header>

      <p v-if="error" class="notice notice--error" role="alert">
        {{ error.message }}
      </p>
      <p v-else-if="!profile" class="loading-state" role="status">
        {{ copy.loading }}
      </p>

      <div v-if="profile && !error" class="profile-grid">
        <section
          class="profile-card personal-card"
          aria-labelledby="personal-title"
        >
          <div class="card-heading">
            <span class="section-number" aria-hidden="true">01</span>
            <div>
              <h2 id="personal-title">{{ copy.personal }}</h2>
              <p>{{ copy.personalHint }}</p>
            </div>
          </div>
          <div class="person-summary">
            <div class="avatar" aria-hidden="true">{{ initials }}</div>
            <div class="person-summary-text">
              <p class="person-name" data-testid="profile-name">
                {{ fullName }}
              </p>
              <p class="person-username" data-testid="profile-username">
                {{ identity.username || copy.notProvided }}
              </p>
            </div>
          </div>
          <dl class="detail-list">
            <div class="detail-row">
              <dt>{{ copy.displayName }}</dt>
              <dd>{{ identity.displayName || copy.notProvided }}</dd>
            </div>
            <div class="detail-row">
              <dt>{{ copy.email }}</dt>
              <dd>
                <span>{{ identity.email || copy.notProvided }}</span>
                <span
                  v-if="identity.email"
                  class="badge"
                  :class="
                    identity.emailVerified
                      ? 'badge--verified'
                      : 'badge--pending'
                  "
                >
                  {{ identity.emailVerified ? copy.verified : copy.unverified }}
                </span>
              </dd>
            </div>
          </dl>
        </section>

        <section class="profile-card" aria-labelledby="phone-title">
          <div class="card-heading">
            <span class="section-number" aria-hidden="true">02</span>
            <div>
              <h2 id="phone-title">{{ copy.contact }}</h2>
              <p>{{ copy.contactHint }}</p>
            </div>
          </div>
          <form
            class="phone-form"
            :aria-busy="phoneSaving"
            @submit.prevent="savePhone"
          >
            <label class="field-label" for="profile-phone">{{
              copy.phone
            }}</label>
            <PhoneNumberInput
              id="profile-phone"
              v-model="phoneNumber"
              required
              :disabled="phoneSaving"
              @validity="phoneValid = $event"
              @update:model-value="phoneResult = 'idle'"
            />
            <p
              v-if="phoneNumber && !phoneValid"
              class="feedback feedback--error"
              role="alert"
            >
              {{ t('phone.invalid') }}
            </p>
            <div class="form-footer">
              <button
                type="submit"
                class="primary-button"
                :disabled="
                  phoneSaving ||
                  !phoneValid ||
                  phoneNumber === profile.phoneNumber ||
                  saveStatus === 'saving'
                "
              >
                {{ t(phoneSaving ? 'phone.saving' : 'phone.save') }}
              </button>
              <p
                v-if="phoneResult === 'saved'"
                class="feedback feedback--success"
                role="status"
              >
                {{ t('phone.saved') }}
              </p>
              <p
                v-else-if="phoneResult === 'error'"
                class="feedback feedback--error"
                role="alert"
              >
                {{ t('phone.error') }}
              </p>
            </div>
          </form>
        </section>

        <section class="profile-card" aria-labelledby="language-title">
          <div class="card-heading">
            <span class="section-number" aria-hidden="true">03</span>
            <div>
              <h2 id="language-title">{{ copy.language }}</h2>
              <p>{{ copy.languageHint }}</p>
            </div>
          </div>
          <div class="language-field">
            <label class="field-label" for="profile-locale">{{
              t('locale.label')
            }}</label>
            <select
              id="profile-locale"
              :value="locale"
              :disabled="phoneSaving"
              @change="
                setLocale(($event.target as HTMLSelectElement).value as Locale)
              "
            >
              <option value="en-US">English</option>
              <option value="th-TH">ไทย</option>
            </select>
            <p v-if="saveStatus === 'saving'" class="feedback" role="status">
              {{ t('locale.saving') }}
            </p>
            <div
              v-else-if="saveStatus === 'error'"
              class="locale-error"
              role="alert"
            >
              <p class="feedback feedback--error">{{ t('locale.error') }}</p>
              <button
                type="button"
                class="retry-button"
                :disabled="phoneSaving"
                @click="retrySave"
              >
                {{ t('locale.retry') }}
              </button>
            </div>
          </div>
          <dl class="detail-list language-details">
            <div class="detail-row">
              <dt>{{ copy.savedLanguage }}</dt>
              <dd>
                {{
                  profile.preferences.preferredLocale === 'th-TH'
                    ? 'ไทย'
                    : profile.preferences.preferredLocale === 'en-US'
                      ? 'English'
                      : profile.preferences.preferredLocale
                }}
                <span class="locale-code"
                  >({{ profile.preferences.preferredLocale }})</span
                >
              </dd>
            </div>
          </dl>
        </section>

        <section class="profile-card" aria-labelledby="account-title">
          <div class="card-heading">
            <span class="section-number" aria-hidden="true">04</span>
            <div>
              <h2 id="account-title">{{ copy.accountDetails }}</h2>
              <p>{{ copy.accountHint }}</p>
            </div>
          </div>
          <dl class="detail-list account-details">
            <div class="detail-row">
              <dt>{{ copy.accountId }}</dt>
              <dd class="identifier" data-testid="profile-account-id">
                {{ profile.id }}
              </dd>
            </div>
            <div class="detail-row">
              <dt>{{ copy.subject }}</dt>
              <dd class="identifier" data-testid="profile-subject">
                {{ identity.subject || copy.notProvided }}
              </dd>
            </div>
            <div class="detail-row">
              <dt>{{ copy.roles }}</dt>
              <dd class="role-list">
                <span
                  v-for="role in identity.roles"
                  :key="role"
                  class="badge badge--role"
                  >{{ role }}</span
                >
                <span v-if="!identity.roles.length">{{
                  copy.notProvided
                }}</span>
              </dd>
            </div>
          </dl>
        </section>
      </div>
    </div>
  </main>
</template>

<style scoped>
.profile-page {
  --ink: #f3ead8;
  --muted: #c9bea7;
  --line: #c6ad7a40;
  --profile-gold: #c6ad7a;
  --profile-green: #152c21;
  color-scheme: dark;
  min-height: 70vh;
  padding: 32px 24px 48px;
  background: transparent;
  color: var(--ink);
  font-family: inherit;
  line-height: 1.6;
}
.profile-page *,
.profile-page *::before,
.profile-page *::after {
  box-sizing: border-box;
}
.profile-shell {
  max-width: 1080px;
  margin: 0 auto;
}
.back-link {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--muted);
  font-size: 14px;
  text-decoration: none;
}
.back-link:hover {
  color: var(--ink);
  text-decoration: underline;
}
.page-heading {
  margin: 32px 0;
}

h1 {
  margin: 0;
  font-size: clamp(28px, 4vw, 38px);
  font-weight: 650;
  line-height: 1.35;
  letter-spacing: -0.025em;
}
.page-description {
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 15px;
}
.profile-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
  align-items: stretch;
}
.profile-card {
  min-width: 0;
  padding: 28px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: #0e1d16;
  box-shadow: 0 3px 14px rgb(0 0 0 / 12%);
}
.card-heading {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 26px;
}
.section-number {
  display: grid;
  place-items: center;
  flex: 0 0 36px;
  height: 36px;
  border: 1px solid var(--line);
  border-radius: 11px;
  background: var(--profile-green);
  color: var(--profile-gold);
  font-size: 12px;
  font-weight: 700;
}
h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 650;
  line-height: 1.5;
}
.card-heading p {
  margin: 4px 0 0;
  color: var(--muted);
  font-size: 13px;
}
.person-summary {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}
.avatar {
  display: grid;
  place-items: center;
  flex: 0 0 60px;
  height: 60px;
  border-radius: 50%;
  background: var(--profile-green);
  color: var(--profile-gold);
  font-size: 21px;
  font-weight: 650;
}
.person-summary-text {
  min-width: 0;
  overflow-wrap: anywhere;
}
.person-name {
  margin: 0;
  font-size: 20px;
  font-weight: 650;
}
.person-username {
  margin: 2px 0 0;
  color: var(--muted);
  font-size: 13px;
}
.detail-list {
  margin: 0;
}
.detail-row {
  display: grid;
  grid-template-columns: minmax(100px, 0.8fr) minmax(0, 1.4fr);
  gap: 16px;
  align-items: baseline;
  padding: 15px 0;
  border-top: 1px solid var(--line);
  font-size: 14px;
}
.detail-row:last-child {
  padding-bottom: 0;
}
dt {
  color: var(--muted);
}
dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}
.badge {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  max-width: 100%;
  padding: 3px 9px;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  overflow-wrap: anywhere;
}
.badge--verified {
  display: flex;
  margin-top: 7px;
  background: #193528;
  color: #b9d3ad;
}
.badge--pending {
  display: flex;
  margin-top: 7px;
  background: #c6ad7a1a;
  color: #ddc18c;
}
.badge--role {
  white-space: normal;
  word-break: break-word;
  background: var(--profile-green);
  color: #ddc18c;
}
.role-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.field-label {
  display: block;
  margin-bottom: 9px;
  font-size: 14px;
  font-weight: 600;
}
.phone-form,
.language-field {
  min-width: 0;
}
.phone-form :deep(.number-input),
select {
  min-height: 48px;
  border: 1px solid #c6ad7a80;
  border-radius: 9px;
  background: #0e1d16;
  color: var(--ink);
  font: inherit;
  font-size: 15px;
}
.phone-form :deep(.phone-control) {
  height: 48px;
  padding: 0 12px 0 4px;
  border: 1px solid #c6ad7a80;
  border-radius: 9px;
  background: #0e1d16;
}
.phone-form :deep(.phone-control:focus-within) {
  outline: 2px solid var(--profile-gold);
  outline-offset: 2px;
}
.phone-form :deep(.number-input) {
  min-width: 0;
  max-width: 100%;
  min-height: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
}
.phone-form :deep(.number-input:focus-visible) {
  outline: none;
}
.phone-form :deep(.country-popup),
.phone-form :deep(.country-trigger.is-open) {
  color-scheme: light;
  color: #14241c;
}
.phone-form :deep(.country-trigger) {
  color: var(--ink);
}
select {
  width: 100%;
  padding: 10px 14px;
}
.form-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 22px;
}
.primary-button {
  min-height: 44px;
  padding: 11px 20px;
  border: 1px solid var(--profile-gold);
  border-radius: 9px;
  background: var(--profile-gold);
  color: #152c21;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.primary-button:hover:not(:disabled) {
  background: #ddc18c;
}
button:disabled,
select:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.profile-page :is(button, select, a):focus-visible,
.phone-form :deep(.country-trigger:focus-visible) {
  outline: 3px solid var(--profile-gold);
  outline-offset: 3px;
}
.feedback {
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 13px;
}
.form-footer .feedback {
  margin: 0;
}
.feedback--success {
  color: #b9d3ad;
}
.feedback--error {
  color: #f0aaa4;
}
.locale-error {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 10px;
}
.retry-button {
  padding: 6px 0;
  border: 0;
  background: transparent;
  color: var(--ink);
  font: inherit;
  font-size: 13px;
  text-decoration: underline;
  cursor: pointer;
}
.locale-code {
  margin-left: 6px;
  color: var(--muted);
  font-size: 12px;
}
.language-details {
  margin-top: 22px;
}
.identifier {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  line-height: 1.8;
}
.notice,
.loading-state {
  padding: 24px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #0e1d16;
}
.notice--error {
  border-color: #f0aaa466;
  background: #3b2020;
  color: #f0aaa4;
}
@media (max-width: 720px) {
  .profile-page {
    padding: 24px 16px 40px;
  }
  .page-heading {
    margin: 26px 0;
  }
  .profile-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }
  .profile-card {
    padding: 22px;
    border-radius: 14px;
  }
}
@media (max-width: 480px) {
  .detail-row {
    grid-template-columns: minmax(0, 1fr);
    gap: 5px;
  }
  .primary-button {
    width: 100%;
  }
}
</style>
