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

package com.mentalresonance.dust.core.system;

import com.mentalresonance.dust.core.actors.Actor;
import com.mentalresonance.dust.core.actors.Props;
import com.mentalresonance.dust.core.actors.SupervisionStrategy;
import com.mentalresonance.dust.core.actors.lib.PubSubActor;
import lombok.extern.slf4j.Slf4j;

/**
 * Managers deadletters and events Actors
 *
 * @author alanl
 */
@Slf4j
public class SystemActor extends Actor {

    /**
     * Name of dead letter actor
     */
    public static final String DEAD_LETTERS = "deadletters";
    /**
     * Name of events actor
     */
    public static final String EVENTS = "events";

    final boolean logDeadLetters;

    /**
     * Create props
     * @param logDeadLetters if true dead letters are to be logged
     * @return Props
     */
    public static Props props(boolean logDeadLetters) {
        return Props.create(SystemActor.class, logDeadLetters);
    }

    /**
     * Constructor
     * @param logDeadLetters if true dead letters are to be logged
     */
    public SystemActor(boolean logDeadLetters) {
        this.logDeadLetters = logDeadLetters;
    }

    @Override
    protected void postStop() {
        log.info("/system shut down");
    }

    @Override
    public void preStart() throws Exception {
        supervisor = new SupervisionStrategy(SupervisionStrategy.SS_RESTART, SupervisionStrategy.MODE_ONE_FOR_ONE);
        actorOf(DeadLetterActor.props(logDeadLetters), DEAD_LETTERS);
        actorOf(PubSubActor.props(), EVENTS);
    }
}
