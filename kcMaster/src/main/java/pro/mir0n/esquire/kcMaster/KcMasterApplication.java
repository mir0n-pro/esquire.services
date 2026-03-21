/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial scaffold
 */

package pro.mir0n.esquire.kcMaster;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@EnableAsync
@SpringBootApplication(scanBasePackages = {
        "pro.mir0n.esquire.kcMaster"
})
public class KcMasterApplication {

    public static void main(String[] args) {
        SpringApplication.run(KcMasterApplication.class, args);
    }
}
