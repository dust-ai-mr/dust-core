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
import com.mentalresonance.dust.core.actors.ActorRef;
import com.mentalresonance.dust.core.actors.Props;
import com.mentalresonance.dust.core.msgs.DelegatingMsg;
import com.mentalresonance.dust.core.msgs.YesNoMsg;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Like it says on the can. Waits to receive a String which it parses into a yes or no
 * and sends the response back as a YesNoMsg to its Delegator. Ignores all other messages
 *
 *  @author alanl
 */
@Slf4j
public class DelegatedYesNoActor extends Actor {

    final ActorRef delegator;

    /**
     * Create props
     * @param delegator explicit delegator. If null then delegator is assumed to be parent
     * @return Props
     */
    public static Props props(ActorRef delegator) {
        return Props.create(DelegatedYesNoActor.class, delegator);
    }

    /**
     * Create props. Delegator is assumed to be parent
     * @return Props
     */
    public static Props props() {
        return Props.create(DelegatedYesNoActor.class, (Object) null);
    }

    /**
     * Constructor
     * @param delegator The delegator (or null)
     */
    public DelegatedYesNoActor(ActorRef delegator) {
        this.delegator = delegator;
    }
    /**
     * Default behavior
     * @return {@link ActorBehavior}
     */
    @Override
    protected ActorBehavior createBehavior() {
        return message -> {
            if (Objects.requireNonNull(message) instanceof String msg) {
                DelegatingMsg dm = new DelegatingMsg(new YesNoMsg(msg.trim().equalsIgnoreCase("yes")));
                if (null != delegator)
                    delegator.tell(dm, self);
                else
                    parent.tell(dm, self);
                stopSelf();
            }
        };
    }
}
