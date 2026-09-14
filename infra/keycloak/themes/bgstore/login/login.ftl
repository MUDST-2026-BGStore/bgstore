<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled?? displayMessage=!messagesPerField.existsError('username', 'password'); section>
  <#if section = "header">
    <div class="bgstore-auth-icon" aria-hidden="true">⌁</div>
    <h1 id="bgstore-page-title">${msg("doLogIn")}</h1>
    <p>${msg("loginSubtitle")}</p>
  <#elseif section = "form">
    <#if realm.password>
      <form id="kc-form-login" class="bgstore-form" action="${url.loginAction}" method="post">
        <div class="bgstore-field">
          <label for="username">${msg("username")}</label>
          <input id="username" name="username" value="${login.username!''}" type="text" autofocus autocomplete="username" aria-invalid="${messagesPerField.existsError('username')?c}">
          <#if messagesPerField.existsError('username')>
            <p class="bgstore-field-error">${kcSanitize(messagesPerField.get('username'))?no_esc}</p>
          </#if>
        </div>
        <div class="bgstore-field">
          <label for="password">${msg("password")}</label>
          <div class="bgstore-password-field">
            <input id="password" name="password" type="password" autocomplete="current-password" aria-invalid="${messagesPerField.existsError('password')?c}">
            <button class="bgstore-password-toggle" type="button" data-password-toggle="password" data-show-label="${msg("showPassword")}" data-hide-label="${msg("hidePassword")}" aria-controls="password" aria-label="${msg("showPassword")}" aria-pressed="false">
              <svg class="bgstore-password-icon bgstore-password-icon--show" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"/><circle cx="12" cy="12" r="3"/></svg>
              <svg class="bgstore-password-icon bgstore-password-icon--hide" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10.733 5.076a10.744 10.744 0 0 1 9.205 6.327 1 1 0 0 1 0 .695 10.75 10.75 0 0 1-1.36 2.21"/><path d="M14.084 14.084a3 3 0 0 1-4.168-4.168"/><path d="M17.479 17.479A10.75 10.75 0 0 1 12 19.75a10.75 10.75 0 0 1-9.938-7.054 1 1 0 0 1 0-.696A10.75 10.75 0 0 1 5.6 6.2"/><path d="m2 2 20 20"/></svg>
            </button>
          </div>
          <#if messagesPerField.existsError('password')>
            <p class="bgstore-field-error">${kcSanitize(messagesPerField.get('password'))?no_esc}</p>
          </#if>
        </div>
        <div class="bgstore-form-options">
          <#if realm.rememberMe && !usernameHidden??>
            <label class="bgstore-check" for="rememberMe">
              <input id="rememberMe" name="rememberMe" type="checkbox" <#if login.rememberMe??>checked</#if>>
              <span>${msg("rememberMe")}</span>
            </label>
          </#if>
          <#if realm.resetPasswordAllowed>
            <a href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a>
          </#if>
        </div>
        <button class="bgstore-submit" type="submit" name="login" id="kc-login">${msg("doLogIn")}</button>
      </form>
    </#if>
  <#elseif section = "info">
    <p class="bgstore-auth-switch-copy">${msg("noAccount")}</p>
    <a class="bgstore-auth-switch" href="${url.registrationUrl}">${msg("doRegister")}</a>
  </#if>
</@layout.registrationLayout>
