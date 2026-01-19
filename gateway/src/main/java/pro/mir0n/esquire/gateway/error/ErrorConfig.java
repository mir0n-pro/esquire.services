/*
 *  Esquire frameworks (tm)
 *  Gateway service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 01/18/2026 mir0n  read serviceMetricsEnabled configuration
 *                   bypass it to exception handler
 */
package pro.mir0n.esquire.gateway.error;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;

@Configuration
public class ErrorConfig {
    @Value("${esquire.gateway.service-metrics.enabled:true}")
    private boolean serviceMetricsEnabled;

    @Bean
    @Order(-1) // High priority to override default handler
    public GatewayErrorWebExceptionHandler gatewayErrorWebExceptionHandler(
            ErrorAttributes errorAttributes,
            WebProperties webProperties,
            ApplicationContext applicationContext,
            ServerCodecConfigurer serverCodecConfigurer) {
        return new GatewayErrorWebExceptionHandler(errorAttributes,
            webProperties,
            applicationContext,
            serverCodecConfigurer,
            serviceMetricsEnabled
        );
    }
}