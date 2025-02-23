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

package com.mentalresonance.dust.core.msgs;

import com.mentalresonance.dust.core.actors.ActorRef;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * A ReturnableMsg always knows who sent it so the receiving Actor does not have to track it. Useful in pipelines.
 *
 * @author alanl
 */
public class ReturnableMsg implements Serializable {
    /**
     * The sender of the message
     */
    @Setter
    @Getter
    ActorRef sender;

    /**
     * A useful flag for determining which way this message is going
     */
    protected boolean returning = false;

    /**
     * Constructor
     * @param sender of this message
     */
    public ReturnableMsg(ActorRef sender) { this.sender = sender; }

    /**
     * Replier can set this flag if they want
     */
    public void setReturning() { returning = true; }

    /**
     * Direction
     * @return true if on its way back
     */
    public boolean isReturning() { return returning; }
}
