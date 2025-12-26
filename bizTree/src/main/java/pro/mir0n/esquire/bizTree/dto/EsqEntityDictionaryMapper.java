/*
 *  Esquire frameworks (tm)
 *  BizTree service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.bizTree.dto;

import pro.mir0n.esquire.bizTree.jpa.EsqCustomEntityFieldJpa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EsqEntityDictionaryMapper {
    private EsqEntityDictionaryMapper() {
    }
    public static EsqEntityDictionary mapTo(List<EsqCustomEntityFieldJpa> fields, EsqEntityDictionary dictDto) {
        if (fields != null && !fields.isEmpty()) {
            EsqEntityLayer currentLayer = null;
            for (EsqCustomEntityFieldJpa field : fields) {
                currentLayer = dictDto.findLayer(field.getLayer());
                if (currentLayer == null) {
                    currentLayer = new EsqEntityLayer();
                    currentLayer.setLayer(field.getLayer());
                    currentLayer.setTitle("Custom"); // for now
                    currentLayer.setFields(new ArrayList<>());
                    dictDto.getLayers().add(currentLayer);
                }
                EsqEntityField newField = new EsqEntityField();
                newField.setName(field.getName());
                newField.setSort(field.getSort());
                newField.setLabel(field.getLabel());
                newField.setType(field.getType());
                newField.setTooltip(field.getTooltip());
                newField.setListvalues(listvaluesArray(field.getListvalues()));
                newField.setNullable("Y".equals(field.getNullable()));
                newField.setNullmeaning(field.getNullmeaning());
                newField.setValidation(field.getValidation());
                newField.setReadwrite(field.getReadwrite());
                newField.setFormat(field.getFormat());
                currentLayer.getFields().add(newField);
            }
            dictDto.sortLayers();
        }
        return dictDto;
    }

    public static List<String> listvaluesArray(String listvalues) {
        List<String> ret = new ArrayList<>();
        if (listvalues != null && !listvalues.isEmpty()) {
            String[] pathArr = listvalues.split("|");
            Collections.addAll(ret, pathArr);
        }
        return ret;

    }


}
