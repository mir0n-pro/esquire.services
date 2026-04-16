<#import "template.ftl" as layout>
<@layout.registrationLayout>
    <div class="esq-login-content">
        <p class="instruction">
            ${msg("pageExpiredMsg1")} <a id="loginRestartLink" href="${url.loginRestartFlowUrl}">${msg("doClickHere")}</a>.
            <br/>
            ${msg("pageExpiredMsg2")} <a id="loginContinueLink" href="${url.loginAction}">${msg("doClickHere")}</a>.
        </p>
    </div>
</@layout.registrationLayout>
