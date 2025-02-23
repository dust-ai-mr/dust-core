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

package com.mentalresonance.dust.core.actors;

import com.mentalresonance.dust.core.msgs.DeadLetter;
import com.mentalresonance.dust.core.system.exceptions.ActorSelectionException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;

/**
 * Container for location information for an Actor itentified by a path
 */
@Slf4j
public class ActorSelection implements Serializable {
    /**
     * Fully expanded path
     */
    @Setter
    String path;

    /**
     * ActorSystem context
     */
    @Setter
    ActorContext context;

    /**
     * Is path path to remote actor
     */
    @Setter
    boolean remote = false;

    /**
     * Actor known to be dead ?
     */
    @Setter
    boolean dead = false;

    /**
     * ActorRef for this selection
     */
    ActorRef target = null;

    /**
     * Ref to parent of the Actor determined by this selection
     */
    ActorRef parent;

    /**
     * Create ActorSelection
     */
    public ActorSelection() {}

    /**
     * Same semantics as actorRef.tell()
     *
     * @param msg message to be sent
     * @param sender the sender of the message
     * @throws ActorSelectionException all other failures with selection
     * @throws InterruptedException if target is interrupted
     */
    public void tell(Serializable msg, ActorRef sender) throws ActorSelectionException, InterruptedException {
        if (dead) {
            context.deadletterActor.tell(new DeadLetter(msg, path, sender), sender);
        }
        else if (remote) {
            getRef().tell(msg, sender);
        }
        else if (! path.endsWith("*")) {
            getRef().tell(msg, sender);
        }
        else { // We are a broadcast
            path = path.substring(0, path.length() - 2);
            parent = context.actorSelection(path);
            parent.tell(new Actor._ChildProxyMsg(msg), sender);
        }
    }

    /**
     * Get a ref to the Actor located at the selection
     * @return the matching ActorRef
     * @throws ActorSelectionException all other failures
     * @throws InterruptedException if interrupted
     */
    public ActorRef getRef() throws ActorSelectionException, InterruptedException {
        if (remote) {
            return new ActorRef(path, context, null);
        }
        else if (null == target) {
            target = context.actorSelection(path);
        }
        return target;
    }
}
