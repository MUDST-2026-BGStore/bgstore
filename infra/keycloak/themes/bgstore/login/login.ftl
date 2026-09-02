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
          <input id="password" name="password" type="password" autocomplete="current-password" aria-invalid="${messagesPerField.existsError('password')?c}">
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
    <p>${msg("noAccount")} <a href="${url.registrationUrl}">${msg("doRegister")}</a></p>
  </#if>
</@layout.registrationLayout>
