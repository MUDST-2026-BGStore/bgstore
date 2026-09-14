<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('password','password-confirm') cardClass="bgstore-auth-card--update-password"; section>
  <#if section = "header">
    <div class="bgstore-auth-icon" aria-hidden="true">BG</div>
    <h1 id="bgstore-page-title">${msg("updatePasswordTitle")}</h1>
    <p>${msg("updatePasswordSubtitle")}</p>
  <#elseif section = "form">
    <form id="kc-passwd-update-form" class="bgstore-form bgstore-update-password-form" action="${url.loginAction}" method="post" data-password-invalid-message="${msg("passwordInvalid")}" data-password-mismatch-message="${msg("passwordMismatch")}">
      <div class="bgstore-field bgstore-field--wide">
        <label for="password-new">${msg("passwordNew")}</label>
        <div class="bgstore-password-field">
          <input id="password-new" name="password-new" type="password" autocomplete="new-password" autofocus required aria-invalid="${messagesPerField.existsError('password','password-confirm')?c}">
          <button class="bgstore-password-toggle" type="button" data-password-toggle="password-new" data-show-label="${msg("showPassword")}" data-hide-label="${msg("hidePassword")}" aria-controls="password-new" aria-label="${msg("showPassword")}" aria-pressed="false">
            <svg class="bgstore-password-icon bgstore-password-icon--show" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"/><circle cx="12" cy="12" r="3"/></svg>
            <svg class="bgstore-password-icon bgstore-password-icon--hide" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10.733 5.076a10.744 10.744 0 0 1 9.205 6.327 1 1 0 0 1 0 .695 10.75 10.75 0 0 1-1.36 2.21"/><path d="M14.084 14.084a3 3 0 0 1-4.168-4.168"/><path d="M17.479 17.479A10.75 10.75 0 0 1 12 19.75a10.75 10.75 0 0 1-9.938-7.054 1 1 0 0 1 0-.696A10.75 10.75 0 0 1 5.6 6.2"/><path d="m2 2 20 20"/></svg>
          </button>
        </div>
        <div class="bgstore-password-help" aria-live="polite">
          <p class="bgstore-password-help-title">${msg("passwordStrengthTitle")}</p>
          <ul class="bgstore-password-rules" aria-label="${msg("passwordStrengthTitle")}">
            <li class="bgstore-password-rule" data-password-rule="length" aria-checked="false">${msg("passwordRuleLength")}</li>
            <li class="bgstore-password-rule" data-password-rule="uppercase" aria-checked="false">${msg("passwordRuleUppercase")}</li>
            <li class="bgstore-password-rule" data-password-rule="lowercase" aria-checked="false">${msg("passwordRuleLowercase")}</li>
            <li class="bgstore-password-rule" data-password-rule="number" aria-checked="false">${msg("passwordRuleNumber")}</li>
            <li class="bgstore-password-rule" data-password-rule="special" aria-checked="false">${msg("passwordRuleSpecial")}</li>
          </ul>
        </div>
        <#if messagesPerField.existsError('password')><p class="bgstore-field-error">${kcSanitize(messagesPerField.get('password'))?no_esc}</p></#if>
      </div>

      <div class="bgstore-field bgstore-field--wide">
        <label for="password-confirm">${msg("passwordConfirm")}</label>
        <div class="bgstore-password-field">
          <input id="password-confirm" name="password-confirm" type="password" autocomplete="new-password" required aria-invalid="${messagesPerField.existsError('password-confirm')?c}">
          <button class="bgstore-password-toggle" type="button" data-password-toggle="password-confirm" data-show-label="${msg("showPassword")}" data-hide-label="${msg("hidePassword")}" aria-controls="password-confirm" aria-label="${msg("showPassword")}" aria-pressed="false">
            <svg class="bgstore-password-icon bgstore-password-icon--show" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"/><circle cx="12" cy="12" r="3"/></svg>
            <svg class="bgstore-password-icon bgstore-password-icon--hide" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10.733 5.076a10.744 10.744 0 0 1 9.205 6.327 1 1 0 0 1 0 .695 10.75 10.75 0 0 1-1.36 2.21"/><path d="M14.084 14.084a3 3 0 0 1-4.168-4.168"/><path d="M17.479 17.479A10.75 10.75 0 0 1 12 19.75a10.75 10.75 0 0 1-9.938-7.054 1 1 0 0 1 0-.696A10.75 10.75 0 0 1 5.6 6.2"/><path d="m2 2 20 20"/></svg>
          </button>
        </div>
        <#if messagesPerField.existsError('password-confirm')><p class="bgstore-field-error">${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}</p></#if>
      </div>

      <label class="bgstore-check bgstore-update-sessions" for="logout-sessions">
        <input id="logout-sessions" name="logout-sessions" type="checkbox" value="on">
        <span>${msg("logoutOtherSessions")}</span>
      </label>

      <div class="bgstore-update-actions">
        <#if isAppInitiatedAction??>
          <button name="login" class="bgstore-submit" type="submit">${msg("updatePasswordSubmit")}</button>
          <button class="bgstore-auth-switch bgstore-update-cancel" type="submit" name="cancel-aia" value="true">${msg("doCancel")}</button>
        <#else>
          <button name="login" class="bgstore-submit" type="submit">${msg("updatePasswordSubmit")}</button>
        </#if>
      </div>
    </form>
  </#if>
</@layout.registrationLayout>
