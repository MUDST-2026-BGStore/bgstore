<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import QRCode from 'qrcode';
import { useCartStore } from '../stores/cart';
import { useOrdersStore } from '../stores/orders';
import { formatPrice } from '../data/catalog';
import { useStoreText } from '../data/store-text';
import { useAuthStore } from '../../../shared/stores/auth';
import CartFulfillment from '../components/CartFulfillment.vue';

const router = useRouter();
const cart = useCartStore();
const ordersStore = useOrdersStore();
const auth = useAuthStore();
const { locale } = useStoreText();

// Redirect back to cart if cart is empty
onMounted(() => {
  if (cart.lines.length === 0) {
    router.replace('/cart');
  }
});

// Pre-fill contact details from logged-in member
const contact = reactive({
  email: '',
  name: '',
  phone: '',
  address: '',
  extra: '',
  city: '',
  region: '',
  postal: '',
  country: 'Thailand',
  note: '',
});

watch(
  () => auth.authenticated,
  (isAuth) => {
    if (isAuth) {
      if (auth.displayName && !contact.name) contact.name = auth.displayName;
      if (auth.username && !contact.name) contact.name = auth.username;
      if (auth.email && !contact.email) contact.email = auth.email;
    }
  },
  { immediate: true },
);

// Payment Method Selector
export type PaymentMethod = 'promptpay' | 'card' | 'bank_transfer' | 'cod';
const selectedPaymentMethod = ref<PaymentMethod>('promptpay');

// Credit Card Form
const cardForm = reactive({
  number: '',
  expiry: '',
  cvv: '',
  holder: '',
});

// Order totals
const subtotal = computed(() => cart.subtotal);
const shippingFee = computed(() =>
  cart.method === 'shipping' ? cart.shippingFee : 0,
);
const totalPayable = computed(() => subtotal.value + shippingFee.value);

// Validation Errors & Formatting Helpers
const errors = reactive({
  name: '',
  phone: '',
  email: '',
  address: '',
  city: '',
  region: '',
  postal: '',
  cardNumber: '',
  cardExpiry: '',
  cardCvv: '',
  cardHolder: '',
});

function formatThaiPhone(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, 10);
  if (digits.length <= 3) return digits;
  if (digits.length <= 6) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
  return `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`;
}

function onPhoneInput(e: Event) {
  const input = e.target as HTMLInputElement;
  contact.phone = formatThaiPhone(input.value);
  validateField('phone');
}

function onPostalInput(e: Event) {
  const input = e.target as HTMLInputElement;
  contact.postal = input.value.replace(/\D/g, '').slice(0, 5);
  validateField('postal');
}

function formatCardNumber(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, 16);
  return digits.replace(/(.{4})/g, '$1 ').trim();
}

function onCardNumberInput(e: Event) {
  const input = e.target as HTMLInputElement;
  cardForm.number = formatCardNumber(input.value);
  validateField('cardNumber');
}

function onCardExpiryInput(e: Event) {
  const input = e.target as HTMLInputElement;
  let digits = input.value.replace(/\D/g, '').slice(0, 4);
  if (digits.length >= 3) {
    digits = `${digits.slice(0, 2)}/${digits.slice(2)}`;
  }
  cardForm.expiry = digits;
  validateField('cardExpiry');
}

function onCardCvvInput(e: Event) {
  const input = e.target as HTMLInputElement;
  cardForm.cvv = input.value.replace(/\D/g, '').slice(0, 4);
  validateField('cardCvv');
}

function validateField(field: keyof typeof errors) {
  const isTh = locale.value === 'th-TH';

  if (field === 'name') {
    const val = contact.name.trim();
    if (!val) {
      errors.name = isTh
        ? 'กรุณากรอกชื่อและนามสกุลผู้รับ'
        : 'Please enter recipient name';
    } else if (!/^[A-Za-zก-๙\s.-]+$/.test(val)) {
      errors.name = isTh
        ? 'กรุณากรอกเป็นตัวอักษรภาษาไทยหรือภาษาอังกฤษเท่านั้น'
        : 'Please enter letters only';
    } else if (val.length < 3) {
      errors.name = isTh
        ? 'กรุณากรอกชื่อและนามสกุลอย่างน้อย 3 ตัวอักษร'
        : 'Name must be at least 3 characters';
    } else {
      errors.name = '';
    }
  }

  if (field === 'phone') {
    const raw = contact.phone.replace(/\D/g, '');
    if (!raw) {
      errors.phone = isTh
        ? 'กรุณากรอกเบอร์โทรศัพท์มือถือ'
        : 'Please enter phone number';
    } else if (!/^0[689]\d{8}$/.test(raw) && !/^0[23457]\d{7,8}$/.test(raw)) {
      errors.phone = isTh
        ? 'กรุณากรอกเบอร์โทรศัพท์ให้ถูกต้อง (เช่น 081-234-5678)'
        : 'Invalid phone number (e.g. 081-234-5678)';
    } else {
      errors.phone = '';
    }
  }

  if (field === 'email') {
    const val = contact.email.trim();
    if (val && !/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(val)) {
      errors.email = isTh
        ? 'กรุณากรอกอีเมลให้ถูกต้อง (เช่น name@example.com)'
        : 'Invalid email format (e.g. name@example.com)';
    } else {
      errors.email = '';
    }
  }

  if (field === 'address') {
    const val = contact.address.trim();
    if (!val) {
      errors.address = isTh
        ? 'กรุณากรอกที่อยู่จัดส่ง (บ้านเลขที่ / ซอย / ถนน)'
        : 'Please enter street address';
    } else if (val.length < 5) {
      errors.address = isTh
        ? 'กรุณากรอกที่อยู่จัดส่งให้ครบถ้วน'
        : 'Please enter complete street address';
    } else {
      errors.address = '';
    }
  }

  if (field === 'city') {
    const val = contact.city.trim();
    if (!val) {
      errors.city = isTh
        ? 'กรุณากรอกตำบลหรือแขวง'
        : 'Please enter sub-district';
    } else {
      errors.city = '';
    }
  }

  if (field === 'region') {
    const val = contact.region.trim();
    if (!val) {
      errors.region = isTh
        ? 'กรุณากรอกอำเภอ/เขต และจังหวัด'
        : 'Please enter district and province';
    } else {
      errors.region = '';
    }
  }

  if (field === 'postal') {
    const raw = contact.postal.replace(/\D/g, '');
    if (!raw) {
      errors.postal = isTh
        ? 'กรุณากรอกรหัสไปรษณีย์ 5 หลัก'
        : 'Please enter postal code';
    } else if (raw.length !== 5) {
      errors.postal = isTh
        ? 'กรุณากรอกรหัสไปรษณีย์ตัวเลข 5 หลักให้ครบถ้วน'
        : 'Postal code must be 5 digits';
    } else {
      errors.postal = '';
    }
  }

  if (field === 'cardNumber' && selectedPaymentMethod.value === 'card') {
    const raw = cardForm.number.replace(/\s/g, '');
    if (!raw) {
      errors.cardNumber = isTh
        ? 'กรุณากรอกหมายเลขบัตร 16 หลัก'
        : 'Please enter 16-digit card number';
    } else if (raw.length < 16) {
      errors.cardNumber = isTh
        ? 'กรุณากรอกหมายเลขบัตรให้ครบ 16 หลัก'
        : 'Card number must be 16 digits';
    } else {
      errors.cardNumber = '';
    }
  }

  if (field === 'cardExpiry' && selectedPaymentMethod.value === 'card') {
    const val = cardForm.expiry.trim();
    if (!val) {
      errors.cardExpiry = isTh
        ? 'กรุณากรอกวันหมดอายุ (MM/YY)'
        : 'Please enter expiry date (MM/YY)';
    } else if (!/^\d{2}\/\d{2}$/.test(val)) {
      errors.cardExpiry = isTh
        ? 'รูปแบบวันหมดอายุต้องเป็นเดือน/ปี เช่น 12/28'
        : 'Format must be MM/YY (e.g. 12/28)';
    } else {
      errors.cardExpiry = '';
    }
  }

  if (field === 'cardCvv' && selectedPaymentMethod.value === 'card') {
    const val = cardForm.cvv.trim();
    if (!val) {
      errors.cardCvv = isTh
        ? 'กรุณากรอกรหัส CVV ด้านหลังบัตร'
        : 'Please enter CVV code';
    } else if (val.length < 3) {
      errors.cardCvv = isTh
        ? 'รหัส CVV ต้องเป็นตัวเลข 3 หรือ 4 หลัก'
        : 'CVV must be 3 or 4 digits';
    } else {
      errors.cardCvv = '';
    }
  }

  if (field === 'cardHolder' && selectedPaymentMethod.value === 'card') {
    const val = cardForm.holder.trim();
    if (!val) {
      errors.cardHolder = isTh
        ? 'กรุณากรอกชื่อผู้ถือบัตร'
        : 'Please enter cardholder name';
    } else if (!/^[A-Za-z\s.-]+$/.test(val)) {
      errors.cardHolder = isTh
        ? 'กรุณากรอกชื่อผู้ถือบัตรเป็นตัวอักษรภาษาอังกฤษเท่านั้น'
        : 'Cardholder name must be in English';
    } else {
      errors.cardHolder = '';
    }
  }
}

function validateAllFields(): boolean {
  if (cart.method === 'shipping') {
    validateField('name');
    validateField('phone');
    validateField('email');
    validateField('address');
    validateField('city');
    validateField('region');
    validateField('postal');

    if (
      errors.name ||
      errors.phone ||
      errors.email ||
      errors.address ||
      errors.city ||
      errors.region ||
      errors.postal
    ) {
      return false;
    }
  }

  if (selectedPaymentMethod.value === 'card') {
    validateField('cardNumber');
    validateField('cardExpiry');
    validateField('cardCvv');
    validateField('cardHolder');
    if (
      errors.cardNumber ||
      errors.cardExpiry ||
      errors.cardCvv ||
      errors.cardHolder
    ) {
      return false;
    }
  }

  return true;
}

// Modal & Processing State
const showPaymentModal = ref(false);
const qrDataUrl = ref<string>('');
const qrReference = ref<string>('');
const isProcessingPayment = ref(false);
const secondsLeft = ref(15 * 60);
let timer: ReturnType<typeof setInterval> | null = null;
const formError = ref('');
const placedOrder = ref<any>(null);
const copiedTracking = ref(false);

const formattedTimer = computed(() => {
  const m = Math.floor(secondsLeft.value / 60);
  const s = secondsLeft.value % 60;
  return `${m}:${s < 10 ? '0' : ''}${s}`;
});

async function handleConfirmOrder() {
  formError.value = '';
  if (!validateAllFields()) return;

  if (cart.lines.length === 0) {
    formError.value =
      locale.value === 'th-TH'
        ? 'ไม่มีรายการสินค้าในคำสั่งซื้อ'
        : 'Your cart is empty.';
    router.replace('/cart');
    return;
  }

  if (cart.method === 'shipping') {
    if (!contact.name || !contact.phone || !contact.address || !contact.city) {
      formError.value =
        locale.value === 'th-TH'
          ? 'กรุณากรอกข้อมูลผู้รับและที่อยู่จัดส่งให้ครบถ้วน'
          : 'Please complete your recipient and delivery address information.';
      const sec = document.querySelector('.delivery-section-box');
      sec?.scrollIntoView({ behavior: 'smooth' });
      return;
    }
  }

  if (selectedPaymentMethod.value === 'card') {
    if (!cardForm.number || !cardForm.expiry || !cardForm.cvv) {
      formError.value =
        locale.value === 'th-TH'
          ? 'กรุณากรอกข้อมูลบัตรเครดิต/เดบิตให้ครบถ้วน'
          : 'Please complete all credit/debit card fields.';
      const sec = document.querySelector('.payment-methods-card');
      sec?.scrollIntoView({ behavior: 'smooth' });
      return;
    }
  }

  // Generate Reference
  const randRef = `PP-ORD-${Math.floor(100000 + Math.random() * 900000)}`;
  qrReference.value = randRef;
  secondsLeft.value = 15 * 60;

  if (selectedPaymentMethod.value === 'promptpay') {
    const qrPayload = JSON.stringify({
      service: 'PromptPay-QR',
      merchant: 'The Parlor Board Game Store',
      ref: randRef,
      amount: totalPayable.value.toFixed(2),
      currency: 'THB',
      created: new Date().toISOString(),
    });

    try {
      qrDataUrl.value = await QRCode.toDataURL(qrPayload, {
        width: 230,
        margin: 1,
        color: { dark: '#142a1f', light: '#ffffff' },
        errorCorrectionLevel: 'M',
      });
    } catch (err) {
      console.error('Failed to generate PromptPay QR:', err);
    }
  }

  showPaymentModal.value = true;

  if (timer) clearInterval(timer);
  if (selectedPaymentMethod.value === 'promptpay') {
    timer = setInterval(() => {
      if (secondsLeft.value > 0) {
        secondsLeft.value--;
      } else {
        if (timer) clearInterval(timer);
      }
    }, 1000);
  }
}

function confirmPayment() {
  isProcessingPayment.value = true;
  if (timer) clearInterval(timer);

  setTimeout(() => {
    isProcessingPayment.value = false;

    // Convert items to OrderItems
    const orderItems = cart.lines.map((line) => ({
      variantId: line.variantId,
      gameId: line.game.id,
      gameName: line.game.name,
      edition:
        line.variant.language === 'th' ? 'ฉบับภาษาไทย' : 'ฉบับภาษาอังกฤษ',
      quantity: line.quantity,
      unitPrice: line.variant.price,
      totalPrice: line.total,
      imageUrl: `/images/games/${line.game.id}.jpg`,
    }));

    const methodLabels: Record<PaymentMethod, string> = {
      promptpay: 'พร้อมเพย์ QR',
      card: 'บัตรเครดิต/เดบิต',
      bank_transfer: 'โอนผ่านบัญชีธนาคาร',
      cod: 'เก็บเงินปลายทาง (COD)',
    };

    const newOrder = ordersStore.createOrder({
      items: orderItems,
      itemSubtotal: subtotal.value,
      fulfillmentMethod: cart.method,
      pickupBranchName: cart.fulfillmentBranch?.name,
      carrier: cart.carrier,
      shippingFee: shippingFee.value,
      shippingAddress: { ...contact },
      paymentReference: `${qrReference.value} (${methodLabels[selectedPaymentMethod.value]})`,
    });

    placedOrder.value = newOrder;

    // Clear cart after checkout
    cart.clearCart();
  }, 700);
}

function copyTracking(trackingNo: string) {
  void navigator.clipboard.writeText(trackingNo);
  copiedTracking.value = true;
  setTimeout(() => {
    copiedTracking.value = false;
  }, 2000);
}

function closeSuccessAndExit() {
  showPaymentModal.value = false;
  placedOrder.value = null;
  router.push('/');
}

function viewOrdersAndExit() {
  showPaymentModal.value = false;
  ordersStore.openOrdersModal(placedOrder.value ?? undefined);
}

onUnmounted(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <main class="shopee-checkout-page">
    <div class="checkout-container">
      <!-- Breadcrumb Navigation -->
      <div class="checkout-top-bar">
        <nav class="checkout-breadcrumb" aria-label="Breadcrumb">
          <RouterLink to="/" class="bc-link">
            <svg
              width="15"
              height="15"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
              <polyline points="9 22 9 12 15 12 15 22" />
            </svg>
            <span>{{ locale === 'th-TH' ? 'หน้าหลัก' : 'Home' }}</span>
          </RouterLink>
          <span class="bc-sep">/</span>
          <RouterLink to="/cart" class="bc-link">
            <span>{{ locale === 'th-TH' ? 'ตะกร้าสินค้า' : 'Cart' }}</span>
          </RouterLink>
          <span class="bc-sep">/</span>
          <span class="bc-curr">{{
            locale === 'th-TH' ? 'สั่งซื้อสินค้า' : 'Checkout'
          }}</span>
        </nav>
      </div>

      <div class="page-title-row">
        <h1 class="page-title">
          <span>{{
            locale === 'th-TH'
              ? 'ยืนยันการสั่งซื้อสินค้า'
              : 'Checkout & Payment'
          }}</span>
        </h1>
        <RouterLink to="/cart" class="btn-back-to-cart">
          <svg
            width="15"
            height="15"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <polyline points="15 18 9 12 15 6" />
          </svg>
          <span>{{
            locale === 'th-TH' ? 'กลับไปที่ตะกร้า' : 'Back to Cart'
          }}</span>
        </RouterLink>
      </div>

      <!-- Error Alert -->
      <div v-if="formError" class="checkout-form-alert" role="alert">
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          aria-hidden="true"
        >
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="8" x2="12" y2="12" />
          <line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
        <span>{{ formError }}</span>
      </div>

      <!-- 1. ORDERED ITEMS SUMMARY CARD -->
      <div class="checkout-card items-summary-card">
        <div class="card-head-row">
          <div class="head-title-wrap">
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z" />
              <line x1="3" y1="6" x2="21" y2="6" />
              <path d="M16 10a4 4 0 0 1-8 0" />
            </svg>
            <h2>
              {{
                locale === 'th-TH' ? 'รายการสินค้าที่สั่งซื้อ' : 'Ordered Items'
              }}
            </h2>
          </div>
          <span class="items-total-badge"
            >{{ cart.count }} {{ locale === 'th-TH' ? 'ชิ้น' : 'items' }}</span
          >
        </div>

        <div class="items-summary-table">
          <div
            v-for="line in cart.lines"
            :key="line.variantId"
            class="summary-row"
          >
            <img
              :src="`/images/games/${line.game.id}.jpg`"
              :alt="line.game.name"
              class="summary-thumb"
              width="64"
              height="64"
            />
            <div class="summary-details">
              <strong class="summary-name">{{ line.game.name }}</strong>
              <span class="summary-edition">
                {{
                  line.variant.language === 'th'
                    ? 'ฉบับภาษาไทย'
                    : 'ฉบับภาษาอังกฤษ'
                }}
              </span>
              <span class="summary-qty-price">
                {{ formatPrice(line.variant.price) }} × {{ line.quantity }}
              </span>
            </div>
            <div class="summary-total">
              {{ formatPrice(line.total) }}
            </div>
          </div>
        </div>
      </div>

      <!-- 2. DELIVERY & CARRIER DETAILS CARD -->
      <div class="checkout-card delivery-section-box">
        <div class="card-head-row">
          <div class="head-title-wrap">
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <rect x="1" y="3" width="15" height="13" />
              <polygon points="16 8 20 8 23 11 23 16 16 16 16 8" />
              <circle cx="5.5" cy="18.5" r="2.5" />
              <circle cx="18.5" cy="18.5" r="2.5" />
            </svg>
            <h2>
              {{
                locale === 'th-TH'
                  ? 'ข้อมูลการจัดส่งและผู้รับ'
                  : 'Delivery & Recipient Information'
              }}
            </h2>
          </div>
        </div>

        <CartFulfillment />

        <!-- Address Form if Shipping -->
        <div v-if="cart.method === 'shipping'" class="shipping-fields-card">
          <h4 class="form-subhead">
            {{
              locale === 'th-TH'
                ? 'ที่อยู่สำหรับจัดส่งพัสดุ'
                : 'Recipient Address'
            }}
          </h4>
          <div class="form-grid-3">
            <div class="field-wrap" :class="{ 'has-error': !!errors.name }">
              <label>{{
                locale === 'th-TH' ? 'ชื่อ-นามสกุล ผู้รับ *' : 'Full Name *'
              }}</label>
              <input
                v-model="contact.name"
                type="text"
                :placeholder="
                  locale === 'th-TH' ? 'เช่น สมชาย ใจดี' : 'e.g. Somchai Jaidee'
                "
                required
                @input="validateField('name')"
                @blur="validateField('name')"
              />
              <span v-if="errors.name" class="field-error-msg">{{
                errors.name
              }}</span>
            </div>
            <div class="field-wrap" :class="{ 'has-error': !!errors.phone }">
              <label>{{
                locale === 'th-TH' ? 'เบอร์โทรศัพท์ติดต่อ *' : 'Phone Number *'
              }}</label>
              <input
                :value="contact.phone"
                type="tel"
                placeholder="08X-XXX-XXXX"
                maxlength="12"
                required
                @input="onPhoneInput"
                @blur="validateField('phone')"
              />
              <span v-if="errors.phone" class="field-error-msg">{{
                errors.phone
              }}</span>
            </div>
            <div class="field-wrap" :class="{ 'has-error': !!errors.email }">
              <label>{{
                locale === 'th-TH' ? 'อีเมลสำหรับรับใบเสร็จ' : 'Email Address'
              }}</label>
              <input
                v-model="contact.email"
                type="email"
                :placeholder="
                  locale === 'th-TH'
                    ? 'เช่น somchai@example.com'
                    : 'e.g. somchai@example.com'
                "
                @input="validateField('email')"
                @blur="validateField('email')"
              />
              <span v-if="errors.email" class="field-error-msg">{{
                errors.email
              }}</span>
            </div>
          </div>

          <div class="form-grid-2">
            <div class="field-wrap" :class="{ 'has-error': !!errors.address }">
              <label>{{
                locale === 'th-TH'
                  ? 'ที่อยู่จัดส่ง (บ้านเลขที่ / ซอย / ถนน) *'
                  : 'Street Address *'
              }}</label>
              <input
                v-model="contact.address"
                type="text"
                :placeholder="
                  locale === 'th-TH'
                    ? 'เช่น 128/4 ซอยสุขุมวิท 21'
                    : 'e.g. 128/4 Sukhumvit 21'
                "
                required
                @input="validateField('address')"
                @blur="validateField('address')"
              />
              <span v-if="errors.address" class="field-error-msg">{{
                errors.address
              }}</span>
            </div>
            <div class="field-wrap">
              <label>{{
                locale === 'th-TH'
                  ? 'อาคาร / ชั้น / ห้อง (ถ้ามี)'
                  : 'Building / Unit (Optional)'
              }}</label>
              <input
                v-model="contact.extra"
                type="text"
                :placeholder="
                  locale === 'th-TH'
                    ? 'เช่น อาคาร B ห้อง 402'
                    : 'e.g. Building B, Room 402'
                "
              />
            </div>
          </div>

          <div class="form-grid-3">
            <div class="field-wrap" :class="{ 'has-error': !!errors.city }">
              <label>{{
                locale === 'th-TH' ? 'ตำบล / แขวง *' : 'Sub-district *'
              }}</label>
              <input
                v-model="contact.city"
                type="text"
                :placeholder="
                  locale === 'th-TH'
                    ? 'เช่น คลองเตยเหนือ'
                    : 'e.g. Khlong Toei Nuea'
                "
                required
                @input="validateField('city')"
                @blur="validateField('city')"
              />
              <span v-if="errors.city" class="field-error-msg">{{
                errors.city
              }}</span>
            </div>
            <div class="field-wrap" :class="{ 'has-error': !!errors.region }">
              <label>{{
                locale === 'th-TH'
                  ? 'อำเภอ / เขต, จังหวัด *'
                  : 'District, Province *'
              }}</label>
              <input
                v-model="contact.region"
                type="text"
                :placeholder="
                  locale === 'th-TH'
                    ? 'เช่น วัฒนา, กรุงเทพฯ'
                    : 'e.g. Watthana, Bangkok'
                "
                required
                @input="validateField('region')"
                @blur="validateField('region')"
              />
              <span v-if="errors.region" class="field-error-msg">{{
                errors.region
              }}</span>
            </div>
            <div class="field-wrap" :class="{ 'has-error': !!errors.postal }">
              <label>{{
                locale === 'th-TH' ? 'รหัสไปรษณีย์ *' : 'Postal Code *'
              }}</label>
              <input
                :value="contact.postal"
                type="text"
                :placeholder="locale === 'th-TH' ? 'เช่น 10110' : 'e.g. 10110'"
                maxlength="5"
                required
                @input="onPostalInput"
                @blur="validateField('postal')"
              />
              <span v-if="errors.postal" class="field-error-msg">{{
                errors.postal
              }}</span>
            </div>
          </div>

          <div class="field-wrap full">
            <label>{{
              locale === 'th-TH'
                ? 'หมายเหตุเพิ่มเติมถึงเจ้าหน้าที่จัดส่ง (ถ้ามี)'
                : 'Delivery Notes (Optional)'
            }}</label>
            <input
              v-model="contact.note"
              type="text"
              :placeholder="
                locale === 'th-TH'
                  ? 'เช่น ฝากไว้กับ รปภ. นิติบุคคลคอนโด'
                  : 'e.g. Please leave with security/condo reception'
              "
            />
          </div>
        </div>
      </div>

      <!-- 3. PAYMENT METHODS SELECTION -->
      <div class="checkout-card payment-methods-card">
        <div class="card-head-row">
          <div class="head-title-wrap">
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
            >
              <rect x="2" y="5" width="20" height="14" rx="2" />
              <line x1="2" y1="10" x2="22" y2="10" />
            </svg>
            <h2>
              {{
                locale === 'th-TH'
                  ? 'เลือกช่องทางการชำระเงิน'
                  : 'Select Payment Method'
              }}
            </h2>
          </div>
        </div>

        <div class="methods-selection-list">
          <!-- 1. PromptPay -->
          <label
            class="method-option-row"
            :class="{ 'is-active': selectedPaymentMethod === 'promptpay' }"
          >
            <input
              type="radio"
              v-model="selectedPaymentMethod"
              value="promptpay"
              class="sr-only"
            />
            <div class="method-radio">
              <span class="radio-dot"></span>
            </div>
            <div class="method-info">
              <div class="method-name-line">
                <strong>{{
                  locale === 'th-TH'
                    ? 'พร้อมเพย์ (PromptPay QR)'
                    : 'PromptPay QR'
                }}</strong>
                <span class="tag-rec">{{
                  locale === 'th-TH' ? 'แนะนำ' : 'Recommended'
                }}</span>
              </div>
              <p class="method-desc">
                {{
                  locale === 'th-TH'
                    ? 'สแกน QR Code ผ่านแอป Mobile Banking ทุกธนาคาร สะดวก รวดเร็ว ไม่มีค่าธรรมเนียม'
                    : 'Scan QR Code with any Thai mobile banking app. Instant & zero fees.'
                }}
              </p>
            </div>
          </label>

          <!-- 2. Credit/Debit Card -->
          <label
            class="method-option-row"
            :class="{ 'is-active': selectedPaymentMethod === 'card' }"
          >
            <input
              type="radio"
              v-model="selectedPaymentMethod"
              value="card"
              class="sr-only"
            />
            <div class="method-radio">
              <span class="radio-dot"></span>
            </div>
            <div class="method-info">
              <div class="method-name-line">
                <strong>{{
                  locale === 'th-TH'
                    ? 'บัตรเครดิต / บัตรเดบิต'
                    : 'Credit / Debit Card'
                }}</strong>
                <span class="card-brands">Visa · Mastercard · JCB</span>
              </div>
              <p class="method-desc">
                {{
                  locale === 'th-TH'
                    ? 'ชำระปลอดภัยด้วยระบบมาตรฐานสากล'
                    : 'Secure payment via Visa, Mastercard, or JCB'
                }}
              </p>

              <div
                v-if="selectedPaymentMethod === 'card'"
                class="card-inline-form"
                @click.stop
              >
                <div
                  class="field-wrap"
                  :class="{ 'has-error': !!errors.cardNumber }"
                >
                  <label>{{
                    locale === 'th-TH'
                      ? 'หมายเลขบัตร 16 หลัก *'
                      : 'Card Number *'
                  }}</label>
                  <input
                    :value="cardForm.number"
                    type="text"
                    placeholder="xxxx xxxx xxxx xxxx"
                    maxlength="19"
                    @input="onCardNumberInput"
                    @blur="validateField('cardNumber')"
                  />
                  <span v-if="errors.cardNumber" class="field-error-msg">{{
                    errors.cardNumber
                  }}</span>
                </div>
                <div class="form-grid-2">
                  <div
                    class="field-wrap"
                    :class="{ 'has-error': !!errors.cardExpiry }"
                  >
                    <label>{{
                      locale === 'th-TH' ? 'วันหมดอายุ (MM/YY) *' : 'Expiry *'
                    }}</label>
                    <input
                      :value="cardForm.expiry"
                      type="text"
                      placeholder="MM/YY"
                      maxlength="5"
                      @input="onCardExpiryInput"
                      @blur="validateField('cardExpiry')"
                    />
                    <span v-if="errors.cardExpiry" class="field-error-msg">{{
                      errors.cardExpiry
                    }}</span>
                  </div>
                  <div
                    class="field-wrap"
                    :class="{ 'has-error': !!errors.cardCvv }"
                  >
                    <label>{{
                      locale === 'th-TH' ? 'รหัส CVV *' : 'CVV *'
                    }}</label>
                    <input
                      :value="cardForm.cvv"
                      type="password"
                      placeholder="•••"
                      maxlength="4"
                      @input="onCardCvvInput"
                      @blur="validateField('cardCvv')"
                    />
                    <span v-if="errors.cardCvv" class="field-error-msg">{{
                      errors.cardCvv
                    }}</span>
                  </div>
                </div>
                <div
                  class="field-wrap"
                  :class="{ 'has-error': !!errors.cardHolder }"
                >
                  <label>{{
                    locale === 'th-TH'
                      ? 'ชื่อผู้ถือบัตร *'
                      : 'Cardholder Name *'
                  }}</label>
                  <input
                    v-model="cardForm.holder"
                    type="text"
                    placeholder="NAME SURNAME"
                    @input="validateField('cardHolder')"
                    @blur="validateField('cardHolder')"
                  />
                  <span v-if="errors.cardHolder" class="field-error-msg">{{
                    errors.cardHolder
                  }}</span>
                </div>
              </div>
            </div>
          </label>

          <!-- 3. Bank Transfer -->
          <label
            class="method-option-row"
            :class="{ 'is-active': selectedPaymentMethod === 'bank_transfer' }"
          >
            <input
              type="radio"
              v-model="selectedPaymentMethod"
              value="bank_transfer"
              class="sr-only"
            />
            <div class="method-radio">
              <span class="radio-dot"></span>
            </div>
            <div class="method-info">
              <div class="method-name-line">
                <strong>{{
                  locale === 'th-TH'
                    ? 'โอนเงินผ่านบัญชีธนาคาร'
                    : 'Bank Transfer'
                }}</strong>
              </div>
              <p class="method-desc">
                {{
                  locale === 'th-TH'
                    ? 'โอนเข้าบัญชีธนาคารกสิกรไทย (KBANK) หรือ ไทยพาณิชย์ (SCB)'
                    : 'Direct transfer to our KBANK or SCB corporate bank account'
                }}
              </p>

              <div
                v-if="selectedPaymentMethod === 'bank_transfer'"
                class="bank-accounts-box"
                @click.stop
              >
                <div class="bank-item">
                  <div class="bank-code kbank">KBANK</div>
                  <div class="bank-text">
                    <strong>ธนาคารกสิกรไทย</strong>
                    <p>
                      {{
                        locale === 'th-TH'
                          ? 'ชื่อบัญชี: บจก. เดอะ พาร์เลอร์'
                          : 'Account: The Parlor Co., Ltd.'
                      }}
                    </p>
                  </div>
                </div>
                <div class="bank-item">
                  <div class="bank-code scb">SCB</div>
                  <div class="bank-text">
                    <strong>ธนาคารไทยพาณิชย์</strong>
                    <p>
                      {{
                        locale === 'th-TH'
                          ? 'ชื่อบัญชี: บจก. เดอะ พาร์เลอร์'
                          : 'Account: The Parlor Co., Ltd.'
                      }}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </label>

          <!-- 4. Cash on Delivery (COD) -->
          <label
            class="method-option-row"
            :class="{ 'is-active': selectedPaymentMethod === 'cod' }"
          >
            <input
              type="radio"
              v-model="selectedPaymentMethod"
              value="cod"
              class="sr-only"
            />
            <div class="method-radio">
              <span class="radio-dot"></span>
            </div>
            <div class="method-info">
              <div class="method-name-line">
                <strong>{{
                  locale === 'th-TH'
                    ? 'ชำระเงินปลายทาง (Cash on Delivery)'
                    : 'Cash on Delivery (COD)'
                }}</strong>
              </div>
              <p class="method-desc">
                {{
                  locale === 'th-TH'
                    ? 'ชำระเงินสดกับเจ้าหน้าที่จัดส่งพัสดุเมื่อได้รับสินค้าถึงบ้าน'
                    : 'Pay cash directly to the courier when receiving the parcel.'
                }}
              </p>
            </div>
          </label>
        </div>
      </div>

      <!-- 4. ORDER SUMMARY & SUBMIT SECTION -->
      <div class="checkout-card order-final-summary">
        <div class="summary-breakdown-table">
          <div class="summary-line">
            <span class="lbl">{{
              locale === 'th-TH' ? 'ยอดรวมสินค้า' : 'Merchandise Subtotal'
            }}</span>
            <span class="val">{{ formatPrice(subtotal) }}</span>
          </div>
          <div class="summary-line">
            <span class="lbl">
              {{ locale === 'th-TH' ? 'ค่าจัดส่งพัสดุ' : 'Shipping Total' }}
              <span v-if="cart.method === 'shipping'" class="carrier-tag">
                ({{ cart.carrierInfo.name }})
              </span>
            </span>
            <span class="val">{{ formatPrice(shippingFee) }}</span>
          </div>
          <div class="summary-line line-grand-total">
            <span class="lbl">{{
              locale === 'th-TH' ? 'ยอดชำระทั้งสิ้น' : 'Total Payment'
            }}</span>
            <strong class="val-grand">{{ formatPrice(totalPayable) }}</strong>
          </div>
        </div>

        <div class="checkout-action-row">
          <button
            type="button"
            class="btn-place-order"
            @click="handleConfirmOrder"
          >
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2.2"
            >
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
            </svg>
            <span>{{
              locale === 'th-TH'
                ? 'ยืนยันการสั่งซื้อและชำระเงิน'
                : 'Place Order & Pay'
            }}</span>
          </button>
        </div>
      </div>
    </div>

    <!-- PAYMENT & ORDER CONFIRMATION MODAL (CENTERED POPUP) -->
    <div
      v-if="showPaymentModal"
      class="checkout-modal-backdrop"
      @click.self="!placedOrder && (showPaymentModal = false)"
    >
      <div class="checkout-modal-card" role="dialog" aria-modal="true">
        <!-- Close Button -->
        <button
          v-if="!placedOrder"
          type="button"
          class="btn-modal-close"
          aria-label="Close"
          @click="showPaymentModal = false"
        >
          ✕
        </button>

        <!-- STATE A: PROMPT / PAYMENT IN PROGRESS -->
        <div v-if="!placedOrder" class="modal-body-payment">
          <!-- 1. PromptPay QR View -->
          <template v-if="selectedPaymentMethod === 'promptpay'">
            <div class="modal-header">
              <h2 class="modal-title">
                {{
                  locale === 'th-TH'
                    ? 'สแกนชำระเงินผ่าน พร้อมเพย์ (PromptPay QR)'
                    : 'Scan & Pay via PromptPay QR'
                }}
              </h2>
              <p class="modal-sub">
                {{
                  locale === 'th-TH'
                    ? 'รองรับแอปพลิเคชันทุกธนาคารในประเทศไทย ไม่มีค่าธรรมเนียม'
                    : 'Supported by all Thai mobile banking apps. Zero fees.'
                }}
              </p>
            </div>

            <!-- Total Amount Card -->
            <div class="bank-total-card">
              <span class="bt-label">{{
                locale === 'th-TH' ? 'ยอดชำระรวม' : 'Total Amount'
              }}</span>
              <strong class="bt-amount">{{ formatPrice(totalPayable) }}</strong>
            </div>

            <div class="qr-presentation-card">
              <div class="qr-code-frame">
                <img
                  v-if="qrDataUrl"
                  :src="qrDataUrl"
                  alt="PromptPay QR Code"
                  class="qr-image"
                />
                <div v-else class="qr-skeleton">
                  {{
                    locale === 'th-TH'
                      ? 'กำลังสร้าง QR Code...'
                      : 'Generating QR Code...'
                  }}
                </div>
              </div>

              <div class="qr-details-grid">
                <div class="detail-line">
                  <span class="dl-label">{{
                    locale === 'th-TH' ? 'ชื่อบัญชี:' : 'Account Name:'
                  }}</span>
                  <strong class="dl-val">{{
                    locale === 'th-TH'
                      ? 'บริษัท เดอะ พาร์เลอร์ จำกัด'
                      : 'The Parlor Co., Ltd.'
                  }}</strong>
                </div>
                <div class="detail-line">
                  <span class="dl-label">{{
                    locale === 'th-TH' ? 'รหัสอ้างอิง:' : 'Reference Code:'
                  }}</span>
                  <code class="dl-code">{{ qrReference }}</code>
                </div>
                <div class="detail-line timer-line">
                  <span class="dl-label">{{
                    locale === 'th-TH' ? 'อายุ QR Code:' : 'Expires In:'
                  }}</span>
                  <span class="timer-tag">
                    <svg
                      width="13"
                      height="13"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="2"
                    >
                      <circle cx="12" cy="12" r="10" />
                      <polyline points="12 6 12 12 16 14" />
                    </svg>
                    <span
                      >{{ formattedTimer }}
                      {{ locale === 'th-TH' ? 'นาที' : 'min' }}</span
                    >
                  </span>
                </div>
              </div>
            </div>

            <p class="qr-instruction">
              <template v-if="locale === 'th-TH'">
                เปิดแอปธนาคารของคุณ แล้วเลือก
                <strong>"สแกน QR"</strong> เพื่อชำระเงิน
                เมื่อทำรายการเรียบร้อยแล้ว
                ให้กดปุ่มด้านล่างเพื่อยืนยันคำสั่งซื้อ
              </template>
              <template v-else>
                Open your mobile banking app, select
                <strong>"Scan QR"</strong> to pay, then click confirm below.
              </template>
            </p>
          </template>

          <!-- 2. Bank Transfer View -->
          <template v-else-if="selectedPaymentMethod === 'bank_transfer'">
            <div class="modal-header">
              <h2 class="modal-title">
                {{
                  locale === 'th-TH'
                    ? 'โอนเงินผ่านบัญชีธนาคาร'
                    : 'Bank Account Transfer'
                }}
              </h2>
              <p class="modal-sub">
                {{
                  locale === 'th-TH'
                    ? 'กรุณาทำรายการโอนเงินตามยอดที่ระบุด้านล่างเข้าบัญชีของทางร้าน'
                    : 'Please transfer the exact amount below to our official bank account'
                }}
              </p>
            </div>

            <!-- Total Amount Card -->
            <div class="bank-total-card">
              <span class="bt-label">{{
                locale === 'th-TH' ? 'ยอดชำระรวม' : 'Total Amount'
              }}</span>
              <strong class="bt-amount">{{ formatPrice(totalPayable) }}</strong>
            </div>

            <div class="bank-accounts-modal-list">
              <!-- Kasikorn Bank -->
              <div class="bank-card-luxury">
                <div class="bank-card-top">
                  <div class="bank-brand-badge kbank-badge">
                    <span class="badge-icon">KBANK</span>
                    <span class="badge-title">{{
                      locale === 'th-TH' ? 'ธนาคารกสิกรไทย' : 'Kasikornbank'
                    }}</span>
                  </div>
                </div>
                <div class="bank-card-body">
                  <div class="account-name-row">
                    <span class="lbl">{{
                      locale === 'th-TH' ? 'ชื่อบัญชี:' : 'Account Name:'
                    }}</span>
                    <strong class="val">{{
                      locale === 'th-TH'
                        ? 'บริษัท เดอะ พาร์เลอร์ จำกัด'
                        : 'The Parlor Co., Ltd.'
                    }}</strong>
                  </div>
                </div>
              </div>

              <!-- SCB Bank -->
              <div class="bank-card-luxury">
                <div class="bank-card-top">
                  <div class="bank-brand-badge scb-badge">
                    <span class="badge-icon">SCB</span>
                    <span class="badge-title">{{
                      locale === 'th-TH'
                        ? 'ธนาคารไทยพาณิชย์'
                        : 'Siam Commercial Bank'
                    }}</span>
                  </div>
                </div>
                <div class="bank-card-body">
                  <div class="account-name-row">
                    <span class="lbl">{{
                      locale === 'th-TH' ? 'ชื่อบัญชี:' : 'Account Name:'
                    }}</span>
                    <strong class="val">{{
                      locale === 'th-TH'
                        ? 'บริษัท เดอะ พาร์เลอร์ จำกัด'
                        : 'The Parlor Co., Ltd.'
                    }}</strong>
                  </div>
                </div>
              </div>
            </div>

            <p class="bank-note-text">
              <template v-if="locale === 'th-TH'">
                เมื่อทำรายการโอนเงินเสร็จเรียบร้อยแล้ว ให้กดปุ่ม
                <strong>"ยืนยันการชำระเงิน"</strong> ด้านล่าง
              </template>
              <template v-else>
                Once payment transfer is completed, click
                <strong>"Confirm Payment"</strong> below.
              </template>
            </p>
          </template>

          <!-- 3. Card View -->
          <template v-else-if="selectedPaymentMethod === 'card'">
            <div class="modal-header">
              <h2 class="modal-title">
                {{
                  locale === 'th-TH'
                    ? 'ยืนยันการชำระเงินผ่านบัตร'
                    : 'Confirm Card Payment'
                }}
              </h2>
              <p class="modal-sub">
                {{
                  locale === 'th-TH'
                    ? 'ระบบจะดำเนินการหักยอดเงินจากบัตรเครดิต/เดบิตของคุณ'
                    : 'Your credit/debit card will be charged directly.'
                }}
              </p>
            </div>

            <div class="bank-total-card">
              <span class="bt-label">{{
                locale === 'th-TH' ? 'ยอดชำระรวม' : 'Total Amount'
              }}</span>
              <strong class="bt-amount">{{ formatPrice(totalPayable) }}</strong>
            </div>

            <div class="card-confirm-card">
              <div class="cc-row">
                <span class="lbl">{{
                  locale === 'th-TH' ? 'หมายเลขบัตร:' : 'Card Number:'
                }}</span>
                <strong class="val"
                  >•••• •••• ••••
                  {{ cardForm.number.slice(-4) || '••••' }}</strong
                >
              </div>
              <div class="cc-row">
                <span class="lbl">{{
                  locale === 'th-TH' ? 'ชื่อผู้ถือบัตร:' : 'Cardholder Name:'
                }}</span>
                <strong class="val">{{
                  cardForm.holder ||
                  (locale === 'th-TH' ? 'ผู้สั่งซื้อ' : 'Customer')
                }}</strong>
              </div>
            </div>
          </template>

          <!-- 4. COD View -->
          <template v-else>
            <div class="modal-header">
              <h2 class="modal-title">
                {{
                  locale === 'th-TH'
                    ? 'ยืนยันการสั่งซื้อแบบเก็บเงินปลายทาง'
                    : 'Confirm Cash on Delivery'
                }}
              </h2>
              <p class="modal-sub">
                {{
                  locale === 'th-TH'
                    ? 'ชำระเงินสดโดยตรงกับเจ้าหน้าที่ขนส่งเมื่อพัสดุจัดส่งถึงมือ'
                    : 'Pay cash directly to the courier when receiving your parcel.'
                }}
              </p>
            </div>

            <div class="bank-total-card">
              <span class="bt-label">{{
                locale === 'th-TH' ? 'ยอดชำระรวม' : 'Total Amount'
              }}</span>
              <strong class="bt-amount">{{ formatPrice(totalPayable) }}</strong>
            </div>
          </template>

          <!-- Modal Action Button -->
          <div class="modal-action-row">
            <button
              type="button"
              class="btn-confirm-payment"
              :disabled="isProcessingPayment"
              @click="confirmPayment"
            >
              <svg
                v-if="isProcessingPayment"
                class="spinner"
                viewBox="0 0 50 50"
              >
                <circle
                  cx="25"
                  cy="25"
                  r="20"
                  fill="none"
                  stroke-width="4"
                ></circle>
              </svg>
              <span>{{
                isProcessingPayment
                  ? locale === 'th-TH'
                    ? 'กำลังตรวจสอบการชำระเงิน...'
                    : 'Verifying Payment...'
                  : selectedPaymentMethod === 'cod'
                    ? locale === 'th-TH'
                      ? 'ยืนยันการสั่งซื้อ'
                      : 'Confirm Order'
                    : locale === 'th-TH'
                      ? 'ยืนยันการชำระเงิน'
                      : 'Confirm Payment'
              }}</span>
            </button>
          </div>
        </div>

        <!-- STATE B: SUCCESS & RECEIPT -->
        <div v-else class="modal-body-success">
          <div class="success-icon-wrap">
            <svg
              width="32"
              height="32"
              viewBox="0 0 24 24"
              fill="none"
              stroke="#16a34a"
              stroke-width="2.5"
            >
              <polyline points="20 6 9 17 4 12" />
            </svg>
          </div>

          <h2 class="success-title">
            {{
              locale === 'th-TH'
                ? 'สั่งซื้อและชำระเงินสำเร็จ'
                : 'Order Placed Successfully!'
            }}
          </h2>
          <p class="success-desc">
            {{
              locale === 'th-TH'
                ? 'ขอบคุณสำหรับการสั่งซื้อบอร์ดเกมกับ The Parlor ทางร้านกำลังเริ่มจัดเตรียมกล่องพัสดุของคุณ'
                : 'Thank you for your order with The Parlor. We are preparing your board game parcel.'
            }}
          </p>

          <div class="success-order-box">
            <div class="order-id-row">
              <span class="label">{{
                locale === 'th-TH' ? 'หมายเลขคำสั่งซื้อ:' : 'Order ID:'
              }}</span>
              <strong class="val">{{ placedOrder.id }}</strong>
            </div>

            <div v-if="placedOrder.trackingNumber" class="tracking-row">
              <div class="tracking-left">
                <span class="label">{{
                  locale === 'th-TH'
                    ? `หมายเลขพัสดุ (${placedOrder.carrierName}):`
                    : `Tracking No (${placedOrder.carrierName}):`
                }}</span>
                <code class="tracking-code">{{
                  placedOrder.trackingNumber
                }}</code>
              </div>
              <button
                type="button"
                class="btn-copy-tracking"
                @click="copyTracking(placedOrder.trackingNumber)"
              >
                {{
                  copiedTracking
                    ? locale === 'th-TH'
                      ? 'คัดลอกแล้ว!'
                      : 'Copied!'
                    : locale === 'th-TH'
                      ? 'คัดลอก'
                      : 'Copy'
                }}
              </button>
            </div>

            <div class="summary-lines">
              <div class="line">
                <span>{{
                  locale === 'th-TH' ? 'จำนวนบอร์ดเกม:' : 'Total Items:'
                }}</span>
                <strong
                  >{{ placedOrder.items.length }}
                  {{ locale === 'th-TH' ? 'รายการ' : 'items' }}</strong
                >
              </div>
              <div class="line">
                <span>{{
                  locale === 'th-TH' ? 'ยอดเงินที่ชำระ:' : 'Total Paid:'
                }}</span>
                <strong class="total-green">{{
                  formatPrice(placedOrder.totalAmount)
                }}</strong>
              </div>
              <div class="line">
                <span>{{
                  locale === 'th-TH'
                    ? 'กำหนดส่งโดยประมาณ:'
                    : 'Estimated Delivery:'
                }}</span>
                <span class="eta-text">{{
                  placedOrder.estimatedDeliveryDate
                }}</span>
              </div>
            </div>
          </div>

          <div class="success-action-row">
            <button
              type="button"
              class="btn-view-all-orders"
              @click="viewOrdersAndExit"
            >
              <svg
                width="15"
                height="15"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path
                  d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"
                />
                <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
                <line x1="12" y1="22.08" x2="12" y2="12" />
              </svg>
              <span>{{
                locale === 'th-TH'
                  ? 'ดูประวัติและติดตามพัสดุ'
                  : 'View Orders & Tracking'
              }}</span>
            </button>
            <button
              type="button"
              class="btn-return-shop"
              @click="closeSuccessAndExit"
            >
              {{ locale === 'th-TH' ? 'กลับสู่หน้าหลัก' : 'Back to Home' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </main>
</template>

<style scoped>
.shopee-checkout-page {
  background: #f4f5f7;
  min-height: calc(100vh - 70px);
  padding: 24px 0 60px;
  font-family: Arial, Helvetica, sans-serif;
  color: #243e32;
}

.checkout-container {
  max-width: 1140px;
  margin: 0 auto;
  padding: 0 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Breadcrumb */
.checkout-top-bar {
  display: flex;
  align-items: center;
}
.checkout-breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.bc-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #555555;
  text-decoration: none;
}
.bc-link:hover {
  color: #142a1f;
  text-decoration: underline;
}
.bc-sep {
  color: #aaaaaa;
}
.bc-curr {
  color: #142a1f;
  font-weight: 700;
}

/* Title row */
.page-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.page-title {
  font-size: 22px;
  font-weight: 800;
  color: #142a1f;
  margin: 0;
}
.btn-back-to-cart {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #2b543e;
  text-decoration: none;
  background: #ffffff;
  padding: 8px 14px;
  border-radius: 4px;
  border: 1px solid #d3ded7;
  transition: all 140ms ease;
}
.btn-back-to-cart:hover {
  background: #f0f5f2;
  border-color: #142a1f;
}

/* Checkout Alert */
.checkout-form-alert {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: #fdf2f2;
  border: 1px solid #f0a8a8;
  border-radius: 4px;
  color: #b91c1c;
  font-size: 13px;
  font-weight: 600;
}

/* Common Card */
.checkout-card {
  background: #ffffff;
  border-radius: 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  border: 1px solid #eaeaea;
  padding: 20px 24px;
}
.card-head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 14px;
  margin-bottom: 16px;
}
.head-title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #142a1f;
}
.head-title-wrap h2 {
  font-size: 16px;
  font-weight: 700;
  margin: 0;
}
.items-total-badge {
  background: #f0f4f2;
  color: #1d3d2e;
  font-size: 12px;
  font-weight: 700;
  padding: 4px 10px;
  border-radius: 12px;
}

/* 1. Items Summary Table */
.items-summary-table {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.summary-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px solid #f7f7f7;
}
.summary-row:last-child {
  border-bottom: none;
}
.summary-thumb {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid #e5e5e5;
  background: #fdfdfd;
}
.summary-details {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.summary-name {
  font-size: 14px;
  color: #222222;
}
.summary-edition {
  font-size: 12px;
  color: #777777;
}
.summary-qty-price {
  font-size: 12px;
  color: #555555;
}
.summary-total {
  font-size: 15px;
  font-weight: 700;
  color: #142a1f;
}

/* 2. Delivery Fields */
.shipping-fields-card {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px dashed #d8e2dc;
}
.form-subhead {
  font-size: 14px;
  font-weight: 700;
  color: #142a1f;
  margin: 0 0 14px 0;
}
.form-grid-3 {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 12px;
}
.form-grid-2 {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 14px;
  margin-bottom: 12px;
}
.field-wrap {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.field-wrap.full {
  margin-top: 4px;
}
.field-wrap label {
  font-size: 12px;
  font-weight: 600;
  color: #333333;
}
.field-wrap input {
  padding: 9px 12px;
  border: 1px solid #d3ded7;
  border-radius: 3px;
  font-size: 13px;
  background: #ffffff;
  color: #142a1f;
  transition: border-color 140ms ease;
}
.field-wrap input:focus {
  outline: none;
  border-color: #142a1f;
  box-shadow: 0 0 0 2px rgba(20, 42, 31, 0.15);
}
.field-wrap.has-error label {
  color: #dc2626 !important;
}
.field-wrap.has-error input {
  border-color: #dc2626 !important;
  background-color: #fef2f2 !important;
}
.field-wrap.has-error input:focus {
  box-shadow: 0 0 0 2px rgba(220, 38, 38, 0.25) !important;
}
.field-error-msg {
  display: block;
  font-size: 11px;
  font-weight: 600;
  color: #dc2626;
  margin-top: 3px;
  line-height: 1.3;
}

/* 3. Payment Methods */
.methods-selection-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.method-option-row {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 14px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  cursor: pointer;
  transition: all 140ms ease;
  background: #ffffff;
}
.method-option-row:hover {
  border-color: #c6ad7a;
  background: #fefdf9;
}
.method-option-row.is-active {
  border-color: #142a1f;
  background: #fbfdfc;
  box-shadow: 0 0 0 1px #142a1f;
}
.sr-only {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}
.method-radio {
  margin-top: 2px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid #aaaaaa;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.method-option-row.is-active .method-radio {
  border-color: #142a1f;
}
.radio-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: transparent;
}
.method-option-row.is-active .radio-dot {
  background: #142a1f;
}
.method-info {
  flex: 1;
}
.method-name-line {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
}
.method-name-line strong {
  font-size: 14px;
  color: #142a1f;
}
.tag-rec {
  background: #c6ad7a;
  color: #142a1f;
  font-size: 10px;
  font-weight: 800;
  padding: 2px 7px;
  border-radius: 3px;
  letter-spacing: 0.05em;
}
.card-brands {
  font-size: 11px;
  color: #666666;
  background: #eeeeee;
  padding: 2px 8px;
  border-radius: 3px;
}
.method-desc {
  font-size: 12px;
  color: #666666;
  margin: 0;
  line-height: 1.4;
}

/* Bank Modal Total & Cards (Clean, Professional, Beautiful) */
.bank-total-card {
  width: 100%;
  background: #f7faf8;
  border: 1px solid #dce8e1;
  border-radius: 8px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}
.bt-label {
  font-size: 13px;
  color: #4b6356;
  font-weight: 600;
}
.bt-amount {
  font-size: 22px;
  font-weight: 800;
  color: #142a1f;
}

.bank-accounts-modal-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}
.bank-card-luxury {
  width: 100%;
  background: #ffffff;
  border: 1px solid #d8e5df;
  border-radius: 8px;
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.bank-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.bank-brand-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.badge-icon {
  font-size: 11px;
  font-weight: 800;
  padding: 3px 7px;
  border-radius: 4px;
  color: #ffffff;
}
.kbank-badge .badge-icon {
  background: #138f2d;
}
.scb-badge .badge-icon {
  background: #4e2a84;
}
.badge-title {
  font-size: 14px;
  font-weight: 700;
  color: #142a1f;
}
.account-type-tag {
  font-size: 11px;
  color: #666666;
  background: #f0f2f1;
  padding: 2px 8px;
  border-radius: 12px;
}
.bank-card-body {
  border-top: 1px solid #f0f4f2;
  padding-top: 8px;
}
.account-name-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
}
.account-name-row .lbl {
  color: #666666;
}
.account-name-row .val {
  color: #142a1f;
}

.bank-note-text {
  font-size: 12px;
  color: #666666;
  line-height: 1.5;
  margin: 4px 0 16px 0;
  text-align: center;
}

/* Card Confirm Box */
.card-confirm-card {
  width: 100%;
  background: #ffffff;
  border: 1px solid #d8e5df;
  border-radius: 8px;
  padding: 14px 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 20px;
}
.cc-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
}
.cc-row .lbl {
  color: #666666;
}
.cc-row .val {
  color: #142a1f;
}

/* Bank accounts inline box */
.bank-accounts-box {
  margin-top: 14px;
  display: flex;
  gap: 12px;
}
.bank-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: #ffffff;
  border: 1px solid #dcdcdc;
  border-radius: 4px;
  flex: 1;
}
.bank-code {
  font-size: 11px;
  font-weight: 800;
  padding: 4px 6px;
  border-radius: 3px;
  color: #ffffff;
}
.bank-code.kbank {
  background: #138f2d;
}
.bank-code.scb {
  background: #4e2a84;
}
.bank-text strong {
  display: block;
  font-size: 12px;
  color: #222222;
}
.bank-text p {
  font-size: 11px;
  color: #555555;
  margin: 2px 0 0 0;
}

/* 4. Final Order Summary */
.order-final-summary {
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.summary-breakdown-table {
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-bottom: 1px solid #f0f0f0;
  padding-bottom: 16px;
}
.summary-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: #555555;
}
.summary-line .carrier-tag {
  color: #777777;
  font-size: 12px;
}
.summary-line.line-grand-total {
  margin-top: 6px;
  padding-top: 10px;
  border-top: 1px dashed #e5e5e5;
  font-size: 16px;
  color: #142a1f;
}
.val-grand {
  font-size: 24px;
  font-weight: 800;
  color: #142a1f;
}

.checkout-action-row {
  display: flex;
  justify-content: flex-end;
}
.btn-place-order {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  background: #142a1f;
  color: #ffd98a;
  border: 1px solid #365e49;
  padding: 14px 36px;
  border-radius: 4px;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(20, 42, 31, 0.2);
  transition: all 140ms ease;
}
.btn-place-order:hover {
  background: #1d3d2e;
  border-color: #c6ad7a;
  color: #ffffff;
  transform: translateY(-1px);
}

/* MODAL STYLES (CENTERED PROMPTPAY / CONFIRMATION) */
.checkout-modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(10, 20, 15, 0.68);
  backdrop-filter: blur(4px);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}
.checkout-modal-card {
  position: relative;
  width: 100%;
  max-width: 480px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 12px 36px rgba(0, 0, 0, 0.16);
  border: 1px solid #e2e8e4;
  padding: 32px 28px;
  max-height: 90vh;
  overflow-y: auto;
}
.btn-modal-close {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 32px;
  height: 32px;
  background: #f0f0f0;
  border: none;
  border-radius: 50%;
  font-size: 14px;
  color: #555555;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}
.btn-modal-close:hover {
  background: #e2e2e2;
  color: #111111;
}

/* Modal Payment Body */
.modal-body-payment {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.modal-brand {
  display: block;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.15em;
  color: #9c7a36;
  margin-bottom: 6px;
}
.modal-title {
  font-size: 18px;
  font-weight: 800;
  color: #142a1f;
  margin: 0 0 6px 0;
}
.modal-sub {
  font-size: 12px;
  color: #666666;
  margin: 0 0 18px 0;
}
.qr-presentation-card {
  width: 100%;
  background: #f8faf9;
  border: 1px solid #dbe5e0;
  border-radius: 6px;
  padding: 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}
.qr-code-frame {
  background: #ffffff;
  padding: 10px;
  border-radius: 6px;
  border: 1px solid #c9d6ce;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.05);
}
.qr-image {
  display: block;
  width: 200px;
  height: 200px;
}
.qr-details-grid {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border-top: 1px solid #e7eee9;
  padding-top: 12px;
  text-align: left;
}
.detail-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
}
.dl-label {
  color: #666666;
}
.dl-val {
  color: #142a1f;
  font-weight: 600;
}
.dl-val.amt {
  font-size: 16px;
  font-weight: 800;
  color: #142a1f;
}
.dl-code {
  font-family: monospace;
  background: #eaefe9;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 12px;
  color: #1d3d2e;
}
.timer-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: #d97706;
  font-weight: 700;
  font-size: 12px;
}
.qr-instruction {
  font-size: 12px;
  color: #555555;
  line-height: 1.5;
  margin: 16px 0 20px;
}

/* Modal Actions */
.modal-action-row {
  width: 100%;
}
.btn-confirm-payment {
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: #142a1f;
  color: #ffd98a;
  border: 1px solid #365e49;
  padding: 14px 20px;
  border-radius: 4px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: all 140ms ease;
}
.btn-confirm-payment:hover:not(:disabled) {
  background: #1d3d2e;
  border-color: #c6ad7a;
  color: #ffffff;
}
.btn-confirm-payment:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
.spinner {
  width: 18px;
  height: 18px;
  animation: spin 800ms linear infinite;
}
.spinner circle {
  stroke: currentColor;
  stroke-linecap: round;
  stroke-dasharray: 90, 150;
  stroke-dashoffset: 0;
}
@keyframes spin {
  100% {
    transform: rotate(360deg);
  }
}

/* State B: Success */
.modal-body-success {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.success-icon-wrap {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #ecfdf5;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 14px;
}
.success-title {
  font-size: 20px;
  font-weight: 800;
  color: #142a1f;
  margin: 0 0 8px 0;
}
.success-desc {
  font-size: 13px;
  color: #555555;
  line-height: 1.5;
  margin: 0 0 20px 0;
  max-width: 380px;
}
.success-order-box {
  width: 100%;
  background: #ffffff;
  border: 1px solid #e1e8e4;
  border-radius: 8px;
  padding: 16px 18px;
  text-align: left;
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 24px;
}
.order-id-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  padding-bottom: 10px;
  border-bottom: 1px dashed #e1e8e4;
}
.order-id-row .val {
  font-size: 15px;
  color: #142a1f;
  font-weight: 800;
}
.tracking-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f7faf8;
  padding: 10px 14px;
  border-radius: 6px;
  border: 1px solid #dce8e1;
}
.tracking-left {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.tracking-left .label {
  font-size: 11px;
  color: #666666;
}
.tracking-code {
  font-size: 14px;
  font-weight: 800;
  color: #142a1f;
  letter-spacing: 0.04em;
}
.btn-copy-tracking {
  background: #ffffff;
  border: 1px solid #c2d6cb;
  color: #1d3d2e;
  padding: 6px 14px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: all 120ms ease;
}
.btn-copy-tracking:hover {
  background: #eaf3ee;
  border-color: #142a1f;
}
.summary-lines {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 4px;
  font-size: 13px;
  color: #555555;
}
.summary-lines .line {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.total-green {
  font-size: 17px;
  font-weight: 800;
  color: #142a1f;
}
.eta-text {
  color: #166534;
  font-weight: 600;
}
.success-action-row {
  width: 100%;
  display: flex;
  gap: 12px;
}
.btn-view-all-orders {
  flex: 1.2;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: #142a1f;
  color: #ffd98a;
  border: 1px solid #365e49;
  padding: 12px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: all 140ms ease;
}
.btn-view-all-orders:hover {
  background: #1d3d2e;
  border-color: #c6ad7a;
  color: #ffffff;
}
.btn-return-shop {
  flex: 1;
  background: #ffffff;
  color: #142a1f;
  border: 1px solid #cfdcd5;
  padding: 12px 14px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 140ms ease;
}
.btn-return-shop:hover {
  background: #f4f7f5;
  border-color: #142a1f;
}

@media (max-width: 768px) {
  .form-grid-3,
  .form-grid-2 {
    grid-template-columns: 1fr;
  }
  .bank-accounts-box {
    flex-direction: column;
  }
}
</style>
