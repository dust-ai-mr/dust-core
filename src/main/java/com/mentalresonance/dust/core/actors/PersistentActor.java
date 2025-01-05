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

import com.mentalresonance.dust.core.msgs.*;
import com.mentalresonance.dust.core.services.PersistenceService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.Objects;

/**
 * Base class for Actors which wish to save important state which can be restored on an Actor restart.
 * Uses an external database (managed through {@link PersistenceService}).
 * <p>PersistentActors have extended lifecycle management.</p>
 *
 *  @author alanl
 */
@Slf4j
public class PersistentActor extends Actor {
    /**
     * Set if we receive a (non-violent) kill. This enables PersistentActors to decide is they are
     * being stopped cleanly (which usually means to drop their state) or if they should retain their state
     * (and probably see if it needs further persisting before stopping).
     */
    @Getter
    @Setter
    private static boolean inShutdown = false;

    /**
     * Persistence service in use
     */
    protected PersistenceService persistenceService = null;

    /**
     * Constructor
     */
    public PersistentActor() {}


    /**
     * Unique id - can be overridden. This is used as the primary database key for the persisted state. The default
     * implementation returns the Actor's path.
     * @return - the unique Id.
     */
    protected String persistenceId() {
        return self.path;
    }
    /**
     * How snapshots are saved. The default is to use the persistence service defined in ActorSystem
     * which just uses the file system but this method can be overridden.

     * @return current system persistence service
     */
    protected PersistenceService getPersistence()  {
        return context.getSystem().getPersistenceService();
    }
    /**
     * Always called after delivery of Snapshot message even if its state  was null. Note that there are no guarantees
     * when this will be called so the Actor might be in recovery behavior still.
     * <p>After postRecovery() is called preStart() will be called as usual. The default postRecovery() does nothing</p>
     */
    protected void postRecovery() {
        // log.info("%s default postRecovery called".formatted(self.path));
    }
    /**
     * Save the object under persistenceId. Send myself  a SnapshotSuccessMsg or SnapshotFailureMsg
     * @param object - state to be saved.
     */
    protected void saveSnapshot(Serializable object) {
        Serializable msg = null;
        try {
            persistenceService.write(persistenceId(), object);
            msg = new SnapshotSuccessMsg();
        }
        catch (Exception e) {
            msg = new SnapshotFailureMsg(e);
        }
        finally {
            self.tell(msg, self);
        }
    }
    /**
     * Deletes my snapshot. Send myself a DeleteSnapshotSuccessMsg or DeleteSnapshotFailureMsg
     */
    protected void deleteSnapshot() {
        Serializable msg = null;
        try {
            persistenceService.delete(persistenceId());
            msg = new DeleteSnapshotSuccessMsg();
        }
        catch (Exception e) {
            msg = new DeleteSnapshotFailureMsg(e);
        }
        finally {
            self.tell(msg, self);
        }
    }
    /**
     * To be overriden. Return the Class of the state being snapshotted/recovered. If not null
     * then the read snapshot will attempt to force the read to be of this class else it uses
     * its heuristics to determine the class to instantiate
     * @return default null
     */
    protected Class<?> getSnapshotClass() {
        return null;
    }


    /*
     * We need to restore our state then call the (no persistent) Actor run
     */
    @Override
    public void run()
    {
        // Get state and chose the recovery behavior
        try {
            Class<?> clz;

            persistenceService = getPersistence();
            Serializable state = (null == (clz = getSnapshotClass())) ?
                    persistenceService.read(persistenceId()) :
                    persistenceService.read(persistenceId(), clz);

            recoveryBehavior().onMessage(new SnapshotMsg(state));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        self.lifecycle = ActorRef.LC_RECOVERED;
        super.run();
    }
    /**
     * The behavior which defines state recovery. This is the behavior when a PersistentActor is started and it
     * will be sent a {@link SnapshotMsg}. It is up to the implementor to access the state via snapshotmsg.getSnapshot()
     * which will be null if no previous persisted state exists.
     * <p>
     *     The default recoveryBehavior() throws away the snapshot and warns if any other message is received. Typically
     *     an implementation would stash() unhandled messages at this point.
     * </p>
     * <p>
     *     After restoring state the next step is usually to become(createBehavior) and unstasahall() messages.
     * </p>
     * @return the behavior
     */
    protected ActorBehavior recoveryBehavior() {
        return message -> {
            if (Objects.requireNonNull(message) instanceof SnapshotMsg msg) {
                log.warn("%s did not handle Snapshot Recovery %s".formatted(self.path, msg.getSnapshot()));
                become(createBehavior());
            } else {
                log.error("%s received unexpected message %s in recoveryBehavior".formatted(self.path, message));
            }
        };
    }

    /**
     * This absorbs snapshot related messages.
     * @return - behavior
     */
    @Override
    protected ActorBehavior createBehavior() {
        return message -> {
            switch(message)
            {
                case SnapshotSuccessMsg ignored -> {}

                case DeleteSnapshotSuccessMsg ignored -> {}

                case DeleteSnapshotFailureMsg fail ->
                        log.error("%s got unhandled DeleteSnapshotFailureMsg %s".formatted(self.path, fail.getException()));

                case SnapshotFailureMsg fail ->
                        log.error("%s got unhandled SnapshotFailureMsg %s".formatted(self.path, fail.getException()));

                default -> super.createBehavior().onMessage(message);
            }
        };
    }
}
