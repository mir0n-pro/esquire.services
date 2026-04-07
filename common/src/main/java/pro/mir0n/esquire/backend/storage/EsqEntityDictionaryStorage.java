/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 12/28/2025 mir0n logging added using Slf4j
 * 03/21/2026 mir0n  devLog added; dual error pattern; unused imports removed
 * 04/07/2026 mir0n  storage changed from List to Map<Integer, EsqEntityDictionary> for O(1) lookup
 *                   get(int kind): direct map lookup; init(fileName): forEach put
 */

package pro.mir0n.esquire.backend.storage;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.backend.dto.EsqEntityDictionaryShell;
import pro.mir0n.esquire.backend.dto.EsqEntityDictionary;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.FileCopyUtils;


import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class EsqEntityDictionaryStorage {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + EsqEntityDictionaryStorage.class.getName());

    private static EsqEntityDictionaryStorage itSelf = new EsqEntityDictionaryStorage();
    private static Map<Integer, EsqEntityDictionary> storage = new HashMap<>();
    //private EsqEntityDictionaryStorage() {};

     public static EsqEntityDictionaryStorage getInstance() {
         return itSelf;
     }

    public void init(EsqEntityDictionary desc) {
        storage.put(desc.getKind(), desc);
    }

    public EsqEntityDictionary get(int kind) {
        return storage.get(kind);
    }

    private String loadResourceContent(String fileName) throws IOException {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource resource = resourceLoader.getResource(fileName == null ? "esq-entity-dictionaries.xml"  : fileName);

        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        }
    }

     public boolean init(String fileName) {

//         serializationExample();

         XmlMapper xmlMapper = new XmlMapper();
         boolean ret = false;
        try {
            String xml = loadResourceContent(fileName);
            EsqEntityDictionaryShell shell = xmlMapper.readValue(xml, EsqEntityDictionaryShell.class);
            if (shell != null && shell.getDictionaries() != null) {
                shell.sortLayers();
                shell.getDictionaries().forEach(d -> storage.put(d.getKind(), d));
            }
            log.info("Dictionaries loaded successfully");
            //System.out.println(shell);
            ret = true;
        } catch (Exception e) {
            System.out.println("failed load dictionary: " + e);
            log.error("EsqEntityDictionaryStorage: init failed: {}", e.getMessage());
            devLog.error("EsqEntityDictionaryStorage: init failed: {}", e.getMessage(), e);
        }
         return ret;
    }


/*
    public void serializationExample() {
         try {
             EsqEntityField f1 = new EsqEntityField(
                     "f1"
                     , 1
                     , "Field 1"
                     , "string"
                     , "Tooltip for field 1"
                     , List.of(new String[]{"va1", "va2"})
                     , false
                     , "nothing"
                     , "validation"
                     , 1
                     , "format"
             );
             EsqEntityField f2 = new EsqEntityField(
                     "f2"
                     , 2
                     , "Field 2"
                     , "string"
                     , "Tooltip for field 2"
                     , null
                     , false
                     , "nothing"
                     , "validation"
                     , 1
                     , "format"
             );
             List<EsqEntityField> flist = Arrays.asList(new EsqEntityField[]{f1, f2});

             EsqEntityLayer l1 = new EsqEntityLayer(
                     1
                     , "Layer 1"
                     , flist
             );
             EsqEntityLayer l2 = new EsqEntityLayer(
                     2
                     , "Layer 2"
                     , flist
             );
             List<EsqEntityLayer> llist = Arrays.asList(new EsqEntityLayer[]{l1, l2});
             EsqEntityDictionary d1 = new EsqEntityDictionary(
                     false
                     , 12
                     , llist
             );
             EsqEntityDictionary d2 = new EsqEntityDictionary(
                     false
                     , 12
                     , llist
             );
             List<EsqEntityDictionary> dlist = Arrays.asList(new EsqEntityDictionary[]{d1, d2});
             EsqEntityDictionaryShell dcts = new EsqEntityDictionaryShell();
             dcts.setDictionaries(dlist);

             XmlMapper xmlMapper = new XmlMapper();
             // Enable pretty print for better readability
             xmlMapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);

             String xml = xmlMapper.writeValueAsString(dcts);
             System.out.println(xml);
         } catch(Exception e) {
             e.printStackTrace();
         }
    }
*/
}

