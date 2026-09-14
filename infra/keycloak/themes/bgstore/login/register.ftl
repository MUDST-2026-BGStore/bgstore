<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true displayMessage=false cardClass="bgstore-auth-card--register"; section>
  <#if section = "header">
    <div class="bgstore-auth-icon" aria-hidden="true">+</div>
    <h1 id="bgstore-page-title">${msg("registerTitle")}</h1>
    <p>${msg("registerSubtitle")}</p>
  <#elseif section = "form">
    <form id="kc-register-form" class="bgstore-form bgstore-register-form" action="${url.registrationAction}" method="post" data-password-invalid-message="${msg("passwordInvalid")}">
      <#if message?has_content && message.type == "error" && !messagesPerField.existsError('firstName', 'lastName', 'email', 'password', 'password-confirm')>
        <div class="bgstore-alert bgstore-alert--error" role="alert">${msg("registrationError")}</div>
      </#if>
      <div class="bgstore-form-grid">
        <div class="bgstore-field">
          <label for="firstName">${msg("firstName")}</label>
          <input id="firstName" name="firstName" value="${register.formData.firstName!''}" type="text" autocomplete="given-name" autofocus required aria-invalid="${messagesPerField.existsError('firstName')?c}">
          <#if messagesPerField.existsError('firstName')><p class="bgstore-field-error">${kcSanitize(messagesPerField.get('firstName'))?no_esc}</p></#if>
        </div>
        <div class="bgstore-field">
          <label for="lastName">${msg("lastName")}</label>
          <input id="lastName" name="lastName" value="${register.formData.lastName!''}" type="text" autocomplete="family-name" required aria-invalid="${messagesPerField.existsError('lastName')?c}">
          <#if messagesPerField.existsError('lastName')><p class="bgstore-field-error">${kcSanitize(messagesPerField.get('lastName'))?no_esc}</p></#if>
        </div>
        <div class="bgstore-field bgstore-field--wide">
          <label for="email">${msg("email")}</label>
          <input id="email" name="email" value="${register.formData.email!''}" type="email" autocomplete="email" required aria-invalid="${messagesPerField.existsError('email')?c}">
          <#if messagesPerField.existsError('email')><p class="bgstore-field-error">${kcSanitize(messagesPerField.get('email'))?no_esc}</p></#if>
        </div>
        <div class="bgstore-field bgstore-field--wide">
          <label for="password">${msg("password")}</label>
          <div class="bgstore-password-field">
            <input id="password" name="password" type="password" autocomplete="new-password" required aria-invalid="${messagesPerField.existsError('password')?c}">
            <button class="bgstore-password-toggle" type="button" data-password-toggle="password" data-show-label="${msg("showPassword")}" data-hide-label="${msg("hidePassword")}" aria-controls="password" aria-label="${msg("showPassword")}" aria-pressed="false">
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
              <li class="bgstore-password-rule" data-password-rule="different" aria-checked="false">${msg("passwordRuleDifferent")}</li>
            </ul>
          </div>
          <#if messagesPerField.existsError('password')><p class="bgstore-field-error">${kcSanitize(messagesPerField.get('password'))?no_esc}</p></#if>
        </div>
      </div>
      <input id="password-confirm" name="password-confirm" type="hidden" value="${register.formData.password!''}">
      <button class="bgstore-submit" type="submit" value="${msg("doRegister")}">${msg("doRegister")}</button>
    </form>
  <#elseif section = "info">
    <p class="bgstore-auth-switch-copy">${msg("alreadyHaveAccount")}</p>
    <a class="bgstore-auth-switch" href="${url.loginUrl}">${msg("backToLogin")}</a>
  </#if>
</@layout.registrationLayout>
