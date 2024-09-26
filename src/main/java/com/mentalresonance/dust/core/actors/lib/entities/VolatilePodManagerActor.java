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

package com.mentalresonance.dust.core.actors.lib.entities;

import com.mentalresonance.dust.core.actors.*;
import com.mentalresonance.dust.core.actors.lib.entities.msgs.DeadLetterProxyMsg;
import com.mentalresonance.dust.core.actors.lib.entities.msgs.RegisterPodDeadLettersMsg;
import com.mentalresonance.dust.core.msgs.CreateChildMsg;
import com.mentalresonance.dust.core.msgs.CreatedChildMsg;
import com.mentalresonance.dust.core.msgs.Terminated;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

/**
 * Manage a Pod of identical children. Responds to a CreateChildMSg by creating the named child
 * and possibly passing on a message.
 *
 * Note - this is a non-persistent version of PodManagerActor which is useful during development
 *
 *  @author alanl
 */
@Slf4j
public class VolatilePodManagerActor extends Actor {

    final Props childProps;
    final HashMap<String, Boolean> kids = new HashMap<>();
    final ActorRef podDeadLetterRef;

    /**
     * Create props
     * @param childProps props to create chidren
     * @param podDeadLetterActor ref to pod dead letter Actor
     * @return Props
     */
    public static Props props(Props childProps, ActorRef podDeadLetterActor) {
        return Props.create(VolatilePodManagerActor.class, childProps, podDeadLetterActor);
    }
    /**
     * Create props
     * @param childProps props to create chidren
     * @return Props
     */
    public static Props props(Props childProps) {
        return Props.create(VolatilePodManagerActor.class, childProps, null);
    }

    /**
     * Constructor
     * @param childProps props to create chidren
     * @param podDeadLetterActor ref to pod dead letter Actor
     */
    public VolatilePodManagerActor(Props childProps, ActorRef podDeadLetterActor) {
        this.childProps = childProps;
        this.podDeadLetterRef = podDeadLetterActor;
    }

    @Override
    protected void preStart() throws Exception {
        super.preStart();
        if (null != podDeadLetterRef) {
            podDeadLetterRef.tell(new RegisterPodDeadLettersMsg(), self);
        }
    }
    /**
     * Default behavior
     * @return {@link ActorBehavior}
     */
    @Override
    protected ActorBehavior createBehavior() {
        return message -> {
            switch(message) {
                case Terminated ignored -> kids.remove(sender.name);

                case CreateChildMsg msg -> {
                    String name = msg.getName();
                    ActorRef child = actorOf(childProps, name);
                    kids.put(name, true);
                    if (null != msg.getMsg()) {
                        child.tell(msg.getMsg(), sender);
                    }
                    if (null != sender) {
                        sender.tell(new CreatedChildMsg(name), self);
                    }
                }

                case DeadLetterProxyMsg msg -> self.tell(new CreateChildMsg(msg.name, msg.msg), msg.sender);

                // Acknowledgement of registration with a PodDeadLetterActor - ignore
                case RegisterPodDeadLettersMsg ignored -> {}

                default -> super.createBehavior().onMessage(message);
            }
        };
    }
}
