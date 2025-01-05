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
 * Msg to be sent to a pipeline. If it has the named stage the PieplineStage message
 * is unwrapped and msg is passed on to the stage (as though from sender)
 */
@Getter
public class PipelineStageMsg implements Serializable {
    /**
     * Name of the target stage
     */
    final String stage;
    /**
     * msg to be sent to target stage
     */
    final Serializable msg;

    /**
     * Constructor
     * @param stage name
     * @param msg to be sent to stage
     */
    public PipelineStageMsg(String stage, Serializable msg) {
        this.stage = stage;
        this.msg = msg;
    }

}
