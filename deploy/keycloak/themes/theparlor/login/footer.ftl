<#macro content>
  <#if pageId == "register">
    <div class="parlor-account-prompt">
      <span>${msg("parlorHaveAccount")}</span>
      <a href="${url.loginUrl}">${msg("parlorSignIn")}</a>
    </div>
  </#if>
  <div class="parlor-return">
    <a href="${properties.parlorHomeUrl}">
      <span aria-hidden="true">←</span> ${msg("parlorBackToStore")}
    </a>
  </div>
</#macro>
