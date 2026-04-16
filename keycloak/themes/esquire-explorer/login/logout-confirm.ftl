<#import "template.ftl" as layout>
<@layout.registrationLayout>
    <div id="kc-logout-confirm" class="content-area">
        <h3>${msg("logoutConfirmHeader")}</h3>
        <form class="form-actions" action="${url.logoutConfirmAction}" method="POST">
            <input type="hidden" name="${properties.kcActionParamName!}" value="${actionTokenInLoginUrl!}"/>
            <div class="esq-logout-buttons">
                <input class="btn-login"
                       name="confirmLogout" id="kc-logout-confirm-button" type="submit" value="${msg("doLogout")}"/>
                <a class="esq-forgot-link"
                   href="${url.loginUrl}">${msg("doDecline")}</a>
            </div>
        </form>
    </div>
</@layout.registrationLayout>
