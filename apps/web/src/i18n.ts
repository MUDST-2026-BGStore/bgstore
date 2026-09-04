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
        'Add your Thai mobile number so we can associate reservations and visits with your account.',
      phone: 'Thai mobile number',
      phonePlaceholder: '081 234 5678',
      error:
        'We could not save that number. Use a valid Thai mobile number and try again.',
      saving: 'Saving…',
      continue: 'Continue',
    },
    branch: {
      management: 'Branch management',
      list: 'Branches',
      add: 'Add branch',
      edit: 'Edit branch',
      delete: 'Delete branch',
      view: 'View',
      cancel: 'Cancel',
      searchPlaceholder: 'Search branch name or code',
      allStatuses: 'All statuses',
      statusActive: 'Active',
      statusInactive: 'Inactive',
      notFoundTitle: 'Branch not found',
      notFoundDesc:
        'The branch you are looking for does not exist or may have been removed.',
      backToBranches: 'Back to branches',
      table: {
        branch: 'Branch',
        address: 'Address',
        contact: 'Contact',
        openingHours: 'Opening hours',
        tables: 'Tables',
        status: 'Status',
      },
      stats: {
        total: 'Total tables',
        available: 'Available',
        reserved: 'Reserved',
        occupied: 'Occupied',
      },
      info: {
        title: 'Branch information',
        code: 'Branch code',
        phone: 'Phone',
        email: 'Email',
        address: 'Address',
        created: 'Created',
      },
      hours: {
        title: 'Opening hours',
        monFri: 'Mon – Fri',
        saturday: 'Saturday',
        sunday: 'Sunday',
      },
      modal: {
        deleteTitle: 'Delete branch',
        deleteWarning:
          'This action cannot be undone. All associated branch details will be permanently removed.',
      },
      empty: {
        title: 'No branches found',
        description: 'Try adjusting your search query or filter criteria.',
      },
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
        'เพิ่มหมายเลขโทรศัพท์มือถือไทยเพื่อให้เราผูกการจองและการเข้าใช้บริการกับบัญชีของคุณได้',
      phone: 'หมายเลขโทรศัพท์มือถือไทย',
      phonePlaceholder: '081 234 5678',
      error:
        'ไม่สามารถบันทึกหมายเลขนี้ได้ โปรดใช้หมายเลขโทรศัพท์มือถือไทยที่ถูกต้องแล้วลองอีกครั้ง',
      saving: 'กำลังบันทึก…',
      continue: 'ดำเนินการต่อ',
    },
    branch: {
      management: 'ระบบจัดการสาขา',
      list: 'สาขา',
      add: 'เพิ่มสาขา',
      edit: 'แก้ไขสาขา',
      delete: 'ลบสาขา',
      view: 'ดูรายละเอียด',
      cancel: 'ยกเลิก',
      searchPlaceholder: 'ค้นหาชื่อหรือรหัสสาขา',
      allStatuses: 'ทุกสถานะ',
      statusActive: 'เปิดใช้งาน',
      statusInactive: 'ปิดใช้งาน',
      notFoundTitle: 'ไม่พบสาขา',
      notFoundDesc: 'ไม่พบสาขาที่คุณค้นหา หรือสาขานี้อาจถูกลบไปแล้ว',
      backToBranches: 'กลับไปหน้ารวมสาขา',
      table: {
        branch: 'สาขา',
        address: 'ที่อยู่',
        contact: 'ติดต่อ',
        openingHours: 'เวลาทำการ',
        tables: 'จำนวนโต๊ะ',
        status: 'สถานะ',
      },
      stats: {
        total: 'โต๊ะทั้งหมด',
        available: 'ว่าง',
        reserved: 'จองแล้ว',
        occupied: 'กำลังใช้งาน',
      },
      info: {
        title: 'ข้อมูลสาขา',
        code: 'รหัสสาขา',
        phone: 'เบอร์โทรศัพท์',
        email: 'อีเมล',
        address: 'ที่อยู่',
        created: 'วันที่สร้าง',
      },
      hours: {
        title: 'เวลาทำการ',
        monFri: 'จันทร์ – ศุกร์',
        saturday: 'เสาร์',
        sunday: 'อาทิตย์',
      },
      modal: {
        deleteTitle: 'ลบสาขา',
        deleteWarning:
          'การดำเนินการนี้ไม่สามารถยกเลิกได้ รายละเอียดสาขาทั้งหมดจะถูกลบอย่างถาวร',
      },
      empty: {
        title: 'ไม่พบข้อมูลสาขา',
        description: 'ลองปรับคำค้นหาหรือตัวกรองใหม่อีกครั้ง',
      },
    },
  },
} as const;

export const i18n = createI18n({
  legacy: false,
  locale: navigator.language.toLowerCase().startsWith('th') ? 'th' : 'en',
  fallbackLocale: 'en',
  messages,
});
