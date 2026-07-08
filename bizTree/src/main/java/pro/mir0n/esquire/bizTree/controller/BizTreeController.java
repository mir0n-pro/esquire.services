/*
 *  Esquire frameworks (tm)
 *  BizTree service
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
 * 01/23/2026 mir0n use common library
 *                  only EsqTreeNode requests
 * 03/21/2026 mir0n  devLog added; log.debug→devLog.debug
 * 05/14/2026 mir0n  GET /esq-tree added: recursive subtree from biztree H2 cache
 *                   (counterpart to enyMan /esq-cmd-tree authoritative DB walk;
 *                   used together by the hauberk CompareTrees scenario)
 * 05/20/2026 mir0n  Taijitu refactor (v1.2.5): forward all reads to IBizTreeDirector
 *                   (was IBizTreeService); thin REST entry point, no business logic
 * 05/23/2026 mir0n  POST /esq-sweep: async force-sweep -> director.sweepAsync(); returns 202 (ACCEPTED).
 * 06/04/2026 mir0n  rootPath / uid no longer extracted from claims; forwards only id / kind / name to
 *                   the director (uid / rootPath ride the unified request context)
 * 07/08/2026 mir0n  @EsqTraced on the four GET reads (esq.svc.tree / node / subtree / path) -- marked
 *                   here, at the REST entry point, so a cache-served read is traced whichever director
 *                   is wired; POST /esq-sweep is not marked
 */

package pro.mir0n.esquire.bizTree.controller;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import pro.mir0n.esquire.backend.dto.EsqTreeNode;
import pro.mir0n.esquire.backend.o11y.EsqTraced;
import pro.mir0n.esquire.bizTree.access.IBizTreeDirector;
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

/**
 * Stable REST entry point for bizTree. Extracts JWT claims and forwards
 * every call to {@link IBizTreeDirector}. Holds zero business logic --
 * cache implementation changes (Step 3 swap to Taijitu) leave this file
 * untouched. The set of endpoints + their contracts is the public API
 * of bizTree; this class is the only place that contract is enforced.
 *
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
public class BizTreeController {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + BizTreeController.class.getName());

    private IBizTreeDirector director;

    @Operation(
            summary = "Esquire Tree REST API",
            description = "REST API to navigate over backoffice tree"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "HTTP Status OK",
                    content = @Content(schema = @Schema(oneOf = {
                              EsqTreeNode.class
                            , String.class
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
    @EsqTraced(name = "esq.svc.tree", label = "read tree")
    public ResponseEntity<List<EsqTreeNode>> esquire(
        @Parameter(description = "Tree node id, a parent node to retrieve children")
        @RequestParam(name = "id", required = false)  String id,
        @Parameter(description = "Support flat tree, how many nodes to skips from response, not in use")
        @RequestParam(name = "skip", required = false) Integer skip,
        @Parameter(description = "Support flat tree, how many nodes to have in response, not in use")
        @RequestParam(name = "take", required = false) Integer take
       ,@AuthenticationPrincipal Claims claims
    ) {
        List<EsqTreeNode> nodes = director.esquire(id, skip == null ? 0 : skip, take == null? 0 : take);
        devLog.debug("esquire: id:{}, result:{}", id, String.valueOf(nodes));
        return ResponseEntity.status(HttpStatus.OK).body(nodes);
    }

    @GetMapping("/esq-enode")
    @EsqTraced(name = "esq.svc.node", label = "read node")
    public ResponseEntity<EsqTreeNode> esquireEntityNode(
            @Parameter(description = "Entity kind code")
            @RequestParam(name = "kind", required = true) Integer kind,
            @Parameter(description = "Entity id, id or name is required")
            @RequestParam(name = "id", required = false) String id,
            @Parameter(description = "Entity name, id or name is required")
            @RequestParam(name = "name", required = false) String name
            ,@AuthenticationPrincipal Claims claims
    ) {
        EsqTreeNode node = director.esquireEntityNode(kind, id, name);
        devLog.debug("esquireEntityNode: kind:{}, id:{}, name:{}, result:{}", kind, id, name, String.valueOf(node));
        return ResponseEntity.status(HttpStatus.OK).body(node);
    }

    @GetMapping("/esq-tree")
    @Operation(
            summary = "Recursive subtree from biztree H2 cache",
            description = "Returns the seed node + every descendant cached node (real entities, "
                        + "virtual folders, and account shortcuts) under one tree-path prefix. "
                        + "Counterpart to enyMan's /esq-cmd-tree (authoritative DB walk); used "
                        + "together by the hauberk CompareTrees diff scenario."
    )
    @EsqTraced(name = "esq.svc.subtree", label = "read subtree")
    public ResponseEntity<List<EsqTreeNode>> esquireSubtree(
            @Parameter(description = "Seed tree-node id (subtree root)")
            @RequestParam(name = "id", required = true) String id,
            @AuthenticationPrincipal Claims claims
    ) {
        List<EsqTreeNode> nodes = director.esquireSubtree(id);
        devLog.debug("esquireSubtree: id:{}, count:{}", id, nodes.size());
        return ResponseEntity.status(HttpStatus.OK).body(nodes);
    }

    @GetMapping("/esq-path")
    @EsqTraced(name = "esq.svc.path", label = "read path")
    public ResponseEntity<List<String>> esquirePath(
        @Parameter(description = "Tree node id")
        @RequestParam(name = "id", required = true) String id,
        @AuthenticationPrincipal Claims claims
    ) {
        List<String> path = director.esquirePath(id);
        devLog.debug("esquirePath: id:{}, result:{}", id, String.valueOf(path));
        return ResponseEntity.status(HttpStatus.OK).body(path);
    }

    @PostMapping("/esq-sweep")
    @Operation(
            summary = "Force a night-watch sweep (asynchronous)",
            description = "Triggers the cache director's night-watch sweep on a background thread and "
                        + "returns immediately (202) -- the request is not held for the full sweep. "
                        + "The sweep also fires periodically via the director's scheduler."
    )
    public ResponseEntity<Void> sweep() {
        director.sweepAsync();
        devLog.debug("sweep: forced via REST (async)");
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

}
