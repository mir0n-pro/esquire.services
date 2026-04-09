/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 04/09/2026 mir0n  created: applyFields() and enforceDefaults() extracted from AEnyManService / EsqEntityLayer
 */

package pro.mir0n.esquire.backend.service;

import java.beans.PropertyDescriptor;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import pro.mir0n.esquire.backend.dto.EsqEntityDictionary;
import pro.mir0n.esquire.backend.dto.EsqEntityField;
import pro.mir0n.esquire.backend.dto.EsqEntityKindFieldLayer;
import pro.mir0n.esquire.backend.dto.EsqEntityLayer;
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
