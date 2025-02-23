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

package com.mentalresonance.dust.core.system.exceptions;

/**
 * Exception during ActorSelection creation
 * @author alanl
 */
public class ActorSelectionException extends Exception {

    /**
     * Constructor
     */
    public ActorSelectionException() {
        super();
    }

    /**
     * Actor Constructor
     * @param msg with this message
     */
    public ActorSelectionException(String msg) {
        super(msg);
    }
}
