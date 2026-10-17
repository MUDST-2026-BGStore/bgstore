<#import "template.ftl" as layout>
<#assign appBaseUrl = "">
<#if realm.attributes?? && realm.attributes.bgstoreAppUrl??>
  <#assign appBaseUrl = realm.attributes.bgstoreAppUrl>
<#elseif client??>
  <#assign appBaseUrl = client.baseUrl!"">
</#if>
<@layout.registrationLayout displayMessage=false; section>
    <#if section = "header">
        <#if messageHeader??>
            ${kcSanitize(msg("${messageHeader}"))?no_esc}
        <#else>
            ${message.summary}
        </#if>
    <#elseif section = "form">
    <div id="kc-info-message">
        <p class="instruction">${message.summary}<#if requiredActions??><#list requiredActions>: <b><#items as reqActionItem>${kcSanitize(msg("requiredAction.${reqActionItem}"))?no_esc}<#sep>, </#items></b></#list><#else></#if></p>
        <#if skipLink??>
        <#else>
            <#if pageRedirectUri?has_content>
                <p><a href="${pageRedirectUri}">${msg("backToApplication")}</a></p>
            <#elseif actionUri?has_content>
                <p><a href="${actionUri}">${msg("proceedWithAction")}</a></p>
            <#elseif appBaseUrl?has_content>
                <p><a href="${appBaseUrl}">${msg("backToApplication")}</a></p>
            </#if>
        </#if>
    </div>
    </#if>
</@layout.registrationLayout>
