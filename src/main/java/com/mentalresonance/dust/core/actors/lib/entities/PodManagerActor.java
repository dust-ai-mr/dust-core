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
import java.util.List;
import java.util.Objects;

/**
 * Manage a Pod of identical children. Responds to a CreateChildMSg by creating the named child
 * and possibly passing on a message. This is persistent so it will create all the children it knew about
 * *but* it is up to those children to restore whatever state they want to depend on.
 *
 *  @author alanl
 */
@Slf4j
public class PodManagerActor extends PersistentActor {
    /**
     * Common Props for children
     */
    protected final Props childProps;
    /**
     * Map of kids - allows restoration on recovery
     */
    public HashMap<String, Boolean> kids;

    /**
     * Usually if we created a child on demand we reply to the sender with a CreatedChildMsg.
     * This breaks if the child is a service Actor (say being completed) and the idea is the service Actor
     * responds to the sender directly. There is now a race condition between the service Actor replying and the
     * PodManager replying. If this is a completion the first one wins, and so the other end of the Completion may get
     * a CreatedChildMsg rather than what they were expecting.
     *
     * The default is to send the CreatedChildMsg, but this flag enables us to turn off that behavior.
     */
    protected boolean notifyCreatedChild;

    /**
     * ref to PodDeadLetterActor
     */
    protected final ActorRef podDeadLetterRef;

    /**
     * If automatically creating children via the dead letter mechanism we can define which messages
     * will cause child creation.
     */
    protected List<Class<?>> acceptedMessages;

    /**
     * Default PodManager creation - children will be determined by childProps
     * @param childProps props defining child to be created
     * @return {@link Props}
     */
    public static Props props(Props childProps) {
        return Props.create(PodManagerActor.class, childProps, null, true, List.of());
    }

    /**
     * Create PodManager but register with pre-existing PodDeadLetterActor so I can automatically
     * create children on demand (a message is being sent to them).
     * @param childProps props defining child to be created
     * @param podDeadLetterRef ref to {@link PodDeadLetterActor}
     * @return {@link Props}
     */
    public static Props props(Props childProps, ActorRef podDeadLetterRef) {
        return Props.create(PodManagerActor.class, childProps, podDeadLetterRef, true, List.of());
    }

    /**
     * Create PodManager but register with pre-existing PodDeadLetterActor so I can automatically
     * create children on demand (a message is being sent to them).
     * @param childProps props defining child to be created
     * @param podDeadLetterRef ref to {@link PodDeadLetterActor}
     * @return {@link Props}
     */
    public static Props props(Props childProps, ActorRef podDeadLetterRef, boolean notifyCreatedChild) {
        return Props.create(PodManagerActor.class, childProps, podDeadLetterRef, notifyCreatedChild, List.of());
    }

    /**
     * Constructor
     * @param childProps props defining child to be created
     * @param podDeadLetterRef ref to {@link PodDeadLetterActor}
     * @param notifyCreatedChild if true notify sender of message which created a child a CreatedChildMsg, else don't
     */
    public PodManagerActor(Props childProps, ActorRef podDeadLetterRef, boolean notifyCreatedChild, List<Class<?>> acceptedMessages) {
        this.childProps = childProps;
        this.podDeadLetterRef = podDeadLetterRef;
        this.notifyCreatedChild = notifyCreatedChild;
        this.acceptedMessages = acceptedMessages;
    }

    /**
     * Constructor
     * @param childProps props defining child to be created
     * @param podDeadLetterRef ref to {@link PodDeadLetterActor}
     */
    public PodManagerActor(Props childProps, ActorRef podDeadLetterRef) {
        this.childProps = childProps;
        this.podDeadLetterRef = podDeadLetterRef;
        this.notifyCreatedChild = true;
        this.acceptedMessages = List.of();
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();
        log.trace("Started {}. podDeadLetterRef={}", self.path, podDeadLetterRef);
        if (null != podDeadLetterRef) {
            podDeadLetterRef.tell(new RegisterPodDeadLettersMsg(acceptedMessages), self);
        }
    }

    @Override
    public Class getSnapshotClass() { return HashMap.class; }

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
                    if (kids.containsKey(name)) {
                        log.warn("{}: CreateChildMsg from {}. Child '{}' already exists.", self.path, sender, name);
                    }
                    else {
                        ActorRef child = actorOf(childProps, name);
                        watch(child);
                        kids.put(name, true);
                        if (null != msg.getMsg()) {
                            child.tell(msg.getMsg(), sender);
                        }
                        saveSnapshot(kids);
                        if (notifyCreatedChild && null != sender) {
                            sender.tell(new CreatedChildMsg(name), self);
                        }
                        log.trace("{} created child: {}", self.path, name);
                    }
                }
                case ChildExceptionMsg msg -> {
                    log.error("{}: ChildException {}", self.path, msg.getException().getMessage());
                    unWatch(sender);
                    kids.remove(sender.name);
                }
                case DeadLetterProxyMsg msg -> self.tell(new CreateChildMsg(msg.name, msg.msg), msg.sender);

                // Acknowledgment of child creation - ignore
                case CreatedChildMsg ignored -> {}

                // Acknowledgement of registration with a PodDeadLetterActor - ignore
                case RegisterPodDeadLettersMsg ignored -> {}

                default -> super.createBehavior().onMessage(message);
            }
        };
    }
}
