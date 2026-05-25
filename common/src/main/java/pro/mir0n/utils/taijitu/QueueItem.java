/*
 *  mir0n java common frameworks
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/20/2026 mir0n  created: flat queue item (generalized from bizTree.taijitu). Commands and
 *                   events ride the SAME record in arrival order -- no sealed hierarchy. A
 *                   command is eventType==MonadCmd.CMD with entityId=the command id; everything
 *                   else is an event carrying the raw message (messageEncoding + text) parsed by
 *                   the worker, plus requestId / correlationId for observability.
 */
package pro.mir0n.utils.taijitu;

/**
 * One item on a monad's single FIFO queue. Commands and events share the queue
 * so their relative arrival order is preserved by the single worker.
 *
 *   - command : {@code eventType == MonadCmd.CMD}, {@code entityId} is the command id.
 *   - event   : any other {@code eventType}; carries the raw message body
 *               ({@code messageEncoding} + {@code text}), applied by the monad's handler.
 *
 * For a command, {@code requestId} is null and {@code correlationId} is a cheap synthesized
 * tracking id (CMD.&lt;cmdId&gt;.&lt;monadId&gt;.&lt;ms&gt;).
 */
public record QueueItem(String eventType,
                        String entityId,
                        int    entityKind,
                        String requestId,
                        String correlationId,
                        String messageEncoding,
                        String text) {
}
