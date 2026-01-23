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
 */

package pro.mir0n.esquire.enyMan.controller;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import pro.mir0n.esquire.enyMan.dto.EsqEntity;
import pro.mir0n.esquire.enyMan.dto.EsqEntityLayer;
import pro.mir0n.esquire.enyMan.dto.EsqTreeNode;
import pro.mir0n.esquire.enyMan.dto.entity.EsqAcct;
import pro.mir0n.esquire.enyMan.dto.entity.EsqOrg;
import pro.mir0n.esquire.enyMan.dto.entity.EsqUsr;
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

import pro.mir0n.esquire.common.EsqConstants;

/**
 * @author mir0n
 */

@Tag(
        name = "Esquire tree REST API",
        description = "REST API to navigate over backoffice tree"
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
            summary = "Esquire EnyMan REST API",
            description = "REST API to navigate over backoffice tree"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "HTTP Status OK",
                    content = @Content(schema = @Schema(oneOf = {EsqTreeNode.class
                            , String.class
                            , EsqEntityLayer.class
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

    @GetMapping("/esq")
    public ResponseEntity<List<EsqTreeNode>> esquire(
        @Parameter(description = "Tree node id, a parent node to retrieve children")
        @RequestParam(name = "id", required = false)  String id,
        @Parameter(description = "Support flat tree, how many nodes to skips from response, not in use")
        @RequestParam(name = "skip", required = false) Integer skip,
        @Parameter(description = "Support flat tree, how many nodes to have in response, not in use")
        @RequestParam(name = "take", required = false) Integer take
       ,@AuthenticationPrincipal Claims claims
    ) {
        String rootPath = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class);
        String uid = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class);

        List<EsqTreeNode> nodes = iEnyManService.esquire(id, skip == null ? 0 : skip, take == null? 0 : take, rootPath, uid);
        log.debug("esquire: id:{}, rootPath:{}, uid:{}, result:{}", id, rootPath,uid, String.valueOf(nodes));
        return ResponseEntity.status(HttpStatus.OK).body(nodes);
    }

    @GetMapping("/esq-enode")
    public ResponseEntity<EsqTreeNode> esquireEntityNode(
          @Parameter(description = "Entity kind code")
          @RequestParam(name = "kind", required = true) Integer kind,
          @Parameter(description = "Entity id, id or name is required")
          @RequestParam(name = "id", required = false) String id,
          @Parameter(description = "Entity name, id or name is required")
          @RequestParam(name = "name", required = false) String name
          ,@AuthenticationPrincipal Claims claims
    ) {
        String rootPath = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class);
        String uid = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ID, String.class);

        EsqTreeNode node = iEnyManService.esquireEntityNode(kind, id, name, rootPath, uid);
        log.debug("esquireEntityNode: kind:{}, id:{}, name:{}, rootPath:{}, result:{}", kind, id, name, rootPath, String.valueOf(node));
        return ResponseEntity.status(HttpStatus.OK).body(node);
    }

    @GetMapping("/esq-path")
    public ResponseEntity<List<String>> esquirePath(
        @Parameter(description = "Tree node id")
        @RequestParam(name = "id", required = true) String id,
        @AuthenticationPrincipal Claims claims
    ) {
        String rootPath = claims.get(EsqConstants.JWT_CLAIM_ENTITY_ROOTPATH, String.class);

        List<String> path = iEnyManService.esquirePath(id, rootPath);
        log.debug("esquirePath: id:{}, result:{}, claims:{}", id, String.valueOf(path), String.valueOf(claims));
        return ResponseEntity.status(HttpStatus.OK).body(path);
    }
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

}
