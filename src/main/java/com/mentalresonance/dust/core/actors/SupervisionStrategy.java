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

import lombok.Getter;

/**
 * What to do when a child hits an error. Extend as needed. The strategies:
 *  - Stop - the Actor is stopped. It processes no more messages. Then postStop() is called and its
 *              children are recursively stopped.
 *  - Resume - onResume() is called and then message processing continues with the next available message
 *              after the message which caused the error
 *  - Restart - the Actor is stopped. postStop() is NOT called. Its children are recursively stopped. Then its
 *              parent recreates it with the same name (if randomly generated) and the same props used initially.
 *              Any ActorRef referencing the Actor will still be valid. When the Actor is initialising
 *              preRestart(Throwable t) will be called rather than preStart(). t is the error which caused the restart.
 * The modes:
 *  - MODE_ONE_FOR_ONE - the strategy applies to the failing Actor
 *  - MODE_ALL_FOR_ONE - the strategy applies to the failing Actor and its siblings
 *
 *   @author alanl
 */
@Getter
public class SupervisionStrategy {

    /** Stop the child on error */
    public final static int SS_STOP = ActorRef.LC_STOP;
    /** Resume the child on error */
    public final static int SS_RESUME = ActorRef.LC_RESUME;
    /** Restart the child on error */
    public final static int SS_RESTART = ActorRef.LC_RESTART;

    /** Apply the strategy only to the child causing error */
    public final static int MODE_ONE_FOR_ONE = 0;
    /** Apply the strategy to the child causing error and all its siblings */
    public final static int MODE_ALL_FOR_ONE = 1;

    final int mode;
    final int strategy;

    /**
     * Default strategy: stop the child with the error for any error
     */
    public SupervisionStrategy() {
        strategy = SS_STOP;
        mode = MODE_ONE_FOR_ONE;
    }

    /**
     * Custom strategy
     * @param strategy to use
     * @param mode to use
     */
    public SupervisionStrategy(int strategy, int mode) {
        this.strategy = strategy;
        this.mode = mode;
    }

    /**
     * To be overriden. Returns a strategy that may depend on the child and the error. The default is to return 
     * the default strategy which applies to all.
     * @param ref child this applies to
     * @param thrown error this applies to
     * @return the custom Strategy
     */
    protected SupervisionStrategy strategy(ActorRef ref, Throwable thrown) {
        return this;
    }

    /**
     * Convenient String for logging
     * @return The string
     */
    @Override
    public String toString()
    {
        String SMode = mode == MODE_ONE_FOR_ONE ? "One for one" : "All for one";
        String SStrategy = strategy == SS_RESTART ? "Restart" : strategy == SS_STOP ? "Stop" : "Resume";

        return String.format("Supervisor: mode=%s, strategy=%s", SMode, SStrategy);
    }
}

