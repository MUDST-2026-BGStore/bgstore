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
      connected: 'Service {service} · Database {database}',
    },
    actions: {
      signIn: 'Sign in',
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
      connected: 'บริการ {service} · ฐานข้อมูล {database}',
    },
    actions: {
      signIn: 'เข้าสู่ระบบ',
    },
  },
} as const;

export const i18n = createI18n({
  legacy: false,
  locale: navigator.language.toLowerCase().startsWith('th') ? 'th' : 'en',
  fallbackLocale: 'en',
  messages,
});
