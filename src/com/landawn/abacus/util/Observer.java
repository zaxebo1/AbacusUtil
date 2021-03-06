/*
 * Copyright (C) 2017 HaiYang Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.landawn.abacus.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.landawn.abacus.annotation.NonNull;
import com.landawn.abacus.util.function.Consumer;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.Predicate;

/**
 * 
 * 
 * @since 0.9
 * 
 * @author Haiyang Li
 * 
 */
public abstract class Observer<T> {

    private static final Object COMPLETE_FLAG = new Object();

    protected static final double INTERVAL_FACTOR = 3;

    protected static final Runnable EMPTY_ACTION = new Runnable() {
        @Override
        public void run() {
            // Do nothing;            
        }
    };

    protected static final Consumer<Throwable> ON_ERROR_MISSING = new Consumer<Throwable>() {
        @Override
        public void accept(Throwable t) {
            throw new RuntimeException(t);
        }
    };

    protected static final Executor asyncExecutor = Executors.newFixedThreadPool(IOUtil.IS_PLATFORM_ANDROID ? IOUtil.CPU_CORES : 32);

    protected static final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(IOUtil.IS_PLATFORM_ANDROID ? IOUtil.CPU_CORES : 32);

    static {
        scheduler.setRemoveOnCancelPolicy(true);
    }

    protected final Map<ScheduledFuture<?>, Long> scheduledFutures = new LinkedHashMap<>();
    protected final Dispatcher<Object> dispatcher;
    protected boolean hasMore = true;

    protected Observer() {
        this(new Dispatcher<>());
    }

    protected Observer(Dispatcher<Object> dispatcher) {
        this.dispatcher = dispatcher;
    }

    @SuppressWarnings("rawtypes")
    public static void complete(BlockingQueue<?> queue) {
        ((Queue) queue).offer(COMPLETE_FLAG);
    }

    public static <T> Observer<T> of(final BlockingQueue<T> queue) {
        N.requireNonNull(queue, "queue");

        return new BlockingQueueObserver<>(queue);
    }

    public static <T> Observer<T> of(final Collection<T> c) {
        return of(N.isNullOrEmpty(c) ? ImmutableIterator.EMPTY : c.iterator());
    }

    public static <T> Observer<T> of(final Iterator<T> iter) {
        N.requireNonNull(iter, "iterator");

        return new IteratorObserver<>(iter);
    }

    /**
     *  
     * @param delayInMillis
     * @return
     * @see <a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Observable.html#timer(long,%20java.util.concurrent.TimeUnit)">RxJava#timer</a>
     */
    public static Observer<Long> timer(long delayInMillis) {
        return timer(delayInMillis, TimeUnit.MILLISECONDS);
    }

    /**
     *  
     * @param delay
     * @param unit
     * @return
     * @see <a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Observable.html#timer(long,%20java.util.concurrent.TimeUnit)">RxJava#timer</a>
     */
    public static Observer<Long> timer(long delay, TimeUnit unit) {
        N.checkArgument(delay >= 0, "delay can't be negative");
        N.requireNonNull(unit, "Time unit can't be null");

        return new TimerObserver<>(delay, unit);
    }

    /**
     *   
     * @param periodInMillis
     * @return
     * @see <a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Observable.html#interval(long,%20long,%20java.util.concurrent.TimeUnit)">RxJava#interval</a>
     */
    public static Observer<Long> interval(long periodInMillis) {
        return interval(0, periodInMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * @param initialDelayInMillis
     * @param periodInMillis
     * @return
     * @see <a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Observable.html#interval(long,%20long,%20java.util.concurrent.TimeUnit)">RxJava#interval</a>
     */
    public static Observer<Long> interval(long initialDelayInMillis, long periodInMillis) {
        return interval(initialDelayInMillis, periodInMillis, TimeUnit.MILLISECONDS);
    }

    /**
     *   
     * @param period
     * @param unit
     * @return
     * @see <a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Observable.html#interval(long,%20long,%20java.util.concurrent.TimeUnit)">RxJava#interval</a>
     */
    public static Observer<Long> interval(long period, TimeUnit unit) {
        return interval(0, period, unit);
    }

    /**
     *  
     * @param initialDelay
     * @param period
     * @param unit
     * @return
     * @see <a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Observable.html#interval(long,%20long,%20java.util.concurrent.TimeUnit)">RxJava#interval</a>
     */
    public static Observer<Long> interval(long initialDelay, long period, TimeUnit unit) {
        N.checkArgument(initialDelay >= 0, "initialDelay can't be negative");
        N.checkArgument(period > 0, "period can't be 0 or negative");
        N.requireNonNull(unit, "Time unit can't be null");

        return new IntervalObserver<>(initialDelay, period, unit);
    }

    /**
     * 
     * @param intervalDurationInMillis
     * @return this instance.
     * @see <a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Observable.html#debounce(long,%20java.util.concurrent.TimeUnit,%20io.reactivex.Scheduler)">RxJava#debounce</a>
     */
    public Observer<T> debounce(final long intervalDurationInMillis) {
        return debounce(intervalDurationInMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 
     * @param intervalDuration
     * @param unit
     * @return this instance.
     * @see <a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Observable.html#debounce(long,%20java.util.concurrent.TimeUnit,%20io.reactivex.Scheduler)">RxJava#debounce</a>
     */
    public Observer<T> debounce(final long intervalDuration, final TimeUnit unit) {
        N.checkArgument(intervalDuration >= 0, "Interval can't be negative");
        N.requireNonNull(unit, "Time unit can't be null");

        if (intervalDuration == 0) {
            return this;
        }

        final long intervalDurationInMillis = unit.toMillis(intervalDuration);

        dispatcher.append(new Dispatcher<Object>() {
            private long prevTimestamp = 0;
            private long lastScheduledTime = 0;

            @Override
            public void onNext(final Object param) {
                synchronized (holder) {
                    final long now = N.currentMillis();

                    if (holder.value() == N.NULL_MASK || now - lastScheduledTime > intervalDurationInMillis * INTERVAL_FACTOR) {
                        holder.setValue(param);
                        prevTimestamp = now;

                        schedule(intervalDuration, unit);
                    } else {
                        holder.setValue(param);
                        prevTimestamp = now;
                    }
                }
            }

            private void schedule(final long delay, final TimeUnit unit) {
                try {
                    scheduler.schedule(new Runnable() {
                        @Override
                        public void run() {
                            final long pastIntervalInMills = N.currentMillis() - prevTimestamp;

                            if (pastIntervalInMills >= intervalDurationInMillis) {
                                Object lastParam = null;

                                synchronized (holder) {
                                    lastParam = holder.value();
                                    holder.setValue(N.NULL_MASK);
                                }

                                if (lastParam != N.NULL_MASK && downDispatcher != null) {
                                    downDispatcher.onNext(lastParam);
                                }
                            } else {
                                schedule(intervalDurationInMillis - pastIntervalInMills, TimeUnit.MILLISECONDS);
                            }
                        }
                    }, delay, unit);

                    lastScheduledTime = N.currentMillis();
                } catch (Throwable e) {
                    holder.setValue(N.NULL_MASK);

                    if (downDispatcher != null) {
                        downDispatcher.onError(e);
                    }
                }
            }
        });

        return this;
    }

    /**
     * 
     * @param intervalDurationInMillis
     * @return this instance.
     * @see <a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Observable.html#throttleFirst(long,%20java.util.concurrent.TimeUnit)">RxJava#throttleFirst</a>
     */
    public Observer<T> throttleFirst(final long intervalDurationInMillis) {
        return throttleFirst(intervalDurationInMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 
     * @param intervalDuration
     * @param unit
     * @return this instance.
     * @see <a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Observable.html#throttleFirst(long,%20java.util.concurrent.TimeUnit)">RxJava#throttleFirst</a>
     */
    public Observer<T> throttleFirst(final long intervalDuration, final TimeUnit unit) {
        N.checkArgument(intervalDuration >= 0, "Interval can't be negative");
        N.requireNonNull(unit, "Time unit can't be null");

        if (intervalDuration == 0) {
            return this;
        }

        final long intervalDurationInMillis = unit.toMillis(intervalDuration);

        dispatcher.append(new Dispatcher<Object>() {
            private long lastScheduledTime = 0;

            @Override
            public void onNext(final Object param) {
                synchronized (holder) {
                    final long now = N.currentMillis();

                    if (holder.value() == N.NULL_MASK || now - lastScheduledTime > intervalDurationInMillis * INTERVAL_FACTOR) {
                        holder.setValue(param);

                        try {
                            scheduler.schedule(new Runnable() {
                                @Override
                                public void run() {
                                    Object firstParam = null;

                                    synchronized (holder) {
                                        firstParam = holder.value();
                                        holder.setValue(N.NULL_MASK);
                                    }

                                    if (firstParam != N.NULL_MASK && downDispatcher != null) {
                                        downDispatcher.onNext(firstParam);
                                    }
                                }
                            }, intervalDuration, unit);

                            lastScheduledTime = now;
                        } catch (Throwable e) {
                            holder.setValue(N.NULL_MASK);

                            if (downDispatcher != null) {
                                downDispatcher.onError(e);
                            }
                        }
                    }
                }
            }
        });

        return this;
    }

    /**
     * 
     * @param intervalDurationInMillis
     * @return this instance.
     * @see <a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Observable.html#throttleLast(long,%20java.util.concurrent.TimeUnit)">RxJava#throttleLast</a>
     */
    public Observer<T> throttleLast(final long intervalDurationInMillis) {
        return throttleLast(intervalDurationInMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 
     * @param intervalDuration
     * @param unit
     * @return this instance.
     * @see <a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Observable.html#throttleLast(long,%20java.util.concurrent.TimeUnit)">RxJava#throttleLast</a>
     */
    public Observer<T> throttleLast(final long intervalDuration, final TimeUnit unit) {
        N.checkArgument(intervalDuration >= 0, "Delay can't be negative");
        N.requireNonNull(unit, "Time unit can't be null");

        if (intervalDuration == 0) {
            return this;
        }

        final long intervalDurationInMillis = unit.toMillis(intervalDuration);

        dispatcher.append(new Dispatcher<Object>() {
            private long lastScheduledTime = 0;

            @Override
            public void onNext(final Object param) {
                synchronized (holder) {
                    final long now = N.currentMillis();

                    if (holder.value() == N.NULL_MASK || now - lastScheduledTime > intervalDurationInMillis * INTERVAL_FACTOR) {
                        holder.setValue(param);

                        try {
                            scheduler.schedule(new Runnable() {
                                @Override
                                public void run() {
                                    Object lastParam = null;

                                    synchronized (holder) {
                                        lastParam = holder.value();
                                        holder.setValue(N.NULL_MASK);
                                    }

                                    if (lastParam != N.NULL_MASK && downDispatcher != null) {
                                        downDispatcher.onNext(lastParam);
                                    }
                                }
                            }, intervalDuration, unit);

                            lastScheduledTime = now;
                        } catch (Throwable e) {
                            holder.setValue(N.NULL_MASK);

                            if (downDispatcher != null) {
                                downDispatcher.onError(e);
                            }
                        }
                    } else {
                        holder.setValue(param);
                    }
                }
            }
        });

        return this;
    }

    /**
     * 
     * @param delayInMillis
     * @return this instance.
     * @see <a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Observable.html#delay(long,%20java.util.concurrent.TimeUnit)">RxJava#delay</a>
     */
    public Observer<T> delay(final long delayInMillis) {
        return delay(delayInMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 
     * @param delayInMillis
     * @param unit
     * @return this instance.
     * @see <a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Observable.html#delay(long,%20java.util.concurrent.TimeUnit)">RxJava#delay</a>
     */
    public Observer<T> delay(final long delay, final TimeUnit unit) {
        N.checkArgument(delay >= 0, "Delay can't be negative");
        N.requireNonNull(unit, "Time unit can't be null");

        if (delay == 0) {
            return this;
        }

        dispatcher.append(new Dispatcher<Object>() {
            private final long startTime = N.currentMillis();
            private boolean isDelayed = false;

            @Override
            public void onNext(final Object param) {
                if (isDelayed == false) {
                    N.sleep(unit.toMillis(delay) - (N.currentMillis() - startTime));
                    isDelayed = true;
                }

                if (downDispatcher != null) {
                    downDispatcher.onNext(param);
                }
            }
        });

        return this;
    }

    /**
     * 
     * @return this instance.
     * @see <a href="http://reactivex.io/RxJava/javadoc/io/reactivex/Observable.html#timeInterval()">RxJava#timeInterval</a>
     */
    public Observer<Timed<T>> timeInterval() {
        dispatcher.append(new Dispatcher<Object>() {
            private long startTime = N.currentMillis();

            @Override
            public synchronized void onNext(final Object param) {
                if (downDispatcher != null) {
                    long now = N.currentMillis();
                    long interval = now - startTime;
                    startTime = now;

                    downDispatcher.onNext(Timed.of(param, interval));
                }
            }
        });

        return (Observer<Timed<T>>) this;
    }

    /**
     * 
     * @return this instance.
     * @see <a href="http://reactivex.io/RxJava/javadoc/io/reactivex/Observable.html#timestamp()">RxJava#timestamp</a>
     */
    public Observer<Timed<T>> timestamp() {
        dispatcher.append(new Dispatcher<Object>() {
            @Override
            public void onNext(final Object param) {
                if (downDispatcher != null) {
                    downDispatcher.onNext(Timed.of(param, N.currentMillis()));
                }
            }
        });

        return (Observer<Timed<T>>) this;
    }

    public Observer<T> skip(final long n) {
        N.checkArgument(n >= 0, "n can't be negative");

        if (n > 0) {
            dispatcher.append(new Dispatcher<Object>() {
                private final AtomicLong counter = new AtomicLong();

                @Override
                public void onNext(final Object param) {
                    if (downDispatcher != null && counter.incrementAndGet() > n) {
                        downDispatcher.onNext(param);
                    }
                }
            });
        }

        return this;
    }

    public Observer<T> limit(final long n) {
        N.checkArgument(n >= 0, "n can't be negative");

        dispatcher.append(new Dispatcher<Object>() {
            private final AtomicLong counter = new AtomicLong();

            @Override
            public void onNext(final Object param) {
                if (downDispatcher != null && counter.incrementAndGet() <= n) {
                    downDispatcher.onNext(param);
                } else {
                    hasMore = false;
                }
            }
        });

        return this;
    }

    /**
     * 
     * @return
     */
    public Observer<T> distinct() {
        dispatcher.append(new Dispatcher<Object>() {
            private Set<T> set = new HashSet<>();

            @Override
            public void onNext(final Object param) {
                if (downDispatcher != null && set.add((T) param)) {
                    downDispatcher.onNext(param);
                }
            }
        });

        return this;
    }

    /**
     * 
     * @param keyExtractor
     * @return
     */
    public Observer<T> distinctBy(final Function<? super T, ?> keyExtractor) {
        dispatcher.append(new Dispatcher<Object>() {
            private Set<Object> set = new HashSet<>();

            @Override
            public void onNext(final Object param) {
                if (downDispatcher != null && set.add(keyExtractor.apply((T) param))) { // onError if keyExtractor.apply throws exception?
                    downDispatcher.onNext(param);
                }
            }
        });

        return this;
    }

    public Observer<T> filter(final Predicate<? super T> filter) {
        dispatcher.append(new Dispatcher<Object>() {
            @Override
            public void onNext(final Object param) {
                if (downDispatcher != null && filter.test((T) param) == true) { // onError if filter.test throws exception?
                    downDispatcher.onNext(param);
                }
            }
        });

        return this;
    }

    public <U> Observer<U> map(final Function<? super T, U> map) {
        dispatcher.append(new Dispatcher<Object>() {
            @Override
            public void onNext(final Object param) {
                if (downDispatcher != null) {
                    downDispatcher.onNext(map.apply((T) param)); // onError if map.apply throws exception?
                }
            }
        });

        return (Observer<U>) this;
    }

    public <U> Observer<U> flatMap(final Function<? super T, Collection<U>> map) {
        dispatcher.append(new Dispatcher<Object>() {
            @Override
            public void onNext(final Object param) {
                if (downDispatcher != null) {
                    final Collection<U> c = map.apply((T) param); // onError if map.apply throws exception?

                    if (N.notNullOrEmpty(c)) {
                        for (U u : c) {
                            downDispatcher.onNext(u);
                        }
                    }
                }
            }
        });

        return (Observer<U>) this;
    }

    /**
     * 
     * @param timespan
     * @param unit
     * @return this instance
     * @see <a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Observable.html#buffer(long,%20java.util.concurrent.TimeUnit)">RxJava#window(long, java.util.concurrent.TimeUnit)</a>
     */
    public Observer<List<T>> buffer(final long timespan, final TimeUnit unit) {
        return buffer(timespan, unit, Integer.MAX_VALUE);
    }

    /**
     * 
     * @param timespan
     * @param unit
     * @return this instance
     * @see <a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Observable.html#buffer(long,%20java.util.concurrent.TimeUnit,%20int)">RxJava#window(long, java.util.concurrent.TimeUnit, int)</a>
     */
    public Observer<List<T>> buffer(final long timespan, final TimeUnit unit, final int count) {
        N.checkArgument(timespan > 0, "timespan can't be 0 or negative");
        N.requireNonNull(unit, "Time unit can't be null");
        N.checkArgument(count > 0, "count can't be 0 or negative");

        dispatcher.append(new Dispatcher<Object>() {
            private final List<T> queue = new ArrayList<>();

            {
                scheduledFutures.put(scheduler.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        List<T> list = null;
                        synchronized (queue) {
                            list = new ArrayList<>(queue);
                            queue.clear();
                        }

                        if (downDispatcher != null) {
                            downDispatcher.onNext(list);
                        }
                    }

                }, timespan, timespan, unit), timespan);
            }

            @Override
            public void onNext(final Object param) {
                List<T> list = null;

                synchronized (queue) {
                    queue.add((T) param);

                    if (queue.size() == count) {
                        list = new ArrayList<>(queue);
                        queue.clear();
                    }
                }

                if (list != null && downDispatcher != null) {
                    downDispatcher.onNext(list);
                }
            }
        });

        return (Observer<List<T>>) this;
    }

    /**
     * 
     * @param timespan
     * @param timeskip
     * @param unit
     * @return
     * @see <a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Observable.html#buffer(long,%20long,%20java.util.concurrent.TimeUnit)">RxJava#window(long, long, java.util.concurrent.TimeUnit)</a>
     */
    public Observer<List<T>> buffer(final long timespan, final long timeskip, final TimeUnit unit) {
        return buffer(timespan, timeskip, unit, Integer.MAX_VALUE);
    }

    /**
     * 
     * @param timespan
     * @param timeskip
     * @param unit
     * @param count
     * @return
     * @see <a href="http://reactivex.io/RxJava/2.x/javadoc/io/reactivex/Observable.html#buffer(long,%20long,%20java.util.concurrent.TimeUnit)">RxJava#window(long, long, java.util.concurrent.TimeUnit)</a>
     */
    public Observer<List<T>> buffer(final long timespan, final long timeskip, final TimeUnit unit, final int count) {
        N.checkArgument(timespan > 0, "timespan can't be 0 or negative");
        N.checkArgument(timeskip > 0, "timeskip can't be 0 or negative");
        N.requireNonNull(unit, "Time unit can't be null");
        N.checkArgument(count > 0, "count can't be 0 or negative");

        dispatcher.append(new Dispatcher<Object>() {
            private final long startTime = N.currentMillis();
            private final long interval = timespan + timeskip;
            private final List<T> queue = new ArrayList<>();

            {
                scheduledFutures.put(scheduler.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        List<T> list = null;
                        synchronized (queue) {
                            list = new ArrayList<>(queue);
                            queue.clear();
                        }

                        if (downDispatcher != null) {
                            downDispatcher.onNext(list);
                        }
                    }

                }, timespan, interval, unit), interval);
            }

            @Override
            public void onNext(final Object param) {
                if ((N.currentMillis() - startTime) % interval <= timespan) {
                    List<T> list = null;

                    synchronized (queue) {
                        queue.add((T) param);

                        if (queue.size() == count) {
                            list = new ArrayList<>(queue);
                            queue.clear();
                        }
                    }

                    if (list != null && downDispatcher != null) {
                        downDispatcher.onNext(list);
                    }
                }
            }
        });

        return (Observer<List<T>>) this;
    }

    public void observe(final Consumer<? super T> action) {
        observe(action, ON_ERROR_MISSING);
    }

    public void observe(final Consumer<? super T> action, final Consumer<? super Throwable> onError) {
        observe(action, onError, EMPTY_ACTION);
    }

    public abstract void observe(final Consumer<? super T> action, final Consumer<? super Throwable> onError, final Runnable onComplete);

    void cancelScheduledFutures() {
        final long startTime = N.currentMillis();

        if (N.notNullOrEmpty(scheduledFutures)) {
            for (Map.Entry<ScheduledFuture<?>, Long> entry : scheduledFutures.entrySet()) {
                final long delay = entry.getValue();

                N.sleep(delay - (N.currentMillis() - startTime)
                        + delay /* Extending another delay just want to make sure last schedule can be completed before the schedule task is cancelled*/);

                entry.getKey().cancel(false);
            }
        }
    }

    protected static class Node<T> {
        public final T value;
        public Node<T> next;

        public Node(final T value) {
            this(value, null);
        }

        public Node(final T value, Node<T> next) {
            this.value = value;
            this.next = next;
        }
    }

    protected static class Dispatcher<T> {
        protected final Holder<Object> holder = Holder.of(N.NULL_MASK);
        protected Dispatcher<T> downDispatcher;

        public void onNext(@NonNull final T value) {
            if (downDispatcher != null) {
                downDispatcher.onNext(value);
            }
        }

        /**
         * Signal a Throwable exception.
         * @param error the Throwable to signal, not null
         */
        public void onError(@NonNull final Throwable error) {
            if (downDispatcher != null) {
                downDispatcher.onError(error);
            }
        }

        /**
         * Signal a completion.
         */
        public void onComplete() {
            if (downDispatcher != null) {
                downDispatcher.onComplete();
            }
        }

        public void append(Dispatcher<T> downDispatcher) {
            Dispatcher<T> tmp = this;

            while (tmp.downDispatcher != null) {
                tmp = tmp.downDispatcher;
            }

            tmp.downDispatcher = downDispatcher;
        }
    }

    protected static abstract class DispatcherBase<T> extends Dispatcher<T> {
        private final Consumer<? super Throwable> onError;
        private final Runnable onComplete;

        protected DispatcherBase(final Consumer<? super Throwable> onError, final Runnable onComplete) {
            this.onError = onError;
            this.onComplete = onComplete;
        }

        @Override
        public void onError(final Throwable error) {
            onError.accept(error);
        }

        @Override
        public void onComplete() {
            onComplete.run();
        }
    }

    protected static abstract class ObserverBase<T> extends Observer<T> {
        protected ObserverBase() {

        }
    }

    static final class BlockingQueueObserver<T> extends ObserverBase<T> {
        private final BlockingQueue<T> queue;

        BlockingQueueObserver(final BlockingQueue<T> queue) {
            this.queue = queue;
        }

        @Override
        public void observe(final Consumer<? super T> action, final Consumer<? super Throwable> onError, final Runnable onComplete) {
            N.requireNonNull(action, "action");

            dispatcher.append(new DispatcherBase<Object>(onError, onComplete) {
                @Override
                public void onNext(Object param) {
                    action.accept((T) param);
                }
            });

            asyncExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    T next = null;
                    boolean isOnError = true;

                    try {
                        while (hasMore && (next = queue.poll(Long.MAX_VALUE, TimeUnit.MILLISECONDS)) != COMPLETE_FLAG) {
                            isOnError = false;

                            dispatcher.onNext(next);

                            isOnError = true;
                        }

                        isOnError = false;

                        onComplete.run();
                    } catch (Throwable e) {
                        if (isOnError) {
                            onError.accept(e);
                        } else {
                            throw N.toRuntimeException(e);
                        }
                    } finally {
                        cancelScheduledFutures();
                    }
                }
            });
        }
    }

    static final class IteratorObserver<T> extends ObserverBase<T> {
        private final Iterator<T> iter;

        IteratorObserver(final Iterator<T> iter) {
            this.iter = iter;
        }

        @Override
        public void observe(final Consumer<? super T> action, final Consumer<? super Throwable> onError, final Runnable onComplete) {
            N.requireNonNull(action, "action");

            dispatcher.append(new DispatcherBase<Object>(onError, onComplete) {
                @Override
                public void onNext(Object param) {
                    action.accept((T) param);
                }
            });

            asyncExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    boolean isOnError = true;

                    try {
                        while (hasMore && iter.hasNext()) {
                            isOnError = false;

                            dispatcher.onNext(iter.next());

                            isOnError = true;
                        }

                        isOnError = false;

                        onComplete.run();
                    } catch (Throwable e) {
                        if (isOnError) {
                            onError.accept(e);
                        } else {
                            throw N.toRuntimeException(e);
                        }
                    } finally {
                        cancelScheduledFutures();
                    }
                }
            });
        }

    }

    static final class TimerObserver<T> extends ObserverBase<T> {
        private final long delay;
        private final TimeUnit unit;

        TimerObserver(long delay, TimeUnit unit) {
            this.delay = delay;
            this.unit = unit;
        }

        @Override
        public void observe(final Consumer<? super T> action, final Consumer<? super Throwable> onError, final Runnable onComplete) {
            N.requireNonNull(action, "action");

            dispatcher.append(new DispatcherBase<Object>(onError, onComplete) {
                @Override
                public void onNext(Object param) {
                    action.accept((T) param);
                }
            });

            scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    try {
                        dispatcher.onNext(0L);

                        onComplete.run();
                    } finally {
                        cancelScheduledFutures();
                    }
                }
            }, delay, unit);
        }
    }

    static final class IntervalObserver<T> extends ObserverBase<T> {
        private final long initialDelay;
        private final long period;
        private final TimeUnit unit;
        private ScheduledFuture<?> future = null;

        IntervalObserver(long initialDelay, long period, TimeUnit unit) {
            this.initialDelay = initialDelay;
            this.period = period;
            this.unit = unit;
        }

        @Override
        public void observe(final Consumer<? super T> action, final Consumer<? super Throwable> onError, final Runnable onComplete) {
            N.requireNonNull(action, "action");

            dispatcher.append(new DispatcherBase<Object>(onError, onComplete) {
                @Override
                public void onNext(Object param) {
                    action.accept((T) param);
                }
            });

            future = scheduler.scheduleAtFixedRate(new Runnable() {
                private long val = 0;

                @Override
                public void run() {
                    if (hasMore == false) {
                        try {
                            dispatcher.onComplete();
                        } finally {
                            try {
                                future.cancel(true);
                            } finally {
                                cancelScheduledFutures();
                            }
                        }
                    } else {
                        try {
                            dispatcher.onNext(val++);
                        } catch (Throwable e) {
                            try {
                                future.cancel(true);
                            } finally {
                                cancelScheduledFutures();
                            }
                        }
                    }
                }
            }, initialDelay, period, unit);
        }
    }
}