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
import com.mentalresonance.dust.core.msgs.ChildExceptionMsg;
import com.mentalresonance.dust.core.msgs.Terminated;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.LinkedList;

/**
 * Maintain a virtual pool of (identical) service Actors. Hand incoming messages off to these Actors when a slot
 * opens up.
 *
 * These Service Actors <b>must</b> die after processing their one task
 *
 *  @author alanl
 */
@Slf4j
public class ServiceManagerActor extends Actor {

    final Props serviceProps;
    final int maxWorkers;
    int currentWorkers;

    // [msg, sender]
    final LinkedList<LinkedList<Serializable>> msgQ = new LinkedList<>();

    /**
     * Create Actor props
     * @param serviceProps the ServiceActor props
     * @param maxWorkers max slots available
     * @return Props
     */
    public static Props props(Props serviceProps, Integer maxWorkers) {
        return Props.create(ServiceManagerActor.class, serviceProps, maxWorkers);
    }

    /**
     * Constructor
     * @param serviceProps the ServiceActor props
     * @param maxWorkers max slots available
     */
    public ServiceManagerActor(Props serviceProps, Integer maxWorkers) {
        this.serviceProps = serviceProps;
        this.maxWorkers = maxWorkers;
        currentWorkers = 0;
    }

    @Override
    protected void preStart() throws Exception {
        super.preStart();
        self.tell(new _StartMsg(), self);
    }

    /**
     * Default behavior
     * @return {@link ActorBehavior}
     */
    @Override
    protected ActorBehavior createBehavior() {
        return message -> {
            switch(message) {
                case _StartMsg ignored -> {
                    if (!msgQ.isEmpty() && currentWorkers < maxWorkers) {
                        LinkedList<Serializable> record = msgQ.removeFirst();
                        watch(actorOf(serviceProps)).tell(record.getFirst(), (ActorRef) record.getLast());
                        ++currentWorkers;
                    }
                }

                case Terminated ignored -> {
                    --currentWorkers;
                    self.tell(new _StartMsg(), self);
                }

                case ChildExceptionMsg ignored -> {} // Otherwise default will create a child for it

                default -> {
                    if (currentWorkers < maxWorkers) {
                        watch(actorOf(serviceProps)).tell(message, sender);
                        ++currentWorkers;
                    }
                    else {
                        // Cannot use List.of() as it does not allow null entries and we may have null sender
                        LinkedList<Serializable> tuple = new LinkedList<>();
                        tuple.add(message);
                        tuple.add(sender);
                        msgQ.add(tuple);
                    }
                }
            }
        };
    }

    // Don't use StartMsg() since we may be sending that to our service workers !
    static class _StartMsg implements Serializable {}
}
