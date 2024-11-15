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

import com.mentalresonance.dust.core.actors.Actor;
import com.mentalresonance.dust.core.actors.ActorBehavior;
import com.mentalresonance.dust.core.actors.Props;
import com.mentalresonance.dust.core.actors.lib.entities.msgs.DeadLetterProxyMsg;
import com.mentalresonance.dust.core.actors.lib.entities.msgs.RegisterPodDeadLettersMsg;
import com.mentalresonance.dust.core.msgs.*;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.List;

/**
 * Intercept dead letters. If the dead letter was to the child of an Actor on registered path
 * then wrap the message in a {@link DeadLetterProxyMsg} and send it to the intended recipient's parent.
 *
 * Typically the recipient will be a {@link PodManagerActor} who will create the named child and resubmit the msg to it.
 *
 * In creating this mechanism (via {@link RegisterPodDeadLettersMsg}) we can supply an (optional) set of classes.
 * If this set is non-empty then only messages in this set will be passed on.
 *
 * @author alanl
 */
@Slf4j
public class PodDeadLetterActor extends Actor {
    final HashMap<String, List<Class>> pathCache = new HashMap();

    /**
     * props
     * @param paths list of [path, [trigger classes]]
     * @return Props for this Actor
     */
    public static Props props(List<List> paths) {
        return Props.create(PodDeadLetterActor.class, paths);
    }

    /**
     * Constructor
     * @param paths list of [path, [trigger classes]]
     */
	public PodDeadLetterActor(List<List> paths) {
        for (List path :paths) {
            pathCache.put(path.get(0).toString(), (List<Class>)path.get(1));
        }
    }

    @Override
    protected void preStart() throws Exception {
        super.preStart();

        PubSubMsg sub = new PubSubMsg(DeadLetter.class);
        try {
            context.getDeadLetterActor().tell(sub, self);
        }
        catch (Exception e) {
            log.error("Cannot subscribe to DeadLetterActor: %s".formatted(e.getMessage()));
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
                case DeadLetter dl -> {
                    String recipientPath = normalizePath(dl.getPath());
                    String senderPath = null != dl.getSender() ? dl.getSender().path : null;
                    String recipientParentPath;

                    recipientParentPath = recipientPath.substring(0, recipientPath.lastIndexOf('/'));

                    /**
                     * We do not start an Actor to accept a message it has sent itself! this can happen when we stop
                     * ourselves with messages still in the Q. They go to dead letters and then here. But I stopped myself !!
                     */
                    if (recipientPath == senderPath) {
                        return;
                    }

                    /**
                     * Ignore dead letters which should not reinvoke the Actor ever
                     */
                    switch (dl.getMessage()) {
                        case SnapshotSuccessMsg ignored:
                            break;
                        case SnapshotFailureMsg ignored:
                            break;
                        case DeleteSnapshotFailureMsg ignored:
                            break;
                        case DeleteSnapshotSuccessMsg ignored:
                            break;

                        default:
                            List<Class> acceptedMessages;

                            if (null != (acceptedMessages = pathCache.get(recipientParentPath))) {

                                if (0 == acceptedMessages.size() || acceptedMessages.contains(dl.getMessage().getClass())) {
                                    context.actorSelection(recipientParentPath).tell(
                                        new DeadLetterProxyMsg(nameFromPath(recipientPath), dl.getMessage(), dl.getSender()),
                                        dl.getSender()
                                    );
                                }
                                else // Todo: this is not rally a warning - it is doing its job. But debugging for now
                                    log.warn(
                                        "Unhandled msg type %s in dead letter handler to: %s from: %s msg: %s".formatted(
                                                dl.getMessage().getClass(),
                                                recipientPath,
                                                senderPath,
                                                dl
                                        )
                                    );
                            }
                            else {
                                log.debug("Unhandled Dead letter to: %s from: %s msg: %s".formatted(
                                    recipientPath,
                                    senderPath,
                                    dl
                                ));
                            }
                    }
                }
                /*
                 * Register the sender and return his message as acknowledgement.
                 */
                case RegisterPodDeadLettersMsg msg -> {
                    log.trace("%s registering with %s. Messages=%s".formatted(
                            sender.path,
                            self.path,
                            null != msg.acceptedMessages ? msg.acceptedMessages.toString() : "")
                    );
                    pathCache.put(normalizePath(sender.path), msg.acceptedMessages);
                    sender.tell(msg, self);
                }

                default -> super.createBehavior().onMessage(message);
            }
        };
    }

    String normalizePath(String s) {
        if (s.endsWith("/"))
            s = s.substring(0, s.length() - 1);
        return s;
    }

    String nameFromPath(String s) {
        return s.substring(1 + s.lastIndexOf('/'));
    }
}
