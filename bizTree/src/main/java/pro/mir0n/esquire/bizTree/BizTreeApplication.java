/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/28/2025 mir0n logging added using Slf4j
 * 01/23/2026 miron no needs for BizTreeApplicationStartingListener
 * 03/10/2026 mir0n  scanBasePackages: backend.service, backend.security, backend.exception added
 */

package pro.mir0n.esquire.bizTree;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Slf4j
@SpringBootApplication(scanBasePackages = {
        "pro.mir0n.esquire.bizTree",
        "pro.mir0n.esquire.backend.service",
        "pro.mir0n.esquire.backend.security",
        "pro.mir0n.esquire.backend.exception"
})
@EntityScan(basePackages = "pro.mir0n.esquire.backend.jpa")
@EnableJpaRepositories(basePackages = "pro.mir0n.esquire.bizTree.jpa")

public class BizTreeApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication( BizTreeApplication.class);
        // Register the listener with the SpringApplication instance
        app.run(args);
    }



}
