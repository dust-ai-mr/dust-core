/*
 *
 *  Copyright 2024-Present Alan Littleford
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

package com.mentalresonance.dust.core.actors;

import lombok.Getter;

import java.io.Serializable;

/**
 * Bundle up actor creation state. For now this just creates a new Props object we can pass to actorOf. Later
 * we may have to deal with more complications.
 *
 *  @author alanl
 */
public class Props implements Serializable {
    /**
     * Class the Actor will be an instance of
     */
    @Getter
    final
    Class<? extends Actor> actorClass;

    /**
     * Arguments to be passed to Actor Constructor
     */
    final Object[] actorArgs;

    /**
     * Constructor for  Props for Actor creation
     * @param clz class of Actor to produce
     * @param args to the Actor constructor
     * @return Props
     */
    Props(Class<? extends Actor> clz, Object... args) {
        actorClass = clz;
        actorArgs = args;
    }

    /**
     * Convenience method Props for Actor creation
     * @param clz class of Actor to produce
     * @param args to the Actor constructor
     * @return Props
     */
    public static Props create(Class<? extends Actor> clz, Object... args) {
        return new Props(clz, args);
    }
}
