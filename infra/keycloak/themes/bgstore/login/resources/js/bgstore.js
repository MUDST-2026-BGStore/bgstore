(function () {
  'use strict';

  document
    .querySelectorAll('[data-password-toggle]')
    .forEach(function (toggle) {
      const input = document.getElementById(
        toggle.getAttribute('data-password-toggle'),
      );
      if (!input) return;

      toggle.addEventListener('click', function () {
        const isVisible = input.type === 'text';
        input.type = isVisible ? 'password' : 'text';
        const label = isVisible
          ? toggle.dataset.showLabel
          : toggle.dataset.hideLabel;
        toggle.classList.toggle('is-visible', !isVisible);
        toggle.setAttribute('aria-pressed', String(!isVisible));
        if (label) {
          toggle.setAttribute('aria-label', label);
        }
      });
    });

  const form = document.querySelector(
    '#kc-register-form, #kc-passwd-update-form',
  );
  const password = document.getElementById(
    form && form.id === 'kc-passwd-update-form' ? 'password-new' : 'password',
  );
  const confirmation = document.getElementById('password-confirm');
  if (form && password) {
    const email = document.getElementById('email');
    const rules = Array.prototype.slice.call(
      form.querySelectorAll('[data-password-rule]'),
    );

    const checkPasswordRules = function () {
      const value = password.value;
      const emailValue = email ? email.value.trim().toLowerCase() : '';
      const checks = {
        length: value.length >= 12,
        uppercase: /[A-Z]/.test(value),
        lowercase: /[a-z]/.test(value),
        number: /\d/.test(value),
        special: /[^A-Za-z0-9]/.test(value),
        different: !emailValue || value.toLowerCase() !== emailValue,
      };

      rules.forEach(function (rule) {
        const isMet = Boolean(checks[rule.dataset.passwordRule]);
        rule.classList.toggle('is-met', isMet);
        rule.setAttribute('aria-checked', String(isMet));
      });

      const isStrong = Object.keys(checks).every(function (key) {
        return checks[key];
      });
      password.setCustomValidity(
        value && !isStrong ? form.dataset.passwordInvalidMessage || '' : '',
      );

      if (confirmation && confirmation.type !== 'hidden') {
        confirmation.setCustomValidity(
          confirmation.value && confirmation.value !== value
            ? form.dataset.passwordMismatchMessage || ''
            : '',
        );
      }
    };

    const syncPassword = function () {
      if (form.id === 'kc-register-form' && confirmation) {
        confirmation.value = password.value;
      }
      checkPasswordRules();
    };
    password.addEventListener('input', syncPassword);
    if (confirmation && confirmation.type !== 'hidden') {
      confirmation.addEventListener('input', checkPasswordRules);
    }
    if (email) email.addEventListener('input', checkPasswordRules);
    form.addEventListener('submit', syncPassword);
    syncPassword();
  }
})();
