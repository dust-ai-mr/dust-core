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

import com.mentalresonance.dust.core.actors.ActorBehavior;
import com.mentalresonance.dust.core.actors.ActorRef;
import com.mentalresonance.dust.core.actors.PersistentActor;
import com.mentalresonance.dust.core.actors.Props;
import com.mentalresonance.dust.core.actors.lib.entities.msgs.DeadLetterProxyMsg;
import com.mentalresonance.dust.core.actors.lib.entities.msgs.RegisterPodDeadLettersMsg;
import com.mentalresonance.dust.core.msgs.*;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Objects;

/**
 * Manage a Pod of identical children. Responds to a CreateChildMSg by creating the named child
 * and possibly passing on a message. This is persistent so it will create all the children it knew about
 * *but* it is up to those children to restore whatever state they want to depend on.
 *
 *  @author alanl
 */
@Slf4j
public abstract class PodManagerActor extends PersistentActor {
    /**
     * Common Props for children
     */
    protected final Props childProps;
    /**
     * Map of kids - allows restoration on recovery
     */
    public HashMap<String, Boolean> kids;

    /**
     * ref to PodDeadLetterActor
     */
    protected final ActorRef podDeadLetterRef;

    /**
     * Default PodManager creation - children will be determined by childProps
     * @param childProps props defining child to be created
     * @return {@link Props}
     */
    public static Props props(Props childProps) {
        return Props.create(PodManagerActor.class, childProps, null);
    }

    /**
     * Create PodManager but register with pre-existing PodDeadLetterActor so I can automatically
     * create children on demand (a message is being sent to them).
     * @param childProps props defining child to be created
     * @param podDeadLetterRef ref to {@link PodDeadLetterActor}
     * @return {@link Props}
     */
    public static Props props(Props childProps, ActorRef podDeadLetterRef) {
        return Props.create(PodManagerActor.class, childProps, podDeadLetterRef);
    }

    /**
     * Constructor
     * @param childProps props defining child to be created
     * @param podDeadLetterRef ref to {@link PodDeadLetterActor}
     */
    public PodManagerActor(Props childProps, ActorRef podDeadLetterRef) {
        this.childProps = childProps;
        this.podDeadLetterRef = podDeadLetterRef;
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        log.trace("Started {}. podDeadLetterRef={}", self.path, podDeadLetterRef);
        if (null != podDeadLetterRef) {
            podDeadLetterRef.tell(new RegisterPodDeadLettersMsg(), self);
        }
    }

    /**
     * Restores children on startup
     * @return ActorBehavior
     */
    @Override
    protected ActorBehavior recoveryBehavior() {
        return message -> {
            if (Objects.requireNonNull(message) instanceof SnapshotMsg msg) {
                kids = (HashMap<String, Boolean>) msg.getSnapshot();
                if (null == kids) kids = new HashMap<>();
                for (String name : kids.keySet()) {
                    watch(actorOf(childProps, name));
                    kids.put(name, true);
                }
                become(createBehavior());
                unstashAll();
            } else {
                stash(message);
            }
        };
    }

    /**
     * Default behavior
     * @return {@link ActorBehavior}
     */
    @Override
    protected ActorBehavior createBehavior() {
        return message -> {
            switch(message) {
                case Terminated ignored -> {
                    kids.remove(sender.name);
                    saveSnapshot(kids);
                }
                case CreateChildMsg msg -> {
                    String name = msg.getName();
                    ActorRef child = actorOf(childProps, name);
                    watch(child);
                    kids.put(name, true);
                    if (null != msg.getMsg()) {
                        child.tell(msg.getMsg(), sender);
                    }
                    saveSnapshot(kids);
                    if (null != sender) {
                        sender.tell(new CreatedChildMsg(name), self);
                    }
                    log.trace("{} created child: {}", self.path, name);
                }
                case ChildExceptionMsg msg -> {
                    log.error("%s: ChildException %s".formatted(self.path, msg.getException().getMessage()));
                    unWatch(sender);
                    kids.remove(sender.name);
                }
                case DeadLetterProxyMsg msg -> self.tell(new CreateChildMsg(msg.name, msg.msg), msg.sender);

                // Acknowledgement of registration with a PodDeadLetterActor - ignore
                case RegisterPodDeadLettersMsg ignored -> {}

                default -> super.createBehavior().onMessage(message);
            }
        };
    }
}
