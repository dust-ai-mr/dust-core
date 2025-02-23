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

/**
 * If an Actor is stopping because it threw an exception the tree of child Actors will
 * be given this exception as though they had thrown it themselves. Thus a child Actor can
 * tailor its postStop() behavior (e.g. not deleting state if a persistent Actor) based on this.
 *
 * So if an Actor sees this Exception it knows 'cause' got thrown by an ancestor.
 */
package com.mentalresonance.dust.core.actors;

import lombok.Getter;

@Getter
public class ParentException extends Exception {

    /**
     * The original sin
     */
    Throwable cause;

    public ParentException(Throwable cause) {
        this.cause = cause;
    }
}
