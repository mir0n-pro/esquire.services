/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/26/2025 mir0n  refine API doc
 * 12/27/2025 mir0n  added entity implementations 
 * 12/28/2025 mir0n logging added using Slf4j
 * 01/10/2026 mir0n added processing of user claims
 * 01/18/2026 mir0n BizTreeConstants moved to common package
 *                  ErrorResponse replaced with ProblemDetail
 * 01/23/2026 mir0n  use common library
 *                  no more EsqTreeNode methods  
 * 02/12/2026 mir0n added "/esq-kinds" access point
 * 02/19/2026 mir0n added esquireCommandSave() POST /esq-cmd-save
 * 03/09/2026 mir0n  realm_access.roles extracted from JWT claims; roles passed to esquireCommandSave()
 * 03/21/2026 mir0n  devLog added; log.debug→devLog.debug
 * 03/26/2026 mir0n  POST /esq-new → esquireCommandNew(); POST /esq-del → esquireCommandDelete()
 * 03/31/2026 mir0n  POST /esq-move → esquireCommandMove()
 * 04/07/2026 mir0n  /esq-new→/esq-cmd-new, /esq-del→/esq-cmd-del
 * 05/14/2026 mir0n  GET /esq-cmd-tree added: FK-based natural-tree traversal from seed entity;
 *                   leaves-first ordering; same EsqTreeNode shape as /esq for response compatibility
 * 06/02/2026 mir0n  esquireCommandMove(): /esq-move returns 202 Accepted (ResponseEntity.accepted())
 *                   -- move is queued and processed async on the worker; OpenAPI response 200 -> 202
 * 06/04/2026 mir0n  rootPath / uid no longer extracted from claims or passed to the service -- they ride
 *                   the unified request context; only roles is still read from realm_access
 * 07/11/2026 mir0n  v1.2.11 O1/T8 -- esquireDictionary() counts esq.biz.dict.lookup.total (tag kind). Not a
 *                   duplicate of http.server.requests: that is tagged by URI TEMPLATE (/esq-dict) and the kind is
 *                   a query param, so the free meter can say the endpoint is busy but never WHICH dictionary
 */

package pro.mir0n.esquire.enyMan.controller;

import pro.mir0n.esquire.backend.o11y.EsqBizMeters;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
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

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + EnyManController.class.getName());

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
        // esq.biz.dict.lookup.total (O1/T8 phase B). Not a duplicate of the free http.server.requests: that meter
        // is tagged by the URI TEMPLATE (/esq-dict), so it cannot tell WHICH dictionary was fetched -- kind is a
        // query param. The kind code is a small fixed set, so the tag stays bounded.
        EsqBizMeters.count("esq.biz.dict.lookup.total", "kind", String.valueOf(kind));
        devLog.debug("esquireDictionary: kind:{}, result:{}, claims:{}", kind, String.valueOf(layers), String.valueOf(claims));
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
        EsqEntity entity = iEnyManService.esquireCommand(kind, id, cmd);
        devLog.debug("esquireCommand: kind:{}, id:{}, cmd:{}, result:{}", kind, id, cmd, String.valueOf(entity));
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
        Map<String, Object> realmAccess = claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class);
        List<String> roles = realmAccess != null ? (List<String>) realmAccess.get(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES) : null;

        EsqEntity ret = iEnyManService.esquireCommandSave(kind, id, cmd, fields, roles);
        devLog.debug("esquireCommandSave: kind:{}, id:{}, cmd:{}, result:{}", kind, id, cmd, String.valueOf(ret));
        return ResponseEntity.status(HttpStatus.OK).body(ret);
    }

    @PostMapping("/esq-cmd-new")
    public ResponseEntity<EsqEntity> esquireCommandNew(
           @Parameter(description = "Entity kind code")
           @RequestParam(name = "kind", required = true) Integer kind,
           @Parameter(description = "Parent entity id")
           @RequestParam(name = "parentId", required = true) String parentId,
           @Parameter(description = "Command code")
           @RequestParam(name = "cmd", required = false, defaultValue = "new") String cmd,
           @RequestBody Map<String, Object> fields,
           @AuthenticationPrincipal Claims claims
    ) {
        Map<String, Object> realmAccess = claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class);
        List<String> roles = realmAccess != null ? (List<String>) realmAccess.get(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES) : null;

        EsqEntity ret = iEnyManService.esquireCommandNew(kind, parentId, cmd, fields, roles);
        devLog.debug("esquireCommandNew: kind:{}, parentId:{}, cmd:{}, result:{}", kind, parentId, cmd, String.valueOf(ret));
        return ResponseEntity.status(HttpStatus.OK).body(ret);
    }

    @PostMapping("/esq-cmd-del")
    public ResponseEntity<Void> esquireCommandDelete(
           @Parameter(description = "Entity kind code")
           @RequestParam(name = "kind", required = true) Integer kind,
           @Parameter(description = "Entity id")
           @RequestParam(name = "id", required = true) String id,
           @Parameter(description = "Command code")
           @RequestParam(name = "cmd", required = false, defaultValue = "delete") String cmd,
           @AuthenticationPrincipal Claims claims
    ) {
        Map<String, Object> realmAccess = claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class);
        List<String> roles = realmAccess != null ? (List<String>) realmAccess.get(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES) : null;

        iEnyManService.esquireCommandDelete(kind, id, cmd, roles);
        devLog.debug("esquireCommandDelete: kind:{}, id:{}, cmd:{}", kind, id, cmd);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/esq-move")
    @Operation(
            summary = "Move an entity to a new parent org (async-ack)",
            description = "Move ORG or USR to a destination ORG. Requires UPDATE permission on both moving entity and destination. "
                        + "v1.2.6: the command is placed on enyMan's move queue and runs on a single worker thread; the response is "
                        + "202 Accepted at submit time, NOT 200 OK after processing."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "202",
                    description = "Move command accepted onto the queue; processing happens asynchronously."
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "HTTP Status Internal Server Error",
                    content = @Content(
                            schema = @Schema(implementation = ProblemDetail.class)
                    )
            )
    })
    public ResponseEntity<Void> esquireCommandMove(
           @Parameter(description = "Entity kind code")
           @RequestParam(name = "kind", required = true) Integer kind,
           @Parameter(description = "Entity id")
           @RequestParam(name = "id", required = true) String id,
           @Parameter(description = "Destination org id")
           @RequestParam(name = "dist_id", required = true) String distId,
           @AuthenticationPrincipal Claims claims
    ) {
        Map<String, Object> realmAccess = claims.get(EsqConstants.JWT_CLAIM_REALM_ACCESS, Map.class);
        List<String> roles = realmAccess != null ? (List<String>) realmAccess.get(EsqConstants.JWT_CLAIM_REALM_ACCESS_ROLES) : null;

        iEnyManService.esquireCommandMove(kind, id, distId, roles);
        devLog.debug("esquireCommandMove: queued kind:{}, id:{}, distId:{}", kind, id, distId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/esq-kinds")
    public ResponseEntity<List<EsqObjectKind>>  esquireKinds(
            @AuthenticationPrincipal Claims claims
    ) {
        //String rootPath = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class);
        //String uid = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class);

        List<EsqObjectKind> ret  = EsqObjectKindStorage.getInstance().getAll();
        devLog.debug("esquireKinds: result:{}",String.valueOf(ret));
        return ResponseEntity.status(HttpStatus.OK).body(ret);
    }

    @GetMapping("/esq-cmd-tree")
    @Operation(
            summary = "Read the natural entity subtree under a seed entity",
            description = "FK-based traversal of esq_org/esq_user/esq_account, independent from the biztree cache. "
                        + "Returns the seed entity and every descendant, leaves-first (level DESC), so callers can "
                        + "delete bottom-up without reordering. Same EsqTreeNode shape as /esq for response compatibility."
    )
    public ResponseEntity<List<EsqTreeNode>> esquireCommandTree(
            @Parameter(description = "Seed entity kind code (ORG/USR/ACCT)")
            @RequestParam(name = "kind", required = true) Integer kind,
            @Parameter(description = "Seed entity id")
            @RequestParam(name = "id", required = true) String id,
            @AuthenticationPrincipal Claims claims
    ) {
        List<EsqTreeNode> ret = iEnyManService.esquireCommandTree(kind, id);
        devLog.debug("esquireCommandTree: kind:{}, id:{}, rows:{}", kind, id, ret.size());
        return ResponseEntity.status(HttpStatus.OK).body(ret);
    }
}
