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
import com.mentalresonance.dust.core.msgs.StartMsg;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Simple throttling Actor. Receives arbitrary messages and passes them on after
 * waiting in a queue. See also {@link ThrottlingRelayActor}
 *
 *  @author alanl
 */
@Slf4j
public class ThrottlingWrapperActor extends Actor {

    final Long intervalMS;
    final LinkedBlockingQueue<QueuedMsg> q = new LinkedBlockingQueue<>();
    ActorRef target;
    Cancellable pump;

    /**
     * Props
     * @param intervalMS - interval between send-on attempts in MS
     * @return Props
     */
    public static Props props(Long intervalMS, ActorRef target) {
        return Props.create(ThrottlingWrapperActor.class, intervalMS, target);
    }

    /**
     * Constructor
     * @param intervalMS minimum interval in ms between sending messages
     * @param target place to send message on
     */
    public ThrottlingWrapperActor(Long intervalMS, ActorRef target) {
        this.intervalMS = intervalMS;
        this.target = target;
    }

    @Override
    public void preStart() {
        pump = scheduleIn(new StartMsg(), intervalMS);
    }

    @Override
    public void postStop() {
        pump.cancel();
    }


    /**
     * Default behavior
     * @return {@link ActorBehavior}
     */
    @Override
    protected ActorBehavior createBehavior() {
        return message -> {
            if (Objects.requireNonNull(message) instanceof StartMsg msg) {
                QueuedMsg m;
                if (null != (m = q.poll())) {
                    target.tell(m.msg(), m.sender());
                }
                pump = scheduleIn(msg, intervalMS);
            }
            else {
                q.add(new QueuedMsg(message, sender));
            }
        };
    }

    private record QueuedMsg(Serializable msg, ActorRef sender) {}
}
