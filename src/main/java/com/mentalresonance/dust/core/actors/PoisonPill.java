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

package com.mentalresonance.dust.core.actors;

import java.io.Serializable;

/**
 * A message which, when processed by an Actor will cause it to do a stop(self).
 * Note This is just a regular message so only when pulled from the recipient's
 * mailbox does it stop the Actor.
 *
 *  @author alanl
 */
public class PoisonPill implements Serializable {
    /**
     * Constructor
     */
    public PoisonPill() {}
}

