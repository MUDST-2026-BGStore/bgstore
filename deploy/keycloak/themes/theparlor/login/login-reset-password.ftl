<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<#-- Keep Keycloak's field validation, action URL and session handling. -->
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username'); section>
  <#if section = "header">
    ${msg("emailForgotTitle")}
  <#elseif section = "form">
    <p id="parlor-reset-description" class="parlor-reset-description">
      <#if realm.duplicateEmailsAllowed>
        ${msg("parlorResetUsernameInstructions")}
      <#else>
        ${msg("parlorResetInstructions")}
      </#if>
    </p>
    <form id="kc-reset-password-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post" aria-describedby="parlor-reset-description">
      <#assign label>
        <#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>
      </#assign>
      <@field.input name="username" label=label value=auth.attemptedUsername!'' autofocus=true />
      <@buttons.actionGroup>
        <@buttons.button id="kc-form-buttons" label="parlorSendResetLink" />
      </@buttons.actionGroup>
      <div class="parlor-secondary-action">
        <a href="${url.loginUrl}">${msg("backToLogin")}</a>
      </div>
    </form>
  </#if>
</@layout.registrationLayout>
