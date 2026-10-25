import { watch } from 'vue';
import { createI18n } from 'vue-i18n';
import { useLocale } from '../../shared/lib/locale';

export const messages = {
  'en-US': {
    phone: {
      label: 'Phone number',
      country: 'Country calling code',
      search: 'Search country or calling code',
      noCountries: 'No countries found',
      invalid: 'Enter a valid phone number for the selected country.',
      save: 'Save phone number',
      saving: 'Saving phone number…',
      saved: 'Phone number saved.',
      error: 'Couldn’t save your phone number. Please try again.',
    },
    app: { title: 'The Parlor' },
    authentication: { login: 'Login', logout: 'Logout', register: 'Register' },
    locale: {
      label: 'Language',
      saving: 'Saving language…',
      error: 'Language changed on this device. Couldn’t save to your account.',
      retry: 'Retry',
    },
    account: { onboarding: { title: 'Welcome to The Parlor' } },
    onboarding: {
      context: 'Account Setup',
      title: 'Introduce yourself.',
      description:
        'Tell us a little about yourself to finish setting up your account.',
      firstName: 'First name',
      lastName: 'Last name',
      phone: 'Phone number',
      required: 'Required',
      continue: 'Continue',
      welcome: 'Welcome in, {name}.',
      ready: 'Your account is ready.',
      saving: 'Setting up your account…',
      error: 'We couldn’t finish setting up your account. Please try again.',
    },
  },
  'th-TH': {
    phone: {
      label: 'เบอร์โทรศัพท์',
      country: 'รหัสโทรศัพท์ประเทศ',
      search: 'ค้นหาประเทศหรือรหัสโทรศัพท์',
      noCountries: 'ไม่พบประเทศ',
      invalid: 'กรุณากรอกเบอร์โทรศัพท์ที่ถูกต้องสำหรับประเทศที่เลือก',
      save: 'บันทึกเบอร์โทรศัพท์',
      saving: 'กำลังบันทึกเบอร์โทรศัพท์…',
      saved: 'บันทึกเบอร์โทรศัพท์แล้ว',
      error: 'บันทึกเบอร์โทรศัพท์ไม่สำเร็จ กรุณาลองอีกครั้ง',
    },
    app: { title: 'The Parlor' },
    authentication: {
      login: 'เข้าสู่ระบบ',
      logout: 'ออกจากระบบ',
      register: 'สมัครสมาชิก',
    },
    locale: {
      label: 'ภาษา',
      saving: 'กำลังบันทึกภาษา…',
      error: 'เปลี่ยนภาษาบนอุปกรณ์นี้แล้ว แต่ยังบันทึกในบัญชีไม่ได้',
      retry: 'ลองอีกครั้ง',
    },
    account: { onboarding: { title: 'ยินดีต้อนรับสู่ The Parlor' } },
    onboarding: {
      context: 'ตั้งค่าบัญชี',
      title: 'แนะนำตัวคุณ',
      description: 'กรอกข้อมูลเล็กน้อยเพื่อตั้งค่าบัญชีให้เสร็จสมบูรณ์',
      firstName: 'ชื่อ',
      lastName: 'นามสกุล',
      phone: 'เบอร์โทรศัพท์',
      required: 'จำเป็น',
      continue: 'ดำเนินการต่อ',
      welcome: 'ยินดีต้อนรับ คุณ{name}',
      ready: 'บัญชีของคุณพร้อมใช้งานแล้ว',
      saving: 'กำลังตั้งค่าบัญชี…',
      error: 'ตั้งค่าบัญชีไม่สำเร็จ กรุณาลองอีกครั้ง',
    },
  },
} as const;

const { locale } = useLocale();
export const i18n = createI18n({
  legacy: false,
  locale: locale.value,
  fallbackLocale: 'en-US',
  messages,
});
watch(
  locale,
  (value) => {
    i18n.global.locale.value = value;
  },
  { flush: 'sync' },
);
