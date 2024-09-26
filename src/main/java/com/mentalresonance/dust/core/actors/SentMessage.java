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

package com.mentalresonance.dust.core.actors;

import java.io.Serializable;

/**
 * An arbitrary message and its sender. This packet is the transport mechanism for messages to our Actors.
 *
 *  @author alanl
 */
public class SentMessage implements Serializable {
    /**
     * The message to be delivered
     */
    public final Serializable message;

    /**
     * The target path if remote else null
     */
    public String remotePath = null;

    /**
     * The sender of the message
     */
    public final ActorRef sender;

    /**
     * Constructor
     * @param message being sent
     * @param sender who claims he is sending it
     */
    public SentMessage(Serializable message, ActorRef sender) {
        this.message = message;
        this.sender = sender;
    }
}
