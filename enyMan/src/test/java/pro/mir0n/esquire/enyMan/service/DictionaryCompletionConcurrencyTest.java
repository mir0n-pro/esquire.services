/*
 *  Esquire frameworks (tm)
 *  EnyMan service -- tests
 *
 *  Copyright(c) 2001, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 */
package pro.mir0n.esquire.enyMan.service;

import org.junit.jupiter.api.Test;
import pro.mir0n.esquire.backend.dto.EsqEntity;
import pro.mir0n.esquire.backend.dto.EsqEntityDictionary;
import pro.mir0n.esquire.backend.dto.EsqEntityLayer;
import pro.mir0n.esquire.backend.jpa.EsqCustomEntityFieldJpa;
import pro.mir0n.esquire.backend.storage.EsqEntityDictionaryStorage;
import pro.mir0n.esquire.enyMan.jpa.EsqEntityDictionaryRepository;
import pro.mir0n.esquire.enyMan.jpa.EsqMoveRecord;
import pro.mir0n.esquire.enyMan.service.impl.AEnyManService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression guard for the dictionary-completion race in {@code AEnyManService.completedDictionary}.
 *
 * <p>The dictionary returned by {@link EsqEntityDictionaryStorage#get(int)} is a SHARED singleton. The first
 * request for a kind lazily merges its custom params into that shared instance ({@code mapTo} adds layers and
 * calls {@code sortLayers()}). Before the fix this was guarded only by an unsynchronized check-then-act on the
 * {@code completed} flag, so when several requests hit a COLD (not-yet-completed) dictionary at once -- the
 * situation right after a pod restart, when the first concurrent creates of a kind arrive together -- one thread
 * sorted the shared {@code layers} list while another mutated it and {@code ArrayList.sort} threw
 * ConcurrentModificationException.
 *
 * <p>The fix completes the dictionary ONCE under its own monitor (double-checked, with a volatile
 * {@code completed} fast path). This test drives many threads at a freshly-cold dictionary for many rounds and
 * asserts (a) no thread ever throws, and (b) the shared dictionary is merged EXACTLY once -- the custom layers
 * are present and each carries its single field, proving the work did not run on multiple threads.
 */
class DictionaryCompletionConcurrencyTest {

    private static final int KIND        = 990001;   // a test-only kind, isolated from the real dictionary set
    private static final int THREADS     = 32;
    private static final int ROUNDS      = 200;
    private static final int BASE_LAYERS = 3;        // base layers 1..3
    private static final int CUST_LO     = 50;       // custom layers 50..57 (8 layers, one field each)
    private static final int CUST_HI     = 57;
    private static final int CUST_COUNT  = CUST_HI - CUST_LO + 1;

    @Test
    void coldConcurrentCompletion_isSafe_andMergesExactlyOnce() throws Exception {
        EsqEntityDictionaryRepository repo = mock(EsqEntityDictionaryRepository.class);
        when(repo.findCustom(KIND)).thenAnswer(inv -> customFields());   // fresh list each call
        TestService svc = new TestService(repo);

        AtomicReference<Throwable> firstError = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                EsqEntityDictionaryStorage.getInstance().init(freshColdDict());   // overwrite -> cold again
                CountDownLatch start = new CountDownLatch(1);
                CountDownLatch done  = new CountDownLatch(THREADS);
                for (int t = 0; t < THREADS; t++) {
                    pool.submit(() -> {
                        try {
                            start.await();
                            svc.complete(KIND);
                        } catch (Throwable ex) {
                            firstError.compareAndSet(null, ex);
                        } finally {
                            done.countDown();
                        }
                    });
                }
                start.countDown();
                done.await(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(firstError.get())
                .as("concurrent cold completion must not throw (shared-singleton race)")
                .isNull();

        // The shared dictionary is completed once and merged once: base layers + the custom layers, each custom
        // layer carrying exactly its single field (a second merge would have appended duplicate fields).
        EsqEntityDictionary done = EsqEntityDictionaryStorage.getInstance().get(KIND);
        assertThat(done.isCompleted()).isTrue();
        assertThat(done.getLayers()).hasSize(BASE_LAYERS + CUST_COUNT);
        for (int layer = CUST_LO; layer <= CUST_HI; layer++) {
            EsqEntityLayer cl = done.findLayer(layer);
            assertThat(cl).as("custom layer " + layer + " present").isNotNull();
            assertThat(cl.getFields()).as("custom layer " + layer + " merged exactly once").hasSize(1);
        }
    }

    // A cold dictionary with a few base layers so sortLayers has real work and findLayer iterates.
    private static EsqEntityDictionary freshColdDict() {
        EsqEntityDictionary d = new EsqEntityDictionary();
        d.setKind(KIND);                       // completed defaults to false -> cold
        List<EsqEntityLayer> base = new ArrayList<>();
        for (int layer = 1; layer <= BASE_LAYERS; layer++) {
            EsqEntityLayer l = new EsqEntityLayer();
            l.setLayer(layer);
            l.setTitle("base-" + layer);
            l.setFields(new ArrayList<>());
            base.add(l);
        }
        d.setLayers(base);
        return d;
    }

    // Custom params spread across several NEW layers, so each completion ADDS layers to the shared list and then
    // sorts it -- the structural-modification-during-sort that tripped ArrayList.sort before the fix.
    private static List<EsqCustomEntityFieldJpa> customFields() {
        List<EsqCustomEntityFieldJpa> fields = new ArrayList<>();
        for (int layer = CUST_LO; layer <= CUST_HI; layer++) {
            EsqCustomEntityFieldJpa f = new EsqCustomEntityFieldJpa();
            f.setLayer(layer);
            f.setSort(layer);
            f.setName("custom_" + layer);
            f.setType("string");
            fields.add(f);
        }
        return fields;
    }

    // Minimal concrete AEnyManService: exposes the protected completedDictionary; the command methods are unused.
    private static final class TestService extends AEnyManService {
        TestService(EsqEntityDictionaryRepository repo) {
            super(repo);
        }

        EsqEntityDictionary complete(int kind) {
            return completedDictionary(kind);
        }

        @Override
        public EsqEntity esquireCommand(int kind, String id, String cmd) {
            return null;
        }

        @Override
        public EsqEntity esquireCommandSave(int kind, String id, String cmd, Map<String, Object> fields, List<String> roles) {
            return null;
        }

        @Override
        public EsqEntity esquireCommandNew(int kind, String parentId, String cmd, Map<String, Object> fields, List<String> roles) {
            return null;
        }

        @Override
        public Long esquireCommandDelete(int kind, String id, String cmd, List<String> roles) {
            return null;
        }

        @Override
        public List<EsqMoveRecord> esquireCommandMove(int kind, String id, String distId, List<String> roles) {
            return null;
        }
    }
}
