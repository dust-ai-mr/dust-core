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
 * Sent to a persistent Actor during recovery process. It contains the last persisted snapshot data dn  is always sent
 * even if snapshot is null - indicating nothing was previously saved or what was saved was null. There is no
 * way to distinguish between these two cases
 *
 * @author alanl
 */
public class SnapshotMsg implements Serializable {
    /**
     * The persisted snapshot
     */
    @Getter
    final Serializable snapshot;

    /**
     * Constructor
     * @param snapshot recovered data
     */
    public SnapshotMsg(Serializable snapshot) {
        this.snapshot = snapshot;
    }
}
