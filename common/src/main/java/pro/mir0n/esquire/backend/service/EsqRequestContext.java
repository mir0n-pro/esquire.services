/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/04/2026 mir0n  created: unified per-request context (correlationId, requestId, uid, rootPath).
 *                   One object captured once per user request and re-hydratable on any thread,
 *                   replacing the split where crl/req were read from headers and uid/rootPath were
 *                   threaded as method params. Read uniformly via RequestContextUtils / EsqContextHolder.
 * 10/19/2026 mir0n  v2.0.1 -- roles (List<String>) added: the caller's realm roles, beside uid and rootPath
 */

package pro.mir0n.esquire.backend.service;

import java.util.List;

/**
 * Immutable carrier of the values that describe "who is asking, on whose behalf, with which rights, under
 * which correlation" for a single user request:
 *
 *   correlationId / requestId -- request tracing (origin: request headers).
 *   uid / rootPath / roles    -- caller identity, scope and roles (origin: the caller's claims).
 *
 * All of them share one origin class: the inbound user request. They are captured once (on the
 * request thread, in JwtClaimsExtractionFilter) and re-established on worker threads that process
 * a queued request, so services and the audit pod read them uniformly via EsqContextHolder
 * regardless of which thread they run on.
 */
public record EsqRequestContext(String correlationId, String requestId, String uid, String rootPath,
                                List<String> roles) {
}
