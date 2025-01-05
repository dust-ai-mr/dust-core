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

package com.mentalresonance.dust.core.actors.lib.entities.msgs;

import lombok.Getter;

import java.io.Serializable;

/**
 * Sent to an Entity waiting to receive it to acquire its state
 * @param <T> type of state
 *
 * @author alanl
 */
@Getter
public class EntityStateMsg<T> implements Serializable {
    /**
     * Recovered state
     */
    final T state;

    /**
     * Message to recover Entity state
     * @param state the state to be recovered
     */
    public EntityStateMsg(T state) { this.state = state; }
}
