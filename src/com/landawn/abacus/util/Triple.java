/*
 * Copyright (c) 2015, Haiyang Li.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.landawn.abacus.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.landawn.abacus.util.function.BiPredicate;
import com.landawn.abacus.util.function.CharConsumer;
import com.landawn.abacus.util.function.Consumer;
import com.landawn.abacus.util.function.DoubleConsumer;
import com.landawn.abacus.util.function.FloatConsumer;
import com.landawn.abacus.util.function.Function;
import com.landawn.abacus.util.function.IntConsumer;
import com.landawn.abacus.util.function.LongConsumer;
import com.landawn.abacus.util.function.Predicate;
import com.landawn.abacus.util.function.QuadPredicate;
import com.landawn.abacus.util.function.TriConsumer;
import com.landawn.abacus.util.function.TriFunction;
import com.landawn.abacus.util.function.TriPredicate;
import com.landawn.abacus.util.stream.Stream;

/**
 * 
 * @since 0.8
 * 
 * @author Haiyang Li
 *
 * @param <L>
 * @param <M>
 * @param <R>
 */
public final class Triple<L, M, R> {
    public volatile L left;
    public volatile M middle;
    public volatile R right;

    public Triple() {
    }

    Triple(final L l, final M m, final R r) {
        this.left = l;
        this.middle = m;
        this.right = r;
    }

    public static <L, M, R> Triple<L, M, R> of(final L l, final M m, final R r) {
        return new Triple<>(l, m, r);
    }

    public static <T> Triple<T, T, T> from(T[] a) {
        if (N.isNullOrEmpty(a)) {
            return new Triple<>(null, null, null);
        } else if (a.length == 1) {
            return new Triple<>(a[0], null, null);
        } else if (a.length == 2) {
            return new Triple<>(a[0], a[1], null);
        } else {
            return new Triple<>(a[0], a[1], a[2]);
        }
    }

    public static <T> Triple<T, T, T> from(Collection<? extends T> c) {
        if (N.isNullOrEmpty(c)) {
            return new Triple<>(null, null, null);
        }

        final List<T> list = c instanceof List ? (List<T>) c : null;

        if (c.size() == 1) {
            if (list != null) {
                return new Triple<T, T, T>(list.get(0), null, null);
            } else {
                return new Triple<T, T, T>(c.iterator().next(), null, null);
            }
        } else if (c.size() == 2) {
            if (list != null) {
                return new Triple<T, T, T>(list.get(0), list.get(1), null);
            } else {
                final Iterator<? extends T> iter = c.iterator();
                return new Triple<T, T, T>(iter.next(), iter.next(), null);
            }
        } else {
            if (list != null) {
                return new Triple<T, T, T>(list.get(0), list.get(1), list.get(2));
            } else {
                final Iterator<? extends T> iter = c.iterator();
                return new Triple<T, T, T>(iter.next(), iter.next(), iter.next());
            }
        }
    }

    public L left() {
        return left;
    }

    public M middle() {
        return middle;
    }

    public R right() {
        return right;
    }

    public L getLeft() {
        return left;
    }

    public Triple<L, M, R> setLeft(final L left) {
        this.left = left;

        return this;
    }

    public M getMiddle() {
        return middle;
    }

    public Triple<L, M, R> setMiddle(final M middle) {
        this.middle = middle;

        return this;
    }

    public R getRight() {
        return right;
    }

    public Triple<L, M, R> setRight(final R right) {
        this.right = right;

        return this;
    }

    public Triple<L, M, R> set(final L left, final M middle, final R right) {
        this.left = left;
        this.middle = middle;
        this.right = right;

        return this;
    }

    public L getAndSetLeft(L newLeft) {
        final L res = left;
        left = newLeft;
        return res;
    }

    public L setAndGetLeft(L newLeft) {
        left = newLeft;
        return left;
    }

    public M getAndSetMiddle(M newMiddle) {
        final M res = middle;
        middle = newMiddle;
        return res;
    }

    public M setAndGetMiddle(M newMiddle) {
        middle = newMiddle;
        return middle;
    }

    public R getAndSetRight(R newRight) {
        final R res = newRight;
        right = newRight;
        return res;
    }

    public R setAndGetRight(R newRight) {
        right = newRight;
        return right;
    }

    /**
     * Set to the specified <code>newLeft</code> and returns <code>true</code>
     * if <code>predicate</code> returns true. Otherwise returns
     * <code>false</code> without setting the value to new value.
     * 
     * @param newLeft
     * @param predicate - the first parameter is current pair, the second
     *        parameter is the <code>newLeft</code>
     * @return
     */
    public boolean setLeftIf(final L newLeft, BiPredicate<? super Triple<L, M, R>, ? super L> predicate) {
        if (predicate.test(this, newLeft)) {
            this.left = newLeft;
            return true;
        }

        return false;
    }

    /**
     * Set to the specified <code>newMiddle</code> and returns <code>true</code>
     * if <code>predicate</code> returns true. Otherwise returns
     * <code>false</code> without setting the value to new value.
     * 
     * @param newMiddle
     * @param predicate - the first parameter is current pair, the second
     *        parameter is the <code>newMiddle</code>
     * @return
     */
    public boolean setMiddleIf(final M newMiddle, BiPredicate<? super Triple<L, M, R>, ? super M> predicate) {
        if (predicate.test(this, newMiddle)) {
            this.middle = newMiddle;
            return true;
        }

        return false;
    }

    /**
     * Set to the specified <code>newRight</code> and returns <code>true</code>
     * if <code>predicate</code> returns true. Otherwise returns
     * <code>false</code> without setting the value to new value.
     * 
     * @param newRight
     * @param predicate - the first parameter is current pair, the second
     *        parameter is the <code>newRight</code>
     * @return
     */
    public boolean setRightIf(final R newRight, BiPredicate<? super Triple<L, M, R>, ? super R> predicate) {
        if (predicate.test(this, newRight)) {
            this.right = newRight;
            return true;
        }

        return false;
    }

    /**
     * Set to the specified <code>newLeft</code> and <code>newRight</code> and
     * returns <code>true</code> if <code>predicate</code> returns true.
     * Otherwise returns <code>false</code> without setting the left/right to
     * new values.
     * 
     * @param newLeft
     * @param newMiddle
     * @param newRight
     * @param predicate - the first parameter is current pair, the second
     *        parameter is the <code>newLeft</code>, the third parameter is the
     *        <code>newMiddle</code>, the fourth parameter is the
     *        <code>newRight</code>
     * @return
     */
    public boolean setIf(final L newLeft, final M newMiddle, final R newRight,
            QuadPredicate<? super Triple<L, M, R>, ? super L, ? super M, ? super R> predicate) {
        if (predicate.test(this, newLeft, newMiddle, newRight)) {
            this.left = newLeft;
            this.middle = newMiddle;
            this.right = newRight;
            return true;
        }

        return false;
    }

    //    /**
    //     * Swap the left and right value. they must be same type.
    //     */
    //    public void reverse() {
    //        Object tmp = left;
    //        this.left = (L) right;
    //        this.right = (R) tmp;
    //    }

    /**
     * 
     * @return a new instance of Triple&lt;R, M, L&gt;.
     */
    public Triple<R, M, L> reversed() {
        return new Triple<>(this.right, this.middle, this.left);
    }

    public Triple<L, M, R> copy() {
        return new Triple<>(this.left, this.middle, this.right);
    }

    public Object[] toArray() {
        return new Object[] { left, middle, right };
    }

    public <A> A[] toArray(A[] a) {
        if (a.length < 3) {
            a = N.copyOf(a, 3);
        }

        a[0] = (A) left;
        a[1] = (A) middle;
        a[2] = (A) right;

        return a;
    }

    public void forEach(Consumer<?> comsumer) {
        final Consumer<Object> objComsumer = (Consumer<Object>) comsumer;

        objComsumer.accept(left);
        objComsumer.accept(middle);
        objComsumer.accept(right);
    }

    public void accept(final TriConsumer<? super L, ? super M, ? super R> action) {
        action.accept(left, middle, right);
    }

    public void accept(final Consumer<Triple<L, M, R>> action) {
        action.accept(this);
    }

    public <U> U map(final TriFunction<? super L, ? super M, ? super R, U> mapper) {
        return mapper.apply(left, middle, right);
    }

    public <U> U map(final Function<Triple<L, M, R>, U> mapper) {
        return mapper.apply(this);
    }

    public Optional<Triple<L, M, R>> filter(final TriPredicate<? super L, ? super M, ? super R> predicate) {
        return predicate.test(left, middle, right) ? Optional.of(this) : Optional.<Triple<L, M, R>> empty();
    }

    public Optional<Triple<L, M, R>> filter(final Predicate<Triple<L, M, R>> predicate) {
        return predicate.test(this) ? Optional.of(this) : Optional.<Triple<L, M, R>> empty();
    }

    public Stream<Triple<L, M, R>> stream() {
        return Stream.of(this);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + N.hashCode(left);
        result = prime * result + N.hashCode(middle);
        result = prime * result + N.hashCode(right);
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof Triple) {
            final Triple<L, M, R> other = (Triple<L, M, R>) obj;

            return N.equals(left, other.left) && N.equals(middle, other.middle) && N.equals(right, other.right);
        }

        return false;
    }

    @Override
    public String toString() {
        return "[" + N.toString(left) + ", " + N.toString(middle) + ", " + N.toString(right) + "]";
    }

    public static final class CharTriple {
        public final char _1;
        public final char _2;
        public final char _3;

        CharTriple() {
            this((char) 0, (char) 0, (char) 0);
        }

        CharTriple(char _1, char _2, char _3) {
            this._1 = _1;
            this._2 = _2;
            this._3 = _3;
        }

        public static CharTriple of(char _1, char _2, char _3) {
            return new CharTriple(_1, _2, _3);
        }

        public char min() {
            return N.min(_1, _2, _3);
        }

        public char max() {
            return N.max(_1, _2, _3);
        }

        public char median() {
            return N.median(_1, _2, _3);
        }

        public int sum() {
            return _1 + _2 + _3;
        }

        public double average() {
            return sum() / 3d;
        }

        public CharTriple reversed() {
            return new CharTriple(_3, _2, _1);
        }

        public char[] toArray() {
            return new char[] { _1, _2, _3 };
        }

        public CharList toList() {
            return CharList.of(_1, _2, _3);
        }

        public void forEach(CharConsumer comsumer) {
            comsumer.accept(this._1);
            comsumer.accept(this._2);
            comsumer.accept(this._3);
        }

        public void accept(Consumer<CharTriple> action) {
            action.accept(this);
        }

        public <U> U map(Function<CharTriple, U> mapper) {
            return mapper.apply(this);
        }

        public Optional<CharTriple> filter(final Predicate<CharTriple> predicate) {
            return predicate.test(this) ? Optional.of(this) : Optional.<CharTriple> empty();
        }

        public Stream<CharTriple> stream() {
            return Stream.of(this);
        }

        @Override
        public int hashCode() {
            return (31 * (31 * _1 + this._2)) + _3;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof CharTriple)) {
                return false;
            } else {
                CharTriple other = (CharTriple) obj;
                return this._1 == other._1 && this._2 == other._2 && this._3 == other._3;
            }
        }

        @Override
        public String toString() {
            return "[" + this._1 + ", " + this._2 + ", " + this._3 + "]";
        }
    }

    public static final class IntTriple {
        public final int _1;
        public final int _2;
        public final int _3;

        IntTriple() {
            this(0, 0, 0);
        }

        IntTriple(int _1, int _2, int _3) {
            this._1 = _1;
            this._2 = _2;
            this._3 = _3;
        }

        public static IntTriple of(int _1, int _2, int _3) {
            return new IntTriple(_1, _2, _3);
        }

        public int min() {
            return N.min(_1, _2, _3);
        }

        public int max() {
            return N.max(_1, _2, _3);
        }

        public int median() {
            return N.median(_1, _2, _3);
        }

        public int sum() {
            return _1 + _2 + _3;
        }

        public double average() {
            return sum() / 3d;
        }

        public IntTriple reversed() {
            return new IntTriple(_3, _2, _1);
        }

        public int[] toArray() {
            return new int[] { _1, _2, _3 };
        }

        public IntList toList() {
            return IntList.of(_1, _2, _3);
        }

        public void forEach(IntConsumer comsumer) {
            comsumer.accept(this._1);
            comsumer.accept(this._2);
            comsumer.accept(this._3);
        }

        public void accept(Consumer<IntTriple> action) {
            action.accept(this);
        }

        public <U> U map(Function<IntTriple, U> mapper) {
            return mapper.apply(this);
        }

        public Optional<IntTriple> filter(final Predicate<IntTriple> predicate) {
            return predicate.test(this) ? Optional.of(this) : Optional.<IntTriple> empty();
        }

        public Stream<IntTriple> stream() {
            return Stream.of(this);
        }

        @Override
        public int hashCode() {
            return (31 * (31 * _1 + this._2)) + _3;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof IntTriple)) {
                return false;
            } else {
                IntTriple other = (IntTriple) obj;
                return this._1 == other._1 && this._2 == other._2 && this._3 == other._3;
            }
        }

        @Override
        public String toString() {
            return "[" + this._1 + ", " + this._2 + ", " + this._3 + "]";
        }
    }

    public static final class LongTriple {
        public final long _1;
        public final long _2;
        public final long _3;

        LongTriple() {
            this(0, 0, 0);
        }

        LongTriple(long _1, long _2, long _3) {
            this._1 = _1;
            this._2 = _2;
            this._3 = _3;
        }

        public static LongTriple of(long _1, long _2, long _3) {
            return new LongTriple(_1, _2, _3);
        }

        public long min() {
            return N.min(_1, _2, _3);
        }

        public long max() {
            return N.max(_1, _2, _3);
        }

        public long median() {
            return N.median(_1, _2, _3);
        }

        public long sum() {
            return _1 + _2 + _3;
        }

        public double average() {
            return sum() / 3d;
        }

        public LongTriple reversed() {
            return new LongTriple(_3, _2, _1);
        }

        public long[] toArray() {
            return new long[] { _1, _2, _3 };
        }

        public LongList toList() {
            return LongList.of(_1, _2, _3);
        }

        public void forEach(LongConsumer comsumer) {
            comsumer.accept(this._1);
            comsumer.accept(this._2);
            comsumer.accept(this._3);
        }

        public void accept(Consumer<LongTriple> action) {
            action.accept(this);
        }

        public <U> U map(Function<LongTriple, U> mapper) {
            return mapper.apply(this);
        }

        public Optional<LongTriple> filter(final Predicate<LongTriple> predicate) {
            return predicate.test(this) ? Optional.of(this) : Optional.<LongTriple> empty();
        }

        public Stream<LongTriple> stream() {
            return Stream.of(this);
        }

        @Override
        public int hashCode() {
            return (int) ((31 * (31 * _1 + this._2)) + _3);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof LongTriple)) {
                return false;
            } else {
                LongTriple other = (LongTriple) obj;
                return this._1 == other._1 && this._2 == other._2 && this._3 == other._3;
            }
        }

        @Override
        public String toString() {
            return "[" + this._1 + ", " + this._2 + ", " + this._3 + "]";
        }
    }

    public static final class FloatTriple {
        public final float _1;
        public final float _2;
        public final float _3;

        FloatTriple() {
            this(0, 0, 0);
        }

        FloatTriple(float _1, float _2, float _3) {
            this._1 = _1;
            this._2 = _2;
            this._3 = _3;
        }

        public static FloatTriple of(float _1, float _2, float _3) {
            return new FloatTriple(_1, _2, _3);
        }

        public float min() {
            return N.min(_1, _2, _3);
        }

        public float max() {
            return N.max(_1, _2, _3);
        }

        public float median() {
            return N.median(_1, _2, _3);
        }

        public float sum() {
            return _1 + _2 + _3;
        }

        public double average() {
            return sum() / 3d;
        }

        public FloatTriple reversed() {
            return new FloatTriple(_3, _2, _1);
        }

        public float[] toArray() {
            return new float[] { _1, _2, _3 };
        }

        public FloatList toList() {
            return FloatList.of(_1, _2, _3);
        }

        public void forEach(FloatConsumer comsumer) {
            comsumer.accept(this._1);
            comsumer.accept(this._2);
            comsumer.accept(this._3);
        }

        public void accept(Consumer<FloatTriple> action) {
            action.accept(this);
        }

        public <U> U map(Function<FloatTriple, U> mapper) {
            return mapper.apply(this);
        }

        public Optional<FloatTriple> filter(final Predicate<FloatTriple> predicate) {
            return predicate.test(this) ? Optional.of(this) : Optional.<FloatTriple> empty();
        }

        public Stream<FloatTriple> stream() {
            return Stream.of(this);
        }

        @Override
        public int hashCode() {
            return (int) ((31 * (31 * _1 + this._2)) + _3);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof FloatTriple)) {
                return false;
            } else {
                FloatTriple other = (FloatTriple) obj;
                return this._1 == other._1 && this._2 == other._2 && this._3 == other._3;
            }
        }

        @Override
        public String toString() {
            return "[" + this._1 + ", " + this._2 + ", " + this._3 + "]";
        }
    }

    public static final class DoubleTriple {
        public final double _1;
        public final double _2;
        public final double _3;

        DoubleTriple() {
            this(0, 0, 0);
        }

        DoubleTriple(double _1, double _2, double _3) {
            this._1 = _1;
            this._2 = _2;
            this._3 = _3;
        }

        public static DoubleTriple of(double _1, double _2, double _3) {
            return new DoubleTriple(_1, _2, _3);
        }

        public double min() {
            return N.min(_1, _2, _3);
        }

        public double max() {
            return N.max(_1, _2, _3);
        }

        public double median() {
            return N.median(_1, _2, _3);
        }

        public double sum() {
            return _1 + _2 + _3;
        }

        public double average() {
            return sum() / 3d;
        }

        public DoubleTriple reversed() {
            return new DoubleTriple(_3, _2, _1);
        }

        public double[] toArray() {
            return new double[] { _1, _2, _3 };
        }

        public DoubleList toList() {
            return DoubleList.of(_1, _2, _3);
        }

        public void forEach(DoubleConsumer comsumer) {
            comsumer.accept(this._1);
            comsumer.accept(this._2);
            comsumer.accept(this._3);
        }

        public void accept(Consumer<DoubleTriple> action) {
            action.accept(this);
        }

        public <U> U map(Function<DoubleTriple, U> mapper) {
            return mapper.apply(this);
        }

        public Optional<DoubleTriple> filter(final Predicate<DoubleTriple> predicate) {
            return predicate.test(this) ? Optional.of(this) : Optional.<DoubleTriple> empty();
        }

        public Stream<DoubleTriple> stream() {
            return Stream.of(this);
        }

        @Override
        public int hashCode() {
            return (int) ((31 * (31 * _1 + this._2)) + _3);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof DoubleTriple)) {
                return false;
            } else {
                DoubleTriple other = (DoubleTriple) obj;
                return this._1 == other._1 && this._2 == other._2 && this._3 == other._3;
            }
        }

        @Override
        public String toString() {
            return "[" + this._1 + ", " + this._2 + ", " + this._3 + "]";
        }
    }
}
