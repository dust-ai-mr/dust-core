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
import java.io.Serializable;

/**
 * Sent by an Actor to itself after the child exception has been handled by the parent's SupervisionStrategy
 *
 *  @author alanl
 */
public record ChildExceptionMsg(ActorRef child, Throwable exception) implements Serializable {
    @Override
    public String toString() {
        return "ChildExceptionMsg from child: " + child + " exception: " + exception.getMessage();
    }
}
