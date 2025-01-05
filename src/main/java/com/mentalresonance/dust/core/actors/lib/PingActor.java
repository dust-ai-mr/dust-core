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
import com.mentalresonance.dust.core.actors.PoisonPill;
import com.mentalresonance.dust.core.actors.Props;
import com.mentalresonance.dust.core.msgs.PingMsg;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Just used for the default Ping handler. Sends a PingMsg back to its sender. If configured with a non-null parameter
 * then treats that as the max number of messages to respond to, then tell the sender to die and the die itself.
 *
 *  @author alanl
 */
@Slf4j
public class PingActor extends Actor {

    /**
     * Messages left
     */
    protected Integer remainingMessages;
    /**
     * How many to send
     */
    protected final Integer startMaxMessage;

    final Long started;

    /**
     * Create Props
     * @param maxMessage max number of messages to send
     * @return Props
     */
    public static Props props(Integer maxMessage) {
        return Props.create(PingActor.class, maxMessage);
    }

    /**
     * Constructor
     * @param maxMessage - if not null max number of messages before stopping
     */
    public PingActor(Integer maxMessage) {
        startMaxMessage = remainingMessages = maxMessage;
        started = System.currentTimeMillis();
    }

    @Override
    public void postStop() {
        log.info(String.format("%s finished in %d MS. Sent: %d messages", self.path, (System.currentTimeMillis() - started), startMaxMessage));
    }

    /**
     * Default behavior
     * @return {@link ActorBehavior}
     */
    @Override
    protected ActorBehavior createBehavior() {

        return message -> {

            if (Objects.requireNonNull(message) instanceof PingMsg msg) {
                if (null != remainingMessages) {
                    if (0 == remainingMessages--) {
                        sender.tell(new PoisonPill(), null);
                        stopSelf();
                        return;
                    }
                }
                if (null != sender)
                    sender.tell(msg, self);
            } else {
                log.warn(self.path + " unexpected message: " + message);
            }
        };
    }

}
