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

package com.mentalresonance.dust.core.actors.lib;

import com.mentalresonance.dust.core.actors.Actor;
import com.mentalresonance.dust.core.actors.Props;

/**
 * Like it says on the can. This Actor just uses the default Actor behavior, adding nothing itself.
 *
 *  @author alanl
 */
public class NullActor extends Actor {
    /**
     * Create the props for Actor
     * @return the Props for the Actor
     */
    public static Props props() {
        return Props.create(NullActor.class);
    }

    /**
     * Constructor
     */
    public NullActor() {}
}
