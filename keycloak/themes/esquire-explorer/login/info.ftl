<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false>
    <div id="kc-info" class="content-area">
        <div id="kc-info-message">
            <p class="instruction">${kcSanitize(msg("${messageHeader!''}","${message.summary}"))?no_esc}</p>
            <#if requiredActions??>
                <ul>
                    <#list requiredActions as reqActionAlias>
                        <li>${msg("requiredAction.${reqActionAlias}")}</li>
                    </#list>
                </ul>
            </#if>
            <#if skipLink??>
            <#else>
                <#if pageRedirectUri?has_content>
                    <p><a href="${pageRedirectUri}">${kcSanitize(msg("backToApplication"))?no_esc}</a></p>
                <#elseif actionUri?has_content>
                    <p><a href="${actionUri}">${kcSanitize(msg("proceedWithAction"))?no_esc}</a></p>
                <#elseif client.baseUrl?has_content>
                    <p><a href="${client.baseUrl}">${kcSanitize(msg("backToApplication"))?no_esc}</a></p>
                </#if>
            </#if>
        </div>
    </div>
</@layout.registrationLayout>
