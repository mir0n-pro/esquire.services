<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="robots" content="noindex, nofollow">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>${msg("loginTitle",(realm.displayName!''))}</title>
    <link rel="icon" href="${url.resourcesPath}/img/favicon.ico" />
    <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500&display=swap" rel="stylesheet">
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
</head>
<body>
    <div class="app-explorer">
        <!-- Top Toolbar -->
        <div class="mat-toolbar">
            <img style="width:48px; height:48px;" src="${url.resourcesPath}/img/main.ico" alt="Esquire Logo"/>
            <h1>Esquire Tree Explorer</h1>
            <div class="name-bar">
                <span>Disconnected</span>
                <img style="width: 24px; height:24px;" src="${url.resourcesPath}/img/unknown.ico" alt="User"/>
            </div>
        </div>

        <!-- Main Content Area (Pre-login state) -->
        <div class="prelogin-container">

            <!-- Login Form -->
            <div class="login-form-wrapper">
                <div class="esq-login-header">
                    <img src="${url.resourcesPath}/img/main.ico" alt=""/>
                    ${msg("loginAccountTitle")}
                </div>
                <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
                    <div class="esq-login-alert alert-${message.type}">
                        <span>${kcSanitize(message.summary)?no_esc}</span>
                    </div>
                </#if>

                <#nested>
            </div>
        </div>

        <!-- Bottom Toolbar -->
        <div class="mat-toolbar">
            <p class="info-text">Logging in...</p>
            <p class="right-aligned-text">Copyright&#169; 2001, 2026 mir0n&co</p>
        </div>
    </div>
</body>
</html>
</#macro>
