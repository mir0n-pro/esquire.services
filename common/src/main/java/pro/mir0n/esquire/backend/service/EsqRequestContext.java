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
 */

package pro.mir0n.esquire.backend.service;

/**
 * Immutable carrier of the four values that describe "who is asking, on whose behalf, under
 * which correlation" for a single user request:
 *
 *   correlationId / requestId — request tracing (origin: request headers).
 *   uid / rootPath            — caller identity and scope (origin: JWT claims).
 *
 * All four share one origin class: the inbound user request. They are captured once (on the
 * request thread, in JwtAuthenticationFilter) and re-established on worker threads that process
 * a queued request, so services and the audit pod read them uniformly via EsqContextHolder
 * regardless of which thread they run on.
 */
public record EsqRequestContext(String correlationId, String requestId, String uid, String rootPath) {
}
