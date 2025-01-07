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

import com.mentalresonance.dust.core.net.CoreTCPObjectServer;
import com.mentalresonance.dust.core.services.PersistenceService;
import com.mentalresonance.dust.core.services.SerializationService;
import com.mentalresonance.dust.core.system.ActorSystemConnectionManager;
import com.mentalresonance.dust.core.system.ActorSystemConnectionManager.WrappedTCPObjectSocket;
import com.mentalresonance.dust.core.system.GuardianActor;
import com.mentalresonance.dust.core.system.exceptions.ActorInstantiationException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.nustaq.net.TCPObjectServer;
import org.nustaq.net.TCPObjectSocket;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * An Actor System. Sets up the Guardian Actor '/' and a dust: server if requested
 *
 * @author alanl
 */
@Slf4j
public class ActorSystem {

    @Getter
    @Setter
    private PersistenceService persistenceService = null;

    @Getter
    final
    String name;

    final int systemLength; // For trimming system off path

    @Getter
    ActorContext context;

    private ActorRef guardianRef;

    @Getter
    Integer port = null;

    CompletableFuture<Boolean> haveStopped = null;

    public static boolean isStopping = false;

    /**
     * Optional runnable to be run after ActorSystem shuts down
     */
    @Setter
    Runnable stopping = null;

    private CoreTCPObjectServer server = null;

    /**
     * Manage connection pool to remote Actor Systems
     */
    final ActorSystemConnectionManager actorSystemConnectionManager;

    /**
     * We might stop the ActorSystem but then get a shutdown message (via the shutdown hook) at a later data
     * (e.g. during tests) so we use isStopped to determined that we do actually think we have stopped
     * and do not try again.
     */
    private boolean isStopped = false;

    /**
     * Create local only Actor system
     *
     * @param name - ActorSystem name
     * @throws ActorInstantiationException creating core service Actors
     * @throws IOException creating core service Actors
     * @throws InvocationTargetException creating core service Actors
     * @throws NoSuchMethodException creating core service Actors
     * @throws InstantiationException creating core service Actors
     * @throws IllegalAccessException creating core service Actors
     */
    public ActorSystem(String name)
            throws ActorInstantiationException, IOException, InvocationTargetException,
            NoSuchMethodException, InstantiationException, IllegalAccessException {
        this(name, null, true);
    }

    /**
     * Create local only Actor system
     *
     * @param name           - actorSystem name
     * @param logDeadLetters - if tru then dead letter deliveries are logged
     * @throws ActorInstantiationException creating core service Actors
     * @throws IOException creating core service Actors
     * @throws InvocationTargetException creating core service Actors
     * @throws NoSuchMethodException creating core service Actors
     * @throws InstantiationException creating core service Actors
     * @throws IllegalAccessException creating core service Actors
     */
    public ActorSystem(String name, boolean logDeadLetters)
            throws ActorInstantiationException, IOException, InvocationTargetException,
            NoSuchMethodException, InstantiationException, IllegalAccessException {
        this(name, null, logDeadLetters);
    }

    /**
     * Create remoting Actor system with name on port
     *
     * @param name unique (on this host) actor name
     * @param port on this port
     * @throws InvocationTargetException creating core service Actors
     * @throws NoSuchMethodException creating core service Actors
     * @throws InstantiationException creating core service Actors
     * @throws IllegalAccessException creating core service Actors
     * @throws ActorInstantiationException creating core service Actors
     */
    public ActorSystem(String name, Integer port)
            throws InvocationTargetException, NoSuchMethodException, InstantiationException,
            IllegalAccessException, ActorInstantiationException {

        this(name, port, true);
    }

    /**
     * Create remoting Actor system with name on port
     *
     * @param name           unique (on this host) actor name
     * @param port           on this port
     * @param logDeadLetters if true log dead letters
     * @throws InvocationTargetException creating core service Actors
     * @throws NoSuchMethodException creating core service Actors
     * @throws InstantiationException creating core service Actors
     * @throws IllegalAccessException creating core service Actors
     * @throws ActorInstantiationException creating core service Actors
     */
    public ActorSystem(String name, Integer port, boolean logDeadLetters)
            throws InvocationTargetException, NoSuchMethodException, InstantiationException,
            IllegalAccessException, ActorInstantiationException {

        this.name = name;
        this.port = port;
        systemLength = name.length() + 1;
        actorSystemConnectionManager = new ActorSystemConnectionManager();
        init(logDeadLetters);
        log.info("Started ActorSystem: " + name + " on port " + port);
    }

    /**
     * Returns the thread the Guardian Actor is on. This allows us to 'join' this thread
     * and so wait until the Actor system has shut down.
     *
     * @return Guardian Actor mailbox thread
     */
    public Thread systemThread() {
        return guardianRef.thread;
    }

    private void init(boolean logDeadLetters)
            throws InvocationTargetException, NoSuchMethodException, InstantiationException,
            IllegalAccessException, ActorInstantiationException {

        context = new ActorContext(this);

        guardianRef = new ActorRef("", "", context,  GuardianActor.class.getDeclaredConstructor().newInstance());
        Guardian guardian = startGuardian(guardianRef);

        context.setGuardianActor(guardianRef);
        guardian.actor.setContext(context);
        guardian.actor.init(logDeadLetters);

        if (null != port) {
            try {
                context.hostContext = String.format("dust://localhost:%d/%s", port, name);
                haveStopped = runServer(port, actorSystemConnectionManager);
            } catch (IOException e) {
                log.error(String.format("Cannot start server on port %d", port));
            }
        }

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> {
                    if (!isStopped) {
                        log.warn("GOT SHUTDOWN - stopping !");
                        stop();
                    }
                }
        ));
    }

    private Guardian startGuardian(ActorRef ref)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException,
            IllegalAccessException {

        Actor actor = guardianRef.actor;
        ref.mailBox = new Actor.MailBox();
        ref.thread = Thread.startVirtualThread(actor);
        actor.setParent(null);
        actor.setSelf(ref);

        return new Guardian(ref, (GuardianActor) actor);
    }

    /**
     * Stop the Actor system. If we are remoting this means stopping the servers
     * If stopping (closure) is defined run it last. <b>Note</b> that shutdown is asynchronous
     * so while stopping is called last it is not guaranteed that the ActorSystem is completely shut down
     * (i.e. the entire Actor tree is stopped)
     *
     * @param inShutdown flag to indicate we are in clean shutdown. PersistentActors should not delete their state. If
     * false then they probably will delete their state
     */
    public void stop(boolean inShutdown) {

        isStopping = true;

        PersistentActor.setInShutdown(inShutdown);
        context.stop(guardianRef);

        try {
            guardianRef.waitForDeath();
        } catch (Exception e) {
            log.warn("Stopping guardian ref resulted in exception .. ignoring");
        }

        if (null != port) {
            try {
                server.stop();
                haveStopped.get(5, TimeUnit.SECONDS);
                actorSystemConnectionManager.shutdown();
                log.info(String.format("Actor system '%s' shut down", name));
            }
            catch (Exception e) {
                log.error("Could not stop server: %s".formatted(e.getMessage()));
                e.printStackTrace();
            }
        }

        isStopped = true;
        if (null != stopping)
            stopping.run();
    }

    /**
     * Default stop. inShutdown is true so Actors will keep their state for next time
     */
    public void stop() { stop(true); }

    /**
     * Run server on port with context /<actor-system-name>
     *
     * @param port                         - the port
     * @param actorSystemConnectionManager connection manager so we can clean up easily
     * @return Future which completes when server stops
     * @throws IOException
     */
    CompletableFuture<Boolean> runServer(int port, ActorSystemConnectionManager actorSystemConnectionManager) throws IOException {
        CompletableFuture<Boolean> haveStopped = new CompletableFuture<>();

        server = new CoreTCPObjectServer(
                SerializationService.getFstConfiguration(),
                port,
                actorSystemConnectionManager,
                haveStopped
        );

        this.port = port;

        server.start(new TCPObjectServer.NewClientListener() {

            @Override
            public void connectionAccepted(TCPObjectSocket client) {
                boolean running = true;
                try {
                    while (running) // Sit on the opened connection
                    {
                        SentMessage msg = (SentMessage) client.readObject();
                        /*
                         * We are either stopping the server or just closing this connection.
                         * (Which it is depends on the server terminated flag)
                         */
                        if (null == msg) {
                            actorSystemConnectionManager.closeRemoteSocket(client.getSocket());
                            running = false;
                        }
                        else {
                            try {
                                /*
                                 * path is /system/...
                                 */
                                String path = new URI(msg.remotePath).getPath().substring(systemLength);
                                ActorRef sender = (null != msg.sender) ? msg.sender.remotify() : null;
                                ActorRef target = context.actorSelection(path);

                                // log.info("ActorSystem received: " + msg.getMessage() + " from " + sender + " to be sent to " + target);
                                if (null == target) {
                                    target = context.getDeadLetterActor();
                                    target.setIsDeadLetter(true);
                                }
                                if (null != sender) {
                                    sender = context.actorSelection(sender.path);
                                }
                                target.tell(msg.message, sender);
                            } catch (Exception e) {
                                log.error(String.format("Error in server(): %s", e.getMessage()));
                            }
                        }
                    }
                } catch (Exception e) {
                    // log.error(String.format("Error in outer server(): %s", e.getMessage()));
                }
            }
        });
        return haveStopped;
    }

    /**
     * Get a connection to the ActorSystem at the remote path
     *
     * @param uri for remote system
     * @return wrapped Connection
     * @throws IOException on io issues
     * @throws InterruptedException if interrupted
     */
    public WrappedTCPObjectSocket getSocket(URI uri) throws IOException, InterruptedException {
        return actorSystemConnectionManager.getSocket(uri);
    }

    /**
     * Returns wrapped socket to the pool
     *
     * @param objectSocket to return to pool
     */
    public void returnSocket(WrappedTCPObjectSocket objectSocket) {
        actorSystemConnectionManager.returnSocket(objectSocket);
    }

    private static class Guardian {
        final ActorRef ref;
        final GuardianActor actor;

        public Guardian(ActorRef ref, GuardianActor actor) {
            this.ref = ref;
            this.actor = actor;
        }
    }
}
