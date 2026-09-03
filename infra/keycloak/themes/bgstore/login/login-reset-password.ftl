<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>
  <#if section = "header">
    <div class="bgstore-auth-icon" aria-hidden="true">?</div>
    <h1 id="bgstore-page-title">${msg("emailForgotTitle")}</h1>
    <p>${msg("emailForgotSubtitle")}</p>
  <#elseif section = "form">
    <form id="kc-reset-password-form" class="bgstore-form" action="${url.loginAction}" method="post">
      <div class="bgstore-field">
        <label for="username">${msg("usernameOrEmail")}</label>
        <input id="username" name="username" value="${auth.attemptedUsername!''}" type="text" autofocus autocomplete="username">
      </div>
      <button class="bgstore-submit" type="submit">${msg("doSubmit")}</button>
    </form>
  <#elseif section = "info">
    <p><a href="${url.loginUrl}">${msg("backToLogin")}</a></p>
  </#if>
</@layout.registrationLayout>
