/*
 *  Esquire frameworks (tm)
 *  Esquire Haubergeon (Gatling stress/load harness)
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *
 *  History:
 * 05/14/2026 mir0n  created: full OIDC code+PKCE handshake via BFF + KC -- GET /auth/login -> KC form -> KC update-password -> BFF callback -> /auth/me check
 * 10/13/2026 mir0n  v2.0.0 -- moved into services2/hauberk, the Esquire half of the harness: the
 *                   generic machinery is Armoury's armoury-hauberk, this project is the Esquire
 *                   routines over it
 */
package pro.mir0n.esquire.hauberk.chain;

import pro.mir0n.esquire.hauberk.config.EsqHauberkConfig;

import io.gatling.javaapi.core.ChainBuilder;


import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Drives an OIDC code+PKCE handshake from a pure-REST Gatling chain,
 * impersonating a browser. The BFF orchestrates the actual code exchange
 * server-side; this chain only follows the redirect chain that a browser
 * would naturally walk:
 *
 *   1. GET BFF /auth/login          -> 302 to KC /authorize
 *   2. GET KC /authorize            -> 200 HTML login form
 *   3. POST KC login form           -> 302 (to update-password OR BFF callback)
 *   4. (if update-password) GET     -> 200 update-password HTML form
 *   5. (if update-password) POST    -> 302 to BFF callback
 *   6. GET BFF /auth/callback       -> 302 to / (session cookie regenerated)
 *   7. GET BFF /auth/me             -> 200 {"authenticated":true,...}
 *
 * Session inputs:
 *   userEmail -- KC username (== loginId == email).
 *
 * Cookie handling: Gatling's built-in CookieJar tracks esq.sid (BFF) and
 * AUTH_SESSION_ID (KC) per host automatically.
 *
 * Race tolerance: kcMaster's KC user-create is async from ConnectUser's
 * URQ. Step 3 wraps in tryMax(3) with pause(1, 3) between attempts to
 * absorb cold-start latency without a fixed sleep.
 *
 * Password: first-login KC user comes with temp password "changeit" and
 * required-action UPDATE_PASSWORD. Step 5 sets the new password to
 * NEW_PASSWORD (a constant; we never need to log in twice as the same user).
 */
public final class LoginViaBff {

    private LoginViaBff() {}

    private static final String INITIAL_PASSWORD = "changeit";
    private static final String NEW_PASSWORD     = "HauberkPass!2026";

    public static final ChainBuilder chain =
        // 1. BFF kicks off the OIDC handshake.
        exec(http("GET BFF /auth/login")
            .get(EsqHauberkConfig.BFF_BASE + "/auth/login")
            .disableFollowRedirect()
            .check(status().is(302))
            .check(header("Location").saveAs("kcAuthUrl")))

        // 2. KC presents the login page.
        .exec(http("GET KC /authorize (login form)")
            .get("#{kcAuthUrl}")
            .disableFollowRedirect()
            .check(status().is(200))
            .check(regex("action=\"([^\"]+)\"").saveAs("kcLoginActionRaw")))
        .exec(session -> session.set("kcLoginAction",
                session.getString("kcLoginActionRaw").replace("&amp;", "&")))

        // 3. Submit credentials. Retry on KC sync race (kcMaster may not
        //    have finished creating the user yet -- KC returns the login
        //    page again with an error rather than a redirect).
        .tryMax(3).on(
            pause(java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(3))
            .exec(http("POST KC login")
                .post("#{kcLoginAction}")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .formParam("username", "#{userEmail}")
                .formParam("password", INITIAL_PASSWORD)
                .formParam("credentialId", "")
                .disableFollowRedirect()
                .check(status().is(302))
                .check(header("Location").saveAs("postLoginLocation")))
        )

        // 4-5. Handle the UPDATE_PASSWORD required action if KC redirected
        //      us to the required-action page (URL contains "login-actions").
        .doIf(session -> {
            String loc = session.getString("postLoginLocation");
            return loc != null && loc.contains("/login-actions/");
        }).then(
            exec(http("GET KC update-password page")
                .get("#{postLoginLocation}")
                .disableFollowRedirect()
                .check(status().is(200))
                .check(regex("action=\"([^\"]+)\"").saveAs("kcUpdActionRaw")))
            .exec(session -> session.set("kcUpdAction",
                    session.getString("kcUpdActionRaw").replace("&amp;", "&")))
            .exec(http("POST KC update-password")
                .post("#{kcUpdAction}")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .formParam("password-new", NEW_PASSWORD)
                .formParam("password-confirm", NEW_PASSWORD)
                .disableFollowRedirect()
                .check(status().is(302))
                .check(header("Location").saveAs("postLoginLocation")))
        )

        // 6. KC's final 302 lands on the BFF callback; BFF exchanges code
        //    for tokens server-side and sets a fresh esq.sid cookie.
        .exec(http("GET BFF /auth/callback")
            .get("#{postLoginLocation}")
            .disableFollowRedirect()
            .check(status().is(302)))

        // 7. Verify the session is good. saveAs + cross-check in a session
        //    lambda since Gatling's .is() doesn't substitute EL on the
        //    expected value -- compare in a follow-on exec instead.
        .exec(http("GET BFF /auth/me")
            .get(EsqHauberkConfig.BFF_BASE + "/auth/me")
            .check(status().is(200))
            .check(jsonPath("$.authenticated").is("true"))
            .check(jsonPath("$.username").saveAs("authMeUsername")))
        .exec(session -> {
            String expected = session.getString("userEmail");
            String actual   = session.getString("authMeUsername");
            if (!expected.equals(actual)) {
                return session.markAsFailed();
            }
            return session;
        })
        // 8. Drop BFF / KC cookies and referer before downstream admin ops.
        //    Gatling's CookieJar is host-scoped, so "localhost" cookies set
        //    by BFF (port 3000) and KC (port 8080) get attached to requests
        //    at gateway (port 7070); gateway rejects the resulting cookie+
        //    JWT combo on state-changing POSTs with 403. flushCookieJar()
        //    and flushSessionCookies() leave Gatling's internal session
        //    attrs (`gatling.http.cookies`, `gatling.http.referer`) intact,
        //    so we also remove them by name to fully reset HTTP state.
        .exec(flushHttpCache())
        .exec(flushSessionCookies())
        .exec(flushCookieJar())
        .exec(session -> session
                .remove("gatling.http.cookies")
                .remove("gatling.http.referer"));
}
