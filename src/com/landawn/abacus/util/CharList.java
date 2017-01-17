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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.landawn.abacus.util.function.CharConsumer;
import com.landawn.abacus.util.function.CharPredicate;
import com.landawn.abacus.util.function.CharUnaryOperator;
import com.landawn.abacus.util.function.IndexedCharConsumer;
import com.landawn.abacus.util.function.IntFunction;
import com.landawn.abacus.util.stream.CharStream;

/**
 * 
 * @since 0.8
 * 
 * @author Haiyang Li
 */
public final class CharList extends AbstractList<CharConsumer, CharPredicate, Character, char[], CharList> {
    private char[] elementData = N.EMPTY_CHAR_ARRAY;
    private int size = 0;

    public CharList() {
        super();
    }

    public CharList(int initialCapacity) {
        elementData = initialCapacity == 0 ? N.EMPTY_CHAR_ARRAY : new char[initialCapacity];
    }

    /**
     * The specified array is used as the element array for this list without copying action.
     * 
     * @param a
     */
    public CharList(char[] a) {
        this(a, a.length);
    }

    public CharList(char[] a, int size) {
        if (a.length < size) {
            throw new IllegalArgumentException("The specified size is bigger than the length of the specified array");
        }

        this.elementData = a;
        this.size = size;
    }

    public static CharList empty() {
        return new CharList(N.EMPTY_CHAR_ARRAY);
    }

    public static CharList of(char... a) {
        return a == null ? empty() : new CharList(a);
    }

    public static CharList of(char[] a, int size) {
        return a == null && size == 0 ? empty() : new CharList(a, size);
    }

    public static CharList from(int... a) {
        return a == null ? empty() : from(a, 0, a.length);
    }

    public static CharList from(int[] a, int startIndex, int endIndex) {
        if (a == null && (startIndex == 0 && endIndex == 0)) {
            return empty();
        }

        N.checkIndex(startIndex, endIndex, a == null ? 0 : a.length);

        final char[] elementData = new char[endIndex - startIndex];

        for (int i = startIndex; i < endIndex; i++) {
            if (a[i] < Character.MIN_VALUE || a[i] > Character.MAX_VALUE) {
                throw new ArithmeticException("overflow");
            }

            elementData[i - startIndex] = (char) a[i];
        }

        return of(elementData);
    }

    static CharList from(List<String> c) {
        if (N.isNullOrEmpty(c)) {
            return empty();
        }

        return from(c, (char) 0);
    }

    static CharList from(List<String> c, char defaultValueForNull) {
        if (N.isNullOrEmpty(c)) {
            return empty();
        }

        final char[] a = new char[c.size()];
        int idx = 0;

        for (String e : c) {
            if (e == null) {
                a[idx++] = defaultValueForNull;
            } else if (e.length() == 1) {
                a[idx++] = e.charAt(0);
            } else {
                throw new IllegalArgumentException("Invalid char: " + e);
            }
        }

        return of(a);
    }

    public static CharList from(Collection<Character> c) {
        if (N.isNullOrEmpty(c)) {
            return empty();
        }

        return from(c, (char) 0);
    }

    public static CharList from(Collection<Character> c, char defaultValueForNull) {
        if (N.isNullOrEmpty(c)) {
            return empty();
        }

        final char[] a = new char[c.size()];
        int idx = 0;

        for (Character e : c) {
            a[idx++] = e == null ? defaultValueForNull : e;
        }

        return of(a);
    }

    public static CharList range(char startInclusive, final char endExclusive) {
        return of(Array.range(startInclusive, endExclusive));
    }

    public static CharList range(char startInclusive, final char endExclusive, final int by) {
        return of(Array.range(startInclusive, endExclusive, by));
    }

    public static CharList rangeClosed(char startInclusive, final char endInclusive) {
        return of(Array.rangeClosed(startInclusive, endInclusive));
    }

    public static CharList rangeClosed(char startInclusive, final char endInclusive, final int by) {
        return of(Array.range(startInclusive, endInclusive, by));
    }

    public static CharList repeat(char element, final int len) {
        return of(Array.repeat(element, len));
    }

    public static CharList random(final int len) {
        final char[] a = new char[len];
        final int mod = Character.MAX_VALUE + 1;

        for (int i = 0; i < len; i++) {
            a[i] = (char) RAND.nextInt(mod);
        }

        return of(a);
    }

    public static CharList random(final char startInclusive, final char endExclusive, final int len) {
        if (startInclusive >= endExclusive) {
            throw new IllegalArgumentException("'startInclusive' must be less than 'endExclusive'");
        }

        final char[] a = new char[len];
        final int mod = endExclusive - startInclusive;

        for (int i = 0; i < len; i++) {
            a[i] = (char) (RAND.nextInt(mod) + startInclusive);
        }

        return of(a);
    }

    public static CharList random(final char[] candicates, final int len) {
        if (N.isNullOrEmpty(candicates) || candicates.length >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException();
        } else if (candicates.length == 1) {
            return repeat(candicates[0], len);
        }

        final int n = candicates.length;
        final char[] a = new char[len];

        for (int i = 0; i < len; i++) {
            a[i] = candicates[RAND.nextInt(n)];
        }

        return of(a);
    }

    /**
     * Returns the original element array without copying.
     * 
     * @return
     */
    public char[] array() {
        return elementData;
    }

    public char get(int index) {
        rangeCheck(index);

        return elementData[index];
    }

    private void rangeCheck(int index) {
        if (index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
    }

    /**
     * 
     * @param index
     * @param e
     * @return the old value in the specified position.
     */
    public char set(int index, char e) {
        rangeCheck(index);

        char oldValue = elementData[index];

        elementData[index] = e;

        return oldValue;
    }

    public void add(char e) {
        ensureCapacityInternal(size + 1);

        elementData[size++] = e;
    }

    public void add(int index, char e) {
        rangeCheckForAdd(index);

        ensureCapacityInternal(size + 1);

        int numMoved = size - index;

        if (numMoved > 0) {
            N.copy(elementData, index, elementData, index + 1, numMoved);
        }

        elementData[index] = e;

        size++;
    }

    public void addAll(CharList c) {
        int numNew = c.size();

        ensureCapacityInternal(size + numNew);

        N.copy(c.array(), 0, elementData, size, numNew);

        size += numNew;
    }

    public void addAll(int index, CharList c) {
        rangeCheckForAdd(index);

        int numNew = c.size();

        ensureCapacityInternal(size + numNew); // Increments modCount

        int numMoved = size - index;

        if (numMoved > 0) {
            N.copy(elementData, index, elementData, index + numNew, numMoved);
        }

        N.copy(c.array(), 0, elementData, index, numNew);

        size += numNew;
    }

    @Override
    public void addAll(char[] a) {
        addAll(size(), a);
    }

    @Override
    public void addAll(int index, char[] a) {
        rangeCheckForAdd(index);

        if (N.isNullOrEmpty(a)) {
            return;
        }

        int numNew = a.length;

        ensureCapacityInternal(size + numNew); // Increments modCount

        int numMoved = size - index;

        if (numMoved > 0) {
            N.copy(elementData, index, elementData, index + numNew, numMoved);
        }

        N.copy(a, 0, elementData, index, numNew);

        size += numNew;
    }

    private void rangeCheckForAdd(int index) {
        if (index > size || index < 0) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }
    }

    /**
     * 
     * @param e
     * @return <tt>true</tt> if this list contained the specified element
     */
    public boolean remove(char e) {
        for (int i = 0; i < size; i++) {
            if (elementData[i] == e) {

                fastRemove(i);

                return true;
            }
        }

        return false;
    }

    /**
     * 
     * @param e
     * @return <tt>true</tt> if this list contained the specified element
     */
    public boolean removeAllOccurrences(char e) {
        int w = 0;

        for (int i = 0; i < size; i++) {
            if (elementData[i] != e) {
                elementData[w++] = elementData[i];
            }
        }

        int numRemoved = size - w;

        if (numRemoved > 0) {
            N.fill(elementData, w, size, (char) 0);

            size = w;
        }

        return numRemoved > 0;
    }

    private void fastRemove(int index) {
        int numMoved = size - index - 1;

        if (numMoved > 0) {
            N.copy(elementData, index + 1, elementData, index, numMoved);
        }

        elementData[--size] = 0; // clear to let GC do its work
    }

    public boolean removeAll(CharList c) {
        return batchRemove(c, false) > 0;
    }

    @Override
    public boolean removeAll(char[] a) {
        if (N.isNullOrEmpty(a)) {
            return false;
        }

        return removeAll(of(a));
    }

    @Override
    public boolean removeIf(CharPredicate p) {
        final CharList tmp = new CharList(size());

        for (int i = 0; i < size; i++) {
            if (p.test(elementData[i]) == false) {
                tmp.add(elementData[i]);
            }
        }

        if (tmp.size() == this.size()) {
            return false;
        }

        N.copy(tmp.elementData, 0, this.elementData, 0, tmp.size());

        return true;
    }

    public boolean retainAll(CharList c) {
        return batchRemove(c, true) > 0;
    }

    public boolean retainAll(char[] a) {
        return retainAll(CharList.of(a));
    }

    private int batchRemove(CharList c, boolean complement) {
        final char[] elementData = this.elementData;

        int w = 0;

        if (c.size() > 3 && size() > 9) {
            final Set<Character> set = c.toSet();

            for (int i = 0; i < size; i++) {
                if (set.contains(elementData[i]) == complement) {
                    elementData[w++] = elementData[i];
                }
            }
        } else {
            for (int i = 0; i < size; i++) {
                if (c.contains(elementData[i]) == complement) {
                    elementData[w++] = elementData[i];
                }
            }
        }

        int numRemoved = size - w;

        if (numRemoved > 0) {
            N.fill(elementData, w, size, (char) 0);

            size = w;
        }

        return numRemoved;
    }

    /**
     * 
     * @param index
     * @return the deleted element
     */
    public char delete(int index) {
        rangeCheck(index);

        char oldValue = elementData[index];

        fastRemove(index);

        return oldValue;
    }

    @Override
    public void deleteAll(int... indices) {
        N.deleteAll(elementData, indices);
    }

    public int replaceAll(char oldVal, char newVal) {
        if (size() == 0) {
            return 0;
        }

        int result = 0;

        for (int i = 0, len = size(); i < len; i++) {
            if (elementData[i] == oldVal) {
                elementData[i] = newVal;

                result++;
            }
        }

        return result;
    }

    public void replaceAll(CharUnaryOperator operator) {
        for (int i = 0, len = size(); i < len; i++) {
            elementData[i] = operator.applyAsChar(elementData[i]);
        }
    }

    public boolean replaceIf(char newValue, CharPredicate predicate) {
        boolean result = false;

        for (int i = 0, len = size(); i < len; i++) {
            if (predicate.test(elementData[i])) {
                elementData[i] = newValue;

                result = true;
            }
        }

        return result;
    }

    public void fill(final char val) {
        fill(0, size(), val);
    }

    public void fill(final int fromIndex, final int toIndex, final char val) {
        checkIndex(fromIndex, toIndex);

        N.fill(elementData, fromIndex, toIndex, val);
    }

    public boolean contains(char e) {
        return indexOf(e) >= 0;
    }

    public boolean containsAll(CharList c) {
        final char[] srcElementData = c.array();

        if (c.size() > 3 && size() > 9) {
            final Set<Character> set = c.toSet();

            for (int i = 0, srcSize = c.size(); i < srcSize; i++) {
                if (set.contains(srcElementData[i]) == false) {
                    return false;
                }
            }
        } else {
            for (int i = 0, srcSize = c.size(); i < srcSize; i++) {
                if (contains(srcElementData[i]) == false) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public boolean containsAll(char[] a) {
        if (N.isNullOrEmpty(a)) {
            return true;
        }

        return containsAll(of(a));
    }

    public boolean disjoint(final CharList c) {
        final CharList container = size() >= c.size() ? this : c;
        final char[] iterElements = size() >= c.size() ? c.array() : this.array();

        if (iterElements.length > 3 && container.size() > 9) {
            final Set<Character> set = container.toSet();

            for (int i = 0, srcSize = size() >= c.size() ? c.size() : this.size(); i < srcSize; i++) {
                if (set.contains(iterElements[i])) {
                    return false;
                }
            }
        } else {
            for (int i = 0, srcSize = size() >= c.size() ? c.size() : this.size(); i < srcSize; i++) {
                if (container.contains(iterElements[i])) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public boolean disjoint(final char[] b) {
        if (N.isNullOrEmpty(b)) {
            return true;
        }

        return disjoint(of(b));
    }

    public int occurrencesOf(final char objectToFind) {
        return N.occurrencesOf(elementData, objectToFind);
    }

    public int indexOf(char e) {
        return indexOf(0, e);
    }

    /**
     * 
     * @param b
     * @return
     * @see IntList#except(IntList)
     */
    public CharList except(CharList b) {
        final Multiset<Character> bOccurrences = b.toMultiset();

        final CharList c = new CharList(N.min(size(), N.max(9, size() - b.size())));

        for (int i = 0, len = size(); i < len; i++) {
            if (bOccurrences.getAndRemove(elementData[i]) < 1) {
                c.add(elementData[i]);
            }
        }

        return c;
    }

    /**
     * 
     * @param b
     * @return
     * @see IntList#intersect(IntList)
     */
    public CharList intersect(CharList b) {
        final Multiset<Character> bOccurrences = b.toMultiset();

        final CharList c = new CharList(N.min(9, size(), b.size()));

        for (int i = 0, len = size(); i < len; i++) {
            if (bOccurrences.getAndRemove(elementData[i]) > 0) {
                c.add(elementData[i]);
            }
        }

        return c;
    }

    /**
     * 
     * @param b
     * @return this.except(b).addAll(b.except(this))
     * @see IntList#xor(IntList)
     */
    public CharList xor(CharList b) {
        final Multiset<Character> bOccurrences = b.toMultiset();

        final CharList c = new CharList(N.max(9, Math.abs(size() - b.size())));

        for (int i = 0, len = size(); i < len; i++) {
            if (bOccurrences.getAndRemove(elementData[i]) < 1) {
                c.add(elementData[i]);
            }
        }

        for (int i = 0, len = b.size(); i < len; i++) {
            if (bOccurrences.getAndRemove(b.elementData[i]) > 0) {
                c.add(b.elementData[i]);
            }

            if (bOccurrences.isEmpty()) {
                break;
            }
        }

        return c;
    }

    public int indexOf(final int fromIndex, char e) {
        checkIndex(fromIndex, size);

        for (int i = fromIndex; i < size; i++) {
            if (elementData[i] == e) {
                return i;
            }
        }

        return -1;
    }

    public int lastIndexOf(char e) {
        return lastIndexOf(size, e);
    }

    /**
     * 
     * @param fromIndex the start index to traverse backwards from. Inclusive.
     * @param e
     * @return
     */
    public int lastIndexOf(final int fromIndex, char e) {
        checkIndex(0, fromIndex);

        for (int i = fromIndex == size ? size - 1 : fromIndex; i >= 0; i--) {
            if (elementData[i] == e) {
                return i;
            }
        }

        return -1;
    }

    public OptionalChar min() {
        return size() == 0 ? OptionalChar.empty() : OptionalChar.of(N.min(elementData, 0, size));
    }

    public OptionalChar min(final int fromIndex, final int toIndex) {
        checkIndex(fromIndex, toIndex);

        return fromIndex == toIndex ? OptionalChar.empty() : OptionalChar.of(N.min(elementData, fromIndex, toIndex));
    }

    public OptionalChar median() {
        return size() == 0 ? OptionalChar.empty() : OptionalChar.of(N.median(elementData, 0, size));
    }

    public OptionalChar median(final int fromIndex, final int toIndex) {
        checkIndex(fromIndex, toIndex);

        return fromIndex == toIndex ? OptionalChar.empty() : OptionalChar.of(N.median(elementData, fromIndex, toIndex));
    }

    public OptionalChar max() {
        return size() == 0 ? OptionalChar.empty() : OptionalChar.of(N.max(elementData, 0, size));
    }

    public OptionalChar max(final int fromIndex, final int toIndex) {
        checkIndex(fromIndex, toIndex);

        return fromIndex == toIndex ? OptionalChar.empty() : OptionalChar.of(N.max(elementData, fromIndex, toIndex));
    }

    public OptionalChar kthLargest(final int k) {
        return kthLargest(0, size(), k);
    }

    public OptionalChar kthLargest(final int fromIndex, final int toIndex, final int k) {
        checkIndex(fromIndex, toIndex);

        return toIndex - fromIndex < k ? OptionalChar.empty() : OptionalChar.of(N.kthLargest(elementData, fromIndex, toIndex, k));
    }

    public Long sum() {
        return sum(0, size());
    }

    public Long sum(final int fromIndex, final int toIndex) {
        checkIndex(fromIndex, toIndex);

        return N.sum(elementData, fromIndex, toIndex);
    }

    public OptionalDouble average() {
        return average(0, size());
    }

    public OptionalDouble average(final int fromIndex, final int toIndex) {
        checkIndex(fromIndex, toIndex);

        return fromIndex == toIndex ? OptionalDouble.empty() : OptionalDouble.of(N.average(elementData, fromIndex, toIndex));
    }

    @Override
    public void forEach(final int fromIndex, final int toIndex, CharConsumer action) {
        if (fromIndex <= toIndex) {
            checkIndex(fromIndex, toIndex);
        } else {
            checkIndex(toIndex, fromIndex);
        }

        if (size > 0) {
            if (fromIndex <= toIndex) {
                for (int i = fromIndex; i < toIndex; i++) {
                    action.accept(elementData[i]);
                }
            } else {
                for (int i = fromIndex - 1; i >= toIndex; i--) {
                    action.accept(elementData[i]);
                }
            }
        }
    }

    public void forEach(IndexedCharConsumer action) {
        forEach(0, size(), action);
    }

    public void forEach(final int fromIndex, final int toIndex, IndexedCharConsumer action) {
        if (fromIndex <= toIndex) {
            checkIndex(fromIndex, toIndex);
        } else {
            checkIndex(toIndex, fromIndex);
        }

        if (size > 0) {
            if (fromIndex <= toIndex) {
                for (int i = fromIndex; i < toIndex; i++) {
                    action.accept(i, elementData[i], elementData);
                }
            } else {
                for (int i = fromIndex - 1; i >= toIndex; i--) {
                    action.accept(i, elementData[i], elementData);
                }
            }
        }
    }

    public OptionalChar first() {
        return size() == 0 ? OptionalChar.empty() : OptionalChar.of(elementData[0]);
    }

    public OptionalChar last() {
        return size() == 0 ? OptionalChar.empty() : OptionalChar.of(elementData[size() - 1]);
    }

    public OptionalChar findFirst(CharPredicate predicate) {
        for (int i = 0; i < size; i++) {
            if (predicate.test(elementData[i])) {
                return OptionalChar.of(elementData[i]);
            }
        }

        return OptionalChar.empty();
    }

    //    public Optional<IndexedChar> findFirst2(CharPredicate predicate) {
    //        for (int i = 0; i < size; i++) {
    //            if (predicate.test(elementData[i])) {
    //                return Optional.of(IndexedChar.of(i, elementData[i]));
    //            }
    //        }
    //
    //        return Optional.empty();
    //    }

    public OptionalChar findLast(CharPredicate predicate) {
        for (int i = size - 1; i >= 0; i--) {
            if (predicate.test(elementData[i])) {
                return OptionalChar.of(elementData[i]);
            }
        }

        return OptionalChar.empty();
    }

    //    public Optional<IndexedChar> findLast2(CharPredicate predicate) {
    //        for (int i = size - 1; i >= 0; i--) {
    //            if (predicate.test(elementData[i])) {
    //                return Optional.of(IndexedChar.of(i, elementData[i]));
    //            }
    //        }
    //
    //        return Optional.empty();
    //    }

    public OptionalInt findFirstIndex(CharPredicate predicate) {
        for (int i = 0; i < size; i++) {
            if (predicate.test(elementData[i])) {
                return OptionalInt.of(i);
            }
        }

        return OptionalInt.empty();
    }

    public OptionalInt findLastIndex(CharPredicate predicate) {
        for (int i = size - 1; i >= 0; i--) {
            if (predicate.test(elementData[i])) {
                return OptionalInt.of(i);
            }
        }

        return OptionalInt.empty();
    }

    @Override
    public boolean allMatch(final int fromIndex, final int toIndex, CharPredicate filter) {
        checkIndex(fromIndex, toIndex);

        if (size > 0) {
            for (int i = fromIndex; i < toIndex; i++) {
                if (filter.test(elementData[i]) == false) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public boolean anyMatch(final int fromIndex, final int toIndex, CharPredicate filter) {
        checkIndex(fromIndex, toIndex);

        if (size > 0) {
            for (int i = fromIndex; i < toIndex; i++) {
                if (filter.test(elementData[i])) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean noneMatch(final int fromIndex, final int toIndex, CharPredicate filter) {
        checkIndex(fromIndex, toIndex);

        if (size > 0) {
            for (int i = fromIndex; i < toIndex; i++) {
                if (filter.test(elementData[i])) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public boolean hasDuplicates() {
        return N.hasDuplicates(elementData, 0, size, false);
    }

    @Override
    public int count(final int fromIndex, final int toIndex, CharPredicate filter) {
        checkIndex(fromIndex, toIndex);

        return N.count(elementData, fromIndex, toIndex, filter);
    }

    @Override
    public CharList filter(final int fromIndex, final int toIndex, CharPredicate filter) {
        checkIndex(fromIndex, toIndex);

        return of(N.filter(elementData, fromIndex, toIndex, filter));
    }

    @Override
    public CharList filter(final int fromIndex, final int toIndex, CharPredicate filter, final int max) {
        checkIndex(fromIndex, toIndex);

        return of(N.filter(elementData, fromIndex, toIndex, filter, max));
    }

    @Override
    public CharList distinct(final int fromIndex, final int toIndex) {
        checkIndex(fromIndex, toIndex);

        if (toIndex - fromIndex > 1) {
            return of(N.distinct(elementData, fromIndex, toIndex));
        } else {
            return of(N.copyOfRange(elementData, fromIndex, toIndex));
        }
    }

    @Override
    public void sort() {
        if (size > 1) {
            N.sort(elementData, 0, size);
        }
    }

    public void parallelSort() {
        if (size > 1) {
            N.parallelSort(elementData, 0, size);
        }
    }

    /**
     * This List should be sorted first.
     * 
     * @param key
     * @return
     */
    public int binarySearch(final char key) {
        return N.binarySearch(elementData, key);
    }

    /**
     * This List should be sorted first.
     *
     * @param fromIndex
     * @param toIndex
     * @param key
     * @return
     */
    public int binarySearch(final int fromIndex, final int toIndex, final char key) {
        checkIndex(fromIndex, toIndex);

        return N.binarySearch(elementData, fromIndex, toIndex, key);
    }

    @Override
    public void reverse() {
        if (size > 1) {
            N.reverse(elementData, 0, size);
        }
    }

    @Override
    public void reverse(final int fromIndex, final int toIndex) {
        checkIndex(fromIndex, toIndex);

        if (toIndex - fromIndex > 1) {
            N.reverse(elementData, fromIndex, toIndex);
        }
    }

    @Override
    public void rotate(int distance) {
        if (size > 1) {
            N.rotate(elementData, distance);
        }
    }

    @Override
    public void shuffle() {
        N.shuffle(elementData);
    }

    @Override
    public void swap(int i, int j) {
        rangeCheck(i);
        rangeCheck(j);

        set(i, set(j, elementData[i]));
    }

    @Override
    public CharList copy(final int fromIndex, final int toIndex) {
        checkIndex(fromIndex, toIndex);

        return new CharList(N.copyOfRange(elementData, fromIndex, toIndex));
    }

    @Override
    public CharList copy() {
        return new CharList(N.copyOfRange(elementData, 0, size));
    }

    @Override
    public List<CharList> split(final int fromIndex, final int toIndex, final int size) {
        checkIndex(fromIndex, toIndex);

        final List<char[]> list = N.split(elementData, fromIndex, toIndex, size);
        final List<CharList> result = new ArrayList<>(list.size());

        for (char[] a : list) {
            result.add(of(a));
        }

        return result;
    }

    //    @Override
    //    public List<CharList> split(int fromIndex, int toIndex, CharPredicate predicate) {
    //        checkIndex(fromIndex, toIndex);
    //
    //        final List<CharList> result = new ArrayList<>();
    //        CharList piece = null;
    //
    //        for (int i = fromIndex; i < toIndex;) {
    //            if (piece == null) {
    //                piece = CharList.of(N.EMPTY_CHAR_ARRAY);
    //            }
    //
    //            if (predicate.test(elementData[i])) {
    //                piece.add(elementData[i]);
    //                i++;
    //            } else {
    //                result.add(piece);
    //                piece = null;
    //            }
    //        }
    //
    //        if (piece != null) {
    //            result.add(piece);
    //        }
    //
    //        return result;
    //    }

    @Override
    public String join(int fromIndex, int toIndex, char delimiter) {
        checkIndex(fromIndex, toIndex);

        return N.join(elementData, fromIndex, toIndex, delimiter);
    }

    @Override
    public String join(int fromIndex, int toIndex, String delimiter) {
        checkIndex(fromIndex, toIndex);

        return N.join(elementData, fromIndex, toIndex, delimiter);
    }

    @Override
    public CharList trimToSize() {
        if (elementData.length > size) {
            elementData = N.copyOfRange(elementData, 0, size);
        }

        return this;
    }

    @Override
    public void clear() {
        if (size > 0) {
            N.fill(elementData, 0, size, (char) 0);
        }

        size = 0;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public int size() {
        return size;
    }

    public ObjectList<Character> boxed() {
        return boxed(0, size);
    }

    public ObjectList<Character> boxed(int fromIndex, int toIndex) {
        checkIndex(fromIndex, toIndex);

        final Character[] b = new Character[toIndex - fromIndex];

        for (int i = fromIndex, j = 0; i < toIndex; i++, j++) {
            b[j] = elementData[i];
        }

        return ObjectList.of(b);
    }

    public IntList toIntList() {
        return IntList.from(elementData, 0, size);
    }

    @Override
    public List<Character> toList(final int fromIndex, final int toIndex, final IntFunction<List<Character>> supplier) {
        checkIndex(fromIndex, toIndex);

        final List<Character> list = supplier.apply(toIndex - fromIndex);

        for (int i = fromIndex; i < toIndex; i++) {
            list.add(elementData[i]);
        }

        return list;
    }

    @Override
    public Set<Character> toSet(final int fromIndex, final int toIndex, final IntFunction<Set<Character>> supplier) {
        checkIndex(fromIndex, toIndex);

        final Set<Character> set = supplier.apply(N.min(16, toIndex - fromIndex));

        for (int i = fromIndex; i < toIndex; i++) {
            set.add(elementData[i]);
        }

        return set;
    }

    @Override
    public Multiset<Character> toMultiset(final int fromIndex, final int toIndex, final IntFunction<Multiset<Character>> supplier) {
        checkIndex(fromIndex, toIndex);

        final Multiset<Character> multiset = supplier.apply(N.min(16, toIndex - fromIndex));
        ;

        for (int i = fromIndex; i < toIndex; i++) {
            multiset.add(elementData[i]);
        }

        return multiset;
    }

    public CharStream stream() {
        return CharStream.of(elementData, 0, size());
    }

    public CharStream stream(final int fromIndex, final int toIndex) {
        checkIndex(fromIndex, toIndex);

        return CharStream.of(elementData, fromIndex, toIndex);
    }

    //    public CharListBuilder __() {
    //        return Builder.of(this);
    //    }
    //
    //    public CharListBuilder __(Consumer<? super CharList> func) {
    //        return Builder.of(this).__(func);
    //    }

    @Override
    public int hashCode() {
        return N.hashCode(elementData, 0, size);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof CharList) {
            final CharList other = (CharList) obj;

            return this.size == other.size && N.equals(this.elementData, 0, other.elementData, 0, this.size);
        }

        return false;
    }

    @Override
    public String toString() {
        return size == 0 ? "[]" : N.toString(elementData, 0, size);
    }

    private void ensureCapacityInternal(int minCapacity) {
        if (elementData == N.EMPTY_CHAR_ARRAY) {
            minCapacity = Math.max(DEFAULT_CAPACITY, minCapacity);
        }

        ensureExplicitCapacity(minCapacity);
    }

    private void ensureExplicitCapacity(int minCapacity) {
        if (minCapacity - elementData.length > 0) {
            grow(minCapacity);
        }
    }

    private void grow(int minCapacity) {
        int oldCapacity = elementData.length;
        int newCapacity = oldCapacity + (oldCapacity >> 1);

        if (newCapacity - minCapacity < 0) {
            newCapacity = minCapacity;
        }

        if (newCapacity - MAX_ARRAY_SIZE > 0) {
            newCapacity = hugeCapacity(minCapacity);
        }

        elementData = Arrays.copyOf(elementData, newCapacity);
    }
}
