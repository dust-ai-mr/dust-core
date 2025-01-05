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
import com.mentalresonance.dust.core.actors.ActorRef;
import com.mentalresonance.dust.core.actors.Props;
import com.mentalresonance.dust.core.msgs.PipelineStageMsg;
import com.mentalresonance.dust.core.msgs.ReturnableMsg;
import com.mentalresonance.dust.core.system.exceptions.ActorInstantiationException;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;

/**
 * A Pipeline is managed by a PipelineActor. Its props specify a sequence of other Actor Props which it
 * instantiates as children. It maintains the order -- C1 -> C2 -> C3 ... -- and if it receives a message from
 * CN it simply passes that on to CN+1.
 *
 * For now the Actors in the pipe have to be unique (we use their name) unless a matching list of distinct names
 * is given.
 *
 * By default if the message didn't originate in the pipe it goes to the first child. If a message
 * emerges from the last stage of the pipe and it is a ReturnableMsg then the pipe sends it back to sender, else
 * it is dropped.
 *
 *  @author alanl
 */
@Slf4j
public class PipelineActor extends Actor {

    /**
     * Map of refN -> refN+1
     */
    final HashMap<ActorRef, ActorRef> pipe = new HashMap<>();

    /**
     * Stage Name -> Stage ref for stage directed messages
     */
    final HashMap<String, ActorRef> stages = new HashMap<>();

    ActorRef first, last = null; // Actors in the pipe

    final List<Props> stageProps;
    final List<String> names;

    /**
     * Construct pipeline with named Actors
     * @param stageProps list of Props to create each stage of the pipe
     * @param names matching list of names for created Actors or null
     * @return Props for the pipeline
     */
    public static Props props(List<Props> stageProps, List<String> names) {
        return Props.create(PipelineActor.class, stageProps, names);
    }
    /**
     * Construct pipeline with randomly named Actors
     * @param stageProps list of Props to create each stage of the pipe
     * @return Props for the pipeline
     */
    public static Props props(List<Props> stageProps) {
        return Props.create(PipelineActor.class, stageProps, null);
    }

    /**
     * Constructor
     * @param stageProps list of Props to create each stage of the pipe
     * @param names matching list of names for created Actors or null
     */
    public PipelineActor(List<Props> stageProps, List<String> names)  {
        this.stageProps = stageProps;
        this.names = names;
    }

    @Override
    protected void preStart() throws ActorInstantiationException {
        ActorRef ref;

        if (null != names && names.size() != stageProps.size())
            throw new ActorInstantiationException("Bad names/props size");

        String name = "";
        try {
            for (int i = 0; i < stageProps.size(); ++i) {
                Props next = stageProps.get(i);

                name = (null == names) ? next.getActorClass().getSimpleName() : names.get(i);
                if (null == last) {
                    first = actorOf(next, name);
                    last = first;
                }
                else {
                    ref = actorOf(next, name);
                    pipe.put(last, ref);
                    last = ref;
                }
            }
            for (ActorRef child : getChildren()) {
                stages.put(child.name, child);
            }
        }
        catch (Exception e) {
            log.error("Pipline %s: %s creating actor '%s'".formatted(self.path, e.getMessage(), name));
            throw new ActorInstantiationException(e.getMessage());
        }
    }

    /**
     * Default behavior
     * @return {@link ActorBehavior}
     */
    @Override
    protected ActorBehavior createBehavior() {
        return message -> {
            if (message instanceof PipelineStageMsg msg) {
                ActorRef child;
                if (null != (child = stages.getOrDefault(msg.getStage(), null))) {
                    child.tell(msg.getMsg(), sender);
                }
            }
            else {
                ActorRef to = pipe.get(sender); // Next stage in pipe
                if (null == to) {
                    if (sender == last) {
                        if (message instanceof ReturnableMsg)
                            to = ((ReturnableMsg)message).getSender();
                        else
                            log.warn("Pipeline %s dropping message %s".formatted(self.path, message));
                    } else
                        to = first;
                }
                if (null != to)
                    to.tell(message, self);
            }
        };
    }
}
