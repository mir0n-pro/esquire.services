/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 03/06/2026 mir0n created: entity kind + layer + field context DTO for validation
 * 03/08/2026 mir0n  layerTitle field added; getLabel() context-aware label for tab vs regular fields
 */

package pro.mir0n.esquire.backend.dto;

import lombok.*;

@Getter @Setter
public class EsqEntityKindFieldLayer {
    private int entityKind;
    private EsqEntityField field;
    private int layer;
    private String layerTitle;

    public String getLabel() {
        if (field == null) {
            return null;
        }
        return field.isTabField()? getLayerTitle() : field.getLabel();
    }
}
