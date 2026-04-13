/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 04/09/2026 mir0n  created: applyFields() and enforceDefaults() extracted from AEnyManService / EsqEntityLayer
 * 04/12/2026 mir0n  applyFields(kind, fields): kind-based validation overload with listvalues constraint check added
 */

package pro.mir0n.esquire.backend.service;

import java.beans.PropertyDescriptor;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import pro.mir0n.esquire.backend.dto.EsqEntityDictionary;
import pro.mir0n.esquire.backend.dto.EsqEntityField;
import pro.mir0n.esquire.backend.dto.EsqEntityKindFieldLayer;
import pro.mir0n.esquire.backend.dto.EsqEntityLayer;
import pro.mir0n.esquire.backend.error.InvalidValueException;
import pro.mir0n.esquire.backend.jpa.EsqEntityJpa;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;

public class EntityFieldUtils {

    private EntityFieldUtils() {}

    public static boolean applyFields(EsqEntityJpa jpa, Map<String, Object> fields,
            boolean personal, int subLayer, Set<String> writables) {
        if (jpa == null || fields == null) {
            return false;
        }
        EsqEntityDictionary dict = EsqEntityDictionaryStorage.getInstance().get(jpa.getKind());
        BeanWrapper wrapper = new BeanWrapperImpl(jpa);
        boolean changed = false;
        EsqEntityKindFieldLayer kfl = new EsqEntityKindFieldLayer();
        for (PropertyDescriptor pd : wrapper.getPropertyDescriptors()) {
            String name = pd.getName();
            if (fields.containsKey(name)) {
                Object value = fields.get(name);
                kfl = (dict != null) ? dict.fillKindFieldLayer(name, kfl) : null;
                EsqEntityField field = (kfl != null) ? kfl.getField() : null;
                if (field != null) {
                    if (subLayer > 0) {
                        kfl.setLayer(subLayer);
                    }
                    if (field.getReadwrite() == null || (field.getReadwrite() & 2) != 2) {
                        // read-only fields
                        if (writables != null && writables.contains(name)) {
                            // writable exceptions: we trust generated value
                            wrapper.setPropertyValue(name, value);
                            changed = true;
                        }
                    } else {
                        // writable fields: need validation
                        value = ValidatorFactory.getInstance().validate(jpa, kfl, personal, value);
                        wrapper.setPropertyValue(name, value);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    public static boolean applyFields(EsqEntityJpa jpa, Map<String, Object> fields) {
        return applyFields(jpa, fields, false, 0, null);
    }

    public static Map<String, Object> applyFields(int kind, Map<String, Object> fields) {
        Map<String, Object> ret = new HashMap<>();
        EsqEntityDictionary dict = EsqEntityDictionaryStorage.getInstance().get(kind);
        if (dict == null) {
            return ret;
        }
        EsqEntityKindFieldLayer kfl = new EsqEntityKindFieldLayer();
        for (EsqEntityLayer layer : dict.getLayers()) {
            if (layer.getFields() == null) {
                continue;
            }
            for (EsqEntityField field : layer.getFields()) {
                if (field.getReadwrite() == null || (field.getReadwrite() & 2) != 2) {
                    continue;
                }
                String name = field.getName();
                kfl.setEntityKind(kind);
                kfl.setLayer(layer.getLayer());
                kfl.setLayerTitle(layer.getTitle());
                kfl.setField(field);
                Object raw = fields.get(name);
                if (raw == null && "N".equals(field.getNullable()) && field.getDefaultValue() != null) {
                    raw = field.getDefaultValue();
                }
                Object validated = ValidatorFactory.getInstance().validate(null, kfl, false, raw);
                if (validated != null && field.getListvalues() != null && !field.getListvalues().isEmpty()) {
                    String strVal = validated.toString();
                    boolean found = false;
                    for (String lv : field.getListvalues()) {
                        String key = lv.contains("~") ? lv.substring(0, lv.indexOf('~')) : lv;
                        if (key.equals(strVal)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        throw new InvalidValueException("Invalid value", name, field.getLabel(), strVal);
                    }
                }
                ret.put(name, validated);
            }
        }
        return ret;
    }

    public static void enforceDefaults(EsqEntityLayer layer, EsqEntityJpa jpa) {
        if (layer == null || jpa == null || layer.getFields() == null) return;
        BeanWrapper wrapper = new BeanWrapperImpl(jpa);
        for (EsqEntityField f : layer.getFields()) {
            if ("N".equals(f.getNullable()) && f.getDefaultValue() != null) {
                try {
                    if (wrapper.getPropertyValue(f.getName()) == null) {
                        wrapper.setPropertyValue(f.getName(), f.getDefaultValue());
                    }
                } catch (Exception ignored) {}
            }
        }
    }

}
