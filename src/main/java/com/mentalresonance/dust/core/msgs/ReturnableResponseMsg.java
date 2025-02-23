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

/**
 * A ReturnableResponse always knows who sent it so the receiving Actor does not have to track it and it contains
 * slots for a response or an Exception
 *
 * @param <T> type of response
 *
 * @author alanl
 */
public class ReturnableResponseMsg<T> extends ReturnableMsg {

    /**
     * Exception if one occurred
     */
    public Exception exception = null;
    /**
     * Response if success
     */
    public T response = null;

    /**
     * Constructor
     * @param sender claims he is sending this message
     */
    public ReturnableResponseMsg(ActorRef sender) { super(sender); }
}
