<#import "template.ftl" as layout>
<#assign appBaseUrl = "">
<#if realm.attributes?? && realm.attributes.bgstoreAppUrl??>
  <#assign appBaseUrl = realm.attributes.bgstoreAppUrl>
<#elseif client?? && client.baseUrl?has_content>
  <#assign appBaseUrl = client.baseUrl>
</#if>
<@layout.registrationLayout displayMessage=false cardClass="bgstore-auth-card--error"; section>
  <#if section = "header">
    <div class="bgstore-error-illustration" aria-hidden="true">
      <svg viewBox="0 0 160 116" role="presentation" fill="none" stroke="currentColor" stroke-width="2.25" stroke-linecap="round" stroke-linejoin="round">
        <circle class="bgstore-error-halo" cx="80" cy="58" r="43" />
        <g transform="rotate(-8 80 58)">
          <rect class="bgstore-error-window" x="46" y="29" width="68" height="58" rx="9" />
          <path class="bgstore-error-divider" d="M46 44h68" />
          <path class="bgstore-error-divider" d="M55 37h1m6 0h1m6 0h1" />
          <path d="M91 58a13 13 0 1 0 1 14M91 51v8h-8" />
        </g>
        <circle class="bgstore-error-badge" cx="113" cy="83" r="13" />
        <path d="M113 77v6m0 5h.01" />
      </svg>
    </div>
    <h1 id="bgstore-page-title">${msg("authErrorTitle")}</h1>
    <p>${msg("authErrorSubtitle")}</p>
  <#elseif section = "form">
    <div class="bgstore-error-content" role="alert">
      <p class="bgstore-error-description">${msg("authErrorDescription")}</p>
      <div class="bgstore-error-actions">
        <#if appBaseUrl?has_content>
          <a class="bgstore-submit" href="${appBaseUrl?remove_ending('/')}/auth/sign-in">${msg("authErrorBackToSignIn")}</a>
        </#if>
        <#if appBaseUrl?has_content>
          <a class="bgstore-auth-switch" href="${appBaseUrl?remove_ending('/')}">${msg("authErrorBackToApp")}</a>
        </#if>
      </div>
    </div>
  </#if>
</@layout.registrationLayout>
