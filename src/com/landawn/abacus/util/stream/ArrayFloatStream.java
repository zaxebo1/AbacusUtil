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
import com.landawn.abacus.util.FloatIterator;
import com.landawn.abacus.util.FloatList;
import com.landawn.abacus.util.FloatSummaryStatistics;
import com.landawn.abacus.util.IntIterator;
import com.landawn.abacus.util.LongIterator;
import com.landawn.abacus.util.LongMultiset;
import com.landawn.abacus.util.Multiset;
import com.landawn.abacus.util.N;
import com.landawn.abacus.util.OptionalFloat;
import com.landawn.abacus.util.function.BiConsumer;
import com.landawn.abacus.util.function.BiFunction;
import com.landawn.abacus.util.function.BinaryOperator;
import com.landawn.abacus.util.function.Consumer;
import com.landawn.abacus.util.function.FloatBinaryOperator;
import com.landawn.abacus.util.function.FloatConsumer;
import com.landawn.abacus.util.function.FloatFunction;
import com.landawn.abacus.util.function.FloatPredicate;
import com.landawn.abacus.util.function.FloatToDoubleFunction;
import com.landawn.abacus.util.function.FloatToIntFunction;
import com.landawn.abacus.util.function.FloatToLongFunction;
import com.landawn.abacus.util.function.FloatUnaryOperator;
import com.landawn.abacus.util.function.ObjFloatConsumer;
import com.landawn.abacus.util.function.Supplier;

/**
 * This class is a sequential, stateful and immutable stream implementation.
 *
 * @since 0.8
 * 
 * @author Haiyang Li
 */
class ArrayFloatStream extends AbstractFloatStream {
    final float[] elements;
    final int fromIndex;
    final int toIndex;

    ArrayFloatStream(final float[] values) {
        this(values, 0, values.length);
    }

    ArrayFloatStream(final float[] values, final Collection<Runnable> closeHandlers) {
        this(values, 0, values.length, closeHandlers);
    }

    ArrayFloatStream(final float[] values, final Collection<Runnable> closeHandlers, final boolean sorted) {
        this(values, 0, values.length, closeHandlers, sorted);
    }

    ArrayFloatStream(final float[] values, final int fromIndex, final int toIndex) {
        this(values, fromIndex, toIndex, null);
    }

    ArrayFloatStream(final float[] values, final int fromIndex, final int toIndex, final Collection<Runnable> closeHandlers) {
        this(values, fromIndex, toIndex, closeHandlers, false);
    }

    ArrayFloatStream(final float[] values, final int fromIndex, final int toIndex, final Collection<Runnable> closeHandlers, final boolean sorted) {
        super(closeHandlers, sorted);

        checkFromToIndex(fromIndex, toIndex, values.length);

        this.elements = values;
        this.fromIndex = fromIndex;
        this.toIndex = toIndex;
    }

    @Override
    public FloatStream filter(final FloatPredicate predicate) {
        return new IteratorFloatStream(new ExFloatIterator() {
            private boolean hasNext = false;
            private int cursor = fromIndex;

            @Override
            public boolean hasNext() {
                if (hasNext == false && cursor < toIndex) {
                    do {
                        if (predicate.test(elements[cursor])) {
                            hasNext = true;
                            break;
                        }
                    } while (++cursor < toIndex);
                }

                return hasNext;
            }

            @Override
            public float nextFloat() {
                if (hasNext == false && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                hasNext = false;

                return elements[cursor++];
            }
        }, closeHandlers, sorted);
    }

    @Override
    public FloatStream takeWhile(final FloatPredicate predicate) {
        return new IteratorFloatStream(new ExFloatIterator() {
            private boolean hasMore = true;
            private boolean hasNext = false;
            private int cursor = fromIndex;

            @Override
            public boolean hasNext() {
                if (hasNext == false && hasMore && cursor < toIndex) {
                    if (predicate.test(elements[cursor])) {
                        hasNext = true;
                    } else {
                        hasMore = false;
                    }
                }

                return hasNext;
            }

            @Override
            public float nextFloat() {
                if (hasNext == false && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                hasNext = false;

                return elements[cursor++];
            }
        }, closeHandlers, sorted);
    }

    @Override
    public FloatStream dropWhile(final FloatPredicate predicate) {
        return new IteratorFloatStream(new ExFloatIterator() {
            private boolean hasNext = false;
            private int cursor = fromIndex;
            private boolean dropped = false;

            @Override
            public boolean hasNext() {
                if (hasNext == false && cursor < toIndex) {
                    if (dropped == false) {
                        do {
                            if (predicate.test(elements[cursor]) == false) {
                                hasNext = true;
                                break;
                            }
                        } while (++cursor < toIndex);

                        dropped = true;
                    } else {
                        hasNext = true;
                    }
                }

                return hasNext;
            }

            @Override
            public float nextFloat() {
                if (hasNext == false && hasNext() == false) {
                    throw new NoSuchElementException();
                }

                hasNext = false;

                return elements[cursor++];
            }
        }, closeHandlers, sorted);
    }

    @Override
    public FloatStream map(final FloatUnaryOperator mapper) {
        return new IteratorFloatStream(new ExFloatIterator() {
            int cursor = fromIndex;

            @Override
            public boolean hasNext() {
                return cursor < toIndex;
            }

            @Override
            public float nextFloat() {
                if (cursor >= toIndex) {
                    throw new NoSuchElementException();
                }

                return mapper.applyAsFloat(elements[cursor++]);
            }

            @Override
            public long count() {
                return toIndex - cursor;
            }

            @Override
            public void skip(long n) {
                cursor = n < toIndex - cursor ? cursor + (int) n : toIndex;
            }

            @Override
            public float[] toArray() {
                final float[] a = new float[toIndex - cursor];

                for (int i = 0, len = toIndex - cursor; i < len; i++) {
                    a[i] = mapper.applyAsFloat(elements[cursor++]);
                }

                return a;
            }
        }, closeHandlers);
    }

    @Override
    public IntStream mapToInt(final FloatToIntFunction mapper) {
        return new IteratorIntStream(new ExIntIterator() {
            int cursor = fromIndex;

            @Override
            public boolean hasNext() {
                return cursor < toIndex;
            }

            @Override
            public int nextInt() {
                if (cursor >= toIndex) {
                    throw new NoSuchElementException();
                }

                return mapper.applyAsInt(elements[cursor++]);
            }

            @Override
            public long count() {
                return toIndex - cursor;
            }

            @Override
            public void skip(long n) {
                cursor = n < toIndex - cursor ? cursor + (int) n : toIndex;
            }

            @Override
            public int[] toArray() {
                final int[] a = new int[toIndex - cursor];

                for (int i = 0, len = toIndex - cursor; i < len; i++) {
                    a[i] = mapper.applyAsInt(elements[cursor++]);
                }

                return a;
            }
        }, closeHandlers);
    }

    @Override
    public LongStream mapToLong(final FloatToLongFunction mapper) {
        return new IteratorLongStream(new ExLongIterator() {
            int cursor = fromIndex;

            @Override
            public boolean hasNext() {
                return cursor < toIndex;
            }

            @Override
            public long nextLong() {
                if (cursor >= toIndex) {
                    throw new NoSuchElementException();
                }

                return mapper.applyAsLong(elements[cursor++]);
            }

            @Override
            public long count() {
                return toIndex - cursor;
            }

            @Override
            public void skip(long n) {
                cursor = n < toIndex - cursor ? cursor + (int) n : toIndex;
            }

            @Override
            public long[] toArray() {
                final long[] a = new long[toIndex - cursor];

                for (int i = 0, len = toIndex - cursor; i < len; i++) {
                    a[i] = mapper.applyAsLong(elements[cursor++]);
                }

                return a;
            }
        }, closeHandlers);
    }

    @Override
    public DoubleStream mapToDouble(final FloatToDoubleFunction mapper) {
        return new IteratorDoubleStream(new ExDoubleIterator() {
            int cursor = fromIndex;

            @Override
            public boolean hasNext() {
                return cursor < toIndex;
            }

            @Override
            public double nextDouble() {
                if (cursor >= toIndex) {
                    throw new NoSuchElementException();
                }

                return mapper.applyAsDouble(elements[cursor++]);
            }

            @Override
            public long count() {
                return toIndex - cursor;
            }

            @Override
            public void skip(long n) {
                cursor = n < toIndex - cursor ? cursor + (int) n : toIndex;
            }

            @Override
            public double[] toArray() {
                final double[] a = new double[toIndex - cursor];

                for (int i = 0, len = toIndex - cursor; i < len; i++) {
                    a[i] = mapper.applyAsDouble(elements[cursor++]);
                }

                return a;
            }
        }, closeHandlers);
    }

    @Override
    public <U> Stream<U> mapToObj(final FloatFunction<? extends U> mapper) {
        return new IteratorStream<U>(new ExIterator<U>() {
            int cursor = fromIndex;

            @Override
            public boolean hasNext() {
                return cursor < toIndex;
            }

            @Override
            public U next() {
                if (cursor >= toIndex) {
                    throw new NoSuchElementException();
                }

                return mapper.apply(elements[cursor++]);
            }

            @Override
            public long count() {
                return toIndex - cursor;
            }

            @Override
            public void skip(long n) {
                cursor = n < toIndex - cursor ? cursor + (int) n : toIndex;
            }

            @Override
            public <A> A[] toArray(A[] a) {
                a = a.length >= toIndex - cursor ? a : (A[]) N.newArray(a.getClass().getComponentType(), toIndex - cursor);

                for (int i = 0, len = toIndex - cursor; i < len; i++) {
                    a[i] = (A) mapper.apply(elements[cursor++]);
                }

                return a;
            }
        }, closeHandlers);
    }

    @Override
    public FloatStream flatMap(final FloatFunction<? extends FloatStream> mapper) {
        return new IteratorFloatStream(new ExFloatIterator() {
            private int cursor = fromIndex;
            private FloatIterator cur = null;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && cursor < toIndex) {
                    cur = mapper.apply(elements[cursor++]).exIterator();
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
    public IntStream flatMapToInt(final FloatFunction<? extends IntStream> mapper) {
        return new IteratorIntStream(new ExIntIterator() {
            private int cursor = fromIndex;
            private IntIterator cur = null;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && cursor < toIndex) {
                    cur = mapper.apply(elements[cursor++]).exIterator();
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
    public LongStream flatMapToLong(final FloatFunction<? extends LongStream> mapper) {
        return new IteratorLongStream(new ExLongIterator() {
            private int cursor = fromIndex;
            private LongIterator cur = null;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && cursor < toIndex) {
                    cur = mapper.apply(elements[cursor++]).exIterator();
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
    public DoubleStream flatMapToDouble(final FloatFunction<? extends DoubleStream> mapper) {
        return new IteratorDoubleStream(new ExDoubleIterator() {
            private int cursor = fromIndex;
            private DoubleIterator cur = null;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && cursor < toIndex) {
                    cur = mapper.apply(elements[cursor++]).exIterator();
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
    public <T> Stream<T> flatMapToObj(final FloatFunction<? extends Stream<T>> mapper) {
        return new IteratorStream<T>(new ExIterator<T>() {
            private int cursor = fromIndex;
            private Iterator<? extends T> cur = null;

            @Override
            public boolean hasNext() {
                while ((cur == null || cur.hasNext() == false) && cursor < toIndex) {
                    cur = mapper.apply(elements[cursor++]).iterator();
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
    public Stream<FloatStream> split(final int size) {
        N.checkArgument(size > 0, "'size' must be bigger than 0");

        return new IteratorStream<FloatStream>(new ExIterator<FloatStream>() {
            private int cursor = fromIndex;

            @Override
            public boolean hasNext() {
                return cursor < toIndex;
            }

            @Override
            public FloatStream next() {
                if (cursor >= toIndex) {
                    throw new NoSuchElementException();
                }

                return new ArrayFloatStream(elements, cursor, (cursor = size < toIndex - cursor ? cursor + size : toIndex), null, sorted);
            }
        }, closeHandlers);
    }

    @Override
    public Stream<FloatList> splitToList(final int size) {
        N.checkArgument(size > 0, "'size' must be bigger than 0");

        return new IteratorStream<FloatList>(new ExIterator<FloatList>() {
            private int cursor = fromIndex;

            @Override
            public boolean hasNext() {
                return cursor < toIndex;
            }

            @Override
            public FloatList next() {
                if (cursor >= toIndex) {
                    throw new NoSuchElementException();
                }

                return new FloatList(N.copyOfRange(elements, cursor, (cursor = size < toIndex - cursor ? cursor + size : toIndex)));
            }
        }, closeHandlers);
    }

    @Override
    public Stream<FloatStream> split(final FloatPredicate predicate) {
        return new IteratorStream<FloatStream>(new ExIterator<FloatStream>() {
            private int cursor = fromIndex;
            private boolean preCondition = false;

            @Override
            public boolean hasNext() {
                return cursor < toIndex;
            }

            @Override
            public FloatStream next() {
                if (cursor >= toIndex) {
                    throw new NoSuchElementException();
                }

                final int from = cursor;

                while (cursor < toIndex) {
                    if (from == cursor) {
                        preCondition = predicate.test(elements[from]);
                        cursor++;
                    } else if (predicate.test(elements[cursor]) == preCondition) {
                        cursor++;
                    } else {
                        break;
                    }
                }

                return new ArrayFloatStream(elements, from, cursor, null, sorted);
            }
        }, closeHandlers);
    }

    @Override
    public Stream<FloatList> splitToList(final FloatPredicate predicate) {
        return new IteratorStream<FloatList>(new ExIterator<FloatList>() {
            private int cursor = fromIndex;
            private boolean preCondition = false;

            @Override
            public boolean hasNext() {
                return cursor < toIndex;
            }

            @Override
            public FloatList next() {
                if (cursor >= toIndex) {
                    throw new NoSuchElementException();
                }

                final int from = cursor;

                while (cursor < toIndex) {
                    if (from == cursor) {
                        preCondition = predicate.test(elements[from]);
                        cursor++;
                    } else if (predicate.test(elements[cursor]) == preCondition) {
                        cursor++;
                    } else {

                        break;
                    }
                }

                return new FloatList(N.copyOfRange(elements, from, cursor));
            }
        }, closeHandlers);
    }

    @Override
    public <U> Stream<FloatStream> split(final U identity, final BiFunction<? super Float, ? super U, Boolean> predicate,
            final Consumer<? super U> identityUpdate) {
        return new IteratorStream<FloatStream>(new ExIterator<FloatStream>() {
            private int cursor = fromIndex;
            private boolean preCondition = false;

            @Override
            public boolean hasNext() {
                return cursor < toIndex;
            }

            @Override
            public FloatStream next() {
                if (cursor >= toIndex) {
                    throw new NoSuchElementException();
                }

                final int from = cursor;

                while (cursor < toIndex) {
                    if (from == cursor) {
                        preCondition = predicate.apply(elements[from], identity);
                        cursor++;
                    } else if (predicate.apply(elements[cursor], identity) == preCondition) {
                        cursor++;
                    } else {
                        if (identityUpdate != null) {
                            identityUpdate.accept(identity);
                        }

                        break;
                    }
                }

                return new ArrayFloatStream(elements, from, cursor, null, sorted);
            }
        }, closeHandlers);
    }

    @Override
    public <U> Stream<FloatList> splitToList(final U identity, final BiFunction<? super Float, ? super U, Boolean> predicate,
            final Consumer<? super U> identityUpdate) {
        return new IteratorStream<FloatList>(new ExIterator<FloatList>() {
            private int cursor = fromIndex;
            private boolean preCondition = false;

            @Override
            public boolean hasNext() {
                return cursor < toIndex;
            }

            @Override
            public FloatList next() {
                if (cursor >= toIndex) {
                    throw new NoSuchElementException();
                }

                final int from = cursor;

                while (cursor < toIndex) {
                    if (from == cursor) {
                        preCondition = predicate.apply(elements[from], identity);
                        cursor++;
                    } else if (predicate.apply(elements[cursor], identity) == preCondition) {
                        cursor++;
                    } else {
                        if (identityUpdate != null) {
                            identityUpdate.accept(identity);
                        }

                        break;
                    }
                }

                return new FloatList(N.copyOfRange(elements, from, cursor));
            }
        }, closeHandlers);
    }

    @Override
    public Stream<FloatStream> splitAt(final int n) {
        if (n < 0) {
            throw new IllegalArgumentException("'n' can't be negative");
        }

        final FloatStream[] a = new FloatStream[2];
        final int middleIndex = n < toIndex - fromIndex ? fromIndex + n : toIndex;
        a[0] = middleIndex == fromIndex ? FloatStream.empty() : new ArrayFloatStream(elements, fromIndex, middleIndex, null, sorted);
        a[1] = middleIndex == toIndex ? FloatStream.empty() : new ArrayFloatStream(elements, middleIndex, toIndex, null, sorted);

        return new ArrayStream<>(a, closeHandlers);
    }

    @Override
    public Stream<FloatStream> splitBy(FloatPredicate where) {
        N.requireNonNull(where);

        int n = 0;

        for (int i = fromIndex; i < toIndex; i++) {
            if (where.test(elements[i])) {
                n++;
            } else {
                break;
            }
        }

        return splitAt(n);
    }

    @Override
    public Stream<FloatStream> sliding(final int windowSize, final int increment) {
        N.checkArgument(windowSize > 0 && increment > 0, "'windowSize'=%s and 'increment'=%s must not be less than 1", windowSize, increment);

        return new IteratorStream<FloatStream>(new ExIterator<FloatStream>() {
            private int cursor = fromIndex;

            @Override
            public boolean hasNext() {
                return cursor < toIndex;
            }

            @Override
            public FloatStream next() {
                if (cursor >= toIndex) {
                    throw new NoSuchElementException();
                }

                final ArrayFloatStream result = new ArrayFloatStream(elements, cursor, windowSize < toIndex - cursor ? cursor + windowSize : toIndex, null,
                        sorted);

                cursor = increment < toIndex - cursor && windowSize < toIndex - cursor ? cursor + increment : toIndex;

                return result;
            }

        }, closeHandlers);
    }

    @Override
    public Stream<FloatList> slidingToList(final int windowSize, final int increment) {
        N.checkArgument(windowSize > 0 && increment > 0, "'windowSize'=%s and 'increment'=%s must not be less than 1", windowSize, increment);

        return new IteratorStream<FloatList>(new ExIterator<FloatList>() {
            private int cursor = fromIndex;

            @Override
            public boolean hasNext() {
                return cursor < toIndex;
            }

            @Override
            public FloatList next() {
                if (cursor >= toIndex) {
                    throw new NoSuchElementException();
                }

                final FloatList result = FloatList.of(N.copyOfRange(elements, cursor, windowSize < toIndex - cursor ? cursor + windowSize : toIndex));

                cursor = increment < toIndex - cursor && windowSize < toIndex - cursor ? cursor + increment : toIndex;

                return result;
            }

        }, closeHandlers);
    }

    @Override
    public FloatStream top(int n) {
        return top(n, FLOAT_COMPARATOR);
    }

    @Override
    public FloatStream top(int n, Comparator<? super Float> comparator) {
        N.checkArgument(n > 0, "'n' must be bigger than 0");

        if (n >= toIndex - fromIndex) {
            return this;
        } else if (sorted && isSameComparator(comparator, FLOAT_COMPARATOR)) {
            return new ArrayFloatStream(elements, toIndex - n, toIndex, closeHandlers, sorted);
        } else {
            return new ArrayFloatStream(N.top(elements, fromIndex, toIndex, n, comparator), closeHandlers, sorted);
        }
    }

    @Override
    public FloatStream sorted() {
        if (sorted) {
            return this;
        }

        final float[] a = N.copyOfRange(elements, fromIndex, toIndex);
        N.sort(a);
        return new ArrayFloatStream(a, closeHandlers, true);
    }

    @Override
    public FloatStream peek(final FloatConsumer action) {
        return new IteratorFloatStream(new ExFloatIterator() {
            int cursor = fromIndex;

            @Override
            public boolean hasNext() {
                return cursor < toIndex;
            }

            @Override
            public float nextFloat() {
                if (cursor >= toIndex) {
                    throw new NoSuchElementException();
                }

                action.accept(elements[cursor]);

                return elements[cursor++];
            }

            @Override
            public float[] toArray() {
                final float[] a = new float[toIndex - cursor];

                for (int i = 0, len = toIndex - cursor; i < len; i++) {
                    action.accept(elements[cursor]);

                    a[i] = elements[cursor++];
                }

                return a;
            }
        }, closeHandlers, sorted);
    }

    @Override
    public FloatStream limit(long maxSize) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("'maxSize' can't be negative: " + maxSize);
        } else if (maxSize >= toIndex - fromIndex) {
            return this;
        }

        return new ArrayFloatStream(elements, fromIndex, (int) (fromIndex + maxSize), closeHandlers, sorted);
    }

    @Override
    public FloatStream skip(long n) {
        if (n < 0) {
            throw new IllegalArgumentException("The skipped number can't be negative: " + n);
        } else if (n == 0) {
            return this;
        }

        if (n >= toIndex - fromIndex) {
            return new ArrayFloatStream(elements, toIndex, toIndex, closeHandlers, sorted);
        } else {
            return new ArrayFloatStream(elements, (int) (fromIndex + n), toIndex, closeHandlers, sorted);
        }
    }

    @Override
    public void forEach(FloatConsumer action) {
        for (int i = fromIndex; i < toIndex; i++) {
            action.accept(elements[i]);
        }
    }

    @Override
    public float[] toArray() {
        return N.copyOfRange(elements, fromIndex, toIndex);
    }

    @Override
    public FloatList toFloatList() {
        return FloatList.of(N.copyOfRange(elements, fromIndex, toIndex));
    }

    @Override
    public List<Float> toList() {
        final List<Float> result = new ArrayList<>(toIndex - fromIndex);

        for (int i = fromIndex; i < toIndex; i++) {
            result.add(elements[i]);
        }

        return result;
    }

    @Override
    public <R extends List<Float>> R toList(Supplier<R> supplier) {
        final R result = supplier.get();

        for (int i = fromIndex; i < toIndex; i++) {
            result.add(elements[i]);
        }

        return result;
    }

    @Override
    public Set<Float> toSet() {
        final Set<Float> result = new HashSet<>(N.min(9, N.initHashCapacity(toIndex - fromIndex)));

        for (int i = fromIndex; i < toIndex; i++) {
            result.add(elements[i]);
        }

        return result;
    }

    @Override
    public <R extends Set<Float>> R toSet(Supplier<R> supplier) {
        final R result = supplier.get();

        for (int i = fromIndex; i < toIndex; i++) {
            result.add(elements[i]);
        }

        return result;
    }

    @Override
    public Multiset<Float> toMultiset() {
        final Multiset<Float> result = new Multiset<>(N.min(9, N.initHashCapacity(toIndex - fromIndex)));

        for (int i = fromIndex; i < toIndex; i++) {
            result.add(elements[i]);
        }

        return result;
    }

    @Override
    public Multiset<Float> toMultiset(Supplier<? extends Multiset<Float>> supplier) {
        final Multiset<Float> result = supplier.get();

        for (int i = fromIndex; i < toIndex; i++) {
            result.add(elements[i]);
        }

        return result;
    }

    @Override
    public LongMultiset<Float> toLongMultiset() {
        final LongMultiset<Float> result = new LongMultiset<>(N.min(9, N.initHashCapacity(toIndex - fromIndex)));

        for (int i = fromIndex; i < toIndex; i++) {
            result.add(elements[i]);
        }

        return result;
    }

    @Override
    public LongMultiset<Float> toLongMultiset(Supplier<? extends LongMultiset<Float>> supplier) {
        final LongMultiset<Float> result = supplier.get();

        for (int i = fromIndex; i < toIndex; i++) {
            result.add(elements[i]);
        }

        return result;
    }

    @Override
    public <K, U, M extends Map<K, U>> M toMap(FloatFunction<? extends K> keyExtractor, FloatFunction<? extends U> valueMapper, BinaryOperator<U> mergeFunction,
            Supplier<M> mapFactory) {
        final M result = mapFactory.get();

        for (int i = fromIndex; i < toIndex; i++) {
            Collectors.merge(result, keyExtractor.apply(elements[i]), valueMapper.apply(elements[i]), mergeFunction);
        }

        return result;
    }

    @Override
    public <K, A, D, M extends Map<K, D>> M toMap(final FloatFunction<? extends K> classifier, final Collector<Float, A, D> downstream,
            final Supplier<M> mapFactory) {
        final M result = mapFactory.get();
        final Supplier<A> downstreamSupplier = downstream.supplier();
        final BiConsumer<A, Float> downstreamAccumulator = downstream.accumulator();
        final Map<K, A> intermediate = (Map<K, A>) result;
        K key = null;
        A v = null;

        for (int i = fromIndex; i < toIndex; i++) {
            key = N.requireNonNull(classifier.apply(elements[i]), "element cannot be mapped to a null key");

            if ((v = intermediate.get(key)) == null) {
                if ((v = downstreamSupplier.get()) != null) {
                    intermediate.put(key, v);
                }
            }

            downstreamAccumulator.accept(v, elements[i]);
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
    public OptionalFloat first() {
        return fromIndex < toIndex ? OptionalFloat.of(elements[fromIndex]) : OptionalFloat.empty();
    }

    @Override
    public OptionalFloat last() {
        return fromIndex < toIndex ? OptionalFloat.of(elements[toIndex - 1]) : OptionalFloat.empty();
    }

    @Override
    public float reduce(float identity, FloatBinaryOperator op) {
        float result = identity;

        for (int i = fromIndex; i < toIndex; i++) {
            result = op.applyAsFloat(result, elements[i]);
        }

        return result;
    }

    @Override
    public OptionalFloat reduce(FloatBinaryOperator op) {
        if (fromIndex == toIndex) {
            return OptionalFloat.empty();
        }

        float result = elements[fromIndex];

        for (int i = fromIndex + 1; i < toIndex; i++) {
            result = op.applyAsFloat(result, elements[i]);
        }

        return OptionalFloat.of(result);
    }

    @Override
    public <R> R collect(Supplier<R> supplier, ObjFloatConsumer<R> accumulator, BiConsumer<R, R> combiner) {
        final R result = supplier.get();

        for (int i = fromIndex; i < toIndex; i++) {
            accumulator.accept(result, elements[i]);
        }

        return result;
    }

    @Override
    public OptionalFloat head() {
        return fromIndex == toIndex ? OptionalFloat.empty() : OptionalFloat.of(elements[fromIndex]);
    }

    @Override
    public FloatStream tail() {
        if (fromIndex == toIndex) {
            return this;
        }

        return new ArrayFloatStream(elements, fromIndex + 1, toIndex, closeHandlers, sorted);
    }

    @Override
    public FloatStream head2() {
        if (fromIndex == toIndex) {
            return this;
        }

        return new ArrayFloatStream(elements, fromIndex, toIndex - 1, closeHandlers, sorted);
    }

    @Override
    public OptionalFloat tail2() {
        return fromIndex == toIndex ? OptionalFloat.empty() : OptionalFloat.of(elements[toIndex - 1]);
    }

    @Override
    public OptionalFloat min() {
        if (fromIndex == toIndex) {
            return OptionalFloat.empty();
        } else if (sorted) {
            return OptionalFloat.of(elements[fromIndex]);
        }

        return OptionalFloat.of(N.min(elements, fromIndex, toIndex));
    }

    @Override
    public OptionalFloat max() {
        if (fromIndex == toIndex) {
            return OptionalFloat.empty();
        } else if (sorted) {
            return OptionalFloat.of(elements[toIndex - 1]);
        }

        return OptionalFloat.of(N.max(elements, fromIndex, toIndex));
    }

    @Override
    public OptionalFloat kthLargest(int k) {
        N.checkArgument(k > 0, "'k' must be bigger than 0");

        if (k > toIndex - fromIndex) {
            return OptionalFloat.empty();
        } else if (sorted) {
            return OptionalFloat.of(elements[toIndex - k]);
        }

        return OptionalFloat.of(N.kthLargest(elements, fromIndex, toIndex, k));
    }

    @Override
    public long count() {
        return toIndex - fromIndex;
    }

    @Override
    public FloatStream reversed() {
        return new IteratorFloatStream(new ExFloatIterator() {
            private int cursor = toIndex;

            @Override
            public boolean hasNext() {
                return cursor > fromIndex;
            }

            @Override
            public float nextFloat() {
                if (cursor <= fromIndex) {
                    throw new NoSuchElementException();
                }
                return elements[--cursor];
            }

            @Override
            public long count() {
                return cursor - fromIndex;
            }

            @Override
            public void skip(long n) {
                cursor = n < cursor - fromIndex ? cursor - (int) n : fromIndex;
            }

            @Override
            public float[] toArray() {
                final float[] a = new float[cursor - fromIndex];

                for (int i = 0, len = a.length; i < len; i++) {
                    a[i] = elements[cursor - i - 1];
                }

                return a;
            }
        }, closeHandlers);
    }

    @Override
    public FloatSummaryStatistics summarize() {
        final FloatSummaryStatistics result = new FloatSummaryStatistics();

        for (int i = fromIndex; i < toIndex; i++) {
            result.accept(elements[i]);
        }

        return result;
    }

    @Override
    public boolean anyMatch(final FloatPredicate predicate) {
        for (int i = fromIndex; i < toIndex; i++) {
            if (predicate.test(elements[i])) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean allMatch(final FloatPredicate predicate) {
        for (int i = fromIndex; i < toIndex; i++) {
            if (predicate.test(elements[i]) == false) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean noneMatch(final FloatPredicate predicate) {
        for (int i = fromIndex; i < toIndex; i++) {
            if (predicate.test(elements[i])) {
                return false;
            }
        }

        return true;
    }

    @Override
    public OptionalFloat findFirst(final FloatPredicate predicate) {
        for (int i = fromIndex; i < toIndex; i++) {
            if (predicate.test(elements[i])) {
                return OptionalFloat.of(elements[i]);
            }
        }

        return OptionalFloat.empty();
    }

    @Override
    public OptionalFloat findLast(final FloatPredicate predicate) {
        for (int i = toIndex - 1; i >= fromIndex; i--) {
            if (predicate.test(elements[i])) {
                return OptionalFloat.of(elements[i]);
            }
        }

        return OptionalFloat.empty();
    }

    @Override
    public DoubleStream asDoubleStream() {
        return new IteratorDoubleStream(new ExDoubleIterator() {
            private int cursor = fromIndex;

            @Override
            public boolean hasNext() {
                return cursor < toIndex;
            }

            @Override
            public double nextDouble() {
                if (cursor >= toIndex) {
                    throw new NoSuchElementException();
                }

                return elements[cursor++];
            }

            @Override
            public long count() {
                return toIndex - cursor;
            }

            @Override
            public void skip(long n) {
                cursor = n < toIndex - cursor ? cursor + (int) n : toIndex;
            }

            @Override
            public double[] toArray() {
                final double[] a = new double[toIndex - cursor];

                for (int i = 0, len = toIndex - cursor; i < len; i++) {
                    a[i] = elements[cursor++];
                }

                return a;
            }
        }, closeHandlers, sorted);
    }

    @Override
    public Stream<Float> boxed() {
        return new IteratorStream<Float>(iterator(), closeHandlers, sorted, sorted ? FLOAT_COMPARATOR : null);
    }

    @Override
    public FloatStream cached() {
        return this;
    }

    @Override
    ExFloatIterator exIterator() {
        return ExFloatIterator.of(elements, fromIndex, toIndex);
    }

    @Override
    public FloatStream parallel(int maxThreadNum, Splitor splitor) {
        if (maxThreadNum < 1 || maxThreadNum > MAX_THREAD_NUM_PER_OPERATION) {
            throw new IllegalArgumentException("'maxThreadNum' must not less than 1 or exceeded: " + MAX_THREAD_NUM_PER_OPERATION);
        }

        return new ParallelArrayFloatStream(elements, fromIndex, toIndex, closeHandlers, sorted, maxThreadNum, splitor);
    }

    @Override
    public FloatStream onClose(Runnable closeHandler) {
        final Set<Runnable> newCloseHandlers = new AbstractStream.LocalLinkedHashSet<>(N.isNullOrEmpty(this.closeHandlers) ? 1 : this.closeHandlers.size() + 1);

        if (N.notNullOrEmpty(this.closeHandlers)) {
            newCloseHandlers.addAll(this.closeHandlers);
        }

        newCloseHandlers.add(closeHandler);

        return new ArrayFloatStream(elements, fromIndex, toIndex, newCloseHandlers, sorted);
    }
}
