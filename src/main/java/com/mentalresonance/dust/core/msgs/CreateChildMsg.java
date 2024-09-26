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
@Getter
public class CreateChildMsg implements Serializable {
    /**
     * Name to be given to child
     */
    final String name;

    /**
     * Optional message to be sent to child
     */
    final Serializable msg;

    /**
     * Constructor
     * @param name of child
     */
    public CreateChildMsg(String name) {
        this.name = name;
        msg = null;
    }

    /**
     * Constructor
     * @param name of child
     * @param msg sent to the child after it creation
     */
    public CreateChildMsg(String name, Serializable msg) {
        this.name = name;
        this.msg = msg;
    }
}
