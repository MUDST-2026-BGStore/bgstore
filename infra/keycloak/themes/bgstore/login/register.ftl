<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>
  <#if section = "header">
    <div class="bgstore-auth-icon" aria-hidden="true">+</div>
    <h1 id="bgstore-page-title">${msg("registerTitle")}</h1>
    <p>${msg("registerSubtitle")}</p>
  <#elseif section = "form">
    <form id="kc-register-form" class="bgstore-form" action="${url.registrationAction}" method="post">
      <div class="bgstore-form-grid">
        <div class="bgstore-field">
          <label for="username">${msg("username")}</label>
          <input id="username" name="username" value="${register.formData.username!''}" type="text" autocomplete="username" autofocus aria-invalid="${messagesPerField.existsError('username')?c}">
          <#if messagesPerField.existsError('username')><p class="bgstore-field-error">${kcSanitize(messagesPerField.get('username'))?no_esc}</p></#if>
        </div>
        <div class="bgstore-field">
          <label for="firstName">${msg("firstName")}</label>
          <input id="firstName" name="firstName" value="${register.formData.firstName!''}" type="text" autocomplete="given-name" aria-invalid="${messagesPerField.existsError('firstName')?c}">
          <#if messagesPerField.existsError('firstName')><p class="bgstore-field-error">${kcSanitize(messagesPerField.get('firstName'))?no_esc}</p></#if>
        </div>
        <div class="bgstore-field">
          <label for="email">${msg("email")}</label>
          <input id="email" name="email" value="${register.formData.email!''}" type="email" autocomplete="email" aria-invalid="${messagesPerField.existsError('email')?c}">
          <#if messagesPerField.existsError('email')><p class="bgstore-field-error">${kcSanitize(messagesPerField.get('email'))?no_esc}</p></#if>
        </div>
        <div class="bgstore-field">
          <label for="lastName">${msg("lastName")}</label>
          <input id="lastName" name="lastName" value="${register.formData.lastName!''}" type="text" autocomplete="family-name" aria-invalid="${messagesPerField.existsError('lastName')?c}">
          <#if messagesPerField.existsError('lastName')><p class="bgstore-field-error">${kcSanitize(messagesPerField.get('lastName'))?no_esc}</p></#if>
        </div>
        <div class="bgstore-field">
          <label for="password">${msg("password")}</label>
          <input id="password" name="password" type="password" autocomplete="new-password" aria-invalid="${messagesPerField.existsError('password')?c}">
          <#if messagesPerField.existsError('password')><p class="bgstore-field-error">${kcSanitize(messagesPerField.get('password'))?no_esc}</p></#if>
        </div>
        <div class="bgstore-field">
          <label for="password-confirm">${msg("passwordConfirm")}</label>
          <input id="password-confirm" name="password-confirm" type="password" autocomplete="new-password" aria-invalid="${messagesPerField.existsError('password-confirm')?c}">
          <#if messagesPerField.existsError('password-confirm')><p class="bgstore-field-error">${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}</p></#if>
        </div>
      </div>
      <button class="bgstore-submit" type="submit" value="${msg("doRegister")}">${msg("doRegister")}</button>
    </form>
  <#elseif section = "info">
    <p>${msg("alreadyHaveAccount")} <a href="${url.loginUrl}">${msg("backToLogin")}</a></p>
  </#if>
</@layout.registrationLayout>
