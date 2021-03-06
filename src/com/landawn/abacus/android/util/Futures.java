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

package com.landawn.abacus.android.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.landawn.abacus.util.N;
import com.landawn.abacus.util.Pair;
import com.landawn.abacus.util.Try;
import com.landawn.abacus.util.Tuple;
import com.landawn.abacus.util.Tuple.Tuple2;
import com.landawn.abacus.util.Tuple.Tuple3;
import com.landawn.abacus.util.Tuple.Tuple4;
import com.landawn.abacus.util.Tuple.Tuple5;
import com.landawn.abacus.util.Tuple.Tuple6;
import com.landawn.abacus.util.Tuple.Tuple7;
import com.landawn.abacus.util.function.BiFunction;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.TriFunction;

/**
 * @since 0.9
 * 
 * @author Haiyang Li 
 *
 */
public final class Futures {
    private Futures() {
        // singleton.
    }

    public static <T1, T2, R> CompletableFuture<R> zip(final CompletableFuture<T1> cf1, final CompletableFuture<T2> cf2,
            final Try.BiFunction<? super CompletableFuture<T1>, ? super CompletableFuture<T2>, R, Exception> zipFunctionForGet) {
        return zip(cf1, cf2, zipFunctionForGet, new Try.Function<Tuple4<CompletableFuture<T1>, CompletableFuture<T2>, Long, TimeUnit>, R, Exception>() {
            @Override
            public R apply(Tuple4<CompletableFuture<T1>, CompletableFuture<T2>, Long, TimeUnit> t) throws Exception {
                return zipFunctionForGet.apply(t._1, t._2);
            }
        });
    }

    public static <T1, T2, R> CompletableFuture<R> zip(final CompletableFuture<T1> cf1, final CompletableFuture<T2> cf2,
            final Try.BiFunction<? super CompletableFuture<T1>, ? super CompletableFuture<T2>, R, Exception> zipFunctionForGet,
            final Try.Function<? super Tuple4<CompletableFuture<T1>, CompletableFuture<T2>, Long, TimeUnit>, R, Exception> zipFunctionTimeoutGet) {
        final List<CompletableFuture<?>> cfs = N.asList(cf1, cf2);

        return zip(cfs, new Try.Function<Collection<? extends CompletableFuture<?>>, R, Exception>() {
            @SuppressWarnings("rawtypes")
            @Override
            public R apply(Collection<? extends CompletableFuture<?>> c) throws Exception {
                return zipFunctionForGet.apply((CompletableFuture<T1>) ((List) c).get(0), (CompletableFuture<T2>) ((List) c).get(1));
            }
        }, new Try.Function<Tuple3<Collection<? extends CompletableFuture<?>>, Long, TimeUnit>, R, Exception>() {
            @SuppressWarnings("rawtypes")
            @Override
            public R apply(Tuple3<Collection<? extends CompletableFuture<?>>, Long, TimeUnit> t) throws Exception {
                return zipFunctionTimeoutGet
                        .apply(Tuple.of((CompletableFuture<T1>) ((List) t._1).get(0), (CompletableFuture<T2>) ((List) t._1).get(1), t._2, t._3));
            }
        });
    }

    public static <T1, T2, T3, R> CompletableFuture<R> zip(final CompletableFuture<T1> cf1, final CompletableFuture<T2> cf2, final CompletableFuture<T3> cf3,
            final Try.TriFunction<? super CompletableFuture<T1>, ? super CompletableFuture<T2>, ? super CompletableFuture<T3>, R, Exception> zipFunctionForGet) {
        return zip(cf1, cf2, cf3, zipFunctionForGet,
                new Try.Function<Tuple5<CompletableFuture<T1>, CompletableFuture<T2>, CompletableFuture<T3>, Long, TimeUnit>, R, Exception>() {
                    @Override
                    public R apply(Tuple5<CompletableFuture<T1>, CompletableFuture<T2>, CompletableFuture<T3>, Long, TimeUnit> t) throws Exception {
                        return zipFunctionForGet.apply(t._1, t._2, t._3);
                    }
                });
    }

    public static <T1, T2, T3, R> CompletableFuture<R> zip(final CompletableFuture<T1> cf1, final CompletableFuture<T2> cf2, final CompletableFuture<T3> cf3,
            final Try.TriFunction<? super CompletableFuture<T1>, ? super CompletableFuture<T2>, ? super CompletableFuture<T3>, R, Exception> zipFunctionForGet,
            final Try.Function<? super Tuple5<CompletableFuture<T1>, CompletableFuture<T2>, CompletableFuture<T3>, Long, TimeUnit>, R, Exception> zipFunctionTimeoutGet) {
        final List<CompletableFuture<?>> cfs = N.asList(cf1, cf2, cf3);

        return zip(cfs, new Try.Function<Collection<? extends CompletableFuture<?>>, R, Exception>() {
            @SuppressWarnings("rawtypes")
            @Override
            public R apply(Collection<? extends CompletableFuture<?>> c) throws Exception {
                return zipFunctionForGet.apply((CompletableFuture<T1>) ((List) c).get(0), (CompletableFuture<T2>) ((List) c).get(1),
                        (CompletableFuture<T3>) ((List) c).get(2));
            }
        }, new Try.Function<Tuple3<Collection<? extends CompletableFuture<?>>, Long, TimeUnit>, R, Exception>() {
            @SuppressWarnings("rawtypes")
            @Override
            public R apply(Tuple3<Collection<? extends CompletableFuture<?>>, Long, TimeUnit> t) throws Exception {
                return zipFunctionTimeoutGet.apply(Tuple.of((CompletableFuture<T1>) ((List) t._1).get(0), (CompletableFuture<T2>) ((List) t._1).get(1),
                        (CompletableFuture<T3>) ((List) t._1).get(2), t._2, t._3));
            }
        });
    }

    public static <R> CompletableFuture<R> zip(final Collection<? extends CompletableFuture<?>> cfs,
            final Try.Function<? super Collection<? extends CompletableFuture<?>>, R, Exception> zipFunctionForGet) {
        return zip(cfs, zipFunctionForGet, new Try.Function<Tuple3<Collection<? extends CompletableFuture<?>>, Long, TimeUnit>, R, Exception>() {
            @Override
            public R apply(Tuple3<Collection<? extends CompletableFuture<?>>, Long, TimeUnit> t) throws Exception {
                return zipFunctionForGet.apply(t._1);
            }
        });
    }

    public static <R> CompletableFuture<R> zip(final Collection<? extends CompletableFuture<?>> cfs,
            final Try.Function<? super Collection<? extends CompletableFuture<?>>, R, Exception> zipFunctionForGet,
            final Try.Function<? super Tuple3<Collection<? extends CompletableFuture<?>>, Long, TimeUnit>, R, Exception> zipFunctionTimeoutGet) {
        N.checkArgument(N.notNullOrEmpty(cfs), "'cfs' can't be null or empty");
        N.requireNonNull(zipFunctionForGet);
        N.requireNonNull(zipFunctionTimeoutGet);

        return new CompletableFuture<>(new Future<R>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                boolean res = true;
                RuntimeException exception = null;

                for (CompletableFuture<?> future : cfs) {
                    try {
                        res = res & future.cancel(mayInterruptIfRunning);
                    } catch (RuntimeException e) {
                        if (exception == null) {
                            exception = e;
                        } else {
                            exception.addSuppressed(e);
                        }
                    }
                }

                if (exception != null) {
                    throw exception;
                }

                return res;
            }

            @Override
            public boolean isCancelled() {
                for (CompletableFuture<?> future : cfs) {
                    if (future.isCancelled()) {
                        return true;
                    }
                }

                return false;
            }

            @Override
            public boolean isDone() {
                for (CompletableFuture<?> future : cfs) {
                    if (future.isDone() == false) {
                        return false;
                    }
                }

                return true;
            }

            @Override
            public R get() throws InterruptedException, ExecutionException {
                try {
                    return zipFunctionForGet.apply(cfs);
                } catch (InterruptedException | ExecutionException e) {
                    throw e;
                } catch (Throwable e) {
                    throw N.toRuntimeException(e);
                }
            }

            @Override
            public R get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                @SuppressWarnings("rawtypes")
                final Tuple3<Collection<? extends CompletableFuture<?>>, Long, TimeUnit> t = (Tuple3) Tuple.of(cfs, timeout, unit);

                try {
                    return zipFunctionTimeoutGet.apply(t);
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    throw e;
                } catch (Throwable e) {
                    throw N.toRuntimeException(e);
                }
            }
        }, null, ((CompletableFuture<?>) cfs.iterator().next()).asyncExecutor);
    }

    public static <T1, T2> CompletableFuture<Tuple2<T1, T2>> combine(final CompletableFuture<? extends T1> cf1, final CompletableFuture<? extends T2> cf2) {
        return allOf(N.asList(cf1, cf2)).thenApply(new Function<List<Object>, Tuple2<T1, T2>>() {
            @Override
            public Tuple2<T1, T2> apply(List<Object> t) {
                return Tuple.of((T1) t.get(0), (T2) t.get(1));
            }
        });
    }

    public static <T1, T2, T3> CompletableFuture<Tuple3<T1, T2, T3>> combine(final CompletableFuture<? extends T1> cf1,
            final CompletableFuture<? extends T2> cf2, final CompletableFuture<? extends T3> cf3) {
        return allOf(N.asList(cf1, cf2, cf3)).thenApply(new Function<List<Object>, Tuple3<T1, T2, T3>>() {
            @Override
            public Tuple3<T1, T2, T3> apply(List<Object> t) {
                return Tuple.of((T1) t.get(0), (T2) t.get(1), (T3) t.get(2));
            }
        });
    }

    public static <T1, T2, T3, T4> CompletableFuture<Tuple4<T1, T2, T3, T4>> combine(final CompletableFuture<? extends T1> cf1,
            final CompletableFuture<? extends T2> cf2, final CompletableFuture<? extends T3> cf3, final CompletableFuture<? extends T4> cf4) {
        return allOf(N.asList(cf1, cf2, cf3, cf4)).thenApply(new Function<List<Object>, Tuple4<T1, T2, T3, T4>>() {
            @Override
            public Tuple4<T1, T2, T3, T4> apply(List<Object> t) {
                return Tuple.of((T1) t.get(0), (T2) t.get(1), (T3) t.get(2), (T4) t.get(3));
            }
        });
    }

    public static <T1, T2, T3, T4, T5> CompletableFuture<Tuple5<T1, T2, T3, T4, T5>> combine(final CompletableFuture<? extends T1> cf1,
            final CompletableFuture<? extends T2> cf2, final CompletableFuture<? extends T3> cf3, final CompletableFuture<? extends T4> cf4,
            final CompletableFuture<? extends T5> cf5) {
        return allOf(N.asList(cf1, cf2, cf3, cf4, cf5)).thenApply(new Function<List<Object>, Tuple5<T1, T2, T3, T4, T5>>() {
            @Override
            public Tuple5<T1, T2, T3, T4, T5> apply(List<Object> t) {
                return Tuple.of((T1) t.get(0), (T2) t.get(1), (T3) t.get(2), (T4) t.get(3), (T5) t.get(4));
            }
        });
    }

    public static <T1, T2, T3, T4, T5, T6> CompletableFuture<Tuple6<T1, T2, T3, T4, T5, T6>> combine(final CompletableFuture<? extends T1> cf1,
            final CompletableFuture<? extends T2> cf2, final CompletableFuture<? extends T3> cf3, final CompletableFuture<? extends T4> cf4,
            final CompletableFuture<? extends T5> cf5, final CompletableFuture<? extends T6> cf6) {
        return allOf(N.asList(cf1, cf2, cf3, cf4, cf5, cf6)).thenApply(new Function<List<Object>, Tuple6<T1, T2, T3, T4, T5, T6>>() {
            @Override
            public Tuple6<T1, T2, T3, T4, T5, T6> apply(List<Object> t) {
                return Tuple.of((T1) t.get(0), (T2) t.get(1), (T3) t.get(2), (T4) t.get(3), (T5) t.get(4), (T6) t.get(5));
            }
        });
    }

    public static <T1, T2, T3, T4, T5, T6, T7> CompletableFuture<Tuple7<T1, T2, T3, T4, T5, T6, T7>> combine(final CompletableFuture<? extends T1> cf1,
            final CompletableFuture<? extends T2> cf2, final CompletableFuture<? extends T3> cf3, final CompletableFuture<? extends T4> cf4,
            final CompletableFuture<? extends T5> cf5, final CompletableFuture<? extends T6> cf6, final CompletableFuture<? extends T7> cf7) {
        return allOf(N.asList(cf1, cf2, cf3, cf4, cf5, cf6, cf7)).thenApply(new Function<List<Object>, Tuple7<T1, T2, T3, T4, T5, T6, T7>>() {
            @Override
            public Tuple7<T1, T2, T3, T4, T5, T6, T7> apply(List<Object> t) {
                return Tuple.of((T1) t.get(0), (T2) t.get(1), (T3) t.get(2), (T4) t.get(3), (T5) t.get(4), (T6) t.get(5), (T7) t.get(6));
            }
        });
    }

    public static <T1, T2, R> CompletableFuture<R> combine(final CompletableFuture<? extends T1> cf1, final CompletableFuture<? extends T2> cf2,
            final BiFunction<? super T1, ? super T2, ? extends R> action) {
        return allOf(N.asList(cf1, cf2)).thenApply(new Function<List<Object>, R>() {
            @Override
            public R apply(List<Object> t) {
                return action.apply((T1) t.get(0), (T2) t.get(1));
            }
        });
    }

    public static <T1, T2, T3, R> CompletableFuture<R> combine(final CompletableFuture<? extends T1> cf1, final CompletableFuture<? extends T2> cf2,
            final CompletableFuture<? extends T3> cf3, final TriFunction<? super T1, ? super T2, ? super T3, ? extends R> action) {
        return allOf(N.asList(cf1, cf2, cf3)).thenApply(new Function<List<Object>, R>() {
            @Override
            public R apply(List<Object> t) {
                return action.apply((T1) t.get(0), (T2) t.get(1), (T3) t.get(2));
            }
        });
    }

    public static <R> CompletableFuture<R> combine(final Collection<? extends CompletableFuture<?>> cfs, final Function<List<Object>, ? extends R> action) {
        return allOf(cfs).thenApply(action);
    }

    //    public static <T, R> CompletableFuture<R> combine(final List<? extends CompletableFuture<? extends T>> cfs, final Function<List<T>, ? extends R> action) {
    //        final CompletableFuture<List<T>> future = allOf(cfs);
    //        return future.thenApply(action);
    //    }

    /**
     * Returns a new CompletableFuture that is completed when all of
     * the given CompletableFutures complete. If any of the given
     * CompletableFutures complete exceptionally, then the returned
     * CompletableFuture also does so.
     * 
     * @param cfs
     * @return
     */
    @SafeVarargs
    public static CompletableFuture<List<Object>> allOf(final CompletableFuture<?>... cfs) {
        return allOf2(Arrays.asList(cfs));
    }

    /**
     * Returns a new CompletableFuture that is completed when all of
     * the given CompletableFutures complete. If any of the given
     * CompletableFutures complete exceptionally, then the returned
     * CompletableFuture also does so.
     * 
     * @param cfs
     * @return
     */
    public static CompletableFuture<List<Object>> allOf(final Collection<? extends CompletableFuture<?>> cfs) {
        return allOf2(cfs);
    }

    /**
     * Returns a new CompletableFuture that is completed when all of
     * the given CompletableFutures complete. If any of the given
     * CompletableFutures complete exceptionally, then the returned
     * CompletableFuture also does so.
     * 
     * @param cfs
     * @return
     */
    @SuppressWarnings("rawtypes")
    public static <T> CompletableFuture<List<T>> allOf(final List<? extends CompletableFuture<? extends T>> cfs) {
        return (CompletableFuture) allOf2(cfs);
    }

    private static CompletableFuture<List<Object>> allOf2(final Collection<? extends CompletableFuture<?>> cfs) {
        N.checkArgument(N.notNullOrEmpty(cfs), "'cfs' can't be null or empty");

        return new CompletableFuture<>(new Future<List<Object>>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                boolean res = true;
                RuntimeException exception = null;

                for (CompletableFuture<?> future : cfs) {
                    try {
                        res = res & future.cancel(mayInterruptIfRunning);
                    } catch (RuntimeException e) {
                        if (exception == null) {
                            exception = e;
                        } else {
                            exception.addSuppressed(e);
                        }
                    }
                }

                if (exception != null) {
                    throw exception;
                }

                return res;
            }

            @Override
            public boolean isCancelled() {
                for (CompletableFuture<?> future : cfs) {
                    if (future.isCancelled()) {
                        return true;
                    }
                }

                return false;
            }

            @Override
            public boolean isDone() {
                for (CompletableFuture<?> future : cfs) {
                    if (future.isDone() == false) {
                        return false;
                    }
                }

                return true;
            }

            @Override
            public List<Object> get() throws InterruptedException, ExecutionException {
                final List<Object> result = new ArrayList<>(cfs.size());

                for (CompletableFuture<?> future : cfs) {
                    result.add(future.get());
                }

                return result;
            }

            @Override
            public List<Object> get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                final long timeoutInMillis = unit.toMillis(timeout);
                final long now = N.currentMillis();
                final long endTime = timeoutInMillis > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + timeoutInMillis;

                final List<Object> result = new ArrayList<>(cfs.size());

                for (CompletableFuture<?> future : cfs) {
                    result.add(future.get(N.max(0, endTime - N.currentMillis()), TimeUnit.MILLISECONDS));
                }

                return result;
            }
        }, null, ((CompletableFuture<?>) cfs.iterator().next()).asyncExecutor);
    }

    /**
     * Returns a new CompletableFuture that, when any of the given CompletableFutures complete normally. 
     * If all of the given CompletableFutures complete exceptionally, then the returned CompletableFuture also does so.
     * 
     * @param cfs
     * @return
     */
    @SafeVarargs
    public static CompletableFuture<Object> anyOf(final CompletableFuture<?>... cfs) {
        return anyOf2(Arrays.asList(cfs));
    }

    /**
     * Returns a new CompletableFuture that, when any of the given CompletableFutures complete normally. 
     * If all of the given CompletableFutures complete exceptionally, then the returned CompletableFuture also does so.
     * 
     * @param cfs
     * @return
     */
    public static CompletableFuture<Object> anyOf(final Collection<? extends CompletableFuture<?>> cfs) {
        return anyOf2(cfs);
    }

    /**
     * Returns a new CompletableFuture that, when any of the given CompletableFutures complete normally. 
     * If all of the given CompletableFutures complete exceptionally, then the returned CompletableFuture also does so.
     * 
     * @param cfs
     * @return
     */
    public static <T> CompletableFuture<T> anyOf(final List<? extends CompletableFuture<? extends T>> cfs) {
        return (CompletableFuture<T>) anyOf2(cfs);
    }

    private static CompletableFuture<Object> anyOf2(final Collection<? extends CompletableFuture<?>> cfs) {
        N.checkArgument(N.notNullOrEmpty(cfs), "'cfs' can't be null or empty");

        return new CompletableFuture<>(new Future<Object>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                boolean res = true;
                RuntimeException exception = null;

                for (CompletableFuture<?> future : cfs) {
                    try {
                        res = res & future.cancel(mayInterruptIfRunning);
                    } catch (RuntimeException e) {
                        if (exception == null) {
                            exception = e;
                        } else {
                            exception.addSuppressed(e);
                        }
                    }
                }

                if (exception != null) {
                    throw exception;
                }

                return res;
            }

            @Override
            public boolean isCancelled() {
                for (CompletableFuture<?> future : cfs) {
                    if (future.isCancelled() == false) {
                        return false;
                    }
                }

                return true;
            }

            @Override
            public boolean isDone() {
                for (CompletableFuture<?> future : cfs) {
                    if (future.isDone()) {
                        return true;
                    }
                }

                return false;
            }

            @Override
            public Object get() throws InterruptedException, ExecutionException {
                final Iterator<Pair<Object, Throwable>> iter = iterate2(cfs);
                Pair<Object, Throwable> result = null;

                while (iter.hasNext()) {
                    result = iter.next();

                    if (result.right == null) {
                        return result.left;
                    }
                }

                return handle(result);
            }

            @Override
            public Object get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                final Iterator<Pair<Object, Throwable>> iter = iterate2(cfs, timeout, unit);
                Pair<Object, Throwable> result = null;

                while (iter.hasNext()) {
                    result = iter.next();

                    if (result.right == null) {
                        return result.left;
                    }
                }

                return handle(result);
            }
        }, null, ((CompletableFuture<?>) cfs.iterator().next()).asyncExecutor);
    }

    @SafeVarargs
    public static Iterator<Object> iterate(final CompletableFuture<?>... cfs) {
        return iterate02(N.asList(cfs));
    }

    public static Iterator<Object> iterate(final Collection<? extends CompletableFuture<?>> cfs) {
        return iterate02(cfs);
    }

    public static Iterator<Object> iterate(final Collection<? extends CompletableFuture<?>> cfs, final long timeout, final TimeUnit unit) {
        return iterate02(cfs, timeout, unit);
    }

    public static <T> Iterator<T> iterate(final List<? extends CompletableFuture<? extends T>> cfs) {
        return (Iterator<T>) iterate02(cfs);
    }

    public static <T> Iterator<T> iterate(final List<? extends CompletableFuture<? extends T>> cfs, final long timeout, final TimeUnit unit) {
        return (Iterator<T>) iterate02(cfs, timeout, unit);
    }

    private static Iterator<Object> iterate02(final Collection<? extends CompletableFuture<?>> cfs) {
        return iterate02(cfs, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    private static Iterator<Object> iterate02(final Collection<? extends CompletableFuture<?>> cfs, final long timeout, final TimeUnit unit) {
        return new Iterator<Object>() {
            private final Iterator<Pair<Object, Throwable>> iter = iterate22(cfs, timeout, unit);

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public Object next() {
                try {
                    return handle(iter.next());
                } catch (InterruptedException | ExecutionException e) {
                    throw N.toRuntimeException(e);
                }
            }

            @Override
            public void remove() {
                iter.remove();
            }
        };
    }

    @SafeVarargs
    public static Iterator<Pair<Object, Throwable>> iterate2(final CompletableFuture<?>... cfs) {
        return iterate22(N.asList(cfs));
    }

    public static Iterator<Pair<Object, Throwable>> iterate2(final Collection<? extends CompletableFuture<?>> cfs) {
        return iterate22(cfs);
    }

    public static Iterator<Pair<Object, Throwable>> iterate2(final Collection<? extends CompletableFuture<?>> cfs, final long timeout, final TimeUnit unit) {
        return iterate22(cfs, timeout, unit);
    }

    @SuppressWarnings("rawtypes")
    public static <T> Iterator<Pair<T, Throwable>> iterate2(final List<? extends CompletableFuture<? extends T>> cfs) {
        return (Iterator) iterate22(cfs);
    }

    @SuppressWarnings("rawtypes")
    public static <T> Iterator<Pair<T, Throwable>> iterate2(final List<? extends CompletableFuture<? extends T>> cfs, final long timeout, final TimeUnit unit) {
        return (Iterator) iterate22(cfs, timeout, unit);
    }

    private static Iterator<Pair<Object, Throwable>> iterate22(final Collection<? extends CompletableFuture<?>> cfs) {
        return iterate22(cfs, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    private static Iterator<Pair<Object, Throwable>> iterate22(final Collection<? extends CompletableFuture<?>> cfs, final long timeout, final TimeUnit unit) {
        final ExecutorService executor = Executors.newFixedThreadPool(cfs.size());
        final BlockingQueue<Pair<Object, Throwable>> queue = new ArrayBlockingQueue<>(cfs.size());

        for (CompletableFuture<?> e : cfs) {
            final CompletableFuture<Object> futuer = (CompletableFuture<Object>) e;

            executor.execute(new Runnable() {
                @Override
                public void run() {
                    queue.offer(futuer.get2(timeout, unit));
                }
            });
        }

        return new Iterator<Pair<Object, Throwable>>() {
            private final int end = cfs.size();
            private int cursor = 0;

            @Override
            public boolean hasNext() {
                return cursor < end;
            }

            @Override
            public Pair<Object, Throwable> next() {
                if (cursor >= end) {
                    throw new NoSuchElementException();
                }

                cursor++;

                try {
                    return queue.poll(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw N.toRuntimeException(e);
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

        };
    }

    private static <R> R handle(final Pair<R, Throwable> result) throws InterruptedException, ExecutionException {
        if (result.right != null) {
            if (result.right instanceof InterruptedException) {
                throw ((InterruptedException) result.right);
            } else if (result.right instanceof ExecutionException) {
                throw ((ExecutionException) result.right);
            } else {
                throw N.toRuntimeException(result.right);
            }
        }

        return result.left;
    }
}
