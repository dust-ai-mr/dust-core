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

package com.mentalresonance.dust.core.actors.lib;

import com.mentalresonance.dust.core.actors.*;
import com.mentalresonance.dust.core.msgs.ReapMsg;
import com.mentalresonance.dust.core.msgs.StopMsg;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * Receives a list of Actors and a class from which to construct a series of messages.
 * Sends instances of these messages to the given Actors and when all have replied (or timed out)
 * send the requesting Actor the map of responses and then die.
 *
 *  @author alanl
 */
@Slf4j
public class ReaperActor extends Actor {

    ActorRef client;
    ReapMsg reapMsg;
    Cancellable cancellable;
    Long handleMs;

    /**
     * Constructor
     *
     * @param handleMs time in MS before dead man's handle drop
     */
    public ReaperActor(Long handleMs) {
        this.handleMs = handleMs;
    }

    /**
     * Create props
     *
     * @param handleMS time in MS before dead man's handle drop
     * @return props
     */
    public static Props props(Long handleMS) {
        return Props.create(ReaperActor.class, handleMS);
    }

    @Override
    protected void preStart() {
        dieIn(handleMs);
    }

    @Override
    protected void dying() {
        log.warn("Reaper {} is dying", self.path);
        reapMsg.response.complete = false;
        reapMsg.response.failedTargets.addAll(
            reapMsg.targets.stream().filter(t -> !reapMsg.response.results.containsKey(t)).toList()
        );
        client.tell(reapMsg.response, null);
    }

    /**
     * Default behavior
     *
     * @return {@link ActorBehavior}
     */
    @Override
    protected ActorBehavior createBehavior() {
        return message -> {
            switch (message) {
                case ReapMsg msg -> {
                    client = sender;
                    reapMsg = msg;
                    if (!reapMsg.targets.isEmpty()) {
                        log.trace("{} reaping {} targets", self.path, reapMsg.targets.size());
                        if (null != reapMsg.clz)
                            for (ActorRef t : reapMsg.targets) {
                                t.tell(reapMsg.clz.getDeclaredConstructor().newInstance(), self);
                            }
                        else if (null != reapMsg.copyableMsg)
                            for (ActorRef t : reapMsg.targets) {
                                t.tell(reapMsg.copyableMsg.copy(), self);
                            }
                        else if (null != reapMsg.msg) {
                            for (ActorRef t : reapMsg.targets) {
                                t.tell(reapMsg.msg, self);
                            }
                        } else
                            self.tell(new StopMsg(), self);
                    } else {
                        self.tell(new StopMsg(), self);
                    }
                }
                case StopMsg ignored -> {
                    client.tell(reapMsg.response, null);
                    cancelDeadMansHandle();
                    stopSelf();
                }
                default -> {
                    reapMsg.response.results.put(sender, message);
                    if (reapMsg.isComplete()) {
                        log.trace("{} reaping complete", self.path);
                        reapMsg.response.complete = true;
                        self.tell(new StopMsg(), self);
                    }
                }
            }
        };
    }
}
