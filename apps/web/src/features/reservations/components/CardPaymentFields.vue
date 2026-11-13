<script setup lang="ts">
// Card details stay in this component until they are exchanged for a single-use
// token; they are never stored, logged or sent anywhere else. The gateway runs the
// real card checks; these only catch obvious slips before asking it.
import { reactive } from 'vue';
import type { CreatePaymentTokenRequest } from '@theparlor/api-client';
import { useStoreText } from '../../../shared/lib/store-text';

const { t } = useStoreText();

const card = reactive({ number: '', expiry: '', cvc: '', holder: '' });
const errors = reactive({ number: '', expiry: '', cvc: '', holder: '' });
type Field = keyof typeof errors;

function onNumber(event: Event) {
  const digits = (event.target as HTMLInputElement).value
    .replace(/\D/g, '')
    .slice(0, 19);
  card.number = digits.replace(/(.{4})/g, '$1 ').trim();
  if (errors.number) check('number');
}

function onExpiry(event: Event) {
  let digits = (event.target as HTMLInputElement).value
    .replace(/\D/g, '')
    .slice(0, 4);
  if (digits.length >= 3) digits = `${digits.slice(0, 2)}/${digits.slice(2)}`;
  card.expiry = digits;
  if (errors.expiry) check('expiry');
}

function onCvc(event: Event) {
  card.cvc = (event.target as HTMLInputElement).value
    .replace(/\D/g, '')
    .slice(0, 4);
  if (errors.cvc) check('cvc');
}

function check(field: Field): boolean {
  let message = '';
  if (field === 'number') {
    const digits = card.number.replace(/\s/g, '');
    if (!digits) message = t('Enter the card number.');
    else if (digits.length < 13) message = t('The card number is too short.');
  }
  if (field === 'expiry') {
    if (!/^(0[1-9]|1[0-2])\/\d{2}$/.test(card.expiry))
      message = t('Enter the expiry as MM/YY.');
  }
  if (field === 'cvc') {
    if (card.cvc.length < 3)
      message = t('Enter the 3 or 4 digit security code.');
  }
  if (field === 'holder') {
    if (!card.holder.trim()) message = t('Enter the name on the card.');
  }
  errors[field] = message;
  return !message;
}

/** The card as the token request needs it, or null after showing what to fix. */
function read(): CreatePaymentTokenRequest | null {
  const valid = (['number', 'expiry', 'cvc', 'holder'] as Field[])
    .map(check)
    .every(Boolean);
  if (!valid) return null;
  const [month, year] = card.expiry.split('/');
  return {
    number: card.number,
    expMonth: Number(month),
    expYear: 2000 + Number(year),
    cvc: card.cvc,
    holderName: card.holder.trim(),
  };
}

/** Forgets the card once it has been tokenized. */
function clear() {
  Object.assign(card, { number: '', expiry: '', cvc: '', holder: '' });
}

defineExpose({ read, clear });
</script>

<template>
  <fieldset class="card-fields">
    <legend class="card-legend">{{ t('Card details') }}</legend>
    <div class="field" :class="{ invalid: errors.number }">
      <label for="card-number">{{ t('Card number') }}</label>
      <input
        id="card-number"
        :value="card.number"
        type="text"
        inputmode="numeric"
        autocomplete="cc-number"
        placeholder="1234 5678 9012 3456"
        maxlength="23"
        :aria-invalid="!!errors.number"
        aria-describedby="card-number-error"
        @input="onNumber"
        @blur="check('number')"
      />
      <span id="card-number-error" class="error" role="alert">{{
        errors.number
      }}</span>
    </div>
    <div class="row">
      <div class="field" :class="{ invalid: errors.expiry }">
        <label for="card-expiry">{{ t('Expiry (MM/YY)') }}</label>
        <input
          id="card-expiry"
          :value="card.expiry"
          type="text"
          inputmode="numeric"
          autocomplete="cc-exp"
          placeholder="MM/YY"
          maxlength="5"
          :aria-invalid="!!errors.expiry"
          aria-describedby="card-expiry-error"
          @input="onExpiry"
          @blur="check('expiry')"
        />
        <span id="card-expiry-error" class="error" role="alert">{{
          errors.expiry
        }}</span>
      </div>
      <div class="field" :class="{ invalid: errors.cvc }">
        <label for="card-cvc">{{ t('Security code') }}</label>
        <input
          id="card-cvc"
          :value="card.cvc"
          type="password"
          inputmode="numeric"
          autocomplete="cc-csc"
          maxlength="4"
          :aria-invalid="!!errors.cvc"
          aria-describedby="card-cvc-error"
          @input="onCvc"
          @blur="check('cvc')"
        />
        <span id="card-cvc-error" class="error" role="alert">{{
          errors.cvc
        }}</span>
      </div>
    </div>
    <div class="field" :class="{ invalid: errors.holder }">
      <label for="card-holder">{{ t('Name on card') }}</label>
      <input
        id="card-holder"
        v-model="card.holder"
        type="text"
        autocomplete="cc-name"
        :aria-invalid="!!errors.holder"
        aria-describedby="card-holder-error"
        @blur="check('holder')"
      />
      <span id="card-holder-error" class="error" role="alert">{{
        errors.holder
      }}</span>
    </div>
  </fieldset>
</template>

<style scoped>
.card-fields {
  border: 1px solid #d8cfbf;
  border-radius: 6px;
  padding: 14px 16px 6px;
  margin: 0 0 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.card-legend {
  padding: 0 6px;
  font-weight: 700;
  font-size: 14px;
  color: #203c30;
}

.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field label {
  font-size: 13px;
  font-weight: 600;
  color: #3b3328;
}

.field input {
  border: 1px solid #c9bfae;
  border-radius: 4px;
  padding: 9px 10px;
  font-size: 15px;
  background: #fff;
  color: #1f1a14;
  transition: border-color 0.15s ease;
}

.field input:focus-visible {
  outline: 2px solid #203c30;
  outline-offset: 1px;
}

.field.invalid input {
  border-color: #a3271b;
}

.error {
  min-height: 18px;
  font-size: 12px;
  color: #a3271b;
}

@media (max-width: 480px) {
  .row {
    grid-template-columns: 1fr;
    gap: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .field input {
    transition: none;
  }
}
</style>
