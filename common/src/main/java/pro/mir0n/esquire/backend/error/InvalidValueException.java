/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/06/2026 mir0n created: validation error exception with field-level error list
 */

package pro.mir0n.esquire.backend.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class InvalidValueException extends RuntimeException {
    List<Map<String,String>> errors = null;

    public InvalidValueException(String message, String fieldName, String fieldLabel, String fieldLayer) {
        super(fieldLabel + ": " + message);
        errors = new ArrayList<>();
        errors.add(Map.of("fieldName", fieldName, "message", message, "fieldLabel", fieldLabel, "tabIndex", fieldLayer));
    }

    //public addError(String message, String fieldName, String fieldLabel, String fieldLayer) {
    //}

    public InvalidValueException(String msg, List<Map<String,String>> errors) {
        super(msg);
        this.errors = errors;
    }

}
