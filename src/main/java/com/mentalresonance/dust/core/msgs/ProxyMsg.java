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

package com.mentalresonance.dust.core.msgs;

import com.mentalresonance.dust.core.actors.ActorRef;
import com.mentalresonance.dust.core.ifc.IKeyed;
import lombok.Getter;
import lombok.Setter;

/**
 * A proxy message is one that might be sent to an intermediate Actor (e.g. a throttler) but then needs to be sent
 * on to the target.
 *
 * The Proxy does whatever it does to the message then sends it to target as though from sender. If target is null
 * it sends it back to sender instead
 *
 * @author alanl
 */
public class ProxyMsg extends ReturnableMsg implements IKeyed {

    /**
     * Has this message been through a proxy
     */
    @Setter
    @Getter
    boolean proxied = false;
    /**
     * Eventual target of the message.
     */
    public ActorRef target = null;

    /**
     * Constructor
     * @param sender of this message
     */
    public ProxyMsg(ActorRef sender) {
        super(sender);
    }

    /**
     * Constructor
     * @param sender of this message
     * @param target of this message
     */
    public ProxyMsg(ActorRef sender, ActorRef target) {
        super(sender);
        this.target = target;
    }

}
