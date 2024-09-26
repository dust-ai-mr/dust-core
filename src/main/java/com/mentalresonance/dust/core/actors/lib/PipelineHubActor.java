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
import com.mentalresonance.dust.core.system.exceptions.ActorInstantiationException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * A stage in a pipeline that acts as a distribution point to a bunch of other Actors which are its children. If it
 * receives a message from its parent it sends that message off to all of its children. If the message sender
 * is not the parent it sends it to its parent. Thus a 'block' of Actors can behave like a single stage in the pipe.
 *
 * Most often used at the beginning of the pipe - e.g. a number of independent feed Actors (RSS say). Each fills the pipe
 * as though it was the only Actor at that stage.
 *
 * Messages from children being sent to the parent are marked as coming from the hub.
 *
 * Note: If the Actors running beneath the hub are persistent then names should be supplied else
 * any persisted state will not be found since names may change.
 */
@Slf4j
public class PipelineHubActor extends Actor {

    final List<Props> actors;
    final List<String> names;

    /**
     * create Props
     * @param actors list of Actor Props to create Actors on the hub
     * @param names name of these Actors (in order)
     * @return Props
     */
    public static Props props(List<Props> actors, List<String> names) {
        return Props.create(PipelineHubActor.class, actors, names);
    }

    /**
     * create Props
     * @param actors list of Actor Props to create Actors on the hub. Get given unique names
     * @return Props
     */
    public static Props props(List<Props> actors) {
        return Props.create(PipelineHubActor.class, actors, null);
    }

    /**
     * Constructor
     * @param actors list of Actor Props to create Actors on the hub
     * @param names name of these Actors (in order)
     */
    public PipelineHubActor(List<Props> actors, List<String> names) {
        this.actors = actors;
        this.names = names;
    }

    @Override
    protected void preStart() throws ActorInstantiationException {
        for (int i = 0; i < actors.size(); ++i)
        {
            Props actor = actors.get(i);
            if (null != names) {
                actorOf(actor, names.get(i));
            } else
                actorOf(actor);
        }
    }

    @Override
    protected ActorBehavior createBehavior() {
        return message -> {
            if (sender == parent) {
                for(ActorRef child: getChildren()) {
                    child.tell(message, sender);
                }
            } else
                parent.tell(message, self);
        };
    }
}
