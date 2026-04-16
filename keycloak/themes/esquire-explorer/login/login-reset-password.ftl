<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username')>
    <form id="kc-reset-password-form" action="${url.loginAction}" method="post">
        <div class="esq-login-content">
            <p class="instruction">
                <#if !realm.loginWithEmailAllowed>${msg("username")}
                <#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}
                <#else>${msg("email")}</#if>
            </p>
            <div class="form-group">
                <label for="username" class="control-label">
                    <#if !realm.loginWithEmailAllowed>${msg("username")}
                    <#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}
                    <#else>${msg("email")}</#if>
                </label>
                <input type="text" id="username" name="username" class="form-control" autofocus
                       value="${(auth.attemptedUsername!'')}"
                       aria-invalid="<#if messagesPerField.existsError('username')>true</#if>" dir="ltr"/>
                <#if messagesPerField.existsError('username')>
                    <span class="esq-field-error" aria-live="polite">
                        ${kcSanitize(messagesPerField.get('username'))?no_esc}
                    </span>
                </#if>
            </div>
            <#if realm.duplicateEmailsAllowed>
                <p class="instruction">${msg("emailInstructionUsername")}</p>
            <#else>
                <p class="instruction">${msg("emailInstruction")}</p>
            </#if>
        </div>

        <div class="esq-login-actions">
            <input class="btn-login" type="submit" value="${msg("doSubmit")}"/>
            <a class="esq-forgot-link" href="${url.loginUrl}">${kcSanitize(msg("backToLogin"))?no_esc}</a>
        </div>
    </form>
</@layout.registrationLayout>
