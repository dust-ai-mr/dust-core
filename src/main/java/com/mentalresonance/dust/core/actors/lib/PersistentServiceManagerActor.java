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
import com.mentalresonance.dust.core.msgs.*;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

/**
 * Maintain a virtual pool of (identical) service Actors. Hand incoming messages off to these Actors when a slot
 * opens up.
 *
 * These Service Actors <b>must</b> die after processing their one task.
 * <p>This version of a ServiceManagerActor is persistent. It periodically saves its message Q in case of interruption</p>
 *
 *  @author alanl
 */
@Slf4j
public abstract class PersistentServiceManagerActor extends PersistentActor {

    final Props serviceProps;
    final int maxWorkers;
    int currentWorkers;
    final Long delayMS;
    boolean dirty;
    Cancellable bump;

    // [msg, sender]
    final LinkedList<LinkedList<Serializable>> msgQ = new LinkedList<>();

    /**
     * Default snapshot period of 5 secs
     * @param serviceProps - Props for the service workers
     * @param maxWorkers - max number of workers
     * @return Props
     */
    public static Props props(Props serviceProps, Integer maxWorkers) {
        return Props.create(PersistentServiceManagerActor.class, serviceProps, maxWorkers, 5000L);
    }

    /**
     * Create props
     * @param serviceProps - Props for the service workers
     * @param maxWorkers - max number of workers
     * @param delayMS - interval in ms between snapshot saves
     * @return Props
     */
    public static Props props(Props serviceProps, Integer maxWorkers, Long delayMS) {
        return Props.create(PersistentServiceManagerActor.class, serviceProps, maxWorkers, delayMS);
    }

    /**
     * Constructor
     * @param serviceProps - Props for the service workers
     * @param maxWorkers - max number of workers
     * @param delayMS - interval in ms between snapshot saves
     */
    public PersistentServiceManagerActor(Props serviceProps, Integer maxWorkers, Long delayMS) {
        this.serviceProps = serviceProps;
        this.maxWorkers = maxWorkers;
        this.delayMS = delayMS;
        currentWorkers = 0;
        dirty = false;
    }

    /**
     * Constructor - defaults to 5 seconds between saves
     * @param serviceProps - Props for the service workers
     * @param maxWorkers - max number of workers
     */
    public PersistentServiceManagerActor(Props serviceProps, Integer maxWorkers) {
        this(serviceProps, maxWorkers, 5000L);
    }

    @Override
    protected void preStart() {
        bump = scheduleIn(new NextMsg(), delayMS);
        self.tell(new StartMsg(), self);
    }

    @Override
    protected void postStop() {
        bump.cancel();
        if (dirty)
            saveSnapshot(msgQ);
    }

    /**
     * Default behavior
     * @return {@link ActorBehavior}
     */
    @Override
    protected ActorBehavior createBehavior() {
        return message -> {
            switch(message) {
                case StartMsg ignored -> {
                    if (msgQ.size() != 0 && currentWorkers < maxWorkers) {
                        LinkedList<Serializable> record = msgQ.removeFirst();
                        watch(actorOf(serviceProps)).tell(record.getFirst(), (ActorRef) record.getLast());
                        ++currentWorkers;
                        dirty = true;
                    }
                }

                case Terminated ignored -> {
                    --currentWorkers;
                    self.tell(new StartMsg(), self);
                }

                case SnapshotSuccessMsg ignored -> {}

                case DeleteSnapshotSuccessMsg ignored -> {}

                case SnapshotFailureMsg msg -> log.error("Snapshot failed: %s".formatted(msg.getException()));

                case DeleteSnapshotFailureMsg msg -> log.error("Delete Snapshot failed: %s".formatted(msg.getException()));

                case NextMsg ignored -> {
                    if (dirty) {
                        saveSnapshot(msgQ);
                        dirty = false;
                    }
                    scheduleIn(message, delayMS);
                }

                default -> {
                    if (currentWorkers < maxWorkers) {
                        watch(actorOf(serviceProps)).tell(message, sender);
                        ++currentWorkers;
                    }
                    else {
                        msgQ.add(new LinkedList<>(List.of(message, sender)));
                        dirty = true;
                    }
                }
            }
        };
    }
}
