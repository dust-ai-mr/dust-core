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

import com.mentalresonance.dust.core.actors.Actor;
import com.mentalresonance.dust.core.actors.ActorBehavior;
import com.mentalresonance.dust.core.actors.Cancellable;
import com.mentalresonance.dust.core.actors.Props;
import com.mentalresonance.dust.core.msgs.ProxyMsg;
import com.mentalresonance.dust.core.msgs.StartMsg;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Simple throttling Actor. Receives Proxy messages and every intervalMS sends one on.
 *
 *  @author alanl
 */
@Slf4j
public class ThrottlingRelayActor extends Actor {

    final Long intervalMS;
    final LinkedBlockingQueue<ProxyMsg> q = new LinkedBlockingQueue<>();
    final StartMsg START = new StartMsg();

    Cancellable pump;

    /**
     * Props
     * @param intervalMS - interval between send-on attempts in MS
     * @return Props
     */
    public static Props props(Long intervalMS) {
        return Props.create(ThrottlingRelayActor.class, intervalMS);
    }

    /**
     * Constructor
     * @param intervalMS minimum interval in ms between sending messages
     */
    public ThrottlingRelayActor(Long intervalMS) {
        this.intervalMS = intervalMS;
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
            switch(message) {
                case StartMsg msg -> {
                    ProxyMsg p;

                    if (null != (p = q.poll()))
                    {
                        p.setProxied(true);
                        if (null != p.target)
                            p.target.tell(p, p.getSender());
                        else
                            p.getSender().tell(p, p.getSender());
                    }
                        pump = scheduleIn(msg, intervalMS);
                    }
                case ProxyMsg msg -> {
                    q.add(msg);
                }

                default -> log.error(String.format("%s got not Proxy message %s", self.path, message));
            }
        };
    }
}
