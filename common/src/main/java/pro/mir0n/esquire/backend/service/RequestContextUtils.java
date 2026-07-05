/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/10/2026 mir0n  created: generalized from per-service implementations; getCorrelationId(), getRequestId()
 * 06/04/2026 mir0n  reads EsqContextHolder first (header fallback for crl/req); getUid() / getRootPath() /
 *                   getContext() added so services read uid/rootPath uniformly instead of via params
 * 07/02/2026 mir0n  requireRequestId(): X-Request-ID presence guard (null/blank -> MissingRequestIdException / 400)
 *                   for write commands
 */

package pro.mir0n.esquire.backend.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pro.mir0n.esquire.backend.error.MissingRequestIdException;
import pro.mir0n.esquire.common.EsqConstants;

public class RequestContextUtils {

    /** The unified per-request context bound to the current thread, or null if none. */
    public static EsqRequestContext getContext() {
        return EsqContextHolder.get();
    }

    public static String getCorrelationId() {
        EsqRequestContext ctx = EsqContextHolder.get();
        String ret = (ctx != null) ? ctx.correlationId() : null;
        if (ret == null) {
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                String id = request.getHeader(EsqConstants.ESQ_CORRELATION_ID);
                ret = (id != null) ? id : request.getHeader(EsqConstants.X_CORRELATION_ID);
            }
        }
        return ret;
    }

    public static String getRequestId() {
        EsqRequestContext ctx = EsqContextHolder.get();
        String ret = (ctx != null) ? ctx.requestId() : null;
        if (ret == null) {
            HttpServletRequest request = getCurrentRequest();
            ret = (request != null) ? request.getHeader(EsqConstants.X_REQUEST_ID) : null;
        }
        return ret;
    }

    /**
     * The client-supplied X-Request-ID, required on every write command. Presence-only: a null or
     * blank id means the request did not carry the client-controlled identity a write must have, and
     * the command is rejected with 400 (MissingRequestIdException). No uniqueness / dedup check.
     */
    public static String requireRequestId() {
        String ret = getRequestId();
        if (ret == null || ret.isBlank()) {
            throw new MissingRequestIdException();
        }
        return ret;
    }

    public static String getUid() {
        EsqRequestContext ctx = EsqContextHolder.get();
        return (ctx != null) ? ctx.uid() : null;
    }

    public static String getRootPath() {
        EsqRequestContext ctx = EsqContextHolder.get();
        return (ctx != null) ? ctx.rootPath() : null;
    }

    private static HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return (attr != null) ? attr.getRequest() : null;
    }
}
