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

package com.mentalresonance.dust.core.actors.lib;

import com.mentalresonance.dust.core.actors.Actor;
import com.mentalresonance.dust.core.actors.ActorBehavior;
import com.mentalresonance.dust.core.actors.ActorRef;
import com.mentalresonance.dust.core.actors.Props;
import com.mentalresonance.dust.core.msgs.ReturnableMsg;

import java.util.Objects;

/**
 * An Actor which replaces the return address in a Returnable with its own, sends the message on to its original target.
 * Then on receipt of the returned message puts the original return address back in, sends the message to its parent
 * as though it came from the target and then stops
 */
public class ReturnableProxyActor extends Actor {

    ActorRef returnRef;
    final ActorRef targetRef;
    final ReturnableMsg msg;

    /**
     * Props
     * @param msg the msg to be proxied
     * @param targetRef the target of the message
     * @return Props
     */
    public static Props props(ReturnableMsg msg, ActorRef targetRef) {
        return Props.create(ReturnableProxyActor.class, msg, targetRef);
    }

    /**
     * Construct
     * @param msg the msg to be proxied
     * @param targetRef the target of the message
     */
    public ReturnableProxyActor(ReturnableMsg msg, ActorRef targetRef) {
        this.msg = msg;
        this.targetRef = targetRef;
    }

    @Override
    protected void preStart() {
        returnRef = msg.getSender();
        msg.setSender(self);
        targetRef.tell(msg, parent);
    }

    @Override
    protected ActorBehavior createBehavior() {
        return message -> {
            if (Objects.requireNonNull(message) instanceof ReturnableMsg msg) {
                msg.setSender(returnRef);
                parent.tell(msg, sender);
            } else {
                super.createBehavior().onMessage(message);
            }
            stopSelf();
        };
    }
}
