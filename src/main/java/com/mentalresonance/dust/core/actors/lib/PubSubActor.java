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
import com.mentalresonance.dust.core.msgs.PubSubMsg;
import com.mentalresonance.dust.core.msgs.Terminated;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;


/**
 * An Actor which other Actors can subscribe to in order to receive passed-on messages.
 * Actors register for subsets of messages by specifying message classes of interest.
 * (Un)Subscribing is done via a {@link PubSubMsg} message.
 *
 * Subscriptions are to classes of a given name.
 *
 * <b>The message being sent to subscribers is not cloned - it is up to the recipients to assume
 * other clients may have it as well and so proceed accordingly</b>
 *
 *  @author alanl
 */
@Slf4j
public class PubSubActor extends Actor {

    /**
     * Constructor
     */
    public PubSubActor() {}

    /**
     * Create Props for PubSubActor
     * @return Props
     */
    public static Props props() {
        return Props.create(PubSubActor.class);
    }

    /**
     * Maps fully qualified class name of message of interest to maps of registrants for that class.
     * Registrants are a map of their Actor Path -> ActorRef
     */
    final protected HashMap<String, HashMap<String, ActorRef>> subs = new HashMap<String, HashMap<String, ActorRef>>();

    /**
     * Default behavior. Handle {@link PubSubMsg}s by updating subs map. On receipt of a message whose
     * class has subscribers send ourselves an internal message (_Publish) with the subscribers and the message.
     * On receipt of a _Publish we take a subscriber off the list and send it the message then send the _Publish back
     * to us. Eventually stop when we run out of subscribers.
     *
     * @return {@link ActorBehavior}
     */
    protected ActorBehavior createBehavior() {
        return message -> {
            if (message instanceof PubSubMsg msg) {
                HashMap<String, ActorRef> registrants = subs.get(((PubSubMsg)message).fqn);

                log.trace("%s received subscription for %s from %s".formatted(self.path, ((PubSubMsg)message).fqn, sender.path));

                if (msg.subscribe) {
                    if (null == registrants) {
                        registrants = new HashMap<>();
                        registrants.put(sender.path, sender);
                        subs.put(msg.fqn, registrants);
                    }
                    else
                        registrants.computeIfAbsent(sender.path, k -> sender);
                    watch(sender);
                }
                else {
                    if (null != registrants) {
                        registrants.remove(sender.path);
                    }
                }
            }
            else if (message instanceof _Publish msg) {
                if (!msg.subs.isEmpty()) {
                    msg.subs.removeFirst().tell(msg.msg, msg.sender);
                    self.tell(msg, self);
                }
            }
            else if (message instanceof Terminated) {
                for (HashMap<String, ActorRef> map: subs.values()) {
                    map.remove(sender.path);
                }
            }
            else {
                HashMap<String, ActorRef> registrants = subs.get(message.getClass().getName());
                if (null != registrants) {
                    _Publish publish = new _Publish(sender, message, new LinkedList<>(registrants.values().stream().toList()));
                    self.tell(publish, self);
                }
            }
        };
    }

    /**
     * Internal message to distribute a subscribed message
     */
    static protected class _Publish implements Serializable {
        /**
         * Sender of the subscribed message
         */
        @Getter
        final
        ActorRef sender;

        /**
         * Subscribed message
         */
        @Getter
        final
        Serializable msg;
        /**
         * Subscribers
         */
        @Getter
        final
        LinkedList<ActorRef> subs;

        /**
         * Constructor
         * @param sender of the event
         * @param msg the event
         * @param subs list of subscribers to receive the message
         */
        public _Publish(ActorRef sender, Serializable msg, LinkedList<ActorRef> subs) {
            this.sender = sender;
            this.msg = msg;
            this.subs = subs;
        }
    }
}
