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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import com.landawn.abacus.util.DoubleIterator;
import com.landawn.abacus.util.DoubleList;
import com.landawn.abacus.util.DoubleSummaryStatistics;
import com.landawn.abacus.util.FloatIterator;
import com.landawn.abacus.util.IntIterator;
import com.landawn.abacus.util.LongIterator;
import com.landawn.abacus.util.LongMultiset;
import com.landawn.abacus.util.Multiset;
import com.landawn.abacus.util.N;
import com.landawn.abacus.util.NullabLe;
import com.landawn.abacus.util.OptionalDouble;
import com.landawn.abacus.util.function.BiConsumer;
import com.landawn.abacus.util.function.BiFunction;
import com.landawn.abacus.util.function.BinaryOperator;
import com.landawn.abacus.util.function.Consumer;
import com.landawn.abacus.util.function.DoubleBinaryOperator;
import com.landawn.abacus.util.function.DoubleConsumer;
import com.landawn.abacus.util.function.DoubleFunction;
import com.landawn.abacus.util.function.DoublePredicate;
import com.landawn.abacus.util.function.DoubleToFloatFunction;
import com.landawn.abacus.util.function.DoubleToIntFunction;
import com.landawn.abacus.util.function.DoubleToLongFunction;
import com.landawn.abacus.util.function.DoubleUnaryOperator;
import com.landawn.abacus.util.function.ObjDoubleConsumer;
import com.landawn.abacus.util.function.Supplier;
import com.landawn.abacus.util.function.ToDoubleFunction;

/**
 * This class is a sequential, stateful and immutable stream implementation.
 *
 * @since 0.8
 * 
 * @author Haiyang Li
 */
class IteratorDoubleStream extends AbstractDoubleStream {
    final ExDoubleIterator elements;

    OptionalDouble head;
    DoubleStream tail;

    DoubleStream head2;
    OptionalDouble tail2;

    IteratorDoubleStream(final DoubleIterator values) {
        this(values, null);
    }

    IteratorDoubleStream(final DoubleIterator values, final Collection<Runnable> closeHandlers) {
        this(values, closeHandlers, false);
    }

    IteratorDoubleStream(final DoubleIterator values, final Collection<Runnable> closeHandlers, final boolean sorted) {
        super(closeHandlers, sorted);

        ExDoubleIterator tmp = null;

        if (values instanceof ExDoubleIterator) {
            tmp = (ExDoubleIterator) values;
        } else if (values instanceof SkippableIterator) {
            tmp = new ExDoubleIterator() {
                @Override
                public boolean hasNext() {
                    return values.hasNext();
                }

                @Override
                public double nextDouble() {
                    return values.nextDouble();
                }

                @Override
                public void skip(long n) {
                    ((SkippableIterator) values).skip(n);
                }

                @Override
                public long count() {
                    return ((SkippableIterator) values).count();
                }
            };
        } else {
            tmp = new ExDoubleIterator() {
                @Override
                public boolean hasNext() {
                    return values.hasNext();
                }

                @Override
                public double nextDouble() {
                    return values.nextDouble();
                }
            };
        }

        this.elements = tmp;
    }

    @Override
    public DoubleStream filter(final DoublePredicate predicate) {
        return new IteratorDoubleStream(new ExDoubleIterator() {
            private boolean hasNext = false;
            private double next = 0;

            @Override
            public boolean hasNext() {
                if (hasNext == false) {
                    while (elements.hasNext()) {
                        next = elements.nextDouble();

                        if (predicate.test(next)) {
                            hasNext = true;
                            break;
                        }
                    }
                }

                return hasNext;
            }

            @Override
            public double nextDouble() {
                if (hasNext == false && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                hasNext = false;

                return next;
            }
        }, closeHandlers, sorted);
    }

    @Override
    public DoubleStream takeWhile(final DoublePredicate predicate) {
        return new IteratorDoubleStream(new ExDoubleIterator() {
            private boolean hasMore = true;
            private boolean hasNext = false;
            private double next = 0;

            @Override
            public boolean hasNext() {
                if (hasNext == false && hasMore && elements.hasNext()) {
                    next = elements.nextDouble();

                    if (predicate.test(next)) {
                        hasNext = true;
                    } else {
                        hasMore = false;
                    }
                }

                return hasNext;
            }

            @Override
            public double nextDouble() {
                if (hasNext == false && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                hasNext = false;

                return next;
            }

        }, closeHandlers, sorted);
    }

    @Override
    public DoubleStream dropWhile(final DoublePredicate predicate) {
        return new IteratorDoubleStream(new ExDoubleIterator() {
            private boolean hasNext = false;
            private double next = 0;
            private boolean dropped = false;

            @Override
            public boolean hasNext() {
                if (hasNext == false) {
                    if (dropped == false) {
                        while (elements.hasNext()) {
                            next = elements.nextDouble();

                            if (predicate.test(next) == false) {
                                hasNext = true;
                                break;
                            }
                        }

                        dropped = true;
                    } else if (elements.hasNext()) {
                        next = elements.nextDouble();
                        hasNext = true;
                    }
                }

                return hasNext;
            }

            @Override
            public double nextDouble() {
                if (hasNext == false && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                hasNext = false;

                return next;
            }

        }, closeHandlers, sorted);
    }

    @Override
    public DoubleStream map(final DoubleUnaryOperator mapper) {
        return new IteratorDoubleStream(new ExDoubleIterator() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public double nextDouble() {
                return mapper.applyAsDouble(elements.nextDouble());
            }

            @Override
            public long count() {
                return elements.count();
            }

            @Override
            public void skip(long n) {
                elements.skip(n);
            }
        }, closeHandlers);
    }

    @Override
    public IntStream mapToInt(final DoubleToIntFunction mapper) {
        return new IteratorIntStream(new ExIntIterator() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public int nextInt() {
                return mapper.applyAsInt(elements.nextDouble());
            }

            @Override
            public long count() {
                return elements.count();
            }

            @Override
            public void skip(long n) {
                elements.skip(n);
            }
        }, closeHandlers);
    }

    @Override
    public LongStream mapToLong(final DoubleToLongFunction mapper) {
        return new IteratorLongStream(new ExLongIterator() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public long nextLong() {
                return mapper.applyAsLong(elements.nextDouble());
            }

            @Override
            public long count() {
                return elements.count();
            }

            @Override
            public void skip(long n) {
                elements.skip(n);
            }
        }, closeHandlers);
    }

    @Override
    public FloatStream mapToFloat(final DoubleToFloatFunction mapper) {
        return new IteratorFloatStream(new ExFloatIterator() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public float nextFloat() {
                return mapper.applyAsFloat(elements.nextDouble());
            }

            @Override
            public long count() {
                return elements.count();
            }

            @Override
            public void skip(long n) {
                elements.skip(n);
            }
        }, closeHandlers);
    }

    @Override
    public <U> Stream<U> mapToObj(final DoubleFunction<? extends U> mapper) {
        return new IteratorStream<U>(new ExIterator<U>() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public U next() {
                return mapper.apply(elements.nextDouble());
            }

            @Override
            public long count() {
                return elements.count();
            }

            @Override
            public void skip(long n) {
                elements.skip(n);
            }
        }, closeHandlers);
    }

    @Override
    public DoubleStream flatMap(final DoubleFunction<? extends DoubleStream> mapper) {
        return new IteratorDoubleStream(new ExDoubleIterator() {
            private DoubleIterator cur = null;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && elements.hasNext()) {
                    cur = mapper.apply(elements.nextDouble()).exIterator();
                }

                return cur != null && cur.hasNext();
            }

            @Override
            public double nextDouble() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.nextDouble();
            }
        }, closeHandlers);
    }

    @Override
    public IntStream flatMapToInt(final DoubleFunction<? extends IntStream> mapper) {
        return new IteratorIntStream(new ExIntIterator() {
            private IntIterator cur = null;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && elements.hasNext()) {
                    cur = mapper.apply(elements.nextDouble()).exIterator();
                }

                return cur != null && cur.hasNext();
            }

            @Override
            public int nextInt() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.nextInt();
            }
        }, closeHandlers);
    }

    @Override
    public LongStream flatMapToLong(final DoubleFunction<? extends LongStream> mapper) {
        return new IteratorLongStream(new ExLongIterator() {
            private LongIterator cur = null;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && elements.hasNext()) {
                    cur = mapper.apply(elements.nextDouble()).exIterator();
                }

                return cur != null && cur.hasNext();
            }

            @Override
            public long nextLong() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.nextLong();
            }
        }, closeHandlers);
    }

    @Override
    public FloatStream flatMapToFloat(final DoubleFunction<? extends FloatStream> mapper) {
        return new IteratorFloatStream(new ExFloatIterator() {
            private FloatIterator cur = null;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && elements.hasNext()) {
                    cur = mapper.apply(elements.nextDouble()).exIterator();
                }

                return cur != null && cur.hasNext();
            }

            @Override
            public float nextFloat() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.nextFloat();
            }
        }, closeHandlers);
    }

    @Override
    public <T> Stream<T> flatMapToObj(final DoubleFunction<? extends Stream<T>> mapper) {
        return new IteratorStream<T>(new ExIterator<T>() {
            private Iterator<? extends T> cur = null;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && elements.hasNext()) {
                    cur = mapper.apply(elements.nextDouble()).iterator();
                }

                return cur != null && cur.hasNext();
            }

            @Override
            public T next() {
                if ((cur == null || cur.hasNext() == false) && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                return cur.next();
            }
        }, closeHandlers);
    }

    @Override
    public Stream<DoubleList> splitToList(final int size) {
        N.checkArgument(size > 0, "'size' must be bigger than 0");

        return new IteratorStream<DoubleList>(new ExIterator<DoubleList>() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public DoubleList next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                final DoubleList result = new DoubleList(size);

                while (result.size() < size && elements.hasNext()) {
                    result.add(elements.nextDouble());
                }

                return result;
            }

        }, closeHandlers);
    }

    @Override
    public Stream<DoubleList> splitToList(final DoublePredicate predicate) {
        return new IteratorStream<DoubleList>(new ExIterator<DoubleList>() {
            private double next;
            private boolean hasNext = false;
            private boolean preCondition = false;

            @Override
            public boolean hasNext() {
                return hasNext == true || elements.hasNext();
            }

            @Override
            public DoubleList next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                final DoubleList result = new DoubleList();

                if (hasNext == false) {
                    next = elements.nextDouble();
                    hasNext = true;
                }

                while (hasNext) {
                    if (result.size() == 0) {
                        result.add(next);
                        preCondition = predicate.test(next);
                        next = (hasNext = elements.hasNext()) ? elements.nextDouble() : 0;
                    } else if (predicate.test(next) == preCondition) {
                        result.add(next);
                        next = (hasNext = elements.hasNext()) ? elements.nextDouble() : 0;
                    } else {
                        break;
                    }
                }

                return result;
            }

        }, closeHandlers);
    }

    @Override
    public <U> Stream<DoubleList> splitToList(final U identity, final BiFunction<? super Double, ? super U, Boolean> predicate,
            final Consumer<? super U> identityUpdate) {
        return new IteratorStream<DoubleList>(new ExIterator<DoubleList>() {
            private double next;
            private boolean hasNext = false;
            private boolean preCondition = false;

            @Override
            public boolean hasNext() {
                return hasNext == true || elements.hasNext();
            }

            @Override
            public DoubleList next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                final DoubleList result = new DoubleList();

                if (hasNext == false) {
                    next = elements.nextDouble();
                    hasNext = true;
                }

                while (hasNext) {
                    if (result.size() == 0) {
                        result.add(next);
                        preCondition = predicate.apply(next, identity);
                        next = (hasNext = elements.hasNext()) ? elements.nextDouble() : 0;
                    } else if (predicate.apply(next, identity) == preCondition) {
                        result.add(next);
                        next = (hasNext = elements.hasNext()) ? elements.nextDouble() : 0;
                    } else {
                        if (identityUpdate != null) {
                            identityUpdate.accept(identity);
                        }

                        break;
                    }
                }

                return result;
            }

        }, closeHandlers);
    }

    @Override
    public Stream<DoubleList> slidingToList(final int windowSize, final int increment) {
        N.checkArgument(windowSize > 0 && increment > 0, "'windowSize'=%s and 'increment'=%s must not be less than 1", windowSize, increment);

        return new IteratorStream<DoubleList>(new ExIterator<DoubleList>() {
            private DoubleList prev = null;

            @Override
            public boolean hasNext() {
                if (prev != null && increment > windowSize) {
                    int skipNum = increment - windowSize;

                    while (skipNum-- > 0 && elements.hasNext()) {
                        elements.nextDouble();
                    }

                    prev = null;
                }

                return elements.hasNext();
            }

            @Override
            public DoubleList next() {
                if (hasNext() == false) {
                    throw new NoSuchElementException();
                }

                DoubleList result = null;
                int cnt = 0;

                if (prev != null && increment < windowSize) {
                    cnt = windowSize - increment;

                    if (cnt <= 8) {
                        result = new DoubleList(windowSize);

                        for (int i = windowSize - cnt; i < windowSize; i++) {
                            result.add(prev.get(i));
                        }
                    } else {
                        final double[] dest = new double[windowSize];
                        N.copy(prev.trimToSize().array(), windowSize - cnt, dest, 0, cnt);
                        result = DoubleList.of(dest, cnt);
                    }
                } else {
                    result = new DoubleList(windowSize);
                }

                while (cnt++ < windowSize && elements.hasNext()) {
                    result.add(elements.nextDouble());
                }

                return prev = result;
            }
        }, closeHandlers);
    }

    @Override
    public DoubleStream top(int n) {
        return top(n, DOUBLE_COMPARATOR);
    }

    @Override
    public DoubleStream top(int n, Comparator<? super Double> comparator) {
        return boxed().top(n, comparator).mapToDouble(ToDoubleFunction.UNBOX);
    }

    @Override
    public DoubleStream sorted() {
        if (sorted) {
            return this;
        }

        return new IteratorDoubleStream(new ExDoubleIterator() {
            double[] a = null;
            int toIndex = 0;
            int cursor = 0;

            @Override
            public boolean hasNext() {
                if (a == null) {
                    sort();
                }

                return cursor < toIndex;
            }

            @Override
            public double nextDouble() {
                if (a == null) {
                    sort();
                }

                if (cursor >= toIndex) {
                    throw new NoSuchElementException();
                }

                return a[cursor++];
            }

            @Override
            public long count() {
                if (a == null) {
                    sort();
                }

                return toIndex - cursor;
            }

            @Override
            public void skip(long n) {
                if (a == null) {
                    sort();
                }

                cursor = n < toIndex - cursor ? cursor + (int) n : toIndex;
            }

            @Override
            public double[] toArray() {
                if (a == null) {
                    sort();
                }

                if (cursor == 0) {
                    return a;
                } else {
                    return N.copyOfRange(a, cursor, toIndex);
                }
            }

            private void sort() {
                a = elements.toArray();
                toIndex = a.length;

                N.sort(a);
            }
        }, closeHandlers, true);
    }

    @Override
    public DoubleStream peek(final DoubleConsumer action) {
        return new IteratorDoubleStream(new ExDoubleIterator() {
            @Override
            public boolean hasNext() {
                return elements.hasNext();
            }

            @Override
            public double nextDouble() {
                final double next = elements.nextDouble();
                action.accept(next);
                return next;
            }
        }, closeHandlers, sorted);
    }

    @Override
    public DoubleStream limit(final long maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("'maxSize' can't be negative: " + maxSize);
        }

        return new IteratorDoubleStream(new ExDoubleIterator() {
            private long cnt = 0;

            @Override
            public boolean hasNext() {
                return cnt < maxSize && elements.hasNext();
            }

            @Override
            public double nextDouble() {
                if (cnt >= maxSize) {
                    throw new NoSuchElementException();
                }

                cnt++;
                return elements.nextDouble();
            }

            @Override
            public void skip(long n) {
                elements.skip(n);
            }
        }, closeHandlers, sorted);
    }

    @Override
    public DoubleStream skip(final long n) {
        if (n < 0) {
            throw new IllegalArgumentException("The skipped number can't be negative: " + n);
        } else if (n == 0) {
            return this;
        }

        return new IteratorDoubleStream(new ExDoubleIterator() {
            private boolean skipped = false;

            @Override
            public boolean hasNext() {
                if (skipped == false) {
                    elements.skip(n);
                    skipped = true;
                }

                return elements.hasNext();
            }

            @Override
            public double nextDouble() {
                if (skipped == false) {
                    elements.skip(n);
                    skipped = true;
                }

                return elements.nextDouble();
            }

            @Override
            public long count() {
                if (skipped == false) {
                    elements.skip(n);
                    skipped = true;
                }

                return elements.count();
            }

            @Override
            public void skip(long n2) {
                if (skipped == false) {
                    elements.skip(n);
                    skipped = true;
                }

                elements.skip(n2);
            }

            @Override
            public double[] toArray() {
                if (skipped == false) {
                    elements.skip(n);
                    skipped = true;
                }

                return elements.toArray();
            }
        }, closeHandlers, sorted);
    }

    @Override
    public void forEach(DoubleConsumer action) {
        while (elements.hasNext()) {
            action.accept(elements.nextDouble());
        }
    }

    @Override
    public double[] toArray() {
        return elements.toArray();
    }

    @Override
    public DoubleList toDoubleList() {
        return DoubleList.of(toArray());
    }

    @Override
    public List<Double> toList() {
        final List<Double> result = new ArrayList<>();

        while (elements.hasNext()) {
            result.add(elements.nextDouble());
        }

        return result;
    }

    @Override
    public <R extends List<Double>> R toList(Supplier<R> supplier) {
        final R result = supplier.get();

        while (elements.hasNext()) {
            result.add(elements.nextDouble());
        }

        return result;
    }

    @Override
    public Set<Double> toSet() {
        final Set<Double> result = new HashSet<>();

        while (elements.hasNext()) {
            result.add(elements.nextDouble());
        }

        return result;
    }

    @Override
    public <R extends Set<Double>> R toSet(Supplier<R> supplier) {
        final R result = supplier.get();

        while (elements.hasNext()) {
            result.add(elements.nextDouble());
        }

        return result;
    }

    @Override
    public Multiset<Double> toMultiset() {
        final Multiset<Double> result = new Multiset<>();

        while (elements.hasNext()) {
            result.add(elements.nextDouble());
        }

        return result;
    }

    @Override
    public Multiset<Double> toMultiset(Supplier<? extends Multiset<Double>> supplier) {
        final Multiset<Double> result = supplier.get();

        while (elements.hasNext()) {
            result.add(elements.nextDouble());
        }

        return result;
    }

    @Override
    public LongMultiset<Double> toLongMultiset() {
        final LongMultiset<Double> result = new LongMultiset<>();

        while (elements.hasNext()) {
            result.add(elements.nextDouble());
        }

        return result;
    }

    @Override
    public LongMultiset<Double> toLongMultiset(Supplier<? extends LongMultiset<Double>> supplier) {
        final LongMultiset<Double> result = supplier.get();

        while (elements.hasNext()) {
            result.add(elements.nextDouble());
        }

        return result;
    }

    @Override
    public <K, U, M extends Map<K, U>> M toMap(DoubleFunction<? extends K> keyExtractor, DoubleFunction<? extends U> valueMapper,
            BinaryOperator<U> mergeFunction, Supplier<M> mapFactory) {
        final M result = mapFactory.get();
        double element = 0;

        while (elements.hasNext()) {
            element = elements.nextDouble();
            Collectors.merge(result, keyExtractor.apply(element), valueMapper.apply(element), mergeFunction);
        }

        return result;
    }

    @Override
    public <K, A, D, M extends Map<K, D>> M toMap(final DoubleFunction<? extends K> classifier, final Collector<Double, A, D> downstream,
            final Supplier<M> mapFactory) {
        final M result = mapFactory.get();
        final Supplier<A> downstreamSupplier = downstream.supplier();
        final BiConsumer<A, Double> downstreamAccumulator = downstream.accumulator();
        final Map<K, A> intermediate = (Map<K, A>) result;
        K key = null;
        A v = null;
        double element = 0;

        while (elements.hasNext()) {
            element = elements.nextDouble();
            key = N.requireNonNull(classifier.apply(element), "element cannot be mapped to a null key");

            if ((v = intermediate.get(key)) == null) {
                if ((v = downstreamSupplier.get()) != null) {
                    intermediate.put(key, v);
                }
            }

            downstreamAccumulator.accept(v, element);
        }

        final BiFunction<? super K, ? super A, ? extends A> function = new BiFunction<K, A, A>() {
            @Override
            public A apply(K k, A v) {
                return (A) downstream.finisher().apply(v);
            }
        };

        Collectors.replaceAll(intermediate, function);

        return result;
    }

    @Override
    public double reduce(double identity, DoubleBinaryOperator op) {
        double result = identity;

        while (elements.hasNext()) {
            result = op.applyAsDouble(result, elements.nextDouble());
        }

        return result;
    }

    @Override
    public OptionalDouble reduce(DoubleBinaryOperator op) {
        if (elements.hasNext() == false) {
            return OptionalDouble.empty();
        }

        double result = elements.nextDouble();

        while (elements.hasNext()) {
            result = op.applyAsDouble(result, elements.nextDouble());
        }

        return OptionalDouble.of(result);
    }

    @Override
    public <R> R collect(Supplier<R> supplier, ObjDoubleConsumer<R> accumulator, BiConsumer<R, R> combiner) {
        final R result = supplier.get();

        while (elements.hasNext()) {
            accumulator.accept(result, elements.nextDouble());
        }

        return result;
    }

    @Override
    public OptionalDouble head() {
        if (head == null) {
            head = elements.hasNext() ? OptionalDouble.of(elements.nextDouble()) : OptionalDouble.empty();
            tail = new IteratorDoubleStream(elements, closeHandlers, sorted);
        }

        return head;
    }

    @Override
    public DoubleStream tail() {
        if (tail == null) {
            head = elements.hasNext() ? OptionalDouble.of(elements.nextDouble()) : OptionalDouble.empty();
            tail = new IteratorDoubleStream(elements, closeHandlers, sorted);
        }

        return tail;
    }

    @Override
    public DoubleStream head2() {
        if (head2 == null) {
            final double[] a = elements.toArray();
            head2 = new ArrayDoubleStream(a, 0, a.length == 0 ? 0 : a.length - 1, closeHandlers, sorted);
            tail2 = a.length == 0 ? OptionalDouble.empty() : OptionalDouble.of(a[a.length - 1]);
        }

        return head2;
    }

    @Override
    public OptionalDouble tail2() {
        if (tail2 == null) {
            final double[] a = elements.toArray();
            head2 = new ArrayDoubleStream(a, 0, a.length == 0 ? 0 : a.length - 1, closeHandlers, sorted);
            tail2 = a.length == 0 ? OptionalDouble.empty() : OptionalDouble.of(a[a.length - 1]);
        }

        return tail2;
    }

    @Override
    public OptionalDouble min() {
        if (elements.hasNext() == false) {
            return OptionalDouble.empty();
        } else if (sorted) {
            return OptionalDouble.of(elements.nextDouble());
        }

        double candidate = elements.nextDouble();
        double next = 0;

        while (elements.hasNext()) {
            next = elements.nextDouble();

            if (N.compare(next, candidate) < 0) {
                candidate = next;
            }
        }

        return OptionalDouble.of(candidate);
    }

    @Override
    public OptionalDouble max() {
        if (elements.hasNext() == false) {
            return OptionalDouble.empty();
        } else if (sorted) {
            double next = 0;

            while (elements.hasNext()) {
                next = elements.nextDouble();
            }

            return OptionalDouble.of(next);
        }

        double candidate = elements.nextDouble();
        double next = 0;

        while (elements.hasNext()) {
            next = elements.nextDouble();

            if (N.compare(next, candidate) > 0) {
                candidate = next;
            }
        }

        return OptionalDouble.of(candidate);
    }

    @Override
    public OptionalDouble kthLargest(int k) {
        N.checkArgument(k > 0, "'k' must be bigger than 0");

        if (elements.hasNext() == false) {
            return OptionalDouble.empty();
        }

        final NullabLe<Double> optional = boxed().kthLargest(k, DOUBLE_COMPARATOR);

        return optional.isPresent() ? OptionalDouble.of(optional.get()) : OptionalDouble.empty();
    }

    @Override
    public long count() {
        return elements.count();
    }

    @Override
    public DoubleSummaryStatistics summarize() {
        final DoubleSummaryStatistics result = new DoubleSummaryStatistics();

        while (elements.hasNext()) {
            result.accept(elements.nextDouble());
        }

        return result;
    }

    @Override
    public boolean anyMatch(DoublePredicate predicate) {
        while (elements.hasNext()) {
            if (predicate.test(elements.nextDouble())) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean allMatch(DoublePredicate predicate) {
        while (elements.hasNext()) {
            if (predicate.test(elements.nextDouble()) == false) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean noneMatch(DoublePredicate predicate) {
        while (elements.hasNext()) {
            if (predicate.test(elements.nextDouble())) {
                return false;
            }
        }

        return true;
    }

    @Override
    public OptionalDouble findFirst(DoublePredicate predicate) {
        while (elements.hasNext()) {
            double e = elements.nextDouble();

            if (predicate.test(e)) {
                return OptionalDouble.of(e);
            }
        }

        return OptionalDouble.empty();
    }

    @Override
    public OptionalDouble findLast(DoublePredicate predicate) {
        if (elements.hasNext() == false) {
            return OptionalDouble.empty();
        }

        boolean hasResult = false;
        double e = 0;
        double result = 0;

        while (elements.hasNext()) {
            e = elements.nextDouble();

            if (predicate.test(e)) {
                result = e;
                hasResult = true;
            }
        }

        return hasResult ? OptionalDouble.of(result) : OptionalDouble.empty();
    }

    @Override
    public Stream<Double> boxed() {
        return new IteratorStream<Double>(iterator(), closeHandlers, sorted, sorted ? DOUBLE_COMPARATOR : null);
    }

    @Override
    ExDoubleIterator exIterator() {
        return elements;
    }

    @Override
    public DoubleStream parallel(int maxThreadNum, Splitor splitor) {
        if (maxThreadNum < 1 || maxThreadNum > MAX_THREAD_NUM_PER_OPERATION) {
            throw new IllegalArgumentException("'maxThreadNum' must not less than 1 or exceeded: " + MAX_THREAD_NUM_PER_OPERATION);
        }

        return new ParallelIteratorDoubleStream(elements, closeHandlers, sorted, maxThreadNum, splitor);
    }

    @Override
    public DoubleStream onClose(Runnable closeHandler) {
        final Set<Runnable> newCloseHandlers = new AbstractStream.LocalLinkedHashSet<>(N.isNullOrEmpty(this.closeHandlers) ? 1 : this.closeHandlers.size() + 1);

        if (N.notNullOrEmpty(this.closeHandlers)) {
            newCloseHandlers.addAll(this.closeHandlers);
        }

        newCloseHandlers.add(closeHandler);

        return new IteratorDoubleStream(elements, newCloseHandlers, sorted);
    }
}
