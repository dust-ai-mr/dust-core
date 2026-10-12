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

import com.mentalresonance.dust.core.actors.ActorRef;
import com.mentalresonance.dust.core.actors.lib.ReaperActor;
import com.mentalresonance.dust.core.ifc.CopyableMsg;
import lombok.Getter;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

/**
 * A request to reap.
 */
public class ReapMsg implements Serializable
{
    /**
     * Container for childRef -> msg from child map
     */
    public static class ReapResponseMsg implements Serializable {
        /**
         * The messages sent back in response to the request
         * as stored here.
         */
        final
        public HashMap<ActorRef, Object> results = new HashMap<>();

        /**
         * If the response is not complete the no-shows in the targets list are
         * put here. NOTE: this assumes that the Actor sender of the completion message was the
         * same as the target Actor. If not then you'll end up with all of targets in here, which
         * is probably not what you want.
         */
        final
        public List<ActorRef> failedTargets = new LinkedList<>();

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
    public ReapResponseMsg response;
    /**
     * List of Actors to which a message
     * will be sent from the ReapActor
     */
    final
    public List<ActorRef> targets;

    /**
     * Message to be instanced and sent to the targets
     */
    public Class<? extends Serializable> clz = null;

    /**
     * Alternative to clz. Message to be copied and sent to the targets
     */
    public CopyableMsg copyableMsg = null;

    /**
     * Alternative. Just send this Message to the targets. Targets should be relied upon not to mutate it.
     */
    public Serializable msg = null;

    /**
     * Constructor
     * @param clz send new instances of this class
     * @param targets to these targets
     */
    public ReapMsg(Class<? extends Serializable> clz, List<ActorRef> targets) {
        this.clz = clz;
        this.targets = targets;
        this.response = new ReapMsg.ReapResponseMsg();
    }

    /**
     * Constructor
     * @param msg send
     * @param targets to these targets
     */
    public ReapMsg(Serializable msg, List<ActorRef> targets) {
        this.msg = msg;
        this.targets = targets;
        this.response = new ReapMsg.ReapResponseMsg();
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
        Class<? extends ReapMsg.ReapResponseMsg> respClz) throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        this.clz = clz;
        this.targets = targets;
        this.response = respClz.getDeclaredConstructor().newInstance();
    }

    /**
     * Construct ReapMsg with a specific Response class (which should extend ReapResponseMsg)
     * @param msg message to be  sent
     * @param targets to these Actors
     * @param respClz custom ReapResponseMsg class
     * @throws NoSuchMethodException on error
     * @throws InstantiationException on error
     * @throws IllegalAccessException on error
     * @throws InvocationTargetException on error
     */
    public ReapMsg(
        Serializable msg,
        List<ActorRef> targets,
        Class<? extends ReapMsg.ReapResponseMsg> respClz) throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        this.msg = msg;
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
        Class<? extends ReapMsg.ReapResponseMsg> respClz) throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        this.copyableMsg = copyableMsg;
        this.targets = targets;
        this.response = respClz.getDeclaredConstructor().newInstance();
    }

    /**
     * Do we have all our responses
     * @return true if yes
     */
    public boolean isComplete() {
        return response.results.size() == targets.size();
    }
}
