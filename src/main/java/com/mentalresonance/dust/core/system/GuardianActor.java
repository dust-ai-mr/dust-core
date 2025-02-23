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
import com.mentalresonance.dust.core.actors.SupervisionStrategy;
import com.mentalresonance.dust.core.system.exceptions.ActorInstantiationException;
import lombok.extern.slf4j.Slf4j;

/**
 * The GuardianActor is reprsented by the initial "/". Its job is to create /user under which all user created
 * Actor trees go and /system under which go system generated utility Actors such as the dead letter handler.
 * The Guardian Actor is involved in startup so we have to break some rules in how it is deployed. In particular we can't rely
 * on preStart() to do our work since the system isn't fully initialized at this point. Therefore we call init()
 *
 * @author alanl
 */
@Slf4j
public class GuardianActor extends Actor {

    /**
     * Constructor
     */
    public GuardianActor() {}

    /**
     * Create /system and /user
     * @param logDeadLetters if true then system dead letter actor should log dead letters
     * @throws ActorInstantiationException on error
     */
    public void init(boolean logDeadLetters) throws ActorInstantiationException {
        supervisor = new SupervisionStrategy(SupervisionStrategy.SS_RESTART, SupervisionStrategy.MODE_ONE_FOR_ONE);
        actorOf(SystemActor.props(logDeadLetters), "system");
        actorOf(UserActor.props(), "user");
    }

    @Override
    protected void postStop() {
        log.info("Guardian shut down");
    }

}
