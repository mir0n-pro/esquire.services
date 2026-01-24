/*
 *  Esquire frameworks (tm)
 *  PAckMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/28/2025 mir0n logging added using Slf4j
 * 01/23/2026 mir0n explicitly EntityScan, EnableJpaRepositories
 */

package pro.mir0n.esquire.pacMan;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
@EntityScan(basePackages = "pro.mir0n.esquire.backend.jpa")
@EnableJpaRepositories(basePackages = "pro.mir0n.esquire.pacMan.jpa")
public class PacManApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication( PacManApplication.class);
        app.run(args);
    }

}
