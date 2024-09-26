/*
 *
 *  Copyright 2024 Alan Littleford
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */

package com.mentalresonance.dust.core.utils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handy utils for random numbers
 *
 * @author alanl
 */
public class RandomUtils {

    /**
     * Constructor
     */
    public RandomUtils() {}

    /**
     * Return random element in list. If list is empty then return null -- so caveat emptor
     * @param list to chose from
     * @param <E> list element types
     * @return the element
     */
    public static <E> E chooseOne(List<E> list) {
        int size = list.size();

        if (size > 0) {
            return list.get(ThreadLocalRandom.current().nextInt(0, size));
        } else
            return null;
    }
}
