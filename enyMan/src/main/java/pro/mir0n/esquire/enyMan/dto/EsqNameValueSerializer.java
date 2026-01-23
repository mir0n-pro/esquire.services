/*
 *  Esquire frameworks (tm)
 *  EnyMan service
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.enyMan.dto;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.List;

public class EsqNameValueSerializer extends JsonSerializer<List<EsqNameValue>> {
    @Override
    public void serialize(List<EsqNameValue> pairs, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        // We do not call gen.writeStartObject() here if using @JsonUnwrapped
        for (EsqNameValue pair : pairs) {
            gen.writeStringField(pair.getName(), pair.getValue());
        }
    }
}

