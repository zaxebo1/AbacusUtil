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

import java.util.Objects;
import java.util.function.Function;

/**
 * 
 * @since 0.8
 * 
 * @author Haiyang Li
 */
public interface BooleanFunction<R> {
    static final BooleanFunction<Boolean> BOX = new BooleanFunction<Boolean>() {
        @Override
        public Boolean apply(boolean value) {
            return value;
        }
    };

    R apply(boolean value);

    default <V> BooleanFunction<V> andThen(Function<? super R, ? extends V> after) {
        Objects.requireNonNull(after);

        return t -> after.apply(apply(t));
    }

    static BooleanFunction<Boolean> identity() {
        return t -> t;
    }
}
