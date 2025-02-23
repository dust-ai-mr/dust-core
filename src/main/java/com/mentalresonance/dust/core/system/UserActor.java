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

package com.mentalresonance.dust.core.system;

import com.mentalresonance.dust.core.actors.Actor;
import com.mentalresonance.dust.core.actors.Props;
import lombok.extern.slf4j.Slf4j;


/**
 * One of the two top level Actors under '/' root. All non-system Actors are under /user here. In contrast,
 * /system managers all the system setup up Actors {@link SystemActor}
 *
 * @author alanl
 */
@Slf4j
public class UserActor extends Actor {

    /**
     * Props for construction
     * @return the Props
     */
    public static Props props() {
        return Props.create(UserActor.class);
    }

    /**
     * Constructor
     */
    public UserActor() {}

    @Override
    protected void postStop() {
        log.info("/user shut down");
    }
}
