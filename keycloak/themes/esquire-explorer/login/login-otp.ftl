<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true>
    <form id="kc-otp-login-form" action="${url.loginAction}" method="post">
        <div class="esq-login-content">
            <#if otpLogin.userOtpCredentials?has_content && otpLogin.userOtpCredentials?size gt 1>
                <div class="form-group">
                    <#list otpLogin.userOtpCredentials as otpCredential>
                        <div>
                            <input id="kc-otp-credential-${otpCredential?index}" type="radio"
                                   name="selectedCredentialId" value="${otpCredential.id}"
                                   <#if otpCredential.id = otpLogin.selectedCredentialId>checked="checked"</#if>>
                            <label for="kc-otp-credential-${otpCredential?index}">
                                ${otpCredential.userLabel}
                            </label>
                        </div>
                    </#list>
                </div>
            </#if>
            <div class="form-group">
                <label for="otp" class="control-label">${msg("loginOtpOneTime")}</label>
                <input id="otp" name="otp" type="text" class="form-control"
                       autocomplete="off" autofocus inputmode="numeric" />
            </div>
        </div>
        <div class="esq-login-actions">
            <input class="btn-login" name="login" id="kc-login" type="submit" value="${msg("doLogIn")}"/>
        </div>
    </form>
</@layout.registrationLayout>
