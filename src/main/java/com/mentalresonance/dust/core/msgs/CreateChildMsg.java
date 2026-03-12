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

import com.mentalresonance.dust.core.actors.lib.entities.PodManagerActor;
import lombok.Getter;

import java.io.Serializable;

/**
 * Recipient is to create a child with the given name. If msg is not null then send the new child the message. The
 * recipient is responsible for knowing what to create and usually is a {@link PodManagerActor}
 *
 *  @author alanl
 */
public record CreateChildMsg(String name, Serializable msg) implements Serializable {

    public CreateChildMsg(String name) {
        this(name, null);
    }

    /**
     * Useful to know the message if being handled by dead-letter/pod manager
     * @return
     */
    @Override
    public String toString() {
        return "CreateChildMsg Name:" + name + " Msg:" + msg;
    }

}

