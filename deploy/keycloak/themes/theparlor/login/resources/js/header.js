document.addEventListener('DOMContentLoaded', () => {
  const pageId = document.body.dataset.pageId;
  if (pageId !== 'login-login' && pageId !== 'login-register') return;
  const header = document.getElementById('kc-header');
  const select = document.getElementById('login-select-toggle');
  const languages = [
    ['th', 'ไทย'],
    ['en', 'EN'],
  ];
  if (!header || !select || select.options.length !== languages.length) return;

  const switcher = document.createElement('div');
  switcher.className = 'parlor-header-language';
  switcher.setAttribute('role', 'group');
  switcher.setAttribute('aria-label', select.getAttribute('aria-label'));

  // Reuse Keycloak's locale links so changing language keeps the current auth flow.
  for (const [locale, label] of languages) {
    const option = Array.from(select.options).find(
      (entry) =>
        new URL(entry.value, window.location.href).searchParams.get(
          'kc_locale',
        ) === locale,
    );
    if (!option) return;

    const link = document.createElement('a');
    link.href = option.value;
    link.lang = locale;
    link.hreflang = locale;
    link.textContent = label;
    if (option.selected) link.setAttribute('aria-current', 'true');
    switcher.append(link);
  }

  header.prepend(switcher);
  select.closest('.pf-v5-c-login__main-header-utilities')?.remove();
});
