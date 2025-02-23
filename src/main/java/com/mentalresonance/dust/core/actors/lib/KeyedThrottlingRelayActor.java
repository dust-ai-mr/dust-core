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

package com.mentalresonance.dust.core.actors.lib;

import com.mentalresonance.dust.core.actors.*;
import com.mentalresonance.dust.core.msgs.ProxyMsg;
import com.mentalresonance.dust.core.system.exceptions.ActorInstantiationException;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Manages a collection of Throttling Actors indexed by the key() in the proxy message.
 * If key() is null or is not registered then message is sent to a default Throttling Actor.
 *
 *  @author alanl
 */
@Slf4j
public class KeyedThrottlingRelayActor extends Actor {

    final Long defaultIntervalMS;
    final HashMap<String, ActorRef> throttlers = new HashMap<>();
    final HashMap<String, Long> initThrottlers;
    ActorRef defaultThrottler;

    /**
     * Create props
     * @param defaultIntervalMS - rate of throttling for default throttler
     * @param throttlers - map of key -> throttler interval
     * @return the PRops
     */
    public static Props props(Long defaultIntervalMS, Map<String, Long> throttlers) {
        return Props.create(KeyedThrottlingRelayActor.class, defaultIntervalMS, throttlers);
    }

    /**
     * Constructor
     * @param defaultIntervalMS minimum interval between emssion of messages
     * @param throttlers map of throttler key -> throttler
     */
    public KeyedThrottlingRelayActor(Long defaultIntervalMS, Map<String, Long> throttlers) {
        this.defaultIntervalMS = defaultIntervalMS;
        this.initThrottlers = new HashMap<>(throttlers);
    }

    @Override
    public void preStart() throws ActorInstantiationException {
        defaultThrottler = actorOf(ThrottlingRelayActor.props(defaultIntervalMS), "defaultthrottler");
        for(String key: initThrottlers.keySet()) {
            ActorRef throttler = actorOf(ThrottlingRelayActor.props(initThrottlers.get(key)));
            throttlers.put(key, throttler);
        }

    }
    /**
     * Default behavior
     * @return {@link ActorBehavior}
     */
    @Override
    protected ActorBehavior createBehavior() {
        return message -> {
            if (Objects.requireNonNull(message) instanceof ProxyMsg msg) {
                String key = msg.key();
                ActorRef throttler = (null == key) ? defaultThrottler : throttlers.getOrDefault(key, defaultThrottler);
                throttler.tell(msg, sender);
            } else {
                log.error(String.format("%s got not Proxy message %s", self.path, message));
            }
        };
    }
}
