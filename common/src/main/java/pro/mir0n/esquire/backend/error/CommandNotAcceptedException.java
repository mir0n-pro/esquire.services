/*
 *  Esquire frameworks (tm)
 *  Common module
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 08/24/2026 mir0n  created: a command the service could not take onto its queue -- answered 503 instead
 *                   of a 202 that promises work nothing will ever do
 */

package pro.mir0n.esquire.backend.error;

public class CommandNotAcceptedException extends GenericRuntimeException {
    public CommandNotAcceptedException(String command) {
        super("The " + command + " command was not accepted -- the queue could not take it");
    }
}
