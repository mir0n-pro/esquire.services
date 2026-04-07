<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true>
    <div class="esq-login-content">
        <p>${msg("errorTryRefreshingPage")}</p>
    </div>
    <#if !skipLink??>
    <div class="esq-login-actions">
        <#if client?? && client.baseUrl?has_content>
            <a class="btn-login" href="${client.baseUrl}">${kcSanitize(msg("backToApplication"))?no_esc}</a>
        </#if>
        <a class="esq-forgot-link" href="${url.loginUrl}">${kcSanitize(msg("backToLogin"))?no_esc}</a>
    </div>
    </#if>
</@layout.registrationLayout>
