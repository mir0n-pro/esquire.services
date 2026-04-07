/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/28/2026 mir0n  created: abstract base service
 *                   esquireDictionary() and applyFields() extracted from EnyManService
 * 03/06/2026 mir0n  applyFields() refactored: dict-driven validation via ValidatorFactory
 *                   subLayer param added for sub-entity field layer context
 * 03/08/2026 mir0n  applyFields(): boolean personal param added; forwarded to ValidatorFactory
 * 03/10/2026 mir0n  fillКindFieldLayer() call updated to fillKindFieldLayer() — Cyrillic К → ASCII K
 * 03/19/2026 mir0n  esquireDictionary(): kind normalized to even number before dictionary lookup
 * 03/21/2026 mir0n  devLog added; log.debug→devLog.debug
 * 04/07/2026 mir0n  esquireDictionary(): kind param Integer → int; normalization removed
 */

package pro.mir0n.esquire.enyMan.service.impl;

import java.beans.PropertyDescriptor;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.jpa.*;
import pro.mir0n.esquire.backend.validator.ValidatorFactory;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.enyMan.service.IEnyManService;

@Slf4j
public abstract class AEnyManService  implements IEnyManService {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + AEnyManService.class.getName());

    private EsqEntityDictionaryRepository entityDictionaryRepository;

    public AEnyManService(EsqEntityDictionaryRepository entityDictionaryRepository) {
        this.entityDictionaryRepository = entityDictionaryRepository;
    }

    @Override
    public List<EsqEntityLayer> esquireDictionary(int kind) {
        //String correlationId = RequestContextUtils.getCorrelationId();
        //String requestId = RequestContextUtils.getRequestId();
        devLog.debug("srvc: esquireDictionary: kind:{}",  kind);

        List<EsqEntityLayer> ret = null;
        EsqEntityDictionary dict  = EsqEntityDictionaryStorage.getInstance().get(kind);
        if  (dict != null) {
            if(!dict.isCompleted()) {
                List<EsqCustomEntityFieldJpa> custom = entityDictionaryRepository.findCustom(kind);
                if   (custom != null && !custom.isEmpty()) {
                    EsqEntityDictionaryMapper.mapTo(custom, dict);
                }
                dict.setCompleted(true);
            }
            ret = dict.getLayers();
        }
        if (ret == null) {
            throw new ResourceNotFoundException("esquireDictionary", "kind", String.valueOf(kind));
        }
        devLog.debug("srvc: esquireDictionary(2): ret:{}",  ret);
        return ret;
    }

    protected boolean applyFields(EsqEntityJpa jpa, Map<String, Object> fields, boolean personal, int subLayer, Set<String> writables) {
        if (jpa == null || fields == null) {
            return false;
        }
        EsqEntityDictionary dict = EsqEntityDictionaryStorage.getInstance().get(jpa.getKind());
        BeanWrapper wrapper = new BeanWrapperImpl(jpa);
        boolean changed = false;
        //we're going to reuse this structure
        EsqEntityKindFieldLayer kfl = new EsqEntityKindFieldLayer();
        for (PropertyDescriptor pd : wrapper.getPropertyDescriptors()) {
            String name = pd.getName();
            if(fields.containsKey(name)) {
                Object value = fields.get(name);
                kfl = (dict != null) ? dict.fillKindFieldLayer(pd.getName(),kfl) : null;
                EsqEntityField field = (kfl != null) ? kfl.getField() : null;
                if (field != null) {
                    if (subLayer > 0) {
                        // layer for subentity
                        kfl.setLayer(subLayer);
                    }
                    if (field.getReadwrite() == null || (field.getReadwrite() & 2) != 2) {
                        // read-only fields
                        if (writables != null && writables.contains(name)) {
                            //writable exceptions: we trust generated value
                            wrapper.setPropertyValue(name, value);
                            changed = true;
                        }
                    } else { //field.getReadwrite() != null && (field.getReadwrite() & 2) == 2)
                        //writable fields: need validation
                        value = ValidatorFactory.getInstance().validate(jpa, kfl, personal, value);
                        wrapper.setPropertyValue(name, value);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }
}
