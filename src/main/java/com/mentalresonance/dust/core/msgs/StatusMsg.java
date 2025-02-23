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

import lombok.Getter;

import java.io.Serializable;

/**
 * A simple class for delivering generic status. Useful when a 'client' Actor needs to know a 'server'
 * has fulfilled the request for synchronization or other reasons
 */
@Getter
public class StatusMsg implements Serializable {
    /**
     * Optional 'marker' so request/response can be aligned
     */
    Serializable tag = null;
    /**
     * true or not
     */
    boolean success;
    /**
     * Optional accompanying message
     */
    String message = null;

    public StatusMsg(boolean success) {
        this.success = success;
    }

    public StatusMsg(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public StatusMsg(boolean success, String message, Serializable tag) {
        this.success = success;
        this.message = message;
        this.tag = tag;
    }
}
