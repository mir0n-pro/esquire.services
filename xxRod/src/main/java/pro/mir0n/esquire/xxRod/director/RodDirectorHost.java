/*
 *  Esquire frameworks (tm)
 *  xxRod service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 06/06/2026 mir0n  created: the generic xRod director lifecycle. Director-agnostic: it takes whichever
 *                   IRodDirector the config selected (xxrod.director.type -> one gated bean), calls init()
 *                   once at startup so the director reads its own properties and wires its sink, and
 *                   shutdown() at stop. The host knows nothing about audit / replication / doc-DB specifics.
 */
package pro.mir0n.esquire.xxRod.director;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class RodDirectorHost {

    private static final Logger devLog = LoggerFactory.getLogger("develop." + RodDirectorHost.class.getName());

    private final IRodDirector director;
    private final Environment  env;

    public RodDirectorHost(IRodDirector director, Environment env) {
        this.director = director;
        this.env      = env;
    }

    @PostConstruct
    public void start() {
        devLog.info("xRod host: director type={}", director.type());
        director.init(env);
    }

    @PreDestroy
    public void stop() {
        director.shutdown();
    }
}
