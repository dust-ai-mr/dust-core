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

package com.mentalresonance.dust.core.actors.lib.entities;

import com.mentalresonance.dust.core.actors.ActorBehavior;
import com.mentalresonance.dust.core.actors.PersistentActor;
import com.mentalresonance.dust.core.actors.lib.entities.msgs.EntityStateMsg;
import com.mentalresonance.dust.core.msgs.SnapshotMsg;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.Objects;

/**
 * An EntityActor corresponds to a real-world entity (a car, a person, a relationship) therefore it has state.
 * It has a unique name which it is always created under but multiple instances may exist on different paths
 * (therefore with different state).
 *
 * @param <T> type of state
 *
 * @author alanl
 */
@Slf4j
public abstract class EntityActor<T extends Serializable> extends PersistentActor {

    /**
     * State defining the Entity
     */
    protected T state;

    /**
     * Constructor
     */
    public EntityActor() {}

    /**
     * Generally if an EntityActor is stopping it means it is at the end of its lifecycle and so should delete
     * its snapshot. If however we are shutting down then we want to do the opposite and ensure our state is saved.
     */
    @Override
    protected void postStop() {
        if (isInShutdown()) {
            saveSnapshot(state);
        }
        else {
            deleteSnapshot();
        }
    }
    /**
     * To be overriden. Supply name's initial state in a EntityStateMsg to be sent to myself. Typically this is
     * only done once - snapshots suffice afterwards.
     * @param name name of the Entity
     */
    protected void getState(String name) {
       self.tell(new EntityStateMsg<T>(null), self);
    }

    /**
     * To be overriden. Called after we receive an EntityStateMsg and have switched to our createBehaviour
     * @param name the name of the Entity
     */
    protected void postGetState(String name) {}

    protected ActorBehavior recoveryBehavior() {
        return message -> {
            if (Objects.requireNonNull(message) instanceof SnapshotMsg msg) {
                state = (T) msg.getSnapshot();
                if (null == state) {
                    become(waitForStateBehavior());
                    getState(self.name);
                } else {
                    become(createBehavior());
                    unstashAll();
                }
            } else {
                stash(message);
            }
        };
    }

    /**
     * Set behavior to waiting to receive state
     * @return {@link ActorBehavior}
     */
    protected ActorBehavior waitForStateBehavior() {
        return message -> {
            if (Objects.requireNonNull(message) instanceof EntityStateMsg msg) {
                state = (T) msg.getState();
                become(createBehavior());
                postGetState(self.name);
                unstashAll();
            } else {
                stash(message);
            }

        };
    }
}
