/*
 *  Esquire frameworks (tm)
 *  Gateway
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 11/17/2026 mir0n  created (v2.0.2): pure OAuth (lab) -- fetch(loginId): the claims of a login id
 * 11/22/2026 mir0n  v2.0.3 -- moved from lab.pureoauth to oauth
 */

package pro.mir0n.esquire.gateway.oauth;

import pro.mir0n.esquire.backend.dto.access.EsqUserClaims;
import reactor.core.publisher.Mono;

public interface IUserClaimsClient {

    Mono<EsqUserClaims> fetch(String loginId);
}
