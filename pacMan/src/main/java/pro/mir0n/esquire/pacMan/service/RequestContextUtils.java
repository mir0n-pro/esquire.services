/*
 *  Esquire frameworks (tm)
 *  PacMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/10/2026 mir0n rootPath and uid params added were required
 */

package pro.mir0n.esquire.pacMan.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pro.mir0n.esquire.common.EsqConstants;

public class RequestContextUtils {
    // ...
    public static String getCorrelationId() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) return null;
        String id = request.getHeader(EsqConstants.ESQ_CORRELATION_ID);
        return (id != null) ? id : request.getHeader(EsqConstants.X_CORRELATION_ID);
    }

    public static String getRequestId() {
        HttpServletRequest request = getCurrentRequest();
        return (request != null) ? request.getHeader(EsqConstants.X_REQUEST_ID) : null;
    }

    private static HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return (attr != null) ? attr.getRequest() : null;
    }
}