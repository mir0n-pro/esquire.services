<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true>
    <form id="kc-passwd-update-form" action="${url.loginAction}" method="post">
        <div class="esq-login-content">
            <div class="form-group">
                <label for="password-new" class="control-label">${msg("passwordNew")}</label>
                <input type="password" id="password-new" name="password-new"
                       class="form-control" autofocus autocomplete="new-password" />
            </div>
            <div class="form-group">
                <label for="password-confirm" class="control-label">${msg("passwordConfirm")}</label>
                <input type="password" id="password-confirm" name="password-confirm"
                       class="form-control" autocomplete="new-password" />
            </div>
        </div>
        <div class="esq-login-actions">
            <#if isAppInitiatedAction??>
                <button class="btn-login" type="submit" value="${msg("doSubmit")}">${msg("doSubmit")}</button>
                <button class="esq-forgot-link" type="submit" name="cancel-aia" value="true">${msg("doCancel")}</button>
            <#else>
                <button class="btn-login" type="submit" value="${msg("doSubmit")}">${msg("doSubmit")}</button>
            </#if>
        </div>
    </form>
</@layout.registrationLayout>
