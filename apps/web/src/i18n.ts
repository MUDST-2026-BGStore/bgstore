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
    userProfile: {
      title: 'User profile',
      profilePhoto: 'Profile photo placeholder',
      changePhoto: 'Change photo',
      username: 'Username',
      password: 'Password',
      confirmPassword: 'Confirm Password',
      firstName: 'First name',
      lastName: 'Last name',
      email: 'Email',
      phone: 'Phone',
      passwordHidden: 'Password is not displayed',
      newPasswordPlaceholder: 'New password (optional)',
      confirmPasswordPlaceholder: 'Enter the new password again',
      passwordMismatch: 'The password confirmation does not match.',
      editProfile: 'Edit profile',
      cancelChanges: 'Cancel',
      confirm: 'Confirm',
      accountApiRequired:
        'This change is ready in the UI, but it needs the account/profile-image API before it can be saved.',
      saving: 'Saving…',
      saveSuccess: 'Your phone number has been updated.',
      saveError:
        'We could not update your profile. Check the phone number and try again.',
    },
    auth: {
      loading: 'Loading your BGStore session…',
      signInTitle: 'Sign in to BGStore',
    },
    onboarding: {
      eyebrow: 'Welcome to BGStore',
      title: 'One last detail',
      description:
        'Add your Thai mobile number so we can associate reservations and visits with your account.',
      phone: 'Thai mobile number',
      phonePlaceholder: '081 234 5678',
      error:
        'We could not save that number. Use a valid Thai mobile number and try again.',
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
    userProfile: {
      title: 'โปรไฟล์ผู้ใช้',
      profilePhoto: 'รูปโปรไฟล์เริ่มต้น',
      changePhoto: 'เปลี่ยนรูป',
      username: 'ชื่อผู้ใช้',
      password: 'รหัสผ่าน',
      confirmPassword: 'ยืนยันรหัสผ่าน',
      firstName: 'ชื่อ',
      lastName: 'นามสกุล',
      email: 'อีเมล',
      phone: 'เบอร์โทรศัพท์',
      passwordHidden: 'ระบบจะไม่แสดงรหัสผ่าน',
      newPasswordPlaceholder: 'รหัสผ่านใหม่ (ไม่บังคับ)',
      confirmPasswordPlaceholder: 'กรอกรหัสผ่านใหม่อีกครั้ง',
      passwordMismatch: 'การยืนยันรหัสผ่านไม่ตรงกัน',
      editProfile: 'แก้ไขโปรไฟล์',
      cancelChanges: 'ยกเลิก',
      confirm: 'ยืนยัน',
      accountApiRequired:
        'หน้าจอรองรับการแก้ไขแล้ว แต่ต้องมี Account/Profile image API ก่อนจึงจะบันทึกข้อมูลนี้ได้',
      saving: 'กำลังบันทึก…',
      saveSuccess: 'อัปเดตเบอร์โทรศัพท์เรียบร้อยแล้ว',
      saveError:
        'ไม่สามารถอัปเดตโปรไฟล์ได้ โปรดตรวจสอบเบอร์โทรศัพท์แล้วลองอีกครั้ง',
    },
    auth: {
      loading: 'กำลังโหลดเซสชัน BGStore ของคุณ…',
      signInTitle: 'เข้าสู่ระบบ BGStore',
    },
    onboarding: {
      eyebrow: 'ยินดีต้อนรับสู่ BGStore',
      title: 'เหลืออีกหนึ่งขั้นตอน',
      description:
        'เพิ่มหมายเลขโทรศัพท์มือถือไทยเพื่อให้เราผูกการจองและการเข้าใช้บริการกับบัญชีของคุณได้',
      phone: 'หมายเลขโทรศัพท์มือถือไทย',
      phonePlaceholder: '081 234 5678',
      error:
        'ไม่สามารถบันทึกหมายเลขนี้ได้ โปรดใช้หมายเลขโทรศัพท์มือถือไทยที่ถูกต้องแล้วลองอีกครั้ง',
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
