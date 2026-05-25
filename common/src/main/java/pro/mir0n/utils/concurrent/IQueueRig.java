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
 */

package pro.mir0n.utils.concurrent;

import org.slf4j.Logger;

public interface IQueueRig <E>
{
    interface IQueueWorker <E> {
        void process (E item);
    }
    interface IErrorListener <E> {
        E onError (Throwable error, E element);
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
     

// possible extentions
    // a) Lifecycle monitor
    //public interface ILifecycleAware <E> {
    //    public void onStart();
    //    public void onShutdown();
    //}

    // b) bulk processing
    //public void put(Collection <E> elements);
    //public interface IQueueListWorker <E> extends IQueueWorker <E> {
    //    public void process (Collection<E> elements);
    //}
    //public interface IErrorListListener <E> extends IErrorElementListener <E>{
    //    public Collection <E> onError (Throwable error, Collection <E> elements);
    //}


    // c) monitoring
    //public default int getActiveTaskCount() {
    //    return 1;
    //}


    // d) gentle queue access: knock on a door first
    //public default boolean tryPut(E element) {
    //    put(element);
    //    return true;
    //};

}
