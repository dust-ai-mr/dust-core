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
 * Any interfaces set up to be ActorTraits should extend this interface to get access to the underlying 'self'
 * and parent and other useful fields and methods of the mixed-in Actor
 *
 * @author alanl
 */
public interface ActorTrait {

    /**
     * Implemented by self in Actor
     * @return myself
     */
    ActorRef getSelf();

    /**
     * Implemented by parent in Actor
     * @return my parent
     */
    ActorRef getParent();

    /**
     * Watch the Actor
     * @param ref to watch
     * @return the watched Actor
     */
    ActorRef watch(ActorRef ref);

    /**
     * Unwatch the Actor
     * @param ref actor to unwatch()
     */
    void unWatch(ActorRef ref);

    /**
     * Implemented by context in Actor
     * @return ActorContext
     */
    ActorContext getContext();

    /**
     * Become the specified
     * @param behavior behavior
     */
    void become(ActorBehavior behavior);

    /**
     * Stash the current behavior then become the ..
     * @param newBehavior the behavior to become
     */
    void stashBecome(ActorBehavior newBehavior);
    /**
     * become() the top of the become stack and pop it
     * @return next nehavior
     * @throws Exception on error
     */
    ActorBehavior unBecome() throws Exception;

    /**
     * Send self a message
     * @param msg to send
     * @return true if no problem sending message else false. Note this says nothing about receipt.
     */
    boolean tellSelf(Serializable msg);

    /**
     * Get sender of last message
     * @return sender
     */
    ActorRef getSender();
}
