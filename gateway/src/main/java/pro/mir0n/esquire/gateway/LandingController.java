/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/14/2026 mir0n  created: GET / -- HTML landing page for api.esquire.mir0n.pro
 *                   (public REST API host). Replaces stock Spring 404 ProblemDetail
 *                   so visitors hitting the root URL get a polite explanation of
 *                   what this host is, with pointers to the GUI host and docs.
 */
package pro.mir0n.esquire.gateway;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the root landing page on the public REST API host
 * (today: https://api.esquire.mir0n.pro/).
 *
 * The host is named in user-facing documentation (README, the GUI host's
 * landing page Architecture tab); visitors who follow that reference arrive
 * at GET /. Without this controller they'd see Spring's default 404
 * ProblemDetail JSON which is hostile to anyone not already integrating.
 *
 * Wiring:
 *   - SecurityConfig has .anyExchange().permitAll() as the fallback, so GET /
 *     is unauthenticated by default. No SecurityConfig change required.
 *   - The CredentialBound + PhantomToken filters only act when an Authorization
 *     header is present, so a no-auth visit to / sails through untouched.
 *
 * Out of scope: /index.html, /favicon.ico, any other root-asset path. They
 * remain 404 -- callers don't need those at an API host.
 */
@RestController
public class LandingController {

    private static final String LANDING_HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1"/>
              <link rel="icon" href="data:,"/>
              <title>Esquire REST API</title>
              <style>
                html, body { margin: 0; padding: 0; height: 100%; background: #ffffff; }
                body {
                  display: flex;
                  align-items: center;
                  justify-content: center;
                }
                img {
                  width: 50%;
                  max-width: 500px;
                  height: auto;
                  display: block;
                }
              </style>
            </head>
            <body>
              <img src="https://esquire.mir0n.pro/img/og-banner.png"
                   alt="Esquire Frameworks"/>
            </body>
            </html>
            """;

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String landing() {
        return LANDING_HTML;
    }
}