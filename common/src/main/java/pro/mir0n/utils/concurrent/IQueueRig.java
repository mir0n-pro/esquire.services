/*
 *  mir0n java common frameworks
 *
 *  Copyright(c) 1998, 2026 mir0n&co www.mir0n.pro
 *  mailto:mir0n.the.programmer@gmail.com
 *
 *  History:
 * 05/20/2026 mir0n  created: generic active-object queue contract <E> -- bounded FIFO drained by
 *                   a single worker (IQueueWorker) behind a processing gate (setProcessing: when
 *                   false the worker leaves the queue UNTOUCHED), with put / size / clear and an
 *                   IErrorListener seam. Generalized from bizTree.taijitu; BoundedQueueRig implements.
 * 05/23/2026 mir0n  dropped redundant public modifiers from the interface members.
 * 06/02/2026 mir0n  tryPut(E) default method enabled (was commented out); default delegates to
 *                   put(E) and returns true, so existing rigs keep blocking semantics.
 * 06/02/2026 mir0n  bulk processing added: IQueueListWorker (process(ArrayList, ISignaler) returning the
 *                   unprocessed remainder), ISignaler (isRunning / isProcessing / shouldContinue), and
 *                   IListErrorListener; put(Collection) + setBulkThreshold(int) default methods
 */

package pro.mir0n.utils.concurrent;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public interface IQueueRig <E>
{
    interface IQueueWorker <E> {
        void process (E item);
    }
    interface IErrorListener <E> {
        E onError (Throwable error, E element);
    }

    /** A worker that can ALSO process a bulk of items in one call (e.g. one DB transaction).
     *  It still IS an {@link IQueueWorker}, so the rig drains it one-by-one while the backlog is
     *  small and switches to {@link #process(ArrayList, ISignaler)} once the queue is overloaded. */
    interface IQueueListWorker <E> extends IQueueWorker <E> {
        /**
         * Process a bulk of items. The list is an {@link ArrayList} for fast indexed iteration
         * ({@code for (int i = 0; i < items.size(); i++)}). The worker SHOULD poll {@code signaler}
         * periodically (e.g. every N items) and stop early once it is no longer
         * {@link ISignaler#shouldContinue()} -- returning the items it did NOT process so the rig
         * can re-queue them. On a clean finish return an empty list or {@code null}.
         *
         * <p>A Throwable is NOT swallowed inside the worker: it propagates to the rig, which routes
         * the FULL bulk to the {@link IListErrorListener} -- same bypass contract as the single-item
         * {@link IQueueWorker#process(Object)} path.
         *
         * @return the unprocessed remainder (empty or {@code null} = everything was handled)
         */
        List<E> process (ArrayList<E> items, ISignaler signaler);
    }

    /** Read-only view of the rig's run/process state -- the single source of truth the rig reads
     *  internally AND hands to a list worker so it can abort a long bulk when the rig is shutting
     *  down or its processing gate closes. */
    interface ISignaler {
        boolean isRunning ();
        boolean isProcessing ();
        /** True while the rig still wants the bulk to keep going. */
        default boolean shouldContinue () { return isRunning() && isProcessing(); }
    }

    /** Bulk counterpart of {@link IErrorListener}: invoked when a list worker throws. Receives the
     *  FULL bulk as it was handed to the worker and returns the items to CONTINUE with (e.g. those
     *  after the failure point, if the impl can extract it from the throwable), or {@code null} to
     *  STOP the bulk. A trivial impl just returns {@code null} -- abandon the rest of this bulk. */
    interface IListErrorListener <E> extends IErrorListener <E> {
        List<E> onError (Throwable error, ArrayList<E> items);
    }

    void init(String name, Logger devLogger, int capacity);
    void setErrorListener (IErrorListener listener);

    /** Processing gate. When false the worker leaves the queue UNTOUCHED -- no items are
     *  dequeued; producers may still put(). When true the worker drains the queue in FIFO
     *  order. Starts false (paused); the owner enables it when ready to process. */
    void setProcessing(boolean enabled);

    void start();
    void shutdown();
    void put(E item);
    int size();

    /** Bulk-drop all queued items without processing them (e.g. discard buffered work
     *  after a failed load). The only removal other than normal worker processing. */
    void clear();
     

    /** Bulk put -- convenience; default loops {@link #put(Object)}. */
    default void put(Collection<E> elements) {
        for (E e : elements) {
            put(e);
        }
    }

    /** Backlog size above which a list worker is handed a bulk (drained one-by-one at/below it).
     *  No-op for rigs that don't batch. Set very high to force one-by-one (A/B / safety). */
    default void setBulkThreshold(int n) { }

// possible extentions
    // a) Lifecycle monitor
    //public interface ILifecycleAware <E> {
    //    public void onStart();
    //    public void onShutdown();
    //}

    // c) monitoring
    //public default int getActiveTaskCount() {
    //    return 1;
    //}


    // d) gentle queue access: knock on a door first
    default boolean tryPut(E element) {
        put(element);
        return true;
    };

}
