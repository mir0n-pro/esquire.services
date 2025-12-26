/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.bizTree.controller;

import pro.mir0n.esquire.bizTree.dto.EsqEntity;
import pro.mir0n.esquire.bizTree.dto.EsqEntityLayer;
import pro.mir0n.esquire.bizTree.dto.EsqTreeNode;
import pro.mir0n.esquire.bizTree.dto.ErrorResponse;
import pro.mir0n.esquire.bizTree.service.IBizTreeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author mir0n
 */

@Tag(
        name = "REST Esquire Biz Tree",
        description = "REST APIs to navigate over Business Tree"
)

@RestController
@RequestMapping(path="", produces = {MediaType.APPLICATION_JSON_VALUE})
@AllArgsConstructor
@Validated
@CrossOrigin(origins = "http://localhost:4200")
public class BizTreeController {
    private IBizTreeService iBizTreeService;

    @Operation(
            summary = "Esquire Tree REST API",
            description = "REST API to navigate over Esquire Tree"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "HTTP Status OK"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "HTTP Status Internal Server Error",
                    content = @Content(
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })

    @GetMapping("/esq")
    public ResponseEntity<List<EsqTreeNode>> esquire(
          @RequestParam(name = "id", required = false)  String id
        , @RequestParam(name = "skip", required = false) Integer skip
        , @RequestParam(name = "take", required = false) Integer take
    ) {
        List<EsqTreeNode> nodes = iBizTreeService.esquire(id, skip == null ? 0 : skip, take == null? 0 : take);
        System.out.println(nodes);
        return ResponseEntity.status(HttpStatus.OK).body(nodes);
    }

    @GetMapping("/esq-enode")
    public ResponseEntity<EsqTreeNode> esquireEntityNode(
         @RequestParam(name = "kind", required = true) Integer kind
        , @RequestParam(name = "id", required = false) String id
        , @RequestParam(name = "name", required = false) String name
    ) {
        EsqTreeNode node = iBizTreeService.esquireEntityNode(kind, id, name);
        System.out.println(node);
        return ResponseEntity.status(HttpStatus.OK).body(node);
    }

    @GetMapping("/esq-path")
    public ResponseEntity<List<String>> esquirePath(
        @RequestParam String id
    ) {
        List<String> path = iBizTreeService.esquirePath(id);
        System.out.println(path);
        return ResponseEntity.status(HttpStatus.OK).body(path);
    }
    @GetMapping("/esq-dict")
    public ResponseEntity<List<EsqEntityLayer>>  esquireDictionary(
            @RequestParam Integer kind
    ) {
        List<EsqEntityLayer> layers = iBizTreeService.esquireDictionary(kind);
        System.out.println(layers);
        return ResponseEntity.status(HttpStatus.OK).body(layers);
    }

    @GetMapping("/esq-cmd")
    public ResponseEntity<EsqEntity>  esquireCommand(
           @RequestParam Integer kind
        ,  @RequestParam String id
        ,  @RequestParam(name = "cmd", required = false, defaultValue = "details") String cmd
    ) {
        EsqEntity entity = iBizTreeService.esquireCommand(kind, id, cmd);
        System.out.println(entity);
        return ResponseEntity.status(HttpStatus.OK).body(entity);
    }

}
