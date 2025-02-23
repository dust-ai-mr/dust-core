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

package com.mentalresonance.dust.core.msgs;

import com.mentalresonance.dust.core.actors.ActorRef;
import com.mentalresonance.dust.core.system.DeadLetterActor;
import lombok.Getter;

import java.io.Serializable;

/**
 * Wraps a message being sent to a non-existent Actor. The DeadLetter is then sent to the
 * {@link DeadLetterActor}
 *
 *  @author alanl
 */
public class DeadLetter extends ReturnableMsg
{
    /**
     * Message that was sent
     */
    @Getter
    final
    Serializable message;

    /**
     * To this path
     */
    @Getter
    final
    String path;

    /**
     * By this actor
     */
    @Getter
    final
    ActorRef sender;

    /**
     * Constructor
     * @param message being sent
     * @param path to the non-existent Actor
     * @param sender by this Actor
     */
    public DeadLetter(Serializable message, String path, ActorRef sender) {
        super(sender);
        this.message = message;
        this.path = path;
        this.sender = sender;
    }

    /**
     * For logging
     * @return logging String rep.
     */
    @Override
    public String toString() {
        return String.format("Dead Letter %s sent to %s by %s", message, path, sender);
    }
}
