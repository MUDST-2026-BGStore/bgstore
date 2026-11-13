<script setup lang="ts">
import { computed } from 'vue';
import { RouterLink } from 'vue-router';
import { useAuthStore } from '../../../shared/stores/auth';
import { useHello, useWhoami } from '@theparlor/api-client';
import { BButton, BCard } from '@theparlor/ui';

import { useLocale } from '../../../shared/lib/locale';
import { canOperateBranches } from '../../../shared/lib/roles';

const auth = useAuthStore();
const canManage = computed(
  () => auth.authenticated && canOperateBranches(auth.roles),
);
const { locale } = useLocale();

const {
  data: apiStatus,
  error: healthError,
  isPending,
  refetch: refreshHealth,
} = useHello();
const {
  data: identity,
  error: identityError,
  refetch: refreshIdentity,
} = useWhoami();
const apiError = computed(() => healthError.value ?? identityError.value);

function checkApiHealth() {
  void refreshHealth();
  void refreshIdentity();
}

const copy = computed(() =>
  locale.value === 'th-TH'
    ? {
        title: 'พื้นที่ทำงาน The Parlor',
        description: 'ข้อมูลบัญชีและสถานะการเชื่อมต่อระบบ',
        accountHint: 'ชื่อผู้ใช้และบทบาทในระบบ',
        apiHint: 'ตรวจสอบบริการและบัญชีที่เชื่อมต่อ',
        home: 'หน้าหลัก',
        homeHint: 'กลับไปเลือกดูเกมและข้อมูลร้าน',
        profile: 'โปรไฟล์ของคุณ',
        profileHint: 'จัดการข้อมูลติดต่อและภาษาที่ใช้',
        signOut: 'ออกจากระบบ',
        account: 'ข้อมูลบัญชี',
        username: 'ชื่อผู้ใช้',
        roles: 'บทบาท',
        authenticating: 'กำลังตรวจสอบบัญชี…',
        noRoles: 'ไม่ได้ระบุ',
        api: 'สถานะ API',
        service: 'บริการ',
        status: 'สถานะ',
        apiIdentity: 'ผู้ใช้ที่เชื่อมต่อ API',
        checking: 'กำลังตรวจสอบ…',
        refresh: 'ตรวจสอบอีกครั้ง',
        workspace: 'พื้นที่ทำงาน',
        signedIn: 'ผู้ใช้ที่เข้าสู่ระบบ',
        realmRoles: 'บทบาทในระบบ',
      }
    : {
        title: 'The Parlor workspace',
        description: 'Your account details and service connection status.',
        accountHint: 'Your username and access roles.',
        apiHint: 'Check the service and connected account.',
        home: 'Public home',
        homeHint: 'Browse games and store information.',
        profile: 'Your profile',
        profileHint: 'Manage your contact details and language.',
        signOut: 'Sign Out',
        account: 'User Info',
        username: 'Username',
        roles: 'Roles',
        authenticating: 'Authenticating…',
        noRoles: 'Not provided',
        api: 'API Status',
        service: 'Service',
        status: 'Status',
        apiIdentity: 'API identity',
        checking: 'Checking…',
        refresh: 'Refresh',
        workspace: 'Workspace',
        signedIn: 'Signed-in user',
        realmRoles: 'Realm roles',
      },
);
</script>

<template>
  <section
    class="workspace-theme workspace-page"
    aria-labelledby="workspace-title"
  >
    <div class="workspace-shell">
      <header class="workspace-heading">
        <h1 id="workspace-title">{{ copy.title }}</h1>
        <p class="page-description">{{ copy.description }}</p>
      </header>
      <nav class="workspace-navigation" :aria-label="copy.workspace">
        <RouterLink class="navigation-card" :to="{ name: 'HomeIndex' }">
          <div>
            <span class="navigation-title">{{ copy.home }}</span>
            <span class="navigation-description">{{ copy.homeHint }}</span>
          </div>
          <span class="navigation-arrow" aria-hidden="true">→</span>
        </RouterLink>
        <RouterLink class="navigation-card" :to="{ name: 'AuthProfile' }">
          <div>
            <span class="navigation-title">{{ copy.profile }}</span>
            <span class="navigation-description">{{ copy.profileHint }}</span>
          </div>
          <span class="navigation-arrow" aria-hidden="true">→</span>
        </RouterLink>
        <RouterLink
          v-if="canManage"
          class="navigation-card"
          :to="{ name: 'WorkspaceManagement' }"
        >
          <span class="navigation-title">{{
            locale === 'th-TH'
              ? 'จัดการสาขาและการจอง'
              : 'Branch Management & Reservations'
          }}</span>
          <span class="navigation-arrow" aria-hidden="true">→</span>
        </RouterLink>
      </nav>

      <div class="workspace-grid">
        <section :aria-label="copy.account">
          <BCard class="workspace-card">
            <div class="card-heading">
              <span class="section-number" aria-hidden="true">01</span>
              <div>
                <h2>{{ copy.account }}</h2>
                <p>{{ copy.accountHint }}</p>
              </div>
            </div>
            <dl v-if="auth.authenticated" class="detail-list">
              <div class="detail-row">
                <dt>{{ copy.username }}</dt>
                <dd>{{ auth.username }}</dd>
              </div>
              <div class="detail-row">
                <dt>{{ copy.roles }}</dt>
                <dd>
                  <ul
                    v-if="auth.roles.length"
                    class="role-list"
                    :aria-label="copy.realmRoles"
                  >
                    <li
                      v-for="role in auth.roles"
                      :key="role"
                      class="role-badge"
                    >
                      {{ role }}
                    </li>
                  </ul>
                  <span v-else class="muted">{{ copy.noRoles }}</span>
                </dd>
              </div>
            </dl>
            <p v-else class="muted" role="status">
              {{ copy.authenticating }}
            </p>
          </BCard>
        </section>

        <section :aria-label="copy.api">
          <BCard class="workspace-card">
            <div class="card-heading">
              <span class="section-number" aria-hidden="true">02</span>
              <div>
                <h2>{{ copy.api }}</h2>
                <p>{{ copy.apiHint }}</p>
              </div>
            </div>
            <dl class="detail-list">
              <template v-if="apiStatus">
                <div class="detail-row">
                  <dt>{{ copy.service }}</dt>
                  <dd data-testid="api-service">{{ apiStatus.service }}</dd>
                </div>
                <div class="detail-row">
                  <dt>{{ copy.status }}</dt>
                  <dd class="service-status">{{ apiStatus.status }}</dd>
                </div>
              </template>
              <div v-if="identity" class="detail-row">
                <dt>{{ copy.apiIdentity }}</dt>
                <dd data-testid="api-identity">{{ identity.username }}</dd>
              </div>
            </dl>
            <p v-if="apiError" class="error-message" role="alert">
              {{ apiError.message }}
            </p>
            <p v-if="isPending" class="muted" role="status">
              {{ copy.checking }}
            </p>
            <div class="card-actions">
              <BButton
                type="button"
                variant="secondary"
                @click="checkApiHealth()"
                >{{ copy.refresh }}</BButton
              >
            </div>
          </BCard>
        </section>
      </div>
      <footer v-if="auth.authenticated" class="workspace-footer">
        <BButton type="button" variant="secondary" @click="auth.logout()">{{
          copy.signOut
        }}</BButton>
      </footer>
    </div>
  </section>
</template>

<style scoped>
.workspace-theme {
  --color-primary: #c6ad7a;
  --color-primary-hover: #ddc18c;
  --color-surface: #0e1d16;
  --color-surface-hover: #193528;
  --color-text: #f3ead8;
  --color-border: #c6ad7a40;
  --workspace-muted: #c9bea7;
  color-scheme: dark;

  line-height: 1.6;
}
.workspace-page {
  padding: 32px 24px 48px;
  color: var(--color-text);
  line-height: 1.6;
}
.workspace-page *,
.workspace-page *::before,
.workspace-page *::after {
  box-sizing: border-box;
}
.workspace-shell {
  max-width: 1080px;
  margin: 0 auto;
}
.workspace-heading {
  margin: 32px 0;
}
h1 {
  margin: 0;
  font-size: clamp(28px, 4vw, 38px);
  font-weight: 650;
  line-height: 1.35;
  letter-spacing: -0.025em;
}
.workspace-navigation,
.workspace-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
}
.workspace-navigation {
  margin-bottom: 24px;
}
.navigation-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  min-width: 0;
  padding: 24px 28px;
  border: 1px solid var(--color-border);
  border-radius: 18px;
  background: var(--color-surface);
  color: var(--color-text);
  text-decoration: none;
  transition:
    background-color 150ms ease,
    border-color 150ms ease;
}
.navigation-card:hover {
  background: var(--color-surface-hover);
  border-color: #c6ad7a80;
}
.navigation-card > div {
  min-width: 0;
  overflow-wrap: anywhere;
}
.navigation-title {
  display: block;
  font-size: 17px;
  font-weight: 650;
}
.navigation-description {
  display: block;
  margin-top: 5px;
  color: var(--workspace-muted);
  font-size: 14px;
}
.navigation-arrow {
  flex-shrink: 0;
  color: var(--color-primary);
  font-size: 22px;
}
.workspace-grid > section {
  min-width: 0;
}
.workspace-card {
  height: 100%;
  border-radius: 18px;
  box-shadow: 0 3px 14px rgb(0 0 0 / 12%);
}

.workspace-card :deep(.b-card__body) {
  padding: 28px;
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
  border-top: 1px solid var(--color-border);
  font-size: 14px;
}
.detail-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}
dt,
.muted {
  color: var(--workspace-muted);
}
dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}
.role-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.role-badge {
  max-width: 100%;
  padding: 3px 9px;
  border: 1px solid var(--color-border);
  border-radius: 6px;
  background: #152c21;
  color: #ddc18c;
  font-size: 12px;
  overflow-wrap: anywhere;
}
.service-status {
  font-weight: 600;
}
.card-actions {
  display: flex;
  margin-top: 22px;
}
.error-message {
  margin: 18px 0 0;
  padding: 12px 14px;
  border: 1px solid #f0aaa466;
  border-radius: 9px;
  background: #3b2020;
  color: #f0aaa4;
  font-size: 14px;
  overflow-wrap: anywhere;
}
.workspace-theme :deep(.b-button) {
  min-height: 44px;
  font-family: inherit;
}
.workspace-theme :deep(.b-button:focus-visible),
.navigation-card:focus-visible {
  outline: 3px solid var(--color-primary);
  outline-offset: 3px;
}
@media (max-width: 720px) {
  .workspace-page {
    padding: 24px 16px 40px;
  }
  .workspace-heading {
    margin: 32px 0;
  }
  .workspace-navigation,
  .workspace-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }
  .workspace-navigation {
    margin-bottom: 16px;
  }
  .navigation-card {
    padding: 22px;
    border-radius: 14px;
  }
  .workspace-card {
    border-radius: 14px;
  }
  .workspace-card :deep(.b-card__header) {
    padding: 20px 22px;
  }
  .workspace-card :deep(.b-card__body) {
    padding: 22px;
  }
}
@media (max-width: 480px) {
  .detail-row {
    grid-template-columns: minmax(0, 1fr);
    gap: 8px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .navigation-card,
  .workspace-theme :deep(.b-button) {
    transition: none;
  }
}
.page-description {
  margin: 10px 0 0;
  color: var(--workspace-muted);
  font-size: 15px;
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
  border: 1px solid var(--color-border);
  border-radius: 11px;
  background: #152c21;
  color: #c6ad7a;
  font-size: 12px;
  font-weight: 700;
}
.card-heading h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 650;
  line-height: 1.5;
}
.card-heading p {
  margin: 4px 0 0;
  color: var(--workspace-muted);
  font-size: 13px;
}
.workspace-theme :deep(.b-button) {
  padding: 11px 20px;
  border-radius: 9px;
  font-size: 14px;
}
.card-actions :deep(.b-button) {
  border: 1px solid #c6ad7a;
  background: #c6ad7a;
  color: #152c21;
}
.card-actions :deep(.b-button:hover:not(:disabled)) {
  background: #ddc18c;
}
.workspace-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 16px;
  margin-top: 32px;
  padding-top: 24px;
  border-top: 1px solid var(--color-border);
}
</style>
