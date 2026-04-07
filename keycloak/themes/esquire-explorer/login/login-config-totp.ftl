<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true>
    <div class="esq-login-content">
        <ol>
            <li>
                <p>${msg("totpStep1")}</p>
                <ul>
                    <#list totp.supportedApplications as app>
                        <li>${app}</li>
                    </#list>
                </ul>
            </li>
            <li>
                <p>${msg("totpStep2")}</p>
                <#if mode?? && mode = "manual">
                    <p><span id="kc-totp-secret-key">${totp.totpSecretEncoded}</span></p>
                    <p><a href="${totp.qrUrl}" id="mode-barcode">${msg("totpScanBarcode")}</a></p>
                <#else>
                    <img id="kc-totp-secret-qr-code" src="data:image/png;base64, ${totp.totpSecretQrCode}" alt="QR Code" style="display:block; margin:8px auto;"/>
                    <p><a href="${totp.manualUrl}" id="mode-manual">${msg("totpUnableToScan")}</a></p>
                </#if>
            </li>
            <li>
                <p>${msg("totpStep3")}</p>
            </li>
        </ol>
    </div>

    <form action="${url.loginAction}" method="post">
        <input type="hidden" name="totpSecret" value="${totp.totpSecret}"/>
        <div class="esq-login-content">
            <div class="form-group">
                <label for="totp" class="control-label">${msg("authenticatorCode")}</label>
                <input type="text" id="totp" name="totp" class="form-control"
                       autocomplete="off" inputmode="numeric" />
            </div>
            <div class="form-group">
                <label for="userLabel" class="control-label">${msg("totpDeviceName")}</label>
                <input type="text" id="userLabel" name="userLabel" class="form-control"
                       autocomplete="off" />
            </div>
        </div>
        <div class="esq-login-actions">
            <button class="btn-login" type="submit">${msg("doSubmit")}</button>
            <#if isAppInitiatedAction??>
                <button class="esq-forgot-link" type="submit" name="cancel-aia" value="true">${msg("doCancel")}</button>
            </#if>
        </div>
    </form>
</@layout.registrationLayout>
