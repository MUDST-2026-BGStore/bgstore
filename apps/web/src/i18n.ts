import { createI18n } from 'vue-i18n';

export const messages = {
  en: {
    app: {
      title: 'BGStore',
      walkingSkeleton: 'Walking skeleton',
    },
    home: {
      eyebrow: 'Board game store operations',
      description:
        'A production-shaped foundation for reservations, play sessions, tables, and in-store game inventory.',
    },
    status: {
      connecting: 'Connecting to the BGStore API…',
      authenticationRequired: 'Sign in required',
      authenticationHint:
        'Your session is handled securely by the BGStore backend.',
      serviceUnavailable: 'Service unavailable',
      serviceUnavailableHint: 'We could not reach the BGStore service.',
      connected: 'Service {service} · Database {database}',
    },
    actions: {
      signIn: 'Sign in',
    },
    navigation: {
      home: 'Home',
      game: 'Game',
      branch: 'Branch',
    },
    auth: {
      loading: 'Loading your BGStore session…',
      signInTitle: 'Sign in to BGStore',
    },
    onboarding: {
      eyebrow: 'Welcome to BGStore',
      title: 'One last detail',
      description:
        'Add your phone number so we can associate reservations and visits with your account.',
      countryCode: 'Country code',
      phoneNumber: 'Phone number',
      phoneNumberPlaceholder: '081 234 5678',
      phoneHint: 'Include your local number without the country code.',
      error:
        'We could not save that number. Check the country code and phone number and try again.',
      saving: 'Saving…',
      continue: 'Continue',
    },
  },
  th: {
    app: {
      title: 'BGStore',
      walkingSkeleton: 'โครงระบบเริ่มต้น',
    },
    home: {
      eyebrow: 'ระบบจัดการร้านบอร์ดเกม',
      description:
        'รากฐานระบบสำหรับการจองโต๊ะ เซสชันการเล่น โต๊ะ และคลังบอร์ดเกมภายในร้าน',
    },
    status: {
      connecting: 'กำลังเชื่อมต่อ BGStore API…',
      authenticationRequired: 'กรุณาเข้าสู่ระบบ',
      authenticationHint:
        'เซสชันของคุณได้รับการจัดการอย่างปลอดภัยโดยระบบ BGStore',
      serviceUnavailable: 'บริการไม่พร้อมใช้งาน',
      serviceUnavailableHint: 'ไม่สามารถเชื่อมต่อกับบริการ BGStore ได้',
      connected: 'บริการ {service} · ฐานข้อมูล {database}',
    },
    actions: {
      signIn: 'เข้าสู่ระบบ',
    },
    navigation: {
      home: 'หน้าแรก',
      game: 'เกม',
      branch: 'สาขา',
    },
    auth: {
      loading: 'กำลังโหลดเซสชัน BGStore ของคุณ…',
      signInTitle: 'เข้าสู่ระบบ BGStore',
    },
    onboarding: {
      eyebrow: 'ยินดีต้อนรับสู่ BGStore',
      title: 'เหลืออีกหนึ่งขั้นตอน',
      description:
        'เพิ่มหมายเลขโทรศัพท์เพื่อให้เราผูกการจองและการเข้าใช้บริการกับบัญชีของคุณได้',
      countryCode: 'รหัสประเทศ',
      phoneNumber: 'หมายเลขโทรศัพท์',
      phoneNumberPlaceholder: '081 234 5678',
      phoneHint: 'กรอกหมายเลขโทรศัพท์โดยไม่ต้องใส่รหัสประเทศ',
      error:
        'ไม่สามารถบันทึกหมายเลขนี้ได้ โปรดตรวจสอบรหัสประเทศและหมายเลขโทรศัพท์แล้วลองอีกครั้ง',
      saving: 'กำลังบันทึก…',
      continue: 'ดำเนินการต่อ',
    },
  },
} as const;

export const i18n = createI18n({
  legacy: false,
  locale: navigator.language.toLowerCase().startsWith('th') ? 'th' : 'en',
  fallbackLocale: 'en',
  messages,
});
