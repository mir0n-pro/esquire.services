/*
 *  Esquire frameworks (tm)
 *  PacMan service -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.pacMan.acct.jpa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.Param;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.assertj.core.api.Assertions.assertThat;

/** Keeps the shipped ledger INSERT, its twin in the other dialect, and its Java signature in step. */
class AcctTransactionSqlTest {

    private static final String QUERY = "EsqAcctTransactionJpa.insertAcctTransaction";
    private static final String MAPPING = "EsqAcctTransactionJpaMapping";

    private static final List<String> DIALECTS = List.of("postgres", "oracle");

    /** The named-native-query body for one dialect, straight out of the shipped mapping file. */
    private static String query(String dialect, String name) throws Exception {
        String ret = null;
        try (InputStream in = AcctTransactionSqlTest.class
                .getResourceAsStream("/META-INF/" + dialect + "-acct-transaction.xml")) {
            assertThat(in).as(dialect + " mapping file is on the classpath").isNotNull();
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            NodeList qs = doc.getElementsByTagName("named-native-query");
            for (int i = 0; i < qs.getLength() && ret == null; i++) {
                Element e = (Element) qs.item(i);
                if (name.equals(e.getAttribute("name"))) {
                    ret = e.getElementsByTagName("query").item(0).getTextContent();
                }
            }
        }
        assertThat(ret).as(dialect + " has the query " + name).isNotNull();
        return ret;
    }

    /** The column names between the first pair of brackets -- the INSERT's column list. */
    private static List<String> columns(String sql) {
        int open = sql.indexOf('(');
        int close = sql.indexOf(") VALUES", open);
        if (close < 0) {
            close = sql.toUpperCase().indexOf(") VALUES", open);
        }
        List<String> ret = new ArrayList<>();
        for (String c : sql.substring(open + 1, close).split(",")) {
            ret.add(c.trim().toLowerCase());
        }
        return ret;
    }

    /** Every {@code :name} bind in the statement, in the order it appears, without duplicates. */
    private static Set<String> binds(String sql) {
        Set<String> ret = new LinkedHashSet<>();
        Matcher m = Pattern.compile(":([A-Za-z][A-Za-z0-9]*)").matcher(sql);
        while (m.find()) {
            ret.add(m.group(1));
        }
        return ret;
    }

    private static Method insertMethod() {
        Method ret = null;
        for (Method m : EsqAcctTransactionRepository.class.getDeclaredMethods()) {
            if ("insertAcctTransaction".equals(m.getName())) {
                ret = m;
            }
        }
        assertThat(ret).as("insertAcctTransaction is declared on the repository").isNotNull();
        return ret;
    }

    private static Set<String> paramNames(Method m) {
        Set<String> ret = new LinkedHashSet<>();
        for (Parameter p : m.getParameters()) {
            Param a = p.getAnnotation(Param.class);
            if (a != null) {
                ret.add(a.value());
            }
        }
        return ret;
    }

    @Test
    @DisplayName("both dialects carry the account change number, on the column list AND as a bind")
    void bothDialectsCarryTheAccountChangeNumber() throws Exception {
        for (String d : DIALECTS) {
            String sql = query(d, QUERY);
            assertThat(columns(sql)).as(d + " column list").contains("atr_acc_change_no");
            assertThat(binds(sql)).as(d + " binds").contains("accChangeNo");
        }
    }

    @Test
    @DisplayName("column count equals value count, per dialect -- a widened column list with no matching value")
    void columnCountMatchesValueCount() throws Exception {
        for (String d : DIALECTS) {
            String sql = query(d, QUERY);
            int cols = columns(sql).size();
            String values = sql.substring(sql.indexOf("VALUES", sql.indexOf('(')));
            int vals = values.substring(values.indexOf('(') + 1, values.lastIndexOf(')')).split(",").length;
            assertThat(vals).as(d + ": VALUES entries vs columns").isEqualTo(cols);
        }
    }

    @Test
    @DisplayName("THE DRIFT GUARD: the two dialects insert the SAME columns, in the same order")
    void dialectsAgreeOnTheColumnList() throws Exception {
        // Updating one dialect and forgetting the other is the exact failure this sprint hit twice. It is
        // invisible until a service runs against the other vendor, which the docker stack does not do.
        assertThat(columns(query("oracle", QUERY)))
                .as("oracle column list vs postgres")
                .isEqualTo(columns(query("postgres", QUERY)));
    }

    @Test
    @DisplayName("THE SIGNATURE GUARD: every bind in the SQL has a @Param, and every @Param is bound")
    void sqlBindsAndJavaParamsAgree() throws Exception {
        Set<String> params = paramNames(insertMethod());
        for (String d : DIALECTS) {
            Set<String> binds = binds(query(d, QUERY));
            assertThat(params).as(d + ": a bind with no @Param would fail at runtime").containsAll(binds);
            assertThat(binds).as(d + ": a @Param nothing binds is dead weight").containsAll(params);
        }
    }

    @Test
    @DisplayName("the result-set mapping reads the column back, in both dialects")
    void resultSetMappingCarriesTheColumn() throws Exception {
        for (String d : DIALECTS) {
            try (InputStream in = AcctTransactionSqlTest.class
                    .getResourceAsStream("/META-INF/" + d + "-acct-transaction.xml")) {
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
                NodeList fields = doc.getElementsByTagName("field-result");
                boolean found = false;
                for (int i = 0; i < fields.getLength() && !found; i++) {
                    Element f = (Element) fields.item(i);
                    found = "accChangeNo".equals(f.getAttribute("name"))
                            && "atr_acc_change_no".equals(f.getAttribute("column"));
                }
                assertThat(found).as(d + " " + MAPPING + " maps accChangeNo").isTrue();
            }
        }
    }
}
