<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true cardClass="bgstore-auth-card--verification"; section>
  <#if section = "header">
    <div class="bgstore-verification-illustration" aria-hidden="true">
      <svg viewBox="0 0 320 116" role="presentation">
        <g class="bgstore-mail-flight">
          <path class="bgstore-mail-speed-line bgstore-mail-speed-line--top" d="M100 42H62" />
          <path class="bgstore-mail-speed-line" d="M106 58H44" />
          <path class="bgstore-mail-speed-line bgstore-mail-speed-line--bottom" d="M98 74H58" />
          <g class="bgstore-mail-envelope">
            <rect x="120" y="32" width="76" height="52" rx="8" />
            <path d="m123 36 35 27 35-27" />
            <path d="m123 80 24-22m22 0 24 22" />
          </g>
        </g>
      </svg>
    </div>
    <h1 id="bgstore-page-title">${msg("emailVerifyTitle")}</h1>
    <p>${msg("emailVerifySubtitle")}</p>
  <#elseif section = "form">
    <p class="bgstore-verify-instruction">
      <#if verifyEmail??>
        ${msg("emailVerifyInstruction1",verifyEmail)}
      <#else>
        ${msg("emailVerifyInstruction4",user.email)}
      </#if>
    </p>
    <#if isAppInitiatedAction??>
      <form id="kc-verify-email-form" class="bgstore-form bgstore-verify-form" action="${url.loginAction}" method="post">
        <div id="kc-form-buttons" class="bgstore-verify-actions">
          <#if verifyEmail??>
            <button class="bgstore-submit" type="submit">${msg("emailVerifyResend")}</button>
          <#else>
            <button class="bgstore-submit" type="submit">${msg("emailVerifySend")}</button>
          </#if>
          <button class="bgstore-auth-switch bgstore-verify-cancel" type="submit" name="cancel-aia" value="true" formnovalidate>${msg("doCancel")}</button>
        </div>
      </form>
    </#if>
  <#elseif section = "info">
    <#if !isAppInitiatedAction??>
      <div class="bgstore-verify-resend">
        <p>${msg("emailVerifyInstruction2")}</p>
        <a href="${url.loginAction}">${msg("emailVerifyResend")}</a>
      </div>
    </#if>
  </#if>
</@layout.registrationLayout>
