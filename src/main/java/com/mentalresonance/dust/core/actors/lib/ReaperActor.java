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

import com.mentalresonance.dust.core.actors.*;
import com.mentalresonance.dust.core.msgs.StopMsg;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
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
     * @param handleMs time in MS before dead man's handle drop
     */
    public ReaperActor(Long handleMs) { this.handleMs = handleMs; }

    /**
     * Create props
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
        client.tell(reapMsg.response, null);
    }

    /**
     * Default behavior
     * @return {@link ActorBehavior}
     */
    @Override
    protected ActorBehavior createBehavior() {
        return message -> {
            switch(message) {
                case ReapMsg msg -> {
                    client = sender;
                    reapMsg = msg;
                    if (!reapMsg.targets.isEmpty()) {
                        if (null != reapMsg.clz)
                            for (ActorRef t : reapMsg.targets) {
                                t.tell(reapMsg.clz.getDeclaredConstructor().newInstance(), self);
                            }
                        else if (null != reapMsg.copyableMsg)
                            for (ActorRef t : reapMsg.targets) {
                                t.tell(reapMsg.copyableMsg.copy(), self);
                            }
                        else
                            self.tell(new StopMsg(), self);
                    }
                    else {
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
                        reapMsg.response.complete = true;
                        self.tell(new StopMsg(), self);
                    }
                }
            }
        };
    }

    /**
     * A request to reap.
     */
    public static class ReapMsg implements Serializable
    {
        /**
         * Container for childRef -> msg from child map
         */
        public static class ReapResponseMsg implements Serializable {
            /**
             * The messages sent back in response to the request
             * as stored here.
             */
            @Getter
            final
            HashMap<ActorRef, Object> results = new HashMap<>();

            /**
             * Did Reaper complete successfully with all results or did handle drop
             */
            public boolean complete; // Are all results gathered ??

            /**
             * Constructor
             */
            public ReapResponseMsg() { }
        }

        /**
         * Response to be sent to sender. Contains map of childRef -> child messages
         */
        @Getter
        ReapResponseMsg response;
        /**
         * List of Actors to which a message
         * will be sent from the ReapActor
         */
        @Getter
        final
        List<ActorRef> targets;

        /**
         * Message to be instanced and sent to the targets
         */
        Class<? extends Serializable> clz = null;

        /**
         * Alternative to clz. Message to be copied and sent to the targets
         */
        CopyableMsg copyableMsg = null;

        /**
         * Constructor
         * @param clz send new instances of this class
         * @param targets to these targets
         */
        public ReapMsg(Class<? extends Serializable> clz, List<ActorRef> targets) {
            this.clz = clz;
            this.targets = targets;
            this.response = new ReapResponseMsg();
        }


        /**
         * Construct ReapMsg with a specific Response class (which should extend ReapResponseMsg)
         * @param clz message to be instanced and sent
         * @param targets to these Actors
         * @param respClz custom ReapResponseMsg class
         * @throws NoSuchMethodException on error
         * @throws InstantiationException on error
         * @throws IllegalAccessException on error
         * @throws InvocationTargetException on error
         */
        public ReapMsg(
                Class<? extends Serializable> clz,
                List<ActorRef> targets,
                Class<? extends ReapResponseMsg> respClz) throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
            this.clz = clz;
            this.targets = targets;
            this.response = respClz.getDeclaredConstructor().newInstance();
        }

        /**
         * Construct ReapMsg with a specific Response class (which should extend ReapResponseMsg)
         * @param copyableMsg message to be copied and sent
         * @param targets to these Actors
         * @param respClz custom ReapResponseMsg class
         * @throws NoSuchMethodException on error
         * @throws InstantiationException on error
         * @throws IllegalAccessException on error
         * @throws InvocationTargetException on error
         */
        public ReapMsg(
                CopyableMsg copyableMsg,
                List<ActorRef> targets,
                Class<? extends ReapResponseMsg> respClz) throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
            this.copyableMsg = copyableMsg;
            this.targets = targets;
            this.response = respClz.getDeclaredConstructor().newInstance();
        }

        /**
         * Do we have all our responses
         * @return true if yes
         */
        boolean isComplete() {
            return response.results.size() == targets.size();
        }
    }

    /**
     * Rather than create a new instance of the message to be passed on we can pass in a copyable object
     * and it will get copied rather than new instanced
     */
    public interface CopyableMsg extends Serializable {
        /**
         * Copy the message
         * @return copy of the message
         */
        Serializable copy();
    }
}
