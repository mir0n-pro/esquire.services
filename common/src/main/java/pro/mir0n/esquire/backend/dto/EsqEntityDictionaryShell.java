/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 */

package pro.mir0n.esquire.backend.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

//import java.lang.foreign.SymbolLookup;
import java.util.ArrayList;
import java.util.List;

@Schema(
        name = "dictionary-shell",
        description = "Entity dictionaries"
)
@Data @Getter @Setter
@JacksonXmlRootElement(localName = "dictionary-shell")
public class EsqEntityDictionaryShell {
    @Schema(
            description = "List of dictionaries"
    )

    @JacksonXmlElementWrapper(localName = "dictionaries") // Wrapper element for the list
    @JacksonXmlProperty(localName = "dictionary")
    //@JsonProperty("dictionary")
    private List<EsqEntityDictionary> dictionaries = new ArrayList<>();

    public void sortLayers() {
        for (EsqEntityDictionary dictionary : dictionaries) {
            dictionary.sortLayers();
        }
    }

};



