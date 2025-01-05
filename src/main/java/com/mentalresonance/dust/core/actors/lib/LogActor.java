/*
 *
 *  Copyright 2024-2025 Alan Littleford
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
import com.mentalresonance.dust.core.actors.ActorBehavior;
import com.mentalresonance.dust.core.actors.Props;
import lombok.extern.slf4j.Slf4j;

/**
 * Like it says on the can. Just log every message it receives
 *
 *  @author alanl
 */
@Slf4j
public class LogActor extends Actor {

    /**
     * create Props
     * @return the Props
     */
    public static Props props() {
        return Props.create(LogActor.class);
    }

    /**
     * Constructor
     */
    public LogActor() {}
    /**
     * Default behavior
     * @return {@link ActorBehavior}
     */
    @Override
    protected ActorBehavior createBehavior() {
        return message -> { log.info(String.format("%s got message %s from %s", self.path, message, sender)); };
    }

}
