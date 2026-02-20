/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/19/2026 mir0n  added esquireKeySave() POST /esq-key-save
 */

package pro.mir0n.esquire.keySmith.controller;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
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
        String rootPath = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class);
        String uid = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class);

        EsqAccessProfile ret = iKeySmithService.esquireKeySave(id, fields, rootPath, uid);
        log.debug("esquireKeySave: id:{}, rootPath:{}, result:{}", id, rootPath, String.valueOf(ret));
        return ResponseEntity.status(HttpStatus.OK).body(ret);
    }

    @GetMapping("/esq-key")
    public ResponseEntity<EsqAccessProfile>  esquireCommand(
           @Parameter(description = "User id")
           @RequestParam(name = "id", required = false) String id,
           @AuthenticationPrincipal Claims claims
    ) {
        String rootPath = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class);
        String uid = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class);

        EsqAccessProfile profile = iKeySmithService.esquireKey(id,rootPath,  uid);
        log.debug("esquireKey: id:{}, rootPath:{}, result:{}", id,rootPath, String.valueOf(profile));
        return ResponseEntity.status(HttpStatus.OK).body(profile);
    }

}
