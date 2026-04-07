/*
 *  Esquire frameworks (tm)
 *  common library
 *
 *  Copyright(c) 2001, 2025 mir0n&co www.mir0n.me
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 02/28/2026 mir0n  UNKNOWN kind updated with address=false
 * 03/21/2026 mir0n  devLog added; dual error pattern; unused imports removed
 * 04/07/2026 mir0n  storage changed from List to Map<Integer, EsqObjectKind> for O(1) lookup
 *                   get(): getOrDefault(id, UNKNOWN); getAll(): returns defensive copy
 */

package pro.mir0n.esquire.backend.storage;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import pro.mir0n.esquire.backend.dto.*;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.FileCopyUtils;


import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class EsqObjectKindStorage {

    private static final org.slf4j.Logger devLog = LoggerFactory.getLogger("develop." + EsqObjectKindStorage.class.getName());

    private static EsqObjectKindStorage itSelf = new EsqObjectKindStorage();
    private static Map<Integer, EsqObjectKind> storage = new HashMap<>();
    public static EsqObjectKind UNKNOWN = new EsqObjectKind(-1, "unknown", "Unknown", "unknown", "Unknown entity", false, false, false, "", false, false, "", null, null, null,false);
    //private EsqEntityDictionaryStorage() {};

     public static EsqObjectKindStorage getInstance() {
         return itSelf;
     }

    public void init(EsqObjectKind kind) {
        storage.put(kind.getId(), kind);
    }

    public EsqObjectKind get(int id) {
        return storage.getOrDefault(id, UNKNOWN);
    }

    public List<EsqObjectKind> getAll() {
        return new ArrayList<>(storage.values());
    }

    private String loadResourceContent(String fileName) throws IOException {
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource resource = resourceLoader.getResource(fileName == null ? "esq-object-kinds.xml"  : fileName);

        try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        }
    }

     public boolean init(String fileName) {

         //serializationExample();

         XmlMapper xmlMapper = new XmlMapper();
         boolean ret = false;
        try {
            String xml = loadResourceContent(fileName);
            EsqObjectKinds kinds = xmlMapper.readValue(xml, EsqObjectKinds.class);
            if (kinds != null && kinds.getKinds() != null) {
                kinds.getKinds().forEach(k -> storage.put(k.getId(), k));
            }
            log.info("Object Kinds loaded successfully");
            //System.out.println(kinds);
            ret = true;
        } catch (Exception e) {
            System.out.println("failed load object kinds: " + e);
            log.error("EsqObjectKindStorage: init failed: {}", e.getMessage());
            devLog.error("EsqObjectKindStorage: init failed: {}", e.getMessage(), e);
        }
         return ret;
    }



/*
    public void serializationExample() {
         try {
             EsqObjectKind system = new EsqObjectKind(
                1,
                "system",
                "System",
                "systems",
                "Esquire system root",
                true,
                false,
                false,
                "img/folders/system.ico",
                true,
                false,
                "BT",
                Arrays.asList(new EsqColumnHeaderDef("name", "Name"), new EsqColumnHeaderDef("desc", "Description")),
                Arrays.asList(20),
                null);

             EsqObjectKind sysAdmins   = new EsqObjectKind(
                     2,
                     "sysAdmins",
                     "Sys Admin-s",
                     "sysAdmins",
                     "System Admins virtual folder",
                     false,
                     false,
                     false,
                     "img/folders/folder.ico",
                     false,
                     false,
                     "BTb",
                     null,
                     Arrays.asList(30,32),
                     null);

             EsqObjectKind merchant   = new EsqObjectKind(
                     36,
                     "merchant",
                     "Merchant",
                     "merchants",
                     "Merchant entity",
                     false,
                     true,
                     false,
                     "img/merchant.ico",
                     true,
                     true,
                     "BTb",
                     Arrays.asList(new EsqColumnHeaderDef("name", "Account"), new EsqColumnHeaderDef("desc", "Description")),
                     Arrays.asList(52),
                     Arrays.asList("move","key"));


             List<EsqObjectKind> kList = Arrays.asList(new EsqObjectKind[]{system, sysAdmins, merchant});
             EsqObjectKinds kinds = new EsqObjectKinds();
             kinds.setKinds(kList);

             XmlMapper xmlMapper = new XmlMapper();
             // Enable pretty print for better readability
             xmlMapper.enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);

             String xml = xmlMapper.writeValueAsString(kinds);
             System.out.println(xml);

         } catch(Exception e) {
             e.printStackTrace();
         }
    }
*/
}

