/*
 *  Esquire frameworks (tm)
 *  KeySmith service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/19/2026 mir0n  added esquireKeySave() POST /esq-key-save
 * 03/09/2026 mir0n  realm_access.roles extracted from JWT claims; roles passed to esquireKeySave()
 * 03/21/2026 mir0n  devLog added; log.debug→devLog.debug
 * 06/04/2026 mir0n  rootPath / uid no longer extracted from claims; delegates without them (roles
 *                   still extracted); uid / rootPath ride the unified request context
 * 08/26/2026 mir0n  the develop log lines pass the value itself instead of String.valueOf
 */

package pro.mir0n.esquire.keySmith.controller;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.dto.access.EsqAccessProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import pro.mir0n.esquire.common.EsqConstants;
import pro.mir0n.esquire.keySmith.service.IKeySmithService;

/**
 * @author mir0n
 */

@Tag(
        name = "Esquire entity REST API",
        description = "REST API to manage entities"
)

@Slf4j
@RestController
@RequestMapping(path="", produces = {MediaType.APPLICATION_JSON_VALUE})
@AllArgsConstructor
@Validated
@CrossOrigin(origins = "http://localhost:4200")
public class KeySmithController {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + KeySmithController.class.getName());

    private IKeySmithService iKeySmithService;

    @Operation(
            summary = "Esquire Entities REST API",
            description = "REST API to manage entities"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "HTTP Status OK",
                    content = @Content(schema = @Schema(oneOf = {
                            EsqAccessProfile.class
                    }))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "HTTP Status Internal Server Error",
                    content = @Content(
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })

    @PostMapping("/esq-key-save")
    public ResponseEntity<EsqAccessProfile> esquireKeySave(
           @Parameter(description = "User id")
           @RequestParam(name = "id", required = true) String id,
           @RequestBody Map<String, Object> fields,
           @AuthenticationPrincipal Claims claims
    ) {
        Map<String, Object> realmAccess = claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class);
        List<String> roles = realmAccess != null ? (List<String>) realmAccess.get(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES) : null;

        EsqAccessProfile ret = iKeySmithService.esquireKeySave(id, fields, roles);
        devLog.debug("esquireKeySave: id:{}, result:{}", id, ret);
        return ResponseEntity.status(HttpStatus.OK).body(ret);
    }

    @GetMapping("/esq-key")
    public ResponseEntity<EsqAccessProfile>  esquireCommand(
           @Parameter(description = "User id")
           @RequestParam(name = "id", required = false) String id,
           @AuthenticationPrincipal Claims claims
    ) {
        EsqAccessProfile profile = iKeySmithService.esquireKey(id);
        devLog.debug("esquireKey: id:{}, result:{}", id, profile);
        return ResponseEntity.status(HttpStatus.OK).body(profile);
    }

}
