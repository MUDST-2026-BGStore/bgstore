<#macro registrationLayout displayInfo=false displayMessage=true displayRequiredFields=false>
<!doctype html>
<html lang="${locale.currentLanguageTag!}">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${msg("loginTitle", realm.displayName!"BGStore")}</title>
    <link rel="stylesheet" href="${url.resourcesPath}/css/bgstore.css">
  </head>
  <body>
    <main class="bgstore-auth-shell">
      <header class="bgstore-auth-header">
        <span class="bgstore-brand" aria-label="BGStore">
          <span class="bgstore-brand-mark" aria-hidden="true">BG</span>
          <span>BGStore</span>
        </span>
        <#if realm.internationalizationEnabled && locale.supported?size gt 1>
          <nav class="bgstore-locale" aria-label="${msg("locales")}">
            <#list locale.supported as language>
              <a class="<#if language.label == locale.current!>is-active</#if>" href="${language.url}">${language.label}</a>
            </#list>
          </nav>
        </#if>
      </header>

      <section class="bgstore-auth-card" aria-labelledby="bgstore-page-title">
        <#nested "header">

        <#if displayMessage && message?has_content>
          <div class="bgstore-alert bgstore-alert--${message.type}" role="alert">
            ${kcSanitize(message.summary)?no_esc}
          </div>
        </#if>

        <#nested "form">

        <#if displayInfo>
          <footer class="bgstore-auth-footer">
            <#nested "info">
          </footer>
        </#if>
      </section>
    </main>
  </body>
</html>
</#macro>
