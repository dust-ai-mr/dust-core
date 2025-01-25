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
import com.mentalresonance.dust.core.msgs.CompletionRequestMsg;
import com.mentalresonance.dust.core.msgs.ReturnableMsg;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Receives a message which is a subclass of CompletionRequestMsg. It retains the future and sends the message off
 * to its target. The first message it receives back is to set the future and stop. This is a Service Actor and so
 * should be run under a {@link ServiceManagerActor}.
 *
 *  @author alanl
 */
public class CompletionServiceActor extends Actor {

    CompletableFuture<Object> future;
    Long maxTime;

    /**
     * Construct Actor's Props
     * @return the Props
     */
    public static Props props() {
        return Props.create(CompletionServiceActor.class, 30000L);
    }

    public static Props props(Long maxTime) {
        return Props.create(CompletionServiceActor.class, maxTime);
    }

    /**
     * Constructor
     */
    public CompletionServiceActor(Long maxTime) {
        this.maxTime = maxTime;
    }

    @Override
    protected void preStart() {
        dieIn(maxTime);
    }

    @Override
    protected void dying() {
        future.complete(null);
        super.dying();
    }

    /**
     * Default behavior
     * @return {@link ActorBehavior}
     */
    @Override
    protected ActorBehavior createBehavior() {
        return message -> {
            if (Objects.requireNonNull(message) instanceof CompletionRequestMsg msg) {
                Serializable passThrough = msg.getPassThroughMsg();
                future = msg.getFuture();
                msg.setSender(self);
                if (null != passThrough) {
                    // If it is returnable (e.g. returned by a pipe) we want to come back to me
                    if (passThrough instanceof ReturnableMsg) {
                        ((ReturnableMsg) passThrough).setSender(self);
                    }
                    msg.target.tell(passThrough, self);
                } else
                    msg.target.tell(msg, self);
            } else {
                future.complete(message);
                stopSelf();
            }
        };
    }
}
