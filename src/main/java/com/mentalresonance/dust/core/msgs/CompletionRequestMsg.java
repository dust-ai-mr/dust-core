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

package com.mentalresonance.dust.core.msgs;

import com.mentalresonance.dust.core.actors.ActorRef;
import com.mentalresonance.dust.core.actors.lib.CompletionServiceActor;
import lombok.Getter;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;

/**
 * Message to convert a response from a message into a completed Future value. This allows us to join, say, the world of
 * Actors and the world of Controllers. If the timeout is exceeded then we complete exceptionally.
 *
 * This message is sent to a {@link CompletionServiceActor} who handles the sending of the message
 * to the target and completion.
 *
 * @param <Response> The Future response type
 *
 * @author alanl
 */
@Getter
public class CompletionRequestMsg<Response> extends ProxyMsg {

    /**
     * The future to complete. The target ref of the completion request *might* by remote, and so this message
     * needs to be serializable, but CompletableFutures aren't So make this transient - it won't get sent to the remote
     * ref. This is fine since it is the CompletionActor that actually does the completion and that is always local.
     */
    final transient CompletableFuture<Response> future;

    /**
     * Optional message to be sent to target in place of this message
     */
    final Serializable passThroughMsg;

    /**
     * The message is sent to the target. On receipt of a response (expected to be of type Response) the provided
     * future is completed.
     * @param target - target of proxy message
     * @param future - user supplied Future to be set
     */
    public CompletionRequestMsg(ActorRef target, CompletableFuture<Response> future) {
        super(null);

        this.target = target;
        this.future = future;
        this.passThroughMsg = null;
    }

    /**
     * The message is sent to the target. On receipt of a response (expected to be of type Response) the provided
     * future is completed.
     * @param target - target of proxy message
     * @param future - user supplied Future to be set
     * @param passThroughMsg - if not null send passThroughMsg to the target instead of me.
     */
    public CompletionRequestMsg(ActorRef target, CompletableFuture<Response> future, Serializable passThroughMsg) {
        super(null);

        this.target = target;
        this.future = future;
        this.passThroughMsg = passThroughMsg;
    }
}
