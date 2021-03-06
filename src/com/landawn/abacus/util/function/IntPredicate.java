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

package com.landawn.abacus.util.function;

import com.landawn.abacus.util.N;

/**
 * Refer to JDK API documentation at: <a href="https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html">https://docs.oracle.com/javase/8/docs/api/java/util/function/package-summary.html</a>
 * @since 0.8
 * 
 * @author Haiyang Li
 */
public interface IntPredicate extends java.util.function.IntPredicate {

   static final IntPredicate ALWAYS_TRUE = new IntPredicate() {
        @Override
        public boolean test(int value) {
            return true;
        }
    };

   static final IntPredicate ALWAYS_FALSE = new IntPredicate() {
        @Override
        public boolean test(int value) {
            return false;
        }
    };

   static final IntPredicate IS_ZERO = new IntPredicate() {
        @Override
        public boolean test(int value) {
            return value == 0;
        }
    };

   static final IntPredicate NOT_ZERO = new IntPredicate() {
        @Override
        public boolean test(int value) {
            return value != 0;
        }
    };

   static final IntPredicate IS_POSITIVE = new IntPredicate() {
        @Override
        public boolean test(int value) {
            return value > 0;
        }
    };

   static final IntPredicate NOT_POSITIVE = new IntPredicate() {
        @Override
        public boolean test(int value) {
            return value <= 0;
        }
    };

   static final IntPredicate IS_NEGATIVE = new IntPredicate() {
        @Override
        public boolean test(int value) {
            return value < 0;
        }
    };

   static final IntPredicate NOT_NEGATIVE = new IntPredicate() {
        @Override
        public boolean test(int value) {
            return value >= 0;
        }
    };

    @Override
    boolean test(int value);

    static IntPredicate equal(int targetInt) {
        return value -> value == targetInt;
    }

    static IntPredicate notEqual(int targetInt) {
        return value -> value != targetInt;
    }

    static IntPredicate greaterThan(int targetInt) {
        return value -> N.compare(value, targetInt) > 0;
    }

    static IntPredicate greaterEqual(int targetInt) {
        return value -> N.compare(value, targetInt) >= 0;
    }

    static IntPredicate lessThan(int targetInt) {
        return value -> N.compare(value, targetInt) < 0;
    }

    static IntPredicate lessEqual(int targetInt) {
        return value -> N.compare(value, targetInt) <= 0;
    }
}
