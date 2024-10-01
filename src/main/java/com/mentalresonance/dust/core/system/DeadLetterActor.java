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

package com.mentalresonance.dust.core.system;

import com.mentalresonance.dust.core.actors.ActorBehavior;
import com.mentalresonance.dust.core.actors.ActorRef;
import com.mentalresonance.dust.core.actors.Props;
import com.mentalresonance.dust.core.actors.lib.PubSubActor;
import com.mentalresonance.dust.core.msgs.DeadLetter;
import com.mentalresonance.dust.core.msgs.PubSubMsg;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * Messages directed to non-existent Actors get delivered here instead. The message is logged.
 * The DeadLetterActor is a Pub/Sub {@link PubSubActor} Actor so other Actors can get notified of failed deliveries
 * but unlike a regular Pub/Sub Actor this wraps subscribed messages in DeadLetters.
 *
 * The DeadLetterActor is created automatically when the ActorSystem is created and can be found at
 * context.getDeadLetterActor()
 */
@Slf4j
public class DeadLetterActor extends PubSubActor {

    ActorBehavior parentBehavior;

    final boolean logDeadLetters;

    /**
     * Create Props
     * @param logDeadLetters if true log dead letters
     * @return Props
     */
    public static Props props(Boolean logDeadLetters) {
        return Props.create(DeadLetterActor.class, logDeadLetters);
    }

    /**
     * Constructor
     * @param logDeadLetters if true log dead letters
     */
    public DeadLetterActor(Boolean logDeadLetters) {
        this.logDeadLetters = logDeadLetters;
    }

    @Override
    protected void preStart() {
        parentBehavior = super.createBehavior();
    }

    @Override
    protected void postStop() {
        log.info("/system/deadletters shut down");
    }

    /**
     * Default behavior
     * @return {@link ActorBehavior}
     */
    @Override
    protected ActorBehavior createBehavior() {
        return message -> {
            switch(message)
            {
                case PubSubMsg pubsub ->  parentBehavior.onMessage(pubsub);

                case DeadLetter dl -> {
                    boolean hasRegistrants = false;
                    HashMap<String, ActorRef> registrants = subs.get(message.getClass().getName());

                    log.trace(dl.toString());

                    if (null != registrants) {
                        hasRegistrants = true;
                        _Publish publish = new _Publish(null, dl, new LinkedList<>(registrants.values().stream().toList()));
                        self.tell(publish, self);
                    }

                    if (logDeadLetters || ! hasRegistrants)
                        log.warn("Dead letter %s from %s to %s [hasRegistrants=%b]".formatted(dl.getMessage(), dl.getSender(), dl.getPath(), hasRegistrants));
                }

                case _Publish pub -> parentBehavior.onMessage(pub);

                default ->  log.error(String.format("Dead Letter actor got message %s", message));

            }
        };
    }
}
