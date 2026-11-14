import {
  createPaymentToken,
  getPaymentConfiguration,
  type CreatePaymentTokenRequest,
  type PaymentConfiguration,
} from '@theparlor/api-client';

/** Omise's browser library, which sends card details straight to Omise's vault. */
const OMISE_SCRIPT = 'https://cdn.omise.co/omise.js';

interface OmiseTokenResponse {
  id?: string;
  code?: string;
  message?: string;
}

interface OmiseJs {
  setPublicKey(key: string): void;
  createToken(
    type: 'card',
    card: Record<string, string | number>,
    callback: (statusCode: number, response: OmiseTokenResponse) => void,
  ): void;
}

declare global {
  interface Window {
    Omise?: OmiseJs;
  }
}

/**
 * A card that could not become a token. It carries an HTTP-like status so pages
 * can treat it like the server's answers: 400 when the card details are wrong,
 * 502 when the provider cannot be reached. Nothing was charged either way.
 */
export class CardTokenizationError extends Error {
  readonly response: { status: number; data: { code: string } };

  constructor(status: 400 | 502, code: string, message: string) {
    super(message);
    this.name = 'CardTokenizationError';
    this.response = { status, data: { code } };
  }
}

let configuration: Promise<PaymentConfiguration> | undefined;
let omiseLoaded: Promise<OmiseJs> | undefined;

function paymentConfiguration(): Promise<PaymentConfiguration> {
  configuration ??= getPaymentConfiguration().catch((error: unknown) => {
    configuration = undefined;
    throw error;
  });
  return configuration;
}

function loadOmise(): Promise<OmiseJs> {
  if (window.Omise) return Promise.resolve(window.Omise);
  omiseLoaded ??= new Promise<OmiseJs>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = OMISE_SCRIPT;
    script.async = true;
    script.onload = () =>
      window.Omise
        ? resolve(window.Omise)
        : reject(new Error('Omise.js loaded without Omise'));
    script.onerror = () => reject(new Error('Omise.js could not be loaded'));
    document.head.append(script);
  }).catch((error: unknown) => {
    omiseLoaded = undefined;
    throw error;
  });
  return omiseLoaded;
}

async function omiseToken(
  publicKey: string,
  card: CreatePaymentTokenRequest,
): Promise<string> {
  let omise: OmiseJs;
  try {
    omise = await loadOmise();
  } catch {
    throw new CardTokenizationError(
      502,
      'PAYMENT_GATEWAY_ERROR',
      'Card payment is unavailable',
    );
  }
  omise.setPublicKey(publicKey);
  return new Promise<string>((resolve, reject) => {
    omise.createToken(
      'card',
      {
        name: card.holderName,
        number: card.number.replace(/\D/g, ''),
        expiration_month: card.expMonth,
        expiration_year: card.expYear,
        security_code: card.cvc,
      },
      (statusCode, response) => {
        if (statusCode === 200 && response.id) {
          resolve(response.id);
          return;
        }
        // Card mistakes come back as 4xx; anything else is the provider's problem.
        const cardProblem =
          statusCode >= 400 &&
          statusCode < 500 &&
          response.code !== 'authentication_failure';
        reject(
          new CardTokenizationError(
            cardProblem ? 400 : 502,
            cardProblem ? 'INVALID_CARD' : 'PAYMENT_GATEWAY_ERROR',
            response.message || 'The card could not be used',
          ),
        );
      },
    );
  });
}

/**
 * Turns card details into a single-use payment token. With Omise the browser
 * sends the card to Omise directly and this server never sees it; otherwise the
 * card goes to the payment token operation. The card is never stored.
 */
export async function tokenizeCard(
  card: CreatePaymentTokenRequest,
): Promise<string> {
  const { cardTokenization, omisePublicKey } = await paymentConfiguration();
  if (cardTokenization === 'OMISE') {
    if (!omisePublicKey)
      throw new CardTokenizationError(
        502,
        'PAYMENT_GATEWAY_ERROR',
        'Card payment is unavailable',
      );
    return omiseToken(omisePublicKey, card);
  }
  return (await createPaymentToken(card)).token;
}

/** For tests: forget the cached configuration and library. */
export function resetCardTokenization() {
  configuration = undefined;
  omiseLoaded = undefined;
}
