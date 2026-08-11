/*
 *  Esquire frameworks (tm)
 *  common library -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.backend;

import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Parses every {@code src/main/resources/META-INF/*.xml} in this module. */
class MappingXmlWellFormedTest {

    @Test
    void everyMappingXmlParses() throws Exception {
        File dir = new File("src/main/resources/META-INF");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".xml"));
        assertThat(files).as("mapping XMLs found in " + dir.getAbsolutePath()).isNotNull().isNotEmpty();

        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        List<String> broken = new ArrayList<>();
        for (File file : files) {
            try {
                f.newDocumentBuilder().parse(file);
            } catch (Exception e) {
                // The message names the row/column, which is what you need to find a stray "--".
                broken.add(file.getName() + " -> " + e.getMessage());
            }
        }
        assertThat(broken).as("malformed mapping XML (Hibernate would fail at BOOT, not here)").isEmpty();
    }
}
