/*
 * Copyright (C) 2016 HaiYang Li
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

package com.landawn.abacus.util.stream;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.landawn.abacus.DataSet;
import com.landawn.abacus.exception.UncheckedIOException;
import com.landawn.abacus.exception.UncheckedSQLException;
import com.landawn.abacus.util.Array;
import com.landawn.abacus.util.BufferedWriter;
import com.landawn.abacus.util.ByteIterator;
import com.landawn.abacus.util.CharIterator;
import com.landawn.abacus.util.DoubleIterator;
import com.landawn.abacus.util.FloatIterator;
import com.landawn.abacus.util.Fn;
import com.landawn.abacus.util.IOUtil;
import com.landawn.abacus.util.Indexed;
import com.landawn.abacus.util.IntIterator;
import com.landawn.abacus.util.ListMultimap;
import com.landawn.abacus.util.LongIterator;
import com.landawn.abacus.util.Matrix;
import com.landawn.abacus.util.Multimap;
import com.landawn.abacus.util.Multiset;
import com.landawn.abacus.util.MutableBoolean;
import com.landawn.abacus.util.MutableLong;
import com.landawn.abacus.util.N;
import com.landawn.abacus.util.Nth;
import com.landawn.abacus.util.NullabLe;
import com.landawn.abacus.util.ObjectFactory;
import com.landawn.abacus.util.Optional;
import com.landawn.abacus.util.OptionalDouble;
import com.landawn.abacus.util.Pair;
import com.landawn.abacus.util.Percentage;
import com.landawn.abacus.util.PermutationIterator;
import com.landawn.abacus.util.Seq;
import com.landawn.abacus.util.ShortIterator;
import com.landawn.abacus.util.Try;
import com.landawn.abacus.util.function.BiConsumer;
import com.landawn.abacus.util.function.BiFunction;
import com.landawn.abacus.util.function.BiPredicate;
import com.landawn.abacus.util.function.BinaryOperator;
import com.landawn.abacus.util.function.Consumer;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.IntFunction;
import com.landawn.abacus.util.function.Predicate;
import com.landawn.abacus.util.function.Supplier;
import com.landawn.abacus.util.function.ToDoubleFunction;
import com.landawn.abacus.util.function.ToIntFunction;
import com.landawn.abacus.util.function.ToLongFunction;
import com.landawn.abacus.util.function.TriConsumer;
import com.landawn.abacus.util.function.TriFunction;

/**
 * This class is a sequential, stateful and immutable stream implementation.
 *
 * @param <T>
 * @since 0.8
 * 
 * @author Haiyang Li
 */
abstract class AbstractStream<T> extends Stream<T> {

    AbstractStream(final Collection<Runnable> closeHandlers, final boolean sorted, final Comparator<? super T> cmp) {
        super(closeHandlers, sorted, cmp);
    }

    @Override
    public <U> Stream<T> filter(final U seed, final BiPredicate<? super T, ? super U> predicate) {
        return filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return predicate.test(value, seed);
            }
        });
    }

    @Override
    public <U> Stream<T> takeWhile(final U seed, final BiPredicate<? super T, ? super U> predicate) {
        return takeWhile(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return predicate.test(value, seed);
            }
        });
    }

    @Override
    public <U> Stream<T> dropWhile(final U seed, final BiPredicate<? super T, ? super U> predicate) {
        return dropWhile(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return predicate.test(value, seed);
            }
        });
    }

    @Override
    public Stream<T> remove(final long n, final Consumer<? super T> action) {
        if (n < 0) {
            throw new IllegalArgumentException("'n' can't be less than 0");
        } else if (n == 0) {
            return this;
        }

        if (this.isParallel()) {
            final AtomicLong cnt = new AtomicLong(n);

            return removeWhile(new Predicate<T>() {
                @Override
                public boolean test(T value) {
                    return cnt.getAndDecrement() > 0;
                }
            }, action);
        } else {
            final MutableLong cnt = MutableLong.of(n);

            return removeWhile(new Predicate<T>() {

                @Override
                public boolean test(T value) {
                    return cnt.getAndDecrement() > 0;
                }

            }, action);
        }
    }

    @Override
    public Stream<T> removeIf(final Predicate<? super T> predicate) {
        N.requireNonNull(predicate);

        return filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return predicate.test(value) == false;
            }
        });
    }

    @Override
    public <U> Stream<T> removeIf(final U seed, final BiPredicate<? super T, ? super U> predicate) {
        N.requireNonNull(predicate);

        return filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return predicate.test(value, seed) == false;
            }
        });
    }

    @Override
    public Stream<T> removeIf(final Predicate<? super T> predicate, final Consumer<? super T> action) {
        N.requireNonNull(predicate);
        N.requireNonNull(action);

        return filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                if (predicate.test(value)) {
                    action.accept(value);
                    return false;
                }

                return true;
            }
        });
    }

    @Override
    public <U> Stream<T> removeIf(final U seed, final BiPredicate<? super T, ? super U> predicate, final Consumer<? super T> action) {
        N.requireNonNull(predicate);
        N.requireNonNull(action);

        return filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                if (predicate.test(value, seed)) {
                    action.accept(value);
                    return false;
                }

                return true;
            }
        });
    }

    @Override
    public Stream<T> removeWhile(final Predicate<? super T> predicate, final Consumer<? super T> action) {
        N.requireNonNull(predicate);
        N.requireNonNull(action);

        return dropWhile(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                if (predicate.test(value)) {
                    action.accept(value);
                    return true;
                }

                return false;
            }
        });
    }

    @Override
    public <U> Stream<T> removeWhile(final U seed, final BiPredicate<? super T, ? super U> predicate, final Consumer<? super T> action) {
        N.requireNonNull(predicate);
        N.requireNonNull(action);

        return dropWhile(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                if (predicate.test(value, seed)) {
                    action.accept(value);
                    return true;
                }

                return false;
            }
        });
    }

    @Override
    public Stream<T> step(final long step) {
        N.checkArgument(step > 0, "'step' can't be 0 or negative: %s", step);

        if (step == 1) {
            return this;
        }

        final long skip = step - 1;
        final ExIterator<T> iter = exIterator();

        final Iterator<T> iterator = new ExIterator<T>() {
            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public T next() {
                final T next = iter.next();
                iter.skip(skip);
                return next;
            }
        };

        return newStream(iterator, sorted, cmp);
    }

    @Override
    public <U, R> Stream<R> map(final U seed, final BiFunction<? super T, ? super U, ? extends R> mapper) {
        return map(new Function<T, R>() {
            @Override
            public R apply(T t) {
                return mapper.apply(t, seed);
            }
        });
    }

    @Override
    public <R> Stream<R> biMap(BiFunction<? super T, ? super T, ? extends R> mapper) {
        return biMap(mapper, false);
    }

    @Override
    public <R> Stream<R> triMap(TriFunction<? super T, ? super T, ? super T, ? extends R> mapper) {
        return triMap(mapper, false);
    }

    @Override
    public <R> Stream<R> slidingMap(BiFunction<? super T, ? super T, R> mapper) {
        return slidingMap(mapper, 1);
    }

    @Override
    public <R> Stream<R> slidingMap(TriFunction<? super T, ? super T, ? super T, R> mapper) {
        return slidingMap(mapper, 1);
    }

    @Override
    public <U> Stream<U> rangeMap(final BiPredicate<? super T, ? super T> sameRange, final BiFunction<? super T, ? super T, ? extends U> mapper) {
        final Iterator<T> iter = iterator();

        return newStream(new ExIterator<U>() {
            private final T NULL = (T) Stream.NONE;
            private T next = NULL;

            @Override
            public boolean hasNext() {
                return next != NULL || iter.hasNext();
            }

            @Override
            public U next() {
                final T left = next == NULL ? iter.next() : next;
                T right = left;

                while (iter.hasNext() && sameRange.test(right, (next = iter.next()))) {
                    right = next;
                }

                if (right == next) {
                    next = NULL;
                }

                return mapper.apply(left, right);
            }
        }, false, null);
    }

    abstract <R> Stream<R> flatMap0(final Function<? super T, ? extends Iterator<? extends R>> mapper);

    //    @Override
    //    public <K, V> EntryStream<K, V> mapToEntry() {
    //        return null;
    //    }

    @Override
    public <K, V> EntryStream<K, V> mapToEntry(final Function<? super T, ? extends Map.Entry<K, V>> mapper) {
        final Function<T, T> mapper2 = Fn.identity();

        if (mapper == mapper2) {
            return EntryStream.of((Stream<Map.Entry<K, V>>) this);
        }

        return EntryStream.of(map(mapper));
    }

    @Override
    public <K, V> EntryStream<K, V> mapToEntry(final Function<? super T, K> keyMapper, final Function<? super T, V> valueMapper) {
        final Function<T, Map.Entry<K, V>> mapper = new Function<T, Map.Entry<K, V>>() {
            @Override
            public Entry<K, V> apply(T t) {
                return new AbstractMap.SimpleImmutableEntry<>(keyMapper.apply(t), valueMapper.apply(t));
            }
        };

        return mapToEntry(mapper);
    }

    @Override
    public <R> Stream<R> flatMap(final Function<? super T, ? extends Stream<? extends R>> mapper) {
        return flatMap0(new Function<T, Iterator<? extends R>>() {
            @Override
            public Iterator<? extends R> apply(T t) {
                return mapper.apply(t).iterator();
            }
        });
    }

    @Override
    public <U, R> Stream<R> flatMap(final U seed, final BiFunction<? super T, ? super U, ? extends Stream<? extends R>> mapper) {
        return flatMap(new Function<T, Stream<? extends R>>() {
            @Override
            public Stream<? extends R> apply(T t) {
                return mapper.apply(t, seed);
            }
        });
    }

    @Override
    public <R> Stream<R> flatCollection(final Function<? super T, ? extends Collection<? extends R>> mapper) {
        return flatMap0(new Function<T, Iterator<? extends R>>() {
            @Override
            public Iterator<? extends R> apply(T t) {
                return mapper.apply(t).iterator();
            }
        });
    }

    @Override
    public <U, R> Stream<R> flatCollection(final U seed, final BiFunction<? super T, ? super U, ? extends Collection<? extends R>> mapper) {
        return flatCollection(new Function<T, Collection<? extends R>>() {
            @Override
            public Collection<? extends R> apply(T t) {
                return mapper.apply(t, seed);
            }
        });
    }

    @Override
    public <R> Stream<R> flatArray(final Function<? super T, ? extends R[]> mapper) {
        return flatMap0(new Function<T, Iterator<? extends R>>() {
            @Override
            public Iterator<? extends R> apply(T t) {
                return ExIterator.of(mapper.apply(t));
            }
        });
    }

    @Override
    public <U, R> Stream<R> flatArray(final U seed, final BiFunction<? super T, ? super U, ? extends R[]> mapper) {
        return flatArray(new Function<T, R[]>() {
            @Override
            public R[] apply(T t) {
                return mapper.apply(t, seed);
            }
        });
    }

    @Override
    public CharStream flatMapToChar(final Function<? super T, ? extends CharStream> mapper) {
        return flatMapToChar0(new Function<T, CharIterator>() {
            @Override
            public CharIterator apply(T t) {
                return mapper.apply(t).exIterator();
            }
        });
    }

    abstract CharStream flatMapToChar0(Function<? super T, CharIterator> function);

    @Override
    public ByteStream flatMapToByte(final Function<? super T, ? extends ByteStream> mapper) {
        return flatMapToByte0(new Function<T, ByteIterator>() {
            @Override
            public ByteIterator apply(T t) {
                return mapper.apply(t).exIterator();
            }
        });
    }

    abstract ByteStream flatMapToByte0(Function<? super T, ByteIterator> function);

    @Override
    public ShortStream flatMapToShort(final Function<? super T, ? extends ShortStream> mapper) {
        return flatMapToShort0(new Function<T, ShortIterator>() {
            @Override
            public ShortIterator apply(T t) {
                return mapper.apply(t).exIterator();
            }
        });
    }

    abstract ShortStream flatMapToShort0(Function<? super T, ShortIterator> function);

    @Override
    public IntStream flatMapToInt(final Function<? super T, ? extends IntStream> mapper) {
        return flatMapToInt0(new Function<T, IntIterator>() {
            @Override
            public IntIterator apply(T t) {
                return mapper.apply(t).exIterator();
            }
        });
    }

    abstract IntStream flatMapToInt0(Function<? super T, IntIterator> function);

    @Override
    public LongStream flatMapToLong(final Function<? super T, ? extends LongStream> mapper) {
        return flatMapToLong0(new Function<T, LongIterator>() {
            @Override
            public LongIterator apply(T t) {
                return mapper.apply(t).exIterator();
            }
        });
    }

    abstract LongStream flatMapToLong0(Function<? super T, LongIterator> function);

    @Override
    public FloatStream flatMapToFloat(final Function<? super T, ? extends FloatStream> mapper) {
        return flatMapToFloat0(new Function<T, FloatIterator>() {
            @Override
            public FloatIterator apply(T t) {
                return mapper.apply(t).exIterator();
            }
        });
    }

    abstract FloatStream flatMapToFloat0(Function<? super T, FloatIterator> function);

    @Override
    public DoubleStream flatMapToDouble(final Function<? super T, ? extends DoubleStream> mapper) {
        return flatMapToDouble0(new Function<T, DoubleIterator>() {
            @Override
            public DoubleIterator apply(T t) {
                return mapper.apply(t).exIterator();
            }
        });
    }

    abstract DoubleStream flatMapToDouble0(Function<? super T, DoubleIterator> function);

    @Override
    public <K, V> EntryStream<K, V> flatMapToEntry(final Function<? super T, ? extends Stream<? extends Map.Entry<K, V>>> mapper) {
        return EntryStream.of(flatMap(mapper));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Stream<T> sortedBy(final Function<? super T, ? extends Comparable> keyExtractor) {
        final Comparator<? super T> comparator = new Comparator<T>() {
            @Override
            public int compare(T o1, T o2) {
                return N.compare(keyExtractor.apply(o1), keyExtractor.apply(o2));
            }
        };

        return sorted(comparator);
    }

    @Override
    public Stream<Stream<T>> split(final int size) {
        return splitToList(size).map(new Function<List<T>, Stream<T>>() {
            @Override
            public Stream<T> apply(List<T> t) {
                return new ArrayStream<>(toArray(t), 0, t.size(), null, sorted, cmp);
            }
        });
    }

    @Override
    public Stream<Stream<T>> split(final Predicate<? super T> predicate) {
        return splitToList(predicate).map(new Function<List<T>, Stream<T>>() {
            @Override
            public Stream<T> apply(List<T> t) {
                return new ArrayStream<>(toArray(t), 0, t.size(), null, sorted, cmp);
            }
        });
    }

    @Override
    public Stream<List<T>> splitToList(final Predicate<? super T> predicate) {
        final BiFunction<T, Object, Boolean> predicate2 = new BiFunction<T, Object, Boolean>() {

            @Override
            public Boolean apply(T t, Object u) {
                return predicate.test(t);
            }
        };

        return splitToList(null, predicate2, null);
    }

    @Override
    public <U> Stream<Stream<T>> split(final U identity, final BiFunction<? super T, ? super U, Boolean> predicate, final Consumer<? super U> identityUpdate) {
        return splitToList(identity, predicate, identityUpdate).map(new Function<List<T>, Stream<T>>() {
            @Override
            public Stream<T> apply(List<T> t) {
                return new ArrayStream<>(toArray(t), 0, t.size(), null, sorted, cmp);
            }
        });
    }

    @Override
    public Stream<Stream<T>> sliding(final int windowSize, final int increment) {
        return slidingToList(windowSize, increment).map(new Function<List<T>, Stream<T>>() {
            @Override
            public Stream<T> apply(List<T> t) {
                return new ArrayStream<>(toArray(t), 0, t.size(), null, sorted, cmp);
            }
        });
    }

    @Override
    public Stream<T> collapse(final BiPredicate<? super T, ? super T> collapsible, final BiFunction<? super T, ? super T, T> mergeFunction) {
        final ExIterator<T> iter = exIterator();

        return this.newStream(new ExIterator<T>() {
            private boolean hasNext = false;
            private T next = null;

            @Override
            public boolean hasNext() {
                return hasNext || iter.hasNext();
            }

            @Override
            public T next() {
                T res = hasNext ? next : (next = iter.next());

                while ((hasNext = iter.hasNext())) {
                    if (collapsible.test(next, (next = iter.next()))) {
                        res = mergeFunction.apply(res, next);
                    } else {
                        break;
                    }
                }

                return res;
            }
        }, false, null);
    }

    @Override
    public <R, A> Stream<R> collapse(final BiPredicate<? super T, ? super T> collapsible, final Collector<? super T, A, R> collector) {
        final Supplier<A> supplier = collector.supplier();
        final BiConsumer<A, ? super T> accumulator = collector.accumulator();
        final Function<A, R> finisher = collector.finisher();
        final ExIterator<T> iter = exIterator();

        return this.newStream(new ExIterator<R>() {
            private boolean hasNext = false;
            private T next = null;

            @Override
            public boolean hasNext() {
                return hasNext || iter.hasNext();
            }

            @Override
            public R next() {
                final A c = supplier.get();
                accumulator.accept(c, hasNext ? next : (next = iter.next()));

                while ((hasNext = iter.hasNext())) {
                    if (collapsible.test(next, (next = iter.next()))) {
                        accumulator.accept(c, next);
                    } else {
                        break;
                    }
                }

                return finisher.apply(c);
            }
        }, false, null);
    }

    @Override
    public Stream<T> scan(final BiFunction<? super T, ? super T, T> accumulator) {
        final ExIterator<T> iter = exIterator();

        return this.newStream(new ExIterator<T>() {
            private T res = null;
            private boolean isFirst = true;

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public T next() {
                if (isFirst) {
                    isFirst = false;
                    return (res = iter.next());
                } else {
                    return (res = accumulator.apply(res, iter.next()));
                }
            }
        }, false, null);
    }

    @Override
    public <R> Stream<R> scan(final R seed, final BiFunction<? super R, ? super T, R> accumulator) {
        final ExIterator<T> iter = exIterator();

        return this.newStream(new ExIterator<R>() {
            private R res = seed;

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public R next() {
                return (res = accumulator.apply(res, iter.next()));
            }
        }, false, null);
    }

    @Override
    public Stream<T> intersperse(final T delimiter) {
        return newStream(new ExIterator<T>() {
            private final Iterator<T> iter = iterator();
            private boolean toInsert = false;

            @Override
            public boolean hasNext() {
                return iter.hasNext();
            }

            @Override
            public T next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                if (toInsert) {
                    toInsert = false;
                    return delimiter;
                } else {
                    final T res = iter.next();
                    toInsert = true;
                    return res;
                }
            }
        }, false, null);
    }

    @Override
    public void forEachPair(final BiConsumer<? super T, ? super T> action) {
        forEachPair(action, 1);
    }

    @Override
    public void forEachTriple(final TriConsumer<? super T, ? super T, ? super T> action) {
        forEachTriple(action, 1);
    }

    @Override
    public <K> Stream<Entry<K, List<T>>> groupBy(final Function<? super T, ? extends K> classifier) {
        final Map<K, List<T>> map = collect(Collectors.groupingBy(classifier));

        return newStream(map.entrySet().iterator(), false, null);
    }

    @Override
    public <K> Stream<Entry<K, List<T>>> groupBy(final Function<? super T, ? extends K> classifier, Supplier<Map<K, List<T>>> mapFactory) {
        final Map<K, List<T>> map = collect(Collectors.groupingBy(classifier, mapFactory));

        return newStream(map.entrySet().iterator(), false, null);
    }

    @Override
    public <K, U> Stream<Entry<K, List<U>>> groupBy(Function<? super T, ? extends K> classifier, Function<? super T, ? extends U> valueMapper) {
        return groupBy(classifier, (Collector<T, ?, List<U>>) (Collector<?, ?, ?>) Collectors.mapping(valueMapper, Collectors.toList()));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public <K, U> Stream<Map.Entry<K, List<U>>> groupBy(Function<? super T, ? extends K> classifier, Function<? super T, ? extends U> valueMapper,
            Supplier<Map<K, List<U>>> mapFactory) {
        final Map<K, List<U>> map = collect(
                Collectors.groupingBy(classifier, (Collector<T, ?, List<U>>) (Collector) Collectors.mapping(valueMapper, Collectors.toList()), mapFactory));

        return newStream(map.entrySet().iterator(), false, null);
    }

    @Override
    public <K, A, D> Stream<Entry<K, D>> groupBy(final Function<? super T, ? extends K> classifier, Collector<? super T, A, D> downstream) {
        final Map<K, D> map = collect(Collectors.groupingBy(classifier, downstream));

        return newStream(map.entrySet().iterator(), false, null);
    }

    @Override
    public <K, A, D> Stream<Entry<K, D>> groupBy(final Function<? super T, ? extends K> classifier, Collector<? super T, A, D> downstream,
            Supplier<Map<K, D>> mapFactory) {
        final Map<K, D> map = collect(Collectors.groupingBy(classifier, downstream, mapFactory));

        return newStream(map.entrySet().iterator(), false, null);
    }

    @Override
    public <K, U> Stream<Entry<K, U>> groupBy(final Function<? super T, ? extends K> classifier, final Function<? super T, ? extends U> valueMapper,
            BinaryOperator<U> mergeFunction) {
        final Map<K, U> map = collect(Collectors.toMap(classifier, valueMapper, mergeFunction));

        return newStream(map.entrySet().iterator(), false, null);
    }

    @Override
    public <K, U> Stream<Entry<K, U>> groupBy(final Function<? super T, ? extends K> classifier, final Function<? super T, ? extends U> valueMapper,
            BinaryOperator<U> mergeFunction, Supplier<Map<K, U>> mapFactory) {
        final Map<K, U> map = collect(Collectors.toMap(classifier, valueMapper, mergeFunction, mapFactory));

        return newStream(map.entrySet().iterator(), false, null);
    }

    @Override
    public <K> EntryStream<K, List<T>> groupByToEntry(Function<? super T, ? extends K> classifier) {
        final Function<Map.Entry<K, List<T>>, Map.Entry<K, List<T>>> mapper = Fn.identity();
        final Function<T, K> classifier2 = (Function<T, K>) classifier;

        return groupBy(classifier2).mapToEntry(mapper);
    }

    @Override
    public <K> EntryStream<K, List<T>> groupByToEntry(Function<? super T, ? extends K> classifier, Supplier<Map<K, List<T>>> mapFactory) {
        final Function<Map.Entry<K, List<T>>, Map.Entry<K, List<T>>> mapper = Fn.identity();

        return groupBy(classifier, mapFactory).mapToEntry(mapper);
    }

    @Override
    public <K, U> EntryStream<K, List<U>> groupByToEntry(Function<? super T, ? extends K> classifier, Function<? super T, ? extends U> valueMapper) {
        final Function<Map.Entry<K, List<U>>, Map.Entry<K, List<U>>> mapper = Fn.identity();
        final Function<T, K> classifier2 = (Function<T, K>) classifier;
        final Function<T, U> valueMapper2 = (Function<T, U>) valueMapper;

        return groupBy(classifier2, valueMapper2).mapToEntry(mapper);
    }

    @Override
    public <K, U> EntryStream<K, List<U>> groupByToEntry(Function<? super T, ? extends K> classifier, Function<? super T, ? extends U> valueMapper,
            Supplier<Map<K, List<U>>> mapFactory) {
        final Function<Map.Entry<K, List<U>>, Map.Entry<K, List<U>>> mapper = Fn.identity();

        return groupBy(classifier, valueMapper, mapFactory).mapToEntry(mapper);
    }

    @Override
    public <K, A, D> EntryStream<K, D> groupByToEntry(Function<? super T, ? extends K> classifier, Collector<? super T, A, D> downstream) {
        final Function<Map.Entry<K, D>, Map.Entry<K, D>> mapper = Fn.identity();
        final Function<T, K> classifier2 = (Function<T, K>) classifier;

        return groupBy(classifier2, downstream).mapToEntry(mapper);
    }

    @Override
    public <K, A, D> EntryStream<K, D> groupByToEntry(Function<? super T, ? extends K> classifier, Collector<? super T, A, D> downstream,
            Supplier<Map<K, D>> mapFactory) {
        final Function<Map.Entry<K, D>, Map.Entry<K, D>> mapper = Fn.identity();

        return groupBy(classifier, downstream, mapFactory).mapToEntry(mapper);
    }

    @Override
    public <K, U> EntryStream<K, U> groupByToEntry(Function<? super T, ? extends K> classifier, Function<? super T, ? extends U> valueMapper,
            BinaryOperator<U> mergeFunction) {
        final Function<Map.Entry<K, U>, Map.Entry<K, U>> mapper = Fn.identity();
        final Function<T, K> classifier2 = (Function<T, K>) classifier;
        final Function<T, U> valueMapper2 = (Function<T, U>) valueMapper;

        return groupBy(classifier2, valueMapper2, mergeFunction).mapToEntry(mapper);
    }

    @Override
    public <K, U> EntryStream<K, U> groupByToEntry(Function<? super T, ? extends K> classifier, Function<? super T, ? extends U> valueMapper,
            BinaryOperator<U> mergeFunction, Supplier<Map<K, U>> mapFactory) {
        final Function<Map.Entry<K, U>, Map.Entry<K, U>> mapper = Fn.identity();

        return groupBy(classifier, valueMapper, mergeFunction, mapFactory).mapToEntry(mapper);
    }

    @Override
    public Stream<Map.Entry<Boolean, List<T>>> partitionBy(Predicate<? super T> predicate) {
        final Map<Boolean, List<T>> map = collect(Collectors.partitioningBy(predicate));

        return newStream(map.entrySet().iterator(), false, null);
    }

    @Override
    public <D> Stream<Map.Entry<Boolean, D>> partitionBy(Predicate<? super T> predicate, Collector<? super T, ?, D> downstream) {
        final Map<Boolean, D> map = collect(Collectors.partitioningBy(predicate, downstream));

        return newStream(map.entrySet().iterator(), false, null);
    }

    @Override
    public EntryStream<Boolean, List<T>> partitionByToEntry(Predicate<? super T> predicate) {
        final Function<Map.Entry<Boolean, List<T>>, Map.Entry<Boolean, List<T>>> mapper = Fn.identity();

        return partitionBy(predicate).mapToEntry(mapper);
    }

    @Override
    public <D> EntryStream<Boolean, D> partitionByToEntry(Predicate<? super T> predicate, Collector<? super T, ?, D> downstream) {
        final Function<Map.Entry<Boolean, D>, Map.Entry<Boolean, D>> mapper = Fn.identity();

        return partitionBy(predicate, downstream).mapToEntry(mapper);
    }

    @Override
    public <K, U> Map<K, U> toMap(Function<? super T, ? extends K> keyExtractor, Function<? super T, ? extends U> valueMapper) {
        final Supplier<Map<K, U>> mapFactory = Fn.Suppliers.ofMap();

        return toMap(keyExtractor, valueMapper, mapFactory);
    }

    @Override
    public <K, U, M extends Map<K, U>> M toMap(Function<? super T, ? extends K> keyExtractor, Function<? super T, ? extends U> valueMapper,
            Supplier<M> mapFactory) {
        final BinaryOperator<U> mergeFunction = Fn.throwingMerger();

        return toMap(keyExtractor, valueMapper, mergeFunction, mapFactory);
    }

    @Override
    public <K, U> Map<K, U> toMap(Function<? super T, ? extends K> keyExtractor, Function<? super T, ? extends U> valueMapper,
            BinaryOperator<U> mergeFunction) {
        final Supplier<Map<K, U>> mapFactory = Fn.Suppliers.ofMap();

        return toMap(keyExtractor, valueMapper, mergeFunction, mapFactory);
    }

    @Override
    public <K, A, D> Map<K, D> toMap(Function<? super T, ? extends K> classifier, Collector<? super T, A, D> downstream) {
        final Supplier<Map<K, D>> mapFactory = Fn.Suppliers.ofMap();

        return toMap(classifier, downstream, mapFactory);
    }

    @Override
    public <K> Map<K, List<T>> groupTo(Function<? super T, ? extends K> classifier) {
        final Supplier<Map<K, List<T>>> mapFactory = Fn.Suppliers.ofMap();

        return groupTo(classifier, mapFactory);
    }

    @Override
    public <K, M extends Map<K, List<T>>> M groupTo(Function<? super T, ? extends K> classifier, Supplier<M> mapFactory) {
        final Collector<? super T, ?, List<T>> downstream = Collectors.toList();

        return toMap(classifier, downstream, mapFactory);
    }

    @Override
    public <K, U> Map<K, List<U>> groupTo(Function<? super T, ? extends K> keyExtractor, Function<? super T, ? extends U> valueMapper) {
        return toMap(keyExtractor, (Collector<T, ?, List<U>>) (Collector<?, ?, ?>) Collectors.mapping(valueMapper, Collectors.toList()));
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <K, U, M extends Map<K, List<U>>> M groupTo(Function<? super T, ? extends K> keyExtractor, Function<? super T, ? extends U> valueMapper,
            Supplier<M> mapFactory) {
        return toMap(keyExtractor, (Collector<T, ?, List<U>>) (Collector) Collectors.mapping(valueMapper, Collectors.toList()), mapFactory);
    }

    @Override
    public <K> ListMultimap<K, T> toMultimap(Function<? super T, ? extends K> keyExtractor) {
        return toMultimap(keyExtractor, new Function<T, T>() {
            @Override
            public T apply(T t) {
                return t;
            }
        });
    }

    @Override
    public <K, V extends Collection<T>, M extends Multimap<K, T, V>> M toMultimap(Function<? super T, ? extends K> keyExtractor, Supplier<M> mapFactory) {
        return toMultimap(keyExtractor, new Function<T, T>() {
            @Override
            public T apply(T t) {
                return t;
            }
        }, mapFactory);
    }

    @Override
    public <K, U> ListMultimap<K, U> toMultimap(Function<? super T, ? extends K> keyExtractor, Function<? super T, ? extends U> valueMapper) {
        return toMultimap(keyExtractor, valueMapper, new Supplier<ListMultimap<K, U>>() {
            @Override
            public ListMultimap<K, U> get() {
                return N.newListMultimap();
            }
        });
    }

    @Override
    public Matrix<T> toMatrix(final Class<T> type) {
        final T[] a = toArray(new IntFunction<T[]>() {
            @Override
            public T[] apply(int value) {
                return (T[]) N.newArray(type, 0);
            }
        });

        return Matrix.of(a);
    }

    @Override
    public int sumInt(ToIntFunction<? super T> mapper) {
        return collect(Collectors.summingInt(mapper));
    }

    @Override
    public long sumLong(ToLongFunction<? super T> mapper) {
        return collect(Collectors.summingLong(mapper));
    }

    @Override
    public double sumDouble(ToDoubleFunction<? super T> mapper) {
        return collect(Collectors.summingDouble(mapper));
    }

    @Override
    public OptionalDouble averageInt(ToIntFunction<? super T> mapper) {
        return collect(Collectors.averagingInt2(mapper));
    }

    @Override
    public OptionalDouble averageLong(ToLongFunction<? super T> mapper) {
        return collect(Collectors.averagingLong2(mapper));
    }

    @Override
    public OptionalDouble averageDouble(ToDoubleFunction<? super T> mapper) {
        return collect(Collectors.averagingDouble2(mapper));
    }

    @Override
    public <U> Stream<Pair<T, U>> innerJoin(final Collection<U> b, final Function<? super T, ?> leftKeyMapper, final Function<? super U, ?> rightKeyMapper) {
        final ListMultimap<Object, U> rightKeyMap = ListMultimap.from(b, rightKeyMapper);
        final Map<Object, Stream<U>> rightKeyStreamMap = new HashMap<>(N.initHashCapacity(rightKeyMap.size()));

        for (Map.Entry<Object, List<U>> entry : rightKeyMap.entrySet()) {
            rightKeyStreamMap.put(entry.getKey(), Stream.of(entry.getValue()).cached());
        }

        return flatMap(new Function<T, Stream<Pair<T, U>>>() {
            @Override
            public Stream<Pair<T, U>> apply(final T t) {
                final Stream<U> s = rightKeyStreamMap.get(leftKeyMapper.apply(t));

                return s == null ? Stream.<Pair<T, U>> empty() : s.map(new Function<U, Pair<T, U>>() {
                    @Override
                    public Pair<T, U> apply(U u) {
                        return Pair.of(t, u);
                    }
                });
            }
        });
    }

    @Override
    public <U> Stream<Pair<T, U>> innerJoin(final Collection<U> b, final BiPredicate<? super T, ? super U> predicate) {
        final Stream<U> s = Stream.of(b).cached();

        return flatMap(new Function<T, Stream<Pair<T, U>>>() {
            @Override
            public Stream<Pair<T, U>> apply(final T t) {
                return s.filter(new Predicate<U>() {
                    @Override
                    public boolean test(final U u) {
                        return predicate.test(t, u);
                    }
                }).map(new Function<U, Pair<T, U>>() {
                    @Override
                    public Pair<T, U> apply(U u) {
                        return Pair.of(t, u);
                    }
                });
            }
        });
    }

    @Override
    public <U> Stream<Pair<T, U>> fullJoin(final Collection<U> b, final Function<? super T, ?> leftKeyMapper, final Function<? super U, ?> rightKeyMapper) {
        final ListMultimap<Object, U> rightKeyMap = ListMultimap.from(b, rightKeyMapper);
        final Map<Object, Stream<U>> rightKeyStreamMap = new HashMap<>(N.initHashCapacity(rightKeyMap.size()));
        final Map<U, U> joinedRights = new IdentityHashMap<>();
        final boolean isParallelStream = this.isParallel();

        for (Map.Entry<Object, List<U>> entry : rightKeyMap.entrySet()) {
            rightKeyStreamMap.put(entry.getKey(), Stream.of(entry.getValue()).cached());
        }

        return flatMap(new Function<T, Stream<Pair<T, U>>>() {
            @Override
            public Stream<Pair<T, U>> apply(final T t) {
                final Stream<U> s = rightKeyStreamMap.get(leftKeyMapper.apply(t));

                return s == null ? Stream.of(Pair.of(t, (U) null)) : s.map(new Function<U, Pair<T, U>>() {
                    @Override
                    public Pair<T, U> apply(U u) {
                        if (isParallelStream) {
                            synchronized (joinedRights) {
                                joinedRights.put(u, u);
                            }
                        } else {
                            joinedRights.put(u, u);
                        }

                        return Pair.of(t, u);
                    }
                });
            }
        }).append(Stream.of(b).filter(new Predicate<U>() {
            @Override
            public boolean test(U u) {
                return joinedRights.containsKey(u) == false;
            }
        }).map(new Function<U, Pair<T, U>>() {
            @Override
            public Pair<T, U> apply(U u) {
                return Pair.of((T) null, u);
            }
        }));
    }

    @Override
    public <U> Stream<Pair<T, U>> fullJoin(final Collection<U> b, final BiPredicate<? super T, ? super U> predicate) {
        final Stream<U> s = Stream.of(b).cached();
        final Map<U, U> joinedRights = new IdentityHashMap<>();
        final boolean isParallelStream = this.isParallel();

        return flatMap(new Function<T, Stream<Pair<T, U>>>() {
            @Override
            public Stream<Pair<T, U>> apply(final T t) {
                final MutableBoolean joined = MutableBoolean.of(false);

                return s.filter(new Predicate<U>() {
                    @Override
                    public boolean test(final U u) {
                        return predicate.test(t, u);
                    }
                }).map(new Function<U, Pair<T, U>>() {
                    @Override
                    public Pair<T, U> apply(U u) {
                        joined.setTrue();

                        if (isParallelStream) {
                            synchronized (joinedRights) {
                                joinedRights.put(u, u);
                            }
                        } else {
                            joinedRights.put(u, u);
                        }

                        return Pair.of(t, u);
                    }
                }).append(Stream.iterate(new Supplier<Boolean>() {
                    @Override
                    public Boolean get() {
                        return joined.isFalse();
                    }
                }, new Supplier<Pair<T, U>>() {
                    @Override
                    public Pair<T, U> get() {
                        joined.setTrue();
                        return Pair.of(t, (U) null);
                    }
                }));
            }
        }).append(Stream.of(b).filter(new Predicate<U>() {
            @Override
            public boolean test(U u) {
                return joinedRights.containsKey(u) == false;
            }
        }).map(new Function<U, Pair<T, U>>() {
            @Override
            public Pair<T, U> apply(U u) {
                return Pair.of((T) null, u);
            }
        }));
    }

    @Override
    public <U> Stream<Pair<T, U>> leftJoin(final Collection<U> b, final Function<? super T, ?> leftKeyMapper, final Function<? super U, ?> rightKeyMapper) {
        final ListMultimap<Object, U> rightKeyMap = ListMultimap.from(b, rightKeyMapper);
        final Map<Object, Stream<U>> rightKeyStreamMap = new HashMap<>(N.initHashCapacity(rightKeyMap.size()));

        for (Map.Entry<Object, List<U>> entry : rightKeyMap.entrySet()) {
            rightKeyStreamMap.put(entry.getKey(), Stream.of(entry.getValue()).cached());
        }

        return flatMap(new Function<T, Stream<Pair<T, U>>>() {
            @Override
            public Stream<Pair<T, U>> apply(final T t) {
                final Stream<U> s = rightKeyStreamMap.get(leftKeyMapper.apply(t));

                return s == null ? Stream.of(Pair.of(t, (U) null)) : s.map(new Function<U, Pair<T, U>>() {
                    @Override
                    public Pair<T, U> apply(U u) {
                        return Pair.of(t, u);
                    }
                });
            }
        });
    }

    @Override
    public <U> Stream<Pair<T, U>> leftJoin(final Collection<U> b, final BiPredicate<? super T, ? super U> predicate) {
        final Stream<U> s = Stream.of(b).cached();

        return flatMap(new Function<T, Stream<Pair<T, U>>>() {
            @Override
            public Stream<Pair<T, U>> apply(final T t) {
                final MutableBoolean joined = MutableBoolean.of(false);

                return s.filter(new Predicate<U>() {
                    @Override
                    public boolean test(final U u) {
                        return predicate.test(t, u);
                    }
                }).map(new Function<U, Pair<T, U>>() {
                    @Override
                    public Pair<T, U> apply(U u) {
                        joined.setTrue();

                        return Pair.of(t, u);
                    }
                }).append(Stream.iterate(new Supplier<Boolean>() {
                    @Override
                    public Boolean get() {
                        return joined.isFalse();
                    }
                }, new Supplier<Pair<T, U>>() {
                    @Override
                    public Pair<T, U> get() {
                        joined.setTrue();
                        return Pair.of(t, (U) null);
                    }
                }));
            }
        });
    }

    @Override
    public <U> Stream<Pair<T, U>> rightJoin(final Collection<U> b, final Function<? super T, ?> leftKeyMapper, final Function<? super U, ?> rightKeyMapper) {
        final ListMultimap<Object, U> rightKeyMap = ListMultimap.from(b, rightKeyMapper);
        final Map<Object, Stream<U>> rightKeyStreamMap = new HashMap<>(N.initHashCapacity(rightKeyMap.size()));
        final Map<U, U> joinedRights = new IdentityHashMap<>();
        final boolean isParallelStream = this.isParallel();

        for (Map.Entry<Object, List<U>> entry : rightKeyMap.entrySet()) {
            rightKeyStreamMap.put(entry.getKey(), Stream.of(entry.getValue()).cached());
        }

        return flatMap(new Function<T, Stream<Pair<T, U>>>() {
            @Override
            public Stream<Pair<T, U>> apply(final T t) {
                final Stream<U> s = rightKeyStreamMap.get(leftKeyMapper.apply(t));

                return s == null ? Stream.<Pair<T, U>> empty() : s.map(new Function<U, Pair<T, U>>() {
                    @Override
                    public Pair<T, U> apply(U u) {
                        if (isParallelStream) {
                            synchronized (joinedRights) {
                                joinedRights.put(u, u);
                            }
                        } else {
                            joinedRights.put(u, u);
                        }

                        return Pair.of(t, u);
                    }
                });
            }
        }).append(Stream.of(b).filter(new Predicate<U>() {
            @Override
            public boolean test(U u) {
                return joinedRights.containsKey(u) == false;
            }
        }).map(new Function<U, Pair<T, U>>() {
            @Override
            public Pair<T, U> apply(U u) {
                return Pair.of((T) null, u);
            }
        }));
    }

    @Override
    public <U> Stream<Pair<T, U>> rightJoin(final Collection<U> b, final BiPredicate<? super T, ? super U> predicate) {
        final Stream<U> s = Stream.of(b).cached();
        final Map<U, U> joinedRights = new IdentityHashMap<>();
        final boolean isParallelStream = this.isParallel();

        return flatMap(new Function<T, Stream<Pair<T, U>>>() {
            @Override
            public Stream<Pair<T, U>> apply(final T t) {
                return s.filter(new Predicate<U>() {
                    @Override
                    public boolean test(final U u) {
                        return predicate.test(t, u);
                    }
                }).map(new Function<U, Pair<T, U>>() {
                    @Override
                    public Pair<T, U> apply(U u) {

                        if (isParallelStream) {
                            synchronized (joinedRights) {
                                joinedRights.put(u, u);
                            }
                        } else {
                            joinedRights.put(u, u);
                        }

                        return Pair.of(t, u);
                    }
                });
            }
        }).append(Stream.of(b).filter(new Predicate<U>() {
            @Override
            public boolean test(U u) {
                return joinedRights.containsKey(u) == false;
            }
        }).map(new Function<U, Pair<T, U>>() {
            @Override
            public Pair<T, U> apply(U u) {
                return Pair.of((T) null, u);
            }
        }));
    }

    @Override
    public <U> NullabLe<T> findFirst(final U seed, final BiPredicate<? super T, ? super U> predicate) {
        return findFirst(new Predicate<T>() {
            @Override
            public boolean test(T t) {
                return predicate.test(t, seed);
            }
        });
    }

    @Override
    public <U> NullabLe<T> findLast(final U seed, final BiPredicate<? super T, ? super U> predicate) {
        return findLast(new Predicate<T>() {
            @Override
            public boolean test(T t) {
                return predicate.test(t, seed);
            }
        });
    }

    @Override
    public NullabLe<T> findFirstOrLast(final Predicate<? super T> predicateForFirst, final Predicate<? super T> predicateForLast) {
        final ExIterator<T> iter = exIterator();
        T last = (T) NONE;
        T next = null;

        while (iter.hasNext()) {
            next = iter.next();

            if (predicateForFirst.test(next)) {
                return NullabLe.of(next);
            } else if (predicateForLast.test(next)) {
                last = next;
            }
        }

        return last == NONE ? (NullabLe<T>) NullabLe.empty() : NullabLe.of(last);
    }

    @Override
    public <U> NullabLe<T> findFirstOrLast(final U seed, final BiPredicate<? super T, ? super U> predicateForFirst,
            final BiPredicate<? super T, ? super U> predicateForLast) {
        final ExIterator<T> iter = exIterator();
        T last = (T) NONE;
        T next = null;

        while (iter.hasNext()) {
            next = iter.next();

            if (predicateForFirst.test(next, seed)) {
                return NullabLe.of(next);
            } else if (predicateForLast.test(next, seed)) {
                last = next;
            }
        }

        return last == NONE ? (NullabLe<T>) NullabLe.empty() : NullabLe.of(last);
    }

    @Override
    public <U> NullabLe<T> findFirstOrLast(final Function<? super T, U> preFunc, final BiPredicate<? super T, ? super U> predicateForFirst,
            final BiPredicate<? super T, ? super U> predicateForLast) {
        final ExIterator<T> iter = exIterator();
        U seed = null;
        T last = (T) NONE;
        T next = null;

        while (iter.hasNext()) {
            next = iter.next();
            seed = preFunc.apply(next);

            if (predicateForFirst.test(next, seed)) {
                return NullabLe.of(next);
            } else if (predicateForLast.test(next, seed)) {
                last = next;
            }
        }

        return last == NONE ? (NullabLe<T>) NullabLe.empty() : NullabLe.of(last);
    }

    @Override
    public <U> NullabLe<T> findAny(final U seed, final BiPredicate<? super T, ? super U> predicate) {
        return findAny(new Predicate<T>() {
            @Override
            public boolean test(T t) {
                return predicate.test(t, seed);
            }
        });
    }

    @Override
    public <U> boolean anyMatch(final U seed, final BiPredicate<? super T, ? super U> predicate) {
        return anyMatch(new Predicate<T>() {
            @Override
            public boolean test(T t) {
                return predicate.test(t, seed);
            }
        });
    }

    @Override
    public <U> boolean allMatch(final U seed, final BiPredicate<? super T, ? super U> predicate) {
        return allMatch(new Predicate<T>() {
            @Override
            public boolean test(T t) {
                return predicate.test(t, seed);
            }
        });
    }

    @Override
    public <U> boolean noneMatch(final U seed, final BiPredicate<? super T, ? super U> predicate) {
        return noneMatch(new Predicate<T>() {
            @Override
            public boolean test(T t) {
                return predicate.test(t, seed);
            }
        });
    }

    @Override
    public boolean containsAll(final T... a) {
        if (N.isNullOrEmpty(a)) {
            return true;
        } else if (a.length == 1) {
            return anyMatch(Fn.equal(a[0]));
        } else if (a.length == 2) {
            return filter(new Predicate<T>() {
                private final T val1 = a[0];
                private final T val2 = a[1];

                @Override
                public boolean test(T t) {
                    return N.equals(t, val1) || N.equals(t, val2);
                }
            }).distinct().limit(2).count() == 2;
        } else {
            return containsAll(N.asSet(a));
        }
    }

    @Override
    public boolean containsAll(final Collection<? extends T> c) {
        if (N.isNullOrEmpty(c)) {
            return true;
        } else if (c.size() == 1) {
            final T val = c instanceof List ? ((List<T>) c).get(0) : c.iterator().next();
            return anyMatch(Fn.equal(val));
        } else {
            final Set<T> set = c instanceof Set ? (Set<T>) c : N.newHashSet(c);
            return filter(new Predicate<T>() {
                @Override
                public boolean test(T t) {
                    return set.contains(t);
                }
            }).distinct().limit(set.size()).count() == set.size();
        }
    }

    @Override
    public NullabLe<T> first() {
        final Iterator<T> iter = this.iterator();

        if (iter.hasNext() == false) {
            return NullabLe.empty();
        }

        return NullabLe.of(iter.next());
    }

    @Override
    public NullabLe<T> last() {
        final Iterator<T> iter = this.iterator();

        if (iter.hasNext() == false) {
            return NullabLe.empty();
        }

        T next = iter.next();

        while (iter.hasNext()) {
            next = iter.next();
        }

        return NullabLe.of(next);
    }

    @Override
    public Stream<T> skipNull() {
        return filter(Fn.notNull());
    }

    @Override
    public Stream<Stream<T>> splitAt(final int n) {
        if (n < 0) {
            throw new IllegalArgumentException("'n' can't be negative");
        }

        final Iterator<T> iter = this.iterator();
        final List<T> list = new ArrayList<>();

        while (list.size() < n && iter.hasNext()) {
            list.add(iter.next());
        }

        final Stream<T>[] a = new Stream[] { new ArrayStream<>(toArray(list), 0, list.size(), null, sorted, cmp),
                new IteratorStream<>(iter, null, sorted, cmp) };

        return newStream(a, false, null);
    }

    @Override
    public Stream<Stream<T>> splitBy(Predicate<? super T> where) {
        N.requireNonNull(where);

        final Iterator<T> iter = this.iterator();
        final List<T> list = new ArrayList<>();
        T next = null;
        Stream<T> s = null;

        while (iter.hasNext()) {
            next = iter.next();

            if (where.test(next)) {
                list.add(next);
            } else {
                s = Stream.of(next);

                break;
            }
        }

        final Stream<T>[] a = new Stream[] { new ArrayStream<>((T[]) list.toArray(), null, sorted, cmp), new IteratorStream<>(iter, null, sorted, cmp) };

        if (s != null) {
            if (sorted) {
                a[1] = new IteratorStream<>(a[1].prepend(s).iterator(), null, sorted, cmp);
            } else {
                a[1] = a[1].prepend(s);
            }
        }

        return this.newStream(a, false, null);
    }

    @Override
    public Stream<List<T>> slidingToList(int windowSize) {
        return slidingToList(windowSize, 1);
    }

    @Override
    public Stream<T> intersection(final Collection<?> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return multiset.getAndRemove(value) > 0;
            }
        }).iterator(), sorted, cmp);
    }

    @Override
    public Stream<T> intersection(final Function<? super T, ?> mapper, final Collection<?> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return multiset.getAndRemove(mapper.apply(value)) > 0;
            }
        }).iterator(), sorted, cmp);
    }

    @Override
    public Stream<T> difference(final Collection<?> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return multiset.getAndRemove(value) < 1;
            }
        }).iterator(), sorted, cmp);
    }

    @Override
    public Stream<T> difference(final Function<? super T, ?> mapper, final Collection<?> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return multiset.getAndRemove(mapper.apply(value)) < 1;
            }
        }).iterator(), sorted, cmp);
    }

    @Override
    public Stream<T> symmetricDifference(final Collection<T> c) {
        final Multiset<?> multiset = Multiset.from(c);

        return newStream(this.sequential().filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return multiset.getAndRemove(value) < 1;
            }
        }).append(Stream.of(c).filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return multiset.getAndRemove(value) > 0;
            }
        })).iterator(), false, null);
    }

    @Override
    public Stream<T> reversed() {
        final T[] tmp = (T[]) toArray();

        return newStream(new ExIterator<T>() {
            private int cursor = tmp.length;

            @Override
            public boolean hasNext() {
                return cursor > 0;
            }

            @Override
            public T next() {
                if (cursor <= 0) {
                    throw new NoSuchElementException();
                }

                return tmp[--cursor];
            }

            @Override
            public long count() {
                return cursor;
            }

            @Override
            public void skip(long n) {
                cursor = n < cursor ? cursor - (int) n : 0;
            }

            @Override
            public <A> A[] toArray(A[] a) {
                a = a.length >= cursor ? a : (A[]) N.newArray(a.getClass().getComponentType(), cursor);

                for (int i = 0, len = a.length; i < len; i++) {
                    a[i] = (A) tmp[cursor - i - 1];
                }

                return a;
            }
        }, false, null);
    }

    @Override
    public Stream<T> shuffled() {
        final T[] a = (T[]) toArray();

        N.shuffle(a);

        return newStream(a, false, null);
    }

    @Override
    public Stream<T> shuffled(final Random rnd) {
        final T[] a = (T[]) toArray();

        N.shuffle(a, rnd);

        return newStream(a, false, null);
    }

    @Override
    public Stream<T> rotated(int distance) {
        final T[] a = (T[]) toArray();

        N.rotate(a, distance);

        return newStream(a, false, null);
    }

    @Override
    public Stream<T> reverseSorted() {
        return sorted(Fn.reversedOrder());
    }

    @Override
    public Stream<T> distinct() {
        final Set<Object> set = new HashSet<>();

        return newStream(this.sequential().filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return set.add(hashKey(value));
            }
        }).iterator(), sorted, cmp);
    }

    @Override
    public Stream<T> distinctBy(final Function<? super T, ?> keyExtractor) {
        final Set<Object> set = new HashSet<>();

        return newStream(this.sequential().filter(new Predicate<T>() {
            @Override
            public boolean test(T value) {
                return set.add(hashKey(keyExtractor.apply(value)));
            }
        }).iterator(), sorted, cmp);
    }

    @Override
    public Optional<Map<Percentage, T>> distribution() {
        final Object[] a = sorted().toArray();

        if (N.isNullOrEmpty(a)) {
            return Optional.empty();
        }

        return Optional.of((Map<Percentage, T>) N.distribution(a));
    }

    @Override
    public Optional<Map<Percentage, T>> distribution(Comparator<? super T> comparator) {
        final Object[] a = sorted(comparator).toArray();

        if (N.isNullOrEmpty(a)) {
            return Optional.empty();
        }

        return Optional.of((Map<Percentage, T>) N.distribution(a));
    }

    //    @Override
    //    public Stream<Set<T>> powerSet() {
    //        final Set<T> set = toSet(new Supplier<Set<T>>() {
    //            @Override
    //            public Set<T> get() {
    //                return new LinkedHashSet<>();
    //            }
    //        });
    //
    //        return newStream(Seq.powerSet(set).iterator(), false, null);
    //    }

    @Override
    public Stream<List<T>> combinations() {
        if (this instanceof ArrayStream) {
            return newStream(IntStream.rangeClosed(0, (int) count()).flatMapToObj(new IntFunction<Stream<List<T>>>() {
                @Override
                public Stream<List<T>> apply(int value) {
                    return combinations(value);
                }
            }).iterator(), false, null);
        } else {
            return newStream((T[]) toArray(), false, null).combinations();
        }
    }

    @Override
    public Stream<List<T>> combinations(final int len) {
        if (this instanceof ArrayStream) {
            N.checkFromIndexSize(0, len, (int) count());

            if (len == 0) {
                return newStream(N.asArray((List<T>) N.EMPTY_LIST), false, null);
            } else if (len == 1) {
                return map(new Function<T, List<T>>() {
                    @Override
                    public List<T> apply(T t) {
                        return N.asList(t);
                    }
                });
            } else if (len == count()) {
                return newStream(N.asArray(toList()), false, null);
            } else {
                final T[] a = ((ArrayStream<T>) this).elements;
                final int fromIndex = ((ArrayStream<T>) this).fromIndex;
                final int toIndex = ((ArrayStream<T>) this).toIndex;

                return newStream(new ExIterator<List<T>>() {

                    private final int[] indices = Array.range(fromIndex, fromIndex + len);

                    @Override
                    public boolean hasNext() {
                        return indices[0] <= toIndex - len;
                    }

                    @Override
                    public List<T> next() {
                        final List<T> result = new ArrayList<>(len);

                        for (int idx : indices) {
                            result.add(a[idx]);
                        }

                        if (++indices[len - 1] == toIndex) {
                            for (int i = len - 1; i > 0; i--) {
                                if (indices[i] > toIndex - (len - i)) {
                                    indices[i - 1]++;

                                    for (int j = i; j < len; j++) {
                                        indices[j] = indices[j - 1] + 1;
                                    }
                                }
                            }
                        }

                        return result;
                    }

                }, false, null);
            }
        } else {
            return newStream((T[]) toArray(), false, null).combinations(len);
        }
    }

    @Override
    public Stream<List<T>> permutations() {
        return newStream(PermutationIterator.of(toList()), false, null);
    }

    @Override
    public Stream<List<T>> orderedPermutations() {
        return orderedPermutations(OBJECT_COMPARATOR);
    }

    @Override
    public Stream<List<T>> orderedPermutations(Comparator<? super T> comparator) {
        final Iterator<List<T>> iter = PermutationIterator.ordered(toList(), comparator == null ? OBJECT_COMPARATOR : comparator);

        return newStream(iter, false, null);
    }

    @Override
    public Stream<List<T>> cartesianProduct(final Collection<? extends Collection<? extends T>> cs) {
        final List<Collection<? extends T>> cList = new ArrayList<>(cs.size() + 1);
        cList.add(this.toList());
        cList.addAll(cs);

        return newStream(Seq.cartesianProduct(cList).iterator(), false, null);
    }

    @Override
    public <A> A[] toArray(IntFunction<A[]> generator) {
        final Object[] src = toArray();
        final A[] res = generator.apply(src.length);
        System.arraycopy(src, 0, res, 0, src.length);
        return res;
    }

    @Override
    public DataSet toDataSet() {
        return toDataSet(null);
    }

    @Override
    public DataSet toDataSet(List<String> columnNames) {
        return N.newDataSet(columnNames, toList());
    }

    @Override
    public String join(CharSequence delimiter) {
        final Function<T, String> mapper = new Function<T, String>() {
            @SuppressWarnings("rawtypes")
            @Override
            public String apply(T t) {
                return t instanceof BaseStream ? ((BaseStream) t).join(", ", "[", "]") : N.toString(t);
            }
        };

        return this.map(mapper).collect(Collectors.joining(delimiter));
    }

    @Override
    public String join(CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
        final Function<T, String> mapper = new Function<T, String>() {
            @SuppressWarnings("rawtypes")
            @Override
            public String apply(T t) {
                return t instanceof BaseStream ? ((BaseStream) t).join(", ", "[", "]") : N.toString(t);
            }
        };

        return this.map(mapper).collect(Collectors.joining(delimiter, prefix, suffix));
    }

    @Override
    public boolean hasDuplicates() {
        final Set<T> set = new HashSet<>();
        final Iterator<T> iter = iterator();

        while (iter.hasNext()) {
            if (set.add(iter.next()) == false) {
                return true;
            }
        }

        return false;
    }

    @Override
    public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator) {
        final BinaryOperator<U> combiner = reducingCombiner;

        return reduce(identity, accumulator, combiner);
    }

    @Override
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator) {
        final BiConsumer<R, R> combiner = collectingCombiner;

        return collect(supplier, accumulator, combiner);
    }

    @Override
    public <R, A> R collect(java.util.stream.Collector<? super T, A, R> collector) {
        return collect(Collector.of(collector));
    }

    @Override
    public <R, A, RR> RR collectAndThen(Collector<? super T, A, R> downstream, Function<R, RR> finisher) {
        return finisher.apply(collect(downstream));
    }

    @Override
    public <R, A, RR> RR collectAndThen(java.util.stream.Collector<? super T, A, R> downstream, Function<R, RR> finisher) {
        return finisher.apply(collect(downstream));
    }

    @Override
    public <R> R toListAndThen(final Function<? super List<T>, R> func) {
        return func.apply(toList());
    }

    @Override
    public <R> R toSetAndThen(final Function<? super Set<T>, R> func) {
        return func.apply(toSet());
    }

    @Override
    public Pair<NullabLe<T>, Stream<T>> headAndTail() {
        return Pair.of(head(), tail());
    }

    @Override
    public Pair<Stream<T>, NullabLe<T>> headAndTail2() {
        return Pair.of(head2(), tail2());
    }

    @Override
    public Stream<Indexed<T>> indexed() {
        final MutableLong idx = MutableLong.of(0);

        return newStream(this.sequential().map(new Function<T, Indexed<T>>() {
            @Override
            public Indexed<T> apply(T t) {
                return Indexed.of(t, idx.getAndIncrement());
            }
        }).iterator(), true, INDEXED_COMPARATOR);
    }

    @Override
    public Stream<T> queued() {
        return queued(DEFAULT_QUEUE_SIZE_PER_ITERATOR);
    }

    @Override
    public Stream<T> append(Stream<T> stream) {
        return Stream.concat(this, stream);
    }

    @Override
    public Stream<T> append(Collection<? extends T> c) {
        if (N.isNullOrEmpty(c)) {
            return this;
        }

        return append(Stream.of(c));
    }

    @Override
    public Stream<T> prepend(Stream<T> stream) {
        return Stream.concat(stream, this);
    }

    @Override
    public Stream<T> prepend(Collection<? extends T> c) {
        if (N.isNullOrEmpty(c)) {
            return this;
        }

        return prepend(Stream.of(c));
    }

    @Override
    public Stream<T> merge(final Stream<? extends T> b, final BiFunction<? super T, ? super T, Nth> nextSelector) {
        return Stream.merge(this, b, nextSelector);
    }

    @Override
    public <T2, R> Stream<R> zipWith(Stream<T2> b, BiFunction<? super T, ? super T2, R> zipFunction) {
        return Stream.zip(this, b, zipFunction);
    }

    @Override
    public <T2, T3, R> Stream<R> zipWith(Stream<T2> b, Stream<T3> c, TriFunction<? super T, ? super T2, ? super T3, R> zipFunction) {
        return Stream.zip(this, b, c, zipFunction);
    }

    @Override
    public <T2, R> Stream<R> zipWith(Stream<T2> b, T valueForNoneA, T2 valueForNoneB, BiFunction<? super T, ? super T2, R> zipFunction) {
        return Stream.zip(this, b, valueForNoneA, valueForNoneB, zipFunction);
    }

    @Override
    public <T2, T3, R> Stream<R> zipWith(Stream<T2> b, Stream<T3> c, T valueForNoneA, T2 valueForNoneB, T3 valueForNoneC,
            TriFunction<? super T, ? super T2, ? super T3, R> zipFunction) {
        return Stream.zip(this, b, c, valueForNoneA, valueForNoneB, valueForNoneC, zipFunction);
    }

    @Override
    public Stream<T> cached() {
        return newStream((T[]) toArray(), sorted, cmp);
    }

    @Override
    public Stream<T> cached(IntFunction<T[]> generator) {
        return newStream(toArray(generator), sorted, cmp);
    }

    @Override
    public long persist(File file, Function<? super T, String> toLine) {
        Writer writer = null;

        try {
            writer = new FileWriter(file);
            return persist(writer, toLine);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            IOUtil.close(writer);
        }
    }

    @Override
    public long persist(OutputStream os, Function<? super T, String> toLine) {
        final BufferedWriter bw = ObjectFactory.createBufferedWriter(os);

        try {
            return persist(bw, toLine);
        } finally {
            ObjectFactory.recycle(bw);
        }
    }

    @Override
    public long persist(Writer writer, Function<? super T, String> toLine) {
        final Iterator<T> iter = iterator();
        final BufferedWriter bw = writer instanceof BufferedWriter ? (BufferedWriter) writer : ObjectFactory.createBufferedWriter(writer);
        long cnt = 0;

        try {
            while (iter.hasNext()) {
                bw.write(toLine.apply(iter.next()));
                bw.write(IOUtil.LINE_SEPARATOR);
                cnt++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (bw != writer) {
                ObjectFactory.recycle(bw);
            }
        }
        return cnt;
    }

    @Override
    public long persist(final Connection conn, final String insertSQL, final int batchSize, final int batchInterval,
            final Try.BiConsumer<? super PreparedStatement, ? super T, SQLException> stmtSetter) {
        PreparedStatement stmt = null;

        try {
            stmt = conn.prepareStatement(insertSQL);

            return persist(stmt, batchSize, batchInterval, stmtSetter);
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        } finally {
            closeQuietly(stmt);
        }
    }

    @Override
    public long persist(final PreparedStatement stmt, final int batchSize, final int batchInterval,
            final Try.BiConsumer<? super PreparedStatement, ? super T, SQLException> stmtSetter) {
        N.checkArgument(batchSize > 0 && batchInterval >= 0, "'batchSize'=%s must be greater than 0 and 'batchInterval'=%s can't be negative", batchSize,
                batchInterval);

        final Iterator<T> iter = iterator();

        long cnt = 0;
        try {
            while (iter.hasNext()) {
                stmtSetter.accept(stmt, iter.next());

                stmt.addBatch();

                if ((++cnt % batchSize) == 0) {
                    stmt.executeBatch();
                    stmt.clearBatch();

                    if (batchInterval > 0) {
                        N.sleep(batchInterval);
                    }
                }
            }

            if ((cnt % batchSize) > 0) {
                stmt.executeBatch();
                stmt.clearBatch();
            }
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }

        return cnt;
    }

    private static void closeQuietly(final Statement stmt) {
        if (stmt != null) {
            if (stmt instanceof PreparedStatement) {
                try {
                    ((PreparedStatement) stmt).clearParameters();
                } catch (Throwable e) {
                    logger.error("Failed to clear parameters", e);
                }
            }

            try {
                stmt.close();
            } catch (Throwable e) {
                logger.error("Failed to close Statement", e);
            }
        }
    }

    @Override
    public <K, V> EntryStream<K, V> mapToEntryER(final Function<? super T, K> keyMapper, final Function<? super T, V> valueMapper) {
        N.checkState(isParallel() == false, "mapToEntryER can't be applied to parallel stream");

        final Function<T, Map.Entry<K, V>> mapper = new Function<T, Map.Entry<K, V>>() {
            private final EntryStream.ReusableEntry<K, V> entry = new EntryStream.ReusableEntry<>();

            @Override
            public Entry<K, V> apply(T t) {
                entry.set(keyMapper.apply(t), valueMapper.apply(t));

                return entry;
            }
        };

        return mapToEntry(mapper);
    }
}
