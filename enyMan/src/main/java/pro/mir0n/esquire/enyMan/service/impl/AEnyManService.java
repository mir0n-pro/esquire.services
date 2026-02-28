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
 */

package pro.mir0n.esquire.enyMan.service.impl;

import java.beans.PropertyDescriptor;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import pro.mir0n.esquire.backend.dto.*;
import pro.mir0n.esquire.backend.jpa.*;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.backend.error.ResourceNotFoundException;
import pro.mir0n.esquire.enyMan.service.IEnyManService;

@Slf4j
public abstract class AEnyManService  implements IEnyManService {

    private EsqEntityDictionaryRepository entityDictionaryRepository;

    public AEnyManService(EsqEntityDictionaryRepository entityDictionaryRepository) {
        this.entityDictionaryRepository = entityDictionaryRepository;
    }

    @Override
    public List<EsqEntityLayer> esquireDictionary(Integer kind) {
        //String correlationId = RequestContextUtils.getCorrelationId();
        //String requestId = RequestContextUtils.getRequestId();
        log.debug("srvc: esquireDictionary: kind:{}",  kind);

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
            throw new ResourceNotFoundException("esquireDictionary", "kind", kind == null?"''":kind.toString());
        }
        log.debug("srvc: esquireDictionary(2): ret:{}",  ret);
        return ret;
    }

    protected boolean applyFields(EsqEntityJpa jpa, Map<String, Object> fields, Set<String> writables) {
        if (jpa == null || fields == null) {
            return false;
        }
        BeanWrapper wrapper = new BeanWrapperImpl(jpa);
        boolean changed = false;
        for (PropertyDescriptor pd : wrapper.getPropertyDescriptors()) {
            if ((writables == null || writables.contains(pd.getName()))
            && fields.containsKey(pd.getName())) {
                changed = true;
                Object newValue = fields.get(pd.getName());
                if (newValue instanceof String && ((String)newValue).isBlank()) {   // isEmpty()?
                    newValue = null;
                }
                //xxx: this updates the given jpa
                wrapper.setPropertyValue(pd.getName(), newValue);
            }
        }
        return changed;
    }
}
