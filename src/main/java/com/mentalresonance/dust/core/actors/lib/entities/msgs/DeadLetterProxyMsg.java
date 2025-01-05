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

package com.mentalresonance.dust.core.actors.lib.entities.msgs;

import java.io.Serializable;
import com.mentalresonance.dust.core.actors.ActorRef;

/**
 * Send by our PodDeadLetterActor. The child Actor at name does not exist, so create it
 * and pass msg on to it.
 *
 * @author alanl
 */
public class DeadLetterProxyMsg implements Serializable {
    /**
     * Name of child to create
     */
    public final String name;
    /**
     * Message to send to it
     */
    public final Serializable msg;
    /**
     * Sender of original message
     */
    public final ActorRef sender;

    /**
     * Constructor
     * @param name of child to create
     * @param msg to send to child
     * @param sender of original message
     */
    public  DeadLetterProxyMsg(String name, Serializable msg, ActorRef sender) {
        this.name = name; this.msg = msg; this.sender = sender;
    }
}
