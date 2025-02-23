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

import com.mentalresonance.dust.core.msgs.DeadLetter;
import com.mentalresonance.dust.core.msgs.UnWatchMsg;
import com.mentalresonance.dust.core.msgs.WatchMsg;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.nustaq.net.TCPObjectSocket;
import java.io.Serializable;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.mentalresonance.dust.core.system.ActorSystemConnectionManager.WrappedTCPObjectSocket;


/**
 * Access to an Actor instance. Generally there will be one instance of this which is shared. This instance is the
 * 'self' variable of the Actor itself.
 *
 * @author alanl
 */
@Slf4j
public class ActorRef implements Serializable {

    final transient ActorContext context;

    transient Actor actor;

    /**
     * If I am telling remotely then recipient needs to be able to find me via host + path
     */
    String host = null;

    /**
     * If true then this is actually a ref to dead letters (i.e. path does not exist.
     * So tell()s will wrap the message in a DeadLetter
     */
    @Setter
    Boolean isDeadLetter = false;

    /**
     * Set if I took an exception. postStop() is called even on an exception and this
     * can sometimes be unwanted (e.g. a persistent Actor may destroy its state in a postStop on the assumption
     * it is no longer needed.
     */
    public Throwable isException = null;

    /**
     * The thread running the Actor if local else null
     */
    public transient Thread thread = null;

    /**
     * Mailbox if local else null
     */
    public transient Actor.MailBox mailBox = null;

    /**
     * Path down to root context /. Always ends in '/'
     */
    public String path;
    /**
     * Array of names from / down to me
     */
    public String[] ancestors;
    /**
     * Name of Actor if local else null
     */
    public String name = null;

    /**
     * Props that created this Actor if local else null
     */
    public transient Props props;

    /**
     * The exception that caused the restart
     */
    public transient Throwable restartCause = null;

    /**
     * Where I am in my lifecycle. My Actor checks this when starting up (to see if it is starting or restarting) and
     * when resuming after an Exception in its message processing loop.
     */
    public transient Integer lifecycle = LC_START;

    /**
     * Conventional start
     */
    public final static int LC_START = 0;
    /**
     * This is a restart. restartCause will have the error.
     */
    public final static int LC_RESTART = 1;
    /**
     * I am to stop on resumption of my thread
     */
    public final static int LC_STOP = 2;
    /**
     * I am to resume message processing
     */
    public final static int LC_RESUME = 3;
    /**
     * I have completed recovery
     */
    public final static int LC_RECOVERED = 4;
    /**
     * Will convert to a LC_RESUME on interrupt handling
     */
    public final static int LC_INTERRUPT_RESUME = 5;
    /**
     * Will convert to a LC_RESTART on interrupt handling
     */
    public final static int LC_INTERRUPT_RESTART = 6;

    /**
     * Construct ActorRef in the context to Actor at path
     * @param path of Actor
     * @param context to use
     */
    public ActorRef(String path, ActorContext context, Actor actor) {
        this.path = path;
        this.context = context;
        this.host = context.hostContext;
        this.actor = actor;
        makeAncestors();
    }

    /**
     * Construct ActorRef with given name and parent path in context
     * @param parentPath path to parent (who is doing the creating)
     * @param name of constructed Actor
     * @param context to use
     */
    public ActorRef(String parentPath, String name, ActorContext context, Actor actor) {
        this.path = parentPath + name + "/";
        this.context = context;
        this.name = name;
        this.host = context.hostContext;
        this.actor = actor;
        makeAncestors();
    }

    /**
     * Send the message to the Actor at this ActorRef marking the sender accordingly
     * @param message to be sent
     * @param sender as though by this sender
     * @return true if no error on send, else false. Does not indicate successful receipt.
     */
    public boolean tell(Serializable message, ActorRef sender) {
        boolean success = true;

        // log.trace("Delivering {} to {} mailbox from {}", message, this, sender);

        try {
            SentMessage sentMessage;

            /*
             * If isDeadletter then I know I have a live mailbox (the DeadLetterActor mailbox) so I simply wrap the
             * message in a DeadLetter message and send it off
             */
            if (isDeadLetter) {
                log.trace("{} is dead letter mailbox", this);
                message = new DeadLetter(message, path, sender);
            }

            sentMessage = new SentMessage(message, sender);

            if (mailBox != null) { // Local
                if (! mailBox.dead) {
                    // log.trace("Adding:{} to mailbox:{}  queue presize={}", message, this, mailBox.queue.size());
                    mailBox.queue.add(sentMessage);
                }
                else {
                    log.trace("{} mailbox is dead .. restarted ??", this);
                    if (!PersistentActor.isInShutdown()) { // May be in shutdown but false -- need to fix this
                        ActorRef deadLetterRef = context.getDeadLetterActor();
                        if (deadLetterRef != null && !deadLetterRef.mailBox.dead) { // We may be globally stopping
                            deadLetterRef.tell(new DeadLetter(sentMessage.message, path, sender), null);
                        }
                    }
                }
            }
            else {
                if (! path.contains(":"))
                    path = host + path;

                WrappedTCPObjectSocket wrappedTCPObjectSocket = null;

                try {
                    TCPObjectSocket socket;
                    sentMessage.remotePath = path;
                    wrappedTCPObjectSocket = context.system.getSocket(new URI(path));
                    socket = wrappedTCPObjectSocket.tcpObjectSocket;
                    // log.trace("ActorSystem sending: " + message + " to " + path + " [Remote]");
                    socket.writeObject(sentMessage);
                    socket.flush();
                }
                catch (Exception e) {
                    log.error("Could not get socket: " + e.getMessage());
                    success = false;
                }
                finally {
                    if (null != wrappedTCPObjectSocket)
                        context.system.returnSocket(wrappedTCPObjectSocket);
                }
            }
        }
        catch (Throwable e) {
            log.error(e.getMessage());
            e.printStackTrace();
            success = false;
        }
        return success;
    }

    /**
     * Turn the absolute (reversed) path to an Actor to a Completable Future which contains
     * the corresponding ActorRef
     * @param path reverse path segments to
     * @return Completable future of corresponding Actor ref
     */
    protected CompletableFuture<ActorRef> resolve(List<String> path) {
        CompletableFuture<ActorRef> ref = new CompletableFuture<>();
        resolve(path, ref);
        return ref;
    }

    private void resolve(List<String> path, CompletableFuture<ActorRef> ref) {
        // log.trace("Ref {} got resolve message {}", this.path, path);
        if (path.isEmpty()) {
            if (!ref.complete(this)) {
                log.error("Resolved path " + path + " did not complete properly");
            }
        }
        else {
            String next = path.removeFirst();
            try {
                ActorRef child = actor.children.get(next);

                if (null == child) {
                    // log.trace("Resolving: {} is not a child of {}", next, path);
                    ref.complete(null);
                } else {
                    // log.trace("Forwarding resolve to {} which is  a child of {}", child, path);
                    child.resolve(path, ref);
                }
            } catch(Exception e) {
                log.error("Resolution error: {}", e.getMessage());
            }
        }
    }

    /**
     * Make sure this ActorRef contains the host in its path
     * @return itself with possibly modified path
     */
    ActorRef remotify() {
        if (! path.contains(":"))
            path = host + path;
        return this;
    }

    /**
     * Adds my Actor to those going to be informed (via a Terminated message) when the Watchee stops.
     * @param watchee he who is watched
     */
    public void watch(ActorRef watchee) {
        watchee.tell(new WatchMsg(), this);
    }

    /**
     * Removes my Actor from those going to be informed (via a Terminated message) when the Watchee stops.
     * @param watchee he who is watched
     */
    public void unwatch(ActorRef watchee) {
        watchee.tell(new UnWatchMsg(), this);
    }

    /**
     * Is this reference to the dead letter Actor
     * @return true if I am the dead letter Actor
     */
    public boolean isDeadLetter() { return isDeadLetter; }

    /**
     * Block calling thread until this Actor has stopped. Note that thread interruption is part of the natural
     * operation of shutting down an Actor and so an interrupt should never propagate to here. If it does
     * it is an error ...
     *
     * It is also an error to waitForDeath on a remote Actor (for now).
     * @throws Exception if Actor is remote, which is not supported at the moment
     */
    public void waitForDeath() throws Exception {
        try {
            if (null == thread) {
                throw new Exception("%s is remote. Cannot waitForDeath on remote Actors".formatted(path));
            }
            thread.join();
        }
        catch (InterruptedException e) {
            log.error("%s waitForDeath was interrupted !!".formatted(path));
        }
    }

    @Override
    public String toString() {
        return path;
    }

    /**
     * My parent's name
     * @return my parent's name
     */
    public String parentName() {
        return ancestors[ancestors.length - 2];
    }
    /**
     * My granadparent's name
     * @return my grandparent's name
     */
    public String grandParentName() {
        return ancestors[ancestors.length - 3];
    }
    /**
     * My great-grandparents name
     * @return my greatgrandparent's name
     */
    public String greatGrandParentName() {
        return ancestors[ancestors.length - 4];
    }

    private void makeAncestors() {
        String newpath = path;

        if ("/".equals(path)) {
            ancestors = new String[0];
            return;
        }
        else if (path.endsWith("/"))
            newpath = path.substring(0, path.length()-1);

        ancestors = newpath.split("/");
    }
}
