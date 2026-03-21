/*
 *  Esquire frameworks (tm)
 *  kcMaster service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/20/2026 mir0n  initial — URQ Text payload POJO
 */

package pro.mir0n.esquire.kcMaster.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * Deserialized body of a URQ message Text property.
 * Self-identifying: always carries id and kind.
 */
@Getter
@Setter
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class KcSyncRequest {
    private String id;
    private int kind;
    private String loginId;
    private String newLoginId;
    private String email;
    private String pwdChangeForced;
    private String tfaMethod;
    private String connectFlg;
    private String path;
    private List<String> roles;
}
