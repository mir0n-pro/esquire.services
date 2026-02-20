/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/26/2025 mir0n  refine API doc
 * 12/27/2025 mir0n  added entity implementations 
 * 12/28/2025 mir0n logging added using Slf4j
 * 01/10/2026 mir0n added processing of user claims
 * 01/18/2026 mir0n BizTreeConstants moved to common package
 *                  ErrorResponse replaced with ProblemDetail
 * 01/23/206 mir0n  use common library
 *                  no more EsqTreeNode methods  
 * 02/12/2026 mir0n added "/esq-kinds" access point
 * 02/19/2026 mir0n added esquireCommandSave() POST /esq-cmd-save
 */

package pro.mir0n.esquire.enyMan.controller;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.dto.entity.*;
import pro.mir0n.esquire.backend.storage.EsqObjectKindStorage;
import pro.mir0n.esquire.enyMan.service.IEnyManService;
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
public class EnyManController {
    private IEnyManService iEnyManService;

    @Operation(
            summary = "Esquire Entities REST API",
            description = "REST API to manage entities"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "HTTP Status OK",
                    content = @Content(schema = @Schema(oneOf = {
                              EsqEntityLayer.class
                            , EsqEntity.class
                            , EsqAcct.class
                            , EsqOrg.class
                            , EsqUsr.class
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

    @GetMapping("/esq-dict")
    public ResponseEntity<List<EsqEntityLayer>>  esquireDictionary(
            @Parameter(description = "Entity kind code")
            @RequestParam(name = "kind", required = true) Integer kind,
            @AuthenticationPrincipal Claims claims
    ) {
        List<EsqEntityLayer> layers = iEnyManService.esquireDictionary(kind);
        log.debug("esquireDictionary: kind:{}, result:{}, claims:{}", kind, String.valueOf(layers), String.valueOf(claims));
        return ResponseEntity.status(HttpStatus.OK).body(layers);
    }

    @GetMapping("/esq-cmd")
    public ResponseEntity<EsqEntity>  esquireCommand(
           @Parameter(description = "Entity kind code")
           @RequestParam(name = "kind", required = true) Integer kind,
           @Parameter(description = "Entity id")
           @RequestParam(name = "id", required = true) String id,
           @Parameter(description = "Command code: 'details' only for now")
           @RequestParam(name = "cmd", required = false, defaultValue = "details") String cmd,
           @AuthenticationPrincipal Claims claims
    ) {
        String rootPath = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class);
        String uid = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class);

        EsqEntity entity = iEnyManService.esquireCommand(kind, id, cmd, rootPath,  uid);
        log.debug("esquireCommand: kind:{}, id:{}, cmd:{}, rootPath:{}, result:{}", kind, id, cmd, rootPath, String.valueOf(entity));
        return ResponseEntity.status(HttpStatus.OK).body(entity);
    }

    @PostMapping("/esq-cmd-save")
    public ResponseEntity<EsqEntity> esquireCommandSave(
           @Parameter(description = "Entity kind code")
           @RequestParam(name = "kind", required = true) Integer kind,
           @Parameter(description = "Entity id")
           @RequestParam(name = "id", required = true) String id,
           @Parameter(description = "Command code")
           @RequestParam(name = "cmd", required = false, defaultValue = "save") String cmd,
           @RequestBody Map<String, Object> fields,
           @AuthenticationPrincipal Claims claims
    ) {
        String rootPath = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class);
        String uid = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class);

        EsqEntity ret = iEnyManService.esquireCommandSave(kind, id, cmd, fields, rootPath, uid);
        log.debug("esquireCommandSave: kind:{}, id:{}, cmd:{}, rootPath:{}, result:{}", kind, id, cmd, rootPath, String.valueOf(ret));
        return ResponseEntity.status(HttpStatus.OK).body(ret);
    }

    @GetMapping("/esq-kinds")
    public ResponseEntity<List<EsqObjectKind>>  esquireKinds(
            @AuthenticationPrincipal Claims claims
    ) {
        //String rootPath = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class);
        //String uid = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class);

        List<EsqObjectKind> ret  = EsqObjectKindStorage.getInstance().getAll();
        log.debug("esquireKinds: result:{}",String.valueOf(ret));
        return ResponseEntity.status(HttpStatus.OK).body(ret);
    }
}
