/*
 *  Esquire frameworks (tm)
 *  PacMan service
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
 * 02/19/2026 mir0n  added esquireCommandSave() POST /esq-cmd-asave
 * 03/09/2026 mir0n  realm_access.roles extracted from JWT claims; roles passed to esquireCommandSave()
 * 03/21/2026 mir0n  devLog added; log.debug->devLog.debug
 * 03/26/2026 mir0n  POST /esq-anew -> esquireCommandNew(); POST /esq-adel -> esquireCommandDelete()
 * 04/07/2026 mir0n  unified facade API: /esq-cmd-asave->/esq-cmd-save, /esq-anew->/esq-new, /esq-adel->/esq-del
 *                   /esq-new->/esq-cmd-new, /esq-del->/esq-cmd-del
 * 04/09/2026 mir0n  POST /esq-acct -> esquireCommandAcct(); AcctTransactionService injected directly
 * 04/13/2026 mir0n  AcctTransactionSimple -> AcctTransactionSingle
 * 06/01/2026 mir0n  esquireCommandNew() / POST /esq-cmd-new mapping removed -- account CREATE
 *                   moved to enyMan
 * 06/04/2026 mir0n  rootPath / uid no longer extracted from claims; delegates without them (roles
 *                   still extracted); uid / rootPath ride the unified request context
 * 08/26/2026 mir0n  the develop log lines pass the value itself instead of String.valueOf
 * 10/19/2026 mir0n  v2.0.1 -- calls PacManCommands; the @AuthenticationPrincipal Claims parameters and the roles
 *                   read from the token removed
 */

package pro.mir0n.esquire.pacMan.controller;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.dto.entity.*;
import pro.mir0n.esquire.pacMan.acct.dto.AcctTransactionSingle;
import pro.mir0n.esquire.pacMan.service.PacManCommands;
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

import java.util.Map;

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
public class PacManController {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + PacManController.class.getName());

    private PacManCommands commands;

    @Operation(
            summary = "Esquire Account REST API",
            description = "REST API to manage account entities"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "HTTP Status OK",
                    content = @Content(schema = @Schema(oneOf = {
                             EsqAcct.class
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

    @PostMapping("/esq-cmd-save")
    public ResponseEntity<EsqEntity> esquireCommandSave(
           @Parameter(description = "Entity kind code")
           @RequestParam(name = "kind", required = true) Integer kind,
           @Parameter(description = "Entity id")
           @RequestParam(name = "id", required = true) String id,
           @Parameter(description = "Command code")
           @RequestParam(name = "cmd", required = false, defaultValue = "save") String cmd,
           @RequestBody Map<String, Object> fields
    ) {
        EsqEntity ret = commands.esquireCommandSave(kind, id, cmd, fields);
        return ResponseEntity.status(HttpStatus.OK).body(ret);
    }

    @PostMapping("/esq-cmd-del")
    public ResponseEntity<Void> esquireCommandDelete(
           @Parameter(description = "Entity kind code")
           @RequestParam(name = "kind", required = true) Integer kind,
           @Parameter(description = "Entity id")
           @RequestParam(name = "id", required = true) String id,
           @Parameter(description = "Command code")
           @RequestParam(name = "cmd", required = false, defaultValue = "delete") String cmd
    ) {
        commands.esquireCommandDelete(kind, id, cmd);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Post account transaction (deposit)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "HTTP Status OK",
                    content = @Content(schema = @Schema(implementation = AcctTransactionSingle.class))),
            @ApiResponse(responseCode = "500", description = "HTTP Status Internal Server Error",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/esq-acct")
    public ResponseEntity<AcctTransactionSingle> esquireCommandAcct(
           @Parameter(description = "Account kind code")
           @RequestParam(name = "kind", required = true) Integer kind,
           @Parameter(description = "Account entity id")
           @RequestParam(name = "id", required = true) String id,
           @Parameter(description = "Command code")
           @RequestParam(name = "cmd", required = false, defaultValue = "acct") String cmd,
           @RequestBody Map<String, Object> fields
    ) {
        AcctTransactionSingle ret = commands.esquireCommandAcct(kind, id, cmd, fields);
        return ResponseEntity.status(HttpStatus.OK).body(ret);
    }

    @GetMapping("/esq-cmd")
    public ResponseEntity<EsqEntity>  esquireCommand(
           @Parameter(description = "Entity kind code")
           @RequestParam(name = "kind", required = true) Integer kind,
           @Parameter(description = "Entity id")
           @RequestParam(name = "id", required = true) String id,
           @Parameter(description = "Command code: 'details' only for now")
           @RequestParam(name = "cmd", required = false, defaultValue = "details") String cmd
    ) {
        EsqEntity entity = commands.esquireCommand(kind, id, cmd);
        return ResponseEntity.status(HttpStatus.OK).body(entity);
    }

}
