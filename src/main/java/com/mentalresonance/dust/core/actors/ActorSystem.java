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

package com.mentalresonance.dust.core.actors;

import com.mentalresonance.dust.core.net.TCPObjectServer;
import com.mentalresonance.dust.core.services.PersistenceService;
import com.mentalresonance.dust.core.net.ActorSystemConnectionManager;
import com.mentalresonance.dust.core.net.ActorSystemConnectionManager.WrappedTCPObjectSocket;
import com.mentalresonance.dust.core.system.GuardianActor;
import com.mentalresonance.dust.core.system.exceptions.ActorInstantiationException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
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

    final
    String host;

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

    private TCPObjectServer server = null;

    /**
     * Manage connection pool to remote Actor Systems
     */
    final ActorSystemConnectionManager actorSystemConnectionManager;

    /**
     * We might stop the ActorSystem but then get a shutdown message (via the shutdown hook) at a later data
     * (e.g. during tests) so we use isStopped to determined that we do actually think we have stopped
     * and do not try again.
     */
    @Getter
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

        this.host = "localhost";
        this.name = name;
        this.port = port;
        systemLength = name.length() + 1;
        actorSystemConnectionManager = new ActorSystemConnectionManager(this);
        init(logDeadLetters);
        log.info("Started ActorSystem: " + name + " on port " + port + " host: " + host);
    }

    /**
     * Create remoting Actor system with name on port
     * @param host           host address for remoting
     * @param name           unique (on this host) actor name
     * @param port           on this port
     * @param logDeadLetters if true log dead letters
     * @throws InvocationTargetException creating core service Actors
     * @throws NoSuchMethodException creating core service Actors
     * @throws InstantiationException creating core service Actors
     * @throws IllegalAccessException creating core service Actors
     * @throws ActorInstantiationException creating core service Actors
     */
    public ActorSystem(String host, String name, Integer port, boolean logDeadLetters)
        throws InvocationTargetException, NoSuchMethodException, InstantiationException,
        IllegalAccessException, ActorInstantiationException {

        this.host = host;
        this.name = name;
        this.port = port;
        systemLength = name.length() + 1;
        actorSystemConnectionManager = new ActorSystemConnectionManager(this);
        init(logDeadLetters);
        log.info("Started ActorSystem: " + name + " on port " + port + " host: " + host);
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
                context.hostContext = String.format("dust://%s:%d/%s", host, port, name);
                haveStopped = runServer(port, actorSystemConnectionManager, this);
            } catch (IOException e) {
                log.error(String.format("Cannot start server on host %s port %d", host, port));
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

    private Guardian startGuardian(ActorRef ref) {

        Actor actor = guardianRef.actor;
        ref.mailBox = new Actor.MailBox();
        ref.thread = Thread.startVirtualThread(actor);
        ref.thread.setName("GuardianActor");
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
    public boolean stop(boolean inShutdown) {

        isStopping = true;

        PersistentActor.setInShutdown(inShutdown);

        log.info("Stopping ActorSystem: " + name + " on port " + port);
        if (null != port) {
            log.info("Stopping server");
            try {
                server.stop();
                haveStopped.get(5, TimeUnit.SECONDS);
                actorSystemConnectionManager.shutdown();
                log.info("Server shut down");
            }
            catch (Exception e) {
                log.error("Could not stop server: %s".formatted(e.getMessage()));
                e.printStackTrace();
            }
        }
        log.info("Stopping Guardian");
        context.stop(guardianRef);
        try {
            guardianRef.waitForDeath();
            log.info("Guardian stopped");
        } catch (Exception e) {
            log.warn("Stopping guardian ref resulted in exception .. ignoring");
        }

        isStopped = true;

        if (null != stopping) {
            log.info("Running stopping()");
            stopping.run();
        }
        log.info("Stopped");
        return true;
    }

    /**
     * Default stop. inShutdown is true so Actors will keep their state for next time
     */
    public boolean stop() { return stop(true); }

    /**
     * Run server on port with context /<actor-system-name>
     *
     * @param port                         - the port
     * @param actorSystemConnectionManager connection manager so we can clean up easily
     * @return Future which completes when server stops
     * @throws IOException
     */
    CompletableFuture<Boolean> runServer(
        int port,
        ActorSystemConnectionManager actorSystemConnectionManager,
        ActorSystem actorSystem
    ) throws IOException
    {
        CompletableFuture<Boolean> haveStopped = new CompletableFuture<>();

        server = new TCPObjectServer(
            port,
            actorSystemConnectionManager,
            haveStopped
        );

        this.port = port;
        server.start(actorSystem);
        return haveStopped;
    }

    public void connectionAccepted(SentMessage msg, TCPObjectServer server) {
        Object o = null;
        try {
            try {
                /*
                 * path is /system/...
                 */
                String path = new URI(msg.remotePath()).getPath().substring(systemLength);
                ActorRef sender = (null != msg.sender()) ? msg.sender().remotify() : null;
                ActorRef target = context.actorSelection(path);

                if (null == target) {
                    target = context.getDeadLetterActor();
                    target.setIsDeadLetter(true);
                }
                if (null != sender) {
                    sender = context.actorSelection(sender.path);
                }
                target.tell(msg.message(), sender);
            }
            catch (Exception e) {
                log.error("Error in server(): {}", e.getMessage());
            }

        }
        catch (Exception e) {
            log.error("Error in outer server(): {} - {}", e.getMessage(), o);
        }
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

    /**
     * Something has gone wrong. Try to close all connections to Actor system
     * defined by URI.
     */
    public void flushPool(URI uri) throws Exception {
        actorSystemConnectionManager.flushPool(uri);
    }

    private static class Guardian {
        final ActorRef ref;
        final GuardianActor actor;

        public Guardian(ActorRef ref, GuardianActor actor) {
            this.ref = ref;
            this.actor = actor;
        }
    }

    @Override
    public String toString() {
        return "ActorSystem: " + name;
    }
}
