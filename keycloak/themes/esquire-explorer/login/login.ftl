<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true>
    <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
        <div class="esq-login-content">
            <div class="form-group">
                <label for="username" class="control-label">
                    <#if !realm.loginWithEmailAllowed>${msg("username")}
                    <#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}
                    <#else>${msg("email")}</#if>
                </label>
                <input tabindex="1" id="username" class="form-control" name="username"
                       value="${(login.username!'')}" type="text" autofocus autocomplete="username" />
            </div>

            <div class="form-group">
                <label for="password" class="control-label">${msg("password")}</label>
                <input tabindex="2" id="password" class="form-control" name="password"
                       type="password" autocomplete="current-password" />
            </div>

            <#if realm.rememberMe && !usernameHidden??>
                <div class="form-group">
                    <span></span>
                    <label class="checkbox-label">
                        <input tabindex="3" id="rememberMe" name="rememberMe" type="checkbox"
                            <#if login.rememberMe??>checked</#if>> ${msg("rememberMe")}
                    </label>
                </div>
            </#if>
        </div>

        <div class="esq-login-actions">
            <button tabindex="4" class="btn-login" name="login" id="kc-login" type="submit">${msg("doLogIn")}</button>
            <#if realm.resetPasswordAllowed>
                <a tabindex="5" class="esq-forgot-link" href="${url.loginResetCredentialsUrl}">${msg("doForgotPassword")}</a>
            </#if>
            <#-- Escape hatch: return to the application without signing in. The target is the
                 app origin, taken from this flow's redirect_uri (this client has no fixed
                 baseUrl and serves many origins). Stashed in sessionStorage so it survives a
                 wrong-password re-render (where the URL no longer carries redirect_uri). Hidden
                 until the origin is known, so it never becomes a misleading dead link. -->
            <a tabindex="6" id="esq-cancel" class="esq-forgot-link" href="#" style="display:none">${msg("doCancel")}</a>
        </div>
    </form>

    <script>
      (function () {
        try {
          var params = new URLSearchParams(window.location.search);
          var ru = params.get('redirect_uri');
          if (ru) {
            try { sessionStorage.setItem('esqAppOrigin', new URL(ru).origin); } catch (e) {}
          }
          var origin = null;
          try { origin = sessionStorage.getItem('esqAppOrigin'); } catch (e) {}
          var link = document.getElementById('esq-cancel');
          if (link && origin) {
            link.setAttribute('href', origin + '/');
            link.style.display = '';
          }
        } catch (e) {}
      })();
    </script>
</@layout.registrationLayout>
