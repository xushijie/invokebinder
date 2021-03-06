/*
 * Copyright 2012-2014 headius.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.headius.invokebinder.transform;

import com.headius.invokebinder.Binder;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * An try-finally transform.
 *
 * Equivalent call: A combination of folds and catches.
 *
 * MethodHandle exceptionHandler = [drop exception and invoke post logic]
 * target = MethodHandles.catchException(target, Throwable.class, exceptionHandler)
 * target = MethodHandles.foldArguments(post, target)
 */
public class TryFinally extends Transform {

    private final MethodHandle post;

    public TryFinally(MethodHandle post) {
        this.post = post;
    }

    public MethodHandle up(MethodHandle target) {
        MethodHandle exceptionHandler = Binder
                .from(target.type().insertParameterTypes(0, Throwable.class).changeReturnType(void.class))
                .drop(0)
                .invoke(post);

        MethodHandle rethrow = Binder
                .from(target.type().insertParameterTypes(0, Throwable.class))
                .fold(exceptionHandler)
                .drop(1, target.type().parameterCount())
                .throwException();

        target = MethodHandles.catchException(target, Throwable.class, rethrow);

        // if target returns a value, we must return it regardless of post
        MethodHandle realPost = post;
        if (target.type().returnType() != void.class) {
            // modify post to ignore return value
            MethodHandle newPost = Binder
                    .from(target.type().insertParameterTypes(0, target.type().returnType()).changeReturnType(void.class))
                    .drop(0)
                    .invoke(post);

            // fold post into an identity chain that only returns the value
            realPost = Binder
                    .from(target.type().insertParameterTypes(0, target.type().returnType()))
                    .fold(newPost)
                    .drop(1, target.type().parameterCount())
                    .identity();
        }

        return MethodHandles.foldArguments(realPost, target);
    }

    public MethodType down(MethodType type) {
        return type;
    }

    public String toString() {
        return "try/finally with " + post;
    }
}
