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

import lombok.Getter;

import java.io.Serializable;

/**
 * Can be sent by a delegated Actor to its parent to both
 * stop it and the delegation and have the parent's new behavior receive the enclosed msg
 */
public class DelegatingMsg implements Serializable {

    /**
     * Optional message to be sent to the delegating Actor
     */
    @Getter
    final
    Serializable msg;

    /**
     * Constructor
     */
    public DelegatingMsg() {
        this.msg = null;
    }

    /**
     * Constructor
     * @param msg to be sent to delegating Actor
     */
    public DelegatingMsg(Serializable msg) {
        this.msg = msg;
    }
}
