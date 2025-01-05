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

package com.mentalresonance.dust.core.msgs;


import com.mentalresonance.dust.core.actors.lib.PubSubActor;

import java.io.Serializable;

/**
 * Message to (Un)Subscribe to messages of a particular class. Sent to a {@link PubSubActor} to indicate the sender
 * is interested in receiving any messages of the given class.
 *
 * @author alanl
 */
public class PubSubMsg implements Serializable {
    /**
     * If true the subscribe else unsubscribe
     */
    public Boolean subscribe = true;
    /**
     * Fully qualified class name we are (un) subscribing
     */
    public final String fqn;

    /**
     * Subscribe to messages
     * @param clz - class of message to subscribe to
     */
    public PubSubMsg(Class<? extends Serializable> clz) {
        this.fqn = clz.getName();
    }

    /**
     * (Un)Subscribe to messages
     * @param clz - class of message to (un) subscribe
     * @param subscribe - if true then subscribe else unsubscribe
     */
    public PubSubMsg(Class<? extends Serializable> clz, Boolean subscribe) {
        this.fqn = clz.getName();
        this.subscribe = subscribe;
    }
}

