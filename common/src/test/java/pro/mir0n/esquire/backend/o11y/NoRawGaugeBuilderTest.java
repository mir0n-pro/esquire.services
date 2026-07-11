package pro.mir0n.esquire.backend.o11y;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The architecture guard behind {@link EsqGauge} (T7 phase A).
 *
 * <p>Micrometer holds a gauge's state object WEAKLY. Every Esquire gauge reads its value through a supplier
 * lambda, and that lambda IS the state object -- so without {@code strongReference(true)} the next GC collects it
 * and the gauge silently reports NaN. A hand-written {@code .strongReference(true)} at each call site is a
 * CONVENTION, and a convention does not survive a new author.
 *
 * <p>This test is what turns the convention into a rule: {@code Gauge.builder} may be CALLED in exactly ONE file,
 * {@code EsqGauge.java}. Anywhere else and the build fails, with the offending file named. That is the difference
 * between a trap that is documented and a trap that is removed.
 *
 * <p>It bans the CALL, not the WORD: comments and string literals are stripped before the scan. A guard nobody
 * can write documentation about is broken -- the history header and the changes.txt entry of the very files
 * involved have to NAME the API being banned, and naming it must not be an offence.
 */
class NoRawGaugeBuilderTest {

    private static final String BANNED = "Gauge.builder";
    private static final String OWNER = "EsqGauge.java";

    @Test
    void gaugeBuilderIsConfinedToEsqGauge() throws IOException {
        Path root = servicesRoot();
        List<Path> offenders = new ArrayList<>();
        List<Path> owners = new ArrayList<>();

        for (Path src : mainSourceRoots(root)) {
            try (Stream<Path> walk = Files.walk(src)) {
                List<Path> javaFiles = walk.filter(Files::isRegularFile)
                        .filter(f -> f.getFileName().toString().endsWith(".java"))
                        .toList();
                for (Path f : javaFiles) {
                    String body = codeOnly(Files.readString(f, StandardCharsets.UTF_8));
                    if (body.contains(BANNED)) {
                        if (f.getFileName().toString().equals(OWNER)) {
                            owners.add(f);
                        } else {
                            offenders.add(f);
                        }
                    }
                }
            }
        }

        if (!offenders.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            msg.append(BANNED).append(" is confined to ").append(OWNER)
               .append(" -- Micrometer gauges are WEAK-referenced, so a raw builder without strongReference(true)")
               .append(" reports NaN once the supplier lambda is collected.\n")
               .append("Use EsqGauge.register(registry, name, supplier, tags...) instead. Offending file(s):\n");
            for (Path f : offenders) {
                msg.append("  - ").append(root.relativize(f)).append('\n');
            }
            fail(msg.toString());
        }

        // The owner must actually still own it: if EsqGauge stops calling Gauge.builder, this guard has quietly
        // stopped guarding anything (the ban would pass vacuously against a codebase with no gauges at all).
        assertEquals(1, owners.size(),
                "expected exactly one file to hold " + BANNED + " (" + OWNER + "), found " + owners);
    }

    /**
     * The java source with comments and string/char literal CONTENTS removed -- what is left is code.
     *
     * <p>A plain substring scan would flag a file for merely MENTIONING the banned call, which makes it
     * impossible to write the history header or the changes.txt entry that explain the ban (this test failed in
     * CI for exactly that: the EsqRodObserver header says it hands off "Gauge.builder" to EsqGauge). Stripping
     * literals as well as comments also closes the reverse hole -- a URL inside a string ("http://...") must not
     * be mistaken for the start of a line comment and hide real code after it on the same line.
     */
    private static String codeOnly(String src) {
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
            } else if (c == '/' && next == '*') {
                inBlock = true;
                i++;
            } else if (c == '/' && next == '/') {
                inLine = true;
                i++;
            } else if (c == '"') {
                inString = true;
            } else if (c == '\'') {
                inChar = true;
            } else {
                ret.append(c);
            }
        }
        return ret.toString();
    }

    /** Walk up from the module dir to the services root -- the one directory holding both common and messaging. */
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

    /** Every module's src/main/java -- the whole compiled surface, not just this module's. */
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
