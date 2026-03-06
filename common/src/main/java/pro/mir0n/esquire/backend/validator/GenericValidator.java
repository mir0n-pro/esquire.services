/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/06/2026 mir0n created: generic implementation of validator interface
 */

package pro.mir0n.esquire.backend.validator;

import pro.mir0n.esquire.backend.dto.EsqEntityField;
import pro.mir0n.esquire.backend.dto.EsqEntityKindFieldLayer;
import pro.mir0n.esquire.backend.error.InvalidValueException;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;

public class GenericValidator implements IValidator {
    public static final String PATTERN_FLAG= "^(Y|N)$";
    public static final String PATTERN_DATE= "^\\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$";
    protected GenericValidator() {}

    @Override
    public Object validate(EsqEntityJpa origin, EsqEntityKindFieldLayer kfl, Object value) {
        Object ret = value;
        EsqEntityField field = kfl.getField();
        if (field != null) {
            if (ret != null) {
                if (ret instanceof String && ((String) ret).isBlank()) {
                    ret = null;
                }
            }
            // Validate required / allow nulls
            if (ret == null) {
                if ("N".equals(field.getNullable())) {
                    throw new InvalidValueException("value is required", field.getName(),
                        field.getLabel(), String.valueOf(kfl.getLayer() -1) );
               }
            } else {
                // Generic non-null value validation
                String type = field.getType();
                if ("number".equals(type)) {
                    validateNumber(kfl, ret, field.getMinmax());
                } else if ("string".equals(type) || "flag".equals(type) || "date".equals(type)){
                    String s = ret.toString();
                    ret = s;
                    String pattern = field.getValidation();
                    if ("flag".equals(type)) {
                        pattern = PATTERN_FLAG;
                    } else if ("date".equals(type)) {
                        pattern = PATTERN_DATE;
                    } else { //string
                        if (field.getMinmax() != null && !field.getMinmax().contains(",")) {
                            try {
                                int maxLen = Integer.parseInt(field.getMinmax().trim());
                                if (s.length() > maxLen) {
                                    ret = s.substring(0, maxLen);
                                }
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    validatePattern(kfl, s, pattern);
                }
            }
        }

        return ret;
    }

    private void validatePattern(EsqEntityKindFieldLayer kfl, String value, String pattern) {
        if (pattern != null && !pattern.isBlank() && !value.matches(pattern)) {
            ///  todo where to get layer from ?
            EsqEntityField field = kfl.getField();
            throw new InvalidValueException("value must be a well-formed", field.getName(),
                field.getLabel(), String.valueOf(kfl.getLayer() -1));
        }
    }

    private void validateNumber(EsqEntityKindFieldLayer kfl, Object value, String minmax) {
        EsqEntityField field = kfl.getField();
        try {
            double num = Double.parseDouble(value.toString());
            if (minmax != null) {
                String[] parts = minmax.split(",");
                if (parts.length == 2) {
                    double min = Double.parseDouble(parts[0].trim());
                    double max = Double.parseDouble(parts[1].trim());
                    if (num < min || num > max) {
                        throw new InvalidValueException("value must be between " + min + " and " + max, field.getName(),
                           field.getLabel(), String.valueOf(kfl.getLayer() -1));
                    }
                }
            }
        } catch (NumberFormatException e) {
            throw new InvalidValueException(e.getMessage(), field.getName(),
                field.getLabel(), String.valueOf(kfl.getLayer() -1));
        }
    }
}
