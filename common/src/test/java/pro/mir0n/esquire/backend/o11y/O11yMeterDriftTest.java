package pro.mir0n.esquire.backend.o11y;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The drift guard behind the o11y sweep (T11/I48 phase b).
 *
 * <p>{@code test/o11y/o11y-verify.py} proves the meters it DECLARES are live. It is therefore structurally
 * blind to a meter nobody declared -- and its list is hand-kept, so the list falls behind the code. It had:
 * {@code esq.biz.acct.tx.duration} and {@code esq.biz.keep.write.duration} were being collected and verified by
 * NOTHING, each of them the {@code .duration} twin of a {@code .total} that IS declared. Two authors added the
 * counter and forgot the timer, independently; a third (this one) then did it again with the BFF's upstream
 * meter. A convention does not survive a new author -- the same lesson {@link NoRawGaugeBuilderTest} exists for.
 *
 * <p>This test turns "remember to declare it" into a rule: every meter/gauge the Java code REGISTERS must appear
 * in one of o11y-verify's declared lists, or the build fails and names the meter. And the reverse -- a declared
 * {@code esq.*}/{@code messaging.*} name that no code emits any more -- fails too, because a sweep asserting a
 * meter that cannot exist is a sweep that will be switched off.
 *
 * <p><b>Scope, deliberately.</b> METERS and GAUGES only -- the things REGISTERED by an explicit call.
 * <ul>
 *   <li>SPANS are out: {@code @EsqTraced} names ({@code esq.svc.*}) are governed by the trace-node checks, and
 *       whether their timer halves should be declared/drawn at all is I48 phase (c)'s open question. Including
 *       them here would fail the build on 18 signals over a question nobody has decided.</li>
 *   <li>The BFF's {@code esq_bff_*} meters are out: they live in the {@code esquire.explorer} REPO, which a build
 *       of {@code esquire.services} cannot see. Their drift is caught by {@code o11y-inventory.py}, which runs where
 *       both trees exist. This is a real hole in the guard and is named here rather than hidden. They share the
 *       {@code esq_} family prefix since I48/d, so the exclusion is stated EXPLICITLY below -- it is a fact about
 *       which REPO emits them, and must not be re-derived from how they happen to be spelled.</li>
 * </ul>
 *
 * <p><b>Why it reads the arguments and not just the first literal.</b> A meter's name is not always the token
 * after the paren:
 * <pre>    EsqBizMeters.count(moved ? "esq.biz.move.processed.total" : "esq.biz.move.failed.total", "kind", k);</pre>
 * A {@code count\(\s*"([^"]+)"} scan silently drops BOTH names -- it did, which is how those two came to look
 * "declared but never emitted". So the whole argument list is read (paren-matched), and every literal shaped like
 * a signal name is taken. Shape is what separates a NAME from a TAG KEY: names are dotted namespaces
 * ({@code esq.*}, {@code messaging.*}); tag keys in the same call are bare words ({@code "kind"}, {@code "op"}).
 */
class O11yMeterDriftTest {

    /**
     * Everything that creates a METER. Keep in step with o11y-inventory.py's CALL_PATTERNS.
     *
     * <p>The Observation marks ({@code @EsqTraced}, {@code EsqTraceMark.around}) are in this list, which reads
     * oddly for a "meter" scan until you remember an Observation is CROSS-PILLAR (I41): ONE mark yields a span
     * AND a timer. {@code @EsqTraced(name = "esq.svc.tree")} really does register {@code esq_svc_tree_seconds} --
     * 66 such series are live -- so for meter purposes a mark IS an emitter. Leaving them out made this guard
     * declare its own truth false: the moment the esq_svc_* timers were declared, the reverse check reported
     * them as "declared but nothing emits them".
     */
    private static final List<String> REGISTRATION_CALLS = List.of(
            "EsqBizMeters.count",
            "EsqBizMeters.time",
            "EsqBizMeters.gauge",
            "registry.counter",
            "registry.timer",
            "registry.summary",
            "registry.gauge",
            "meterRegistry.counter",
            "meterRegistry.timer",
            "meterRegistry.summary",
            "meterRegistry.gauge",
            "EsqGauge.register",
            // Cross-pillar marks -- a span AND a timer. @EsqTraced names itself with a NAMED parameter
            // (name = "..."), which is why the argument span is read rather than the first literal.
            "@EsqTraced",
            "EsqTraceMark.around",
            "EsqTraceMark.aroundChecked");

    /** A mark whose observation name is a CONSTANT rather than a call argument (EsqAsyncTrace's esq.async). */
    private static final Pattern OBS_NAME_CONSTANT =
            Pattern.compile("OBS_NAME\\s*=\\s*\"((?:esq|messaging)\\.[a-z][a-z0-9.]*)\"");

    /** A signal NAME (dotted namespace), never a tag key (a bare word). */
    private static final Pattern NAME_SHAPE = Pattern.compile("\"((?:esq|messaging)\\.[a-z][a-z0-9.]*)\"");

    /** The declared lists this guard holds the code to. Trace-node lists are NOT meters and are excluded. */
    private static final List<String> DECLARED_LISTS =
            List.of("METERS_EXPECTED", "METERS_CONDITIONAL", "GAUGES");

    @Test
    void everyRegisteredMeterIsDeclaredInO11yVerify() throws IOException {
        Path root = servicesRoot();
        Map<String, String> emitted = scanEmittedMeters(root);   // signal -> the file that registers it
        Set<String> declaredStems = declaredStems(root);

        assertTrue(!emitted.isEmpty(),
                "scanned no meters at all -- the scan is broken, not the code (a guard that matches nothing "
                + "passes vacuously forever)");

        List<String> undeclared = new ArrayList<>();
        for (Map.Entry<String, String> e : emitted.entrySet()) {
            if (!declaredStems.contains(promStem(e.getKey()))) {
                undeclared.add(e.getKey() + "   (registered in " + e.getValue() + ")");
            }
        }
        if (!undeclared.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            msg.append("These meters are COLLECTED but declared in NO list of test/o11y/o11y-verify.py, so the "
                       + "live sweep does not check them and nobody would notice them going dark:\n");
            for (String u : undeclared) {
                msg.append("  - ").append(u).append('\n');
            }
            msg.append("\nAdd each to METERS_EXPECTED (empty = a bug) or METERS_CONDITIONAL (legitimately empty "
                       + "until its condition happens), using the PROMETHEUS name -- dots become underscores and "
                       + "a timer gains _seconds (esq.biz.x.duration -> esq_biz_x_duration_seconds).\n"
                       + "A meter and its TWIN belong in the SAME list: if x.total is EXPECTED then x.duration is "
                       + "too -- breaking that pairing by hand is exactly the drift this guard exists to stop.\n"
                       + "Then refresh the sheet: python test/o11y/o11y-inventory.py");
            fail(msg.toString());
        }
    }

    @Test
    void everyDeclaredEsquireMeterIsStillEmitted() throws IOException {
        Path root = servicesRoot();
        Set<String> emittedStems = new TreeSet<>();
        for (String name : scanEmittedMeters(root).keySet()) {
            emittedStems.add(promStem(name));
        }

        List<String> stale = new ArrayList<>();
        for (String declared : declaredNames(root)) {
            // Only names THIS BUILD CAN SCAN may be checked here: http_server_requests_* is Boot's, and the BFF's
            // esq_bff_* live in the explorer repo -- neither is scannable from here, so absence proves nothing.
            // The esq_bff_* exclusion is EXPLICIT on purpose (I48/d): the BFF meters used to be named bff_* and were
            // skipped only as a side effect of not matching "esq_". Unifying the prefix pulled them INTO this check
            // and failed the build on two meters that are alive and well -- one repo away. The rule being applied is
            // REPO VISIBILITY, not spelling, so it is now written as such and cannot be re-broken by a rename.
            boolean ours = (declared.startsWith("esq_") || declared.startsWith("messaging_"))
                           && !declared.startsWith("esq_bff_");
            if (ours && !emittedStems.contains(promStem(declared))) {
                stale.add(declared);
            }
        }
        if (!stale.isEmpty()) {
            fail("test/o11y/o11y-verify.py declares these Esquire meters, but NO code registers them any more "
                 + "-- the sweep is asserting meters that cannot appear, which is how a sweep earns a reputation "
                 + "for crying wolf and gets ignored:\n  - " + String.join("\n  - ", stale)
                 + "\nRemove them from the declared lists, or restore whatever stopped emitting them.");
        }
    }

    // ---------------------------------------------------------------------------------------------------------

    /** Every meter/gauge name registered by an explicit call in the Java main sources -> the file it lives in. */
    private static Map<String, String> scanEmittedMeters(Path root) throws IOException {
        Map<String, String> ret = new LinkedHashMap<>();
        for (Path src : mainSourceRoots(root)) {
            try (Stream<Path> walk = Files.walk(src)) {
                List<Path> javaFiles = walk.filter(Files::isRegularFile)
                        .filter(f -> f.getFileName().toString().endsWith(".java"))
                        .toList();
                for (Path f : javaFiles) {
                    String body = codeWithStrings(Files.readString(f, StandardCharsets.UTF_8));
                    for (String call : REGISTRATION_CALLS) {
                        int from = 0;
                        while (true) {
                            int at = body.indexOf(call + "(", from);
                            if (at < 0) {
                                break;
                            }
                            int open = at + call.length();
                            Matcher m = NAME_SHAPE.matcher(argSpan(body, open));
                            while (m.find()) {
                                ret.putIfAbsent(m.group(1), root.relativize(f).toString());
                            }
                            from = open + 1;
                        }
                    }
                    Matcher constant = OBS_NAME_CONSTANT.matcher(body);
                    while (constant.find()) {
                        ret.putIfAbsent(constant.group(1), root.relativize(f).toString());
                    }
                }
            }
        }
        return ret;
    }

    /** The text between a call's '(' and its MATCHING ')', so a ternary or a nested call stays whole. */
    private static String argSpan(String src, int openParen) {
        int depth = 0;
        boolean inString = false, inChar = false;
        for (int i = openParen; i < src.length(); i++) {
            char c = src.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (inChar) {
                if (c == '\\') {
                    i++;
                } else if (c == '\'') {
                    inChar = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return src.substring(openParen, i);
                }
            }
        }
        return src.substring(openParen, Math.min(src.length(), openParen + 400));
    }

    /**
     * Source with COMMENTS removed and STRING LITERALS KEPT -- the mirror image of
     * {@link NoRawGaugeBuilderTest}'s codeOnly(). That guard bans a CALL, so it strips literals to avoid flagging
     * a file for merely MENTIONING the name. Here the meter name IS a literal, so literals must survive -- but
     * comments must not, or a javadoc showing an example registration is scanned as a real one.
     */
    private static String codeWithStrings(String src) {
        StringBuilder ret = new StringBuilder(src.length());
        boolean inBlock = false, inLine = false, inString = false, inChar = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char next = (i + 1 < src.length()) ? src.charAt(i + 1) : '\0';
            if (inBlock) {
                if (c == '*' && next == '/') {
                    inBlock = false;
                    i++;
                }
            } else if (inLine) {
                if (c == '\n') {
                    inLine = false;
                    ret.append(c);
                }
            } else if (inString) {
                ret.append(c);
                if (c == '\\') {
                    ret.append(next);
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (inChar) {
                if (c == '\\') {
                    i++;
                } else if (c == '\'') {
                    inChar = false;
                }
            } else if (c == '/' && next == '*') {
                inBlock = true;
                i++;
            } else if (c == '/' && next == '/') {
                inLine = true;
                i++;
            } else if (c == '"') {
                inString = true;
                ret.append(c);
            } else if (c == '\'') {
                inChar = true;
            } else {
                ret.append(c);
            }
        }
        return ret.toString();
    }

    /** The names declared across o11y-verify's meter/gauge lists, verbatim (Prometheus spelling). */
    private static Set<String> declaredNames(Path root) throws IOException {
        Path verify = root.resolve("test/o11y/o11y-verify.py");
        assertTrue(Files.isRegularFile(verify), "cannot find " + verify + " -- this guard has nothing to check "
                + "against, which must fail loudly rather than pass by default");
        String body = Files.readString(verify, StandardCharsets.UTF_8);

        Set<String> ret = new TreeSet<>();
        for (String list : DECLARED_LISTS) {
            Matcher block = Pattern.compile(Pattern.quote(list) + "\\s*=\\s*\\[(.*?)^\\]",
                    Pattern.DOTALL | Pattern.MULTILINE).matcher(body);
            assertTrue(block.find(), "o11y-verify.py has no " + list + " list -- the guard cannot verify a list "
                    + "that moved or was renamed, and must not pass silently");
            // Drop '#' comments first: a quoted phrase inside an explanatory note is prose, not a meter. A note
            // reading ... what "after some activity (an e2e run)" means ... really did register itself as an
            // asset named `after some activity (an e2e run)` before this strip existed.
            String entries = block.group(1).replaceAll("#[^\\n]*", "");
            Matcher name = Pattern.compile("\"([^\"]+)\"").matcher(entries);
            while (name.find()) {
                ret.add(name.group(1));
            }
        }
        return ret;
    }

    private static Set<String> declaredStems(Path root) throws IOException {
        Set<String> ret = new TreeSet<>();
        for (String d : declaredNames(root)) {
            ret.add(promStem(d));
        }
        return ret;
    }

    /**
     * The Prometheus base name with the unit/type suffix removed -- the one shape both spellings agree on.
     *
     * <p>Micrometer renders {@code esq.biz.kc.sync.duration} as {@code esq_biz_kc_sync_duration_seconds}, and the
     * declared list carries the Prometheus spelling, so neither side can be compared raw. (Unifying the naming is
     * I48 phase (d); when that lands this normalisation gets simpler, not more complex.)
     */
    private static String promStem(String name) {
        String ret = name.replace('.', '_');
        if (ret.endsWith("_seconds")) {
            ret = ret.substring(0, ret.length() - "_seconds".length());
        } else if (ret.endsWith("_total")) {
            ret = ret.substring(0, ret.length() - "_total".length());
        }
        return ret;
    }

    private static Path servicesRoot() {
        Path ret = null;
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && ret == null) {
            if (Files.isDirectory(dir.resolve("common")) && Files.isDirectory(dir.resolve("messaging"))) {
                ret = dir;
            }
            dir = dir.getParent();
        }
        assertTrue(ret != null, "could not locate the services root from " + Paths.get("").toAbsolutePath());
        return ret;
    }

    private static List<Path> mainSourceRoots(Path root) throws IOException {
        List<Path> ret;
        try (Stream<Path> modules = Files.list(root)) {
            ret = modules.filter(Files::isDirectory)
                    .map(m -> m.resolve("src/main/java"))
                    .filter(Files::isDirectory)
                    .toList();
        }
        assertTrue(!ret.isEmpty(), "no module source roots found under " + root);
        return ret;
    }
}
