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

import lombok.Getter;

import java.io.Serializable;

/**
 * Handy message. What it means is up to the recipient
 *
 * @author alanl
 */
@Getter
public class StartMsg implements Serializable {

    /**
     * Handy (optional) message
     */
    final Serializable msg;

    /**
     * Constructor
     */
    public StartMsg() { msg = null; }

    /**
     * Constructor
     * @param msg wrapped msg
     */
    public StartMsg(Serializable msg) { this.msg = msg; }
}
