/*
 * Copyright (c) 2015, Haiyang Li. All rights reserved.
 */
package com.landawn.abacus.util.function;

import java.util.Objects;

import com.landawn.abacus.util.N;

public interface BytePredicate {

    static final BytePredicate ALWAYS_TRUE = new BytePredicate() {
        @Override
        public boolean test(byte value) {
            return true;
        }
    };

    static final BytePredicate ALWAYS_FALSE = new BytePredicate() {
        @Override
        public boolean test(byte value) {
            return false;
        }
    };

    static final BytePredicate IS_ZERO = new BytePredicate() {
        @Override
        public boolean test(byte value) {
            return value == 0;
        }
    };

    static final BytePredicate NOT_ZERO = new BytePredicate() {
        @Override
        public boolean test(byte value) {
            return value != 0;
        }
    };

    static final BytePredicate IS_POSITIVE = new BytePredicate() {
        @Override
        public boolean test(byte value) {
            return value > 0;
        }
    };

    static final BytePredicate NOT_POSITIVE = new BytePredicate() {
        @Override
        public boolean test(byte value) {
            return value <= 0;
        }
    };

    static final BytePredicate IS_NEGATIVE = new BytePredicate() {
        @Override
        public boolean test(byte value) {
            return value < 0;
        }
    };

    static final BytePredicate NOT_NEGATIVE = new BytePredicate() {
        @Override
        public boolean test(byte value) {
            return value >= 0;
        }
    };

    boolean test(byte value);

    default BytePredicate negate() {
        return (t) -> !test(t);
    }

    default BytePredicate and(BytePredicate other) {
        Objects.requireNonNull(other);

        return (t) -> test(t) && other.test(t);
    }

    default BytePredicate or(BytePredicate other) {
        Objects.requireNonNull(other);

        return (t) -> test(t) || other.test(t);
    }

    static BytePredicate equal(byte targetByte) {
        return value -> value == targetByte;
    }

    static BytePredicate notEqual(byte targetByte) {
        return value -> value != targetByte;
    }

    static BytePredicate greaterThan(byte targetByte) {
        return value -> N.compare(value, targetByte) > 0;
    }

    static BytePredicate greaterEqual(byte targetByte) {
        return value -> N.compare(value, targetByte) >= 0;
    }

    static BytePredicate lessThan(byte targetByte) {
        return value -> N.compare(value, targetByte) < 0;
    }

    static BytePredicate lessEqual(byte targetByte) {
        return value -> N.compare(value, targetByte) <= 0;
    }
}
