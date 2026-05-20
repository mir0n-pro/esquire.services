/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/20/2026 mir0n  created: default IErrorListener -- logs (v1.2.5 Taijitu refactor Step 2).
 */
package pro.mir0n.esquire.bizTree.taijitu;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link IErrorListener}: logs the recovered fault to console (error)
 * and develop (error + stacktrace). The monad ships with one of these; a
 * caller can replace it via {@code IMonad.setErrorListener(...)}.
 */
@Slf4j
public final class LoggingErrorListener implements IErrorListener {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + LoggingErrorListener.class.getName());

    private final String monadName;

    public LoggingErrorListener(String monadName) {
        this.monadName = monadName;
    }

    @Override
    public void onError(String context, Throwable t) {
        log.error("monad[{}]: recovered worker error -- {}: {}", monadName, context, t.getMessage());
        devLog.error("monad[{}]: recovered worker error -- {}", monadName, context, t);
    }
}
