/*
 *
 *  Copyright 2024 Alan Littleford
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
import com.mentalresonance.dust.core.system.exceptions.ActorInstantiationException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import static com.mentalresonance.dust.core.actors.SupervisionStrategy.*;

/**
 * The base class for all Dusty things.
 */
@Slf4j
public class Actor implements Runnable {

    /**
     * ONE_FOR_ONE / STOP default strategy
     */
    protected final static SupervisionStrategy defaultStrategyFactory = new SupervisionStrategy();

    /**
     * Context for the Actor {@link ActorContext}
     */
    @Getter
    @Setter
    protected ActorContext context;

    /**
     * My parent Actor
     */
    @Getter
    @Setter
    protected ActorRef parent;

    /**
     * My grandparentparent Actor
     */
    @Getter
    @Setter
    protected ActorRef grandParent;

    /**
     * Me
     */
    @Setter
    @Getter
    protected ActorRef self;

    /**
     * Actor Ref of the sender of currently being processed message
     */
    @Getter
    protected ActorRef sender;

    /**
     * Currently active behavior.
     */
    protected ActorBehavior behavior;

    private final HashMap<String, ActorRef> children = new HashMap<>(8);

    // Who is watching me ?
    private final List<ActorRef> watchers = new LinkedList<>();

    private final Object LOCK = new Object();

    private Boolean tellParentOnStop = true;

    private final ArrayDeque<ActorBehavior> behaviors = new ArrayDeque<>();

    /**
     * Currently active {@link SupervisionStrategy}
     */
    protected SupervisionStrategy supervisor = defaultStrategyFactory;

    /**
     * Fifo list of any stashed messages
     */
    protected List<SentMessage> stashed = new LinkedList<>();

    /**
     * Optional dead man's handle
     */
    private Cancellable deadMansHandle = null;

    /**
     * Constructor. <b>This should not be called directly</b> rather rely on it being called
     * via a Props.create
     */
    public Actor() {
        behavior = this instanceof PersistentActor ? ((PersistentActor)this).recoveryBehavior() : createBehavior();
    }

    /**
     * To be overriden. If the Actor specifies no overriding message then all unhandled (system) messages
     * are handled here. For convenience we just soak up messages and log them.
     * @return a default behaviour
     */
    protected ActorBehavior createBehavior() {
        return msg -> {
            if (Objects.requireNonNull(msg) instanceof ChildExceptionMsg cem) {
                log.warn(String.format("%s: Exception from child: %s - %s", self.path, sender, cem.getException().getMessage()));
            } else {
                log.warn(String.format("%s: no behavior. Cannot handle %s from %s", self.path, msg, sender));
            }
        };
    }

    /**
     * Called when clean starting. Mailbox is set up but processing is not running. To be overriden.
     *
     * @throws Exception can be thrown on preStart.
     */
    protected void preStart() throws Exception {
        log.trace("Started: " + self.path + " context:" + context);
    }

    /**
     * Called when stopping. Mailbox is no longer being processed but can still tell() etc. To be overriden
     *
     * @throws Exception can be thrown on postStop.
     */
    protected void postStop() throws Exception {
        log.trace("Stopped: " + self.path);
    }

    /**
     * Called when the Actor has been restarted by its parent because of an error. To be overriden.
     * @param t - The error which caused the restart
     */
    protected void preRestart(Throwable t) {
        log.trace("Restarted: " + self.path + " because of " + t.getMessage());
    }

    /**
     * Called when the Actor has been resumed by its parent after stopping. To be overriden.
     */
    protected void onResume() {
        // log.trace("Resumed: " + self.path);
    }
    /**
     * Call to set dead man's handle if required. If the timer expires before it is cancelled
     * I will call dying() and then stop myself.
     *
     * @param millis millisecond delay before handle is dropped
     */
    protected void dieIn(long millis) {
        cancelDeadMansHandle();
        deadMansHandle = new Cancellable(Thread.startVirtualThread(
            () -> {
                try {
                    Thread.sleep(millis);
                    dying();
                    stopSelf();
                }
                catch (InterruptedException e) { // Cancel !!

                }
                catch (Throwable t) {
                    log.warn(t.getMessage());
                    stopSelf();
                }
            }
        ));
    }

    /**
     * Called before the dead man's handle shuts us down. Override if you want to handle this event yourself.
     */
    protected void dying() {
        log.warn("%s dying() via dead man's handle.".formatted(self.path));
    }

    /**
     * Cancel the dead man's handle
     */
    protected void cancelDeadMansHandle() {
        if (null != deadMansHandle) {
            deadMansHandle.cancel();
            deadMansHandle = null;
        }
    }

    /**
     * Put the mailbox thread to sleep. The virtual thread sleeps but gets detached from its host thread so
     * this is *not* an expensive action.
     * @param millis milliseconds to sleep
     */
    protected void sleep(Long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception ignore) {} // Don't want to throw exception if we are interrupted by a stop
    }

    /**
     * Get children of this Actor
     * @return The children
     */
    protected Collection<ActorRef> getChildren() {
        return children.values();
    }
    /**
     * Get the current number of messages waiting in the mailbox
     *
     * @return size of current mailbox
     */
    protected int mailboxSize() {
        return self.mailBox.queue.size();
    }

    /**
     * Watch the referenced Actor. If it stops then the watcher receives a Terminated message.
     * Note: The watcher has no way of knowing if the Actor is going to be restarted by its parent, only
     * that it was stopped, so this mechanism should not be used for lifecycle management.
     * NB: public because it is in ActorTrait
     * @param ref the Actor to watch.
     * @return ref for fluency
     */
    public ActorRef watch(ActorRef ref) {
        ref.tell(new WatchMsg(), self);
        return ref;
    }

    /**
     * unWatch the Actor if it was being watched.
     * @param ref the Actor to unwatch.
     */
    public void unWatch(ActorRef ref) {
        ref.tell(new UnWatchMsg(), self);
    }

    /**
     * (Re) start mailbox processing by giving this to a lightweight thread.
     */
    public void run()
    {
        boolean running;

        try {
            switch (self.lifecycle) {
                case ActorRef.LC_RECOVERED -> {
                    ((PersistentActor)this).postRecovery();
                    preStart();
                    running = true;
                }
                case ActorRef.LC_START -> {
                    preStart();
                    running = true;
                }
                case ActorRef.LC_RESTART -> {
                    preRestart(self.restartCause);
                    self.lifecycle = ActorRef.LC_RESTART;
                    running = true;
                }
                default -> {
                    log.error(String.format("%s: unknown lifecycle state in start: %d. .... Stopping.", self.path, self.lifecycle));
                    running = false;
                }
            }
        }
        /*
         * I failed to restart (preStart or preRestart through Exception) so stop and tell my parent.
         */
        catch (Exception e) {
            if (parent != null)
                parent.tell(new _Throwable(e), self);
            running = false;
        }

        /*
         * The main loop: get next message, hand it off to behaviour and manage what happens. We first look to see
         * if the message is internal book keeping (_Convention) or a PoisonPill otherwise we send it off
         * to our currently defined user behaviour.
         */

        SentMessage sentMessage;

        while (running)
        {
            try {
                sentMessage = self.mailBox.queue.take();
                sender = sentMessage.sender;
            }
            /*
             * If I'm interrupted outside of waiting for LOCK below then it must be someone wanting me to stop
             * running if my lifecycle is LC_START otherwise there is an ALL_FOR_ONE supervision going on.
             * In this case my parent will have set my lifecycle to tell me what to do:
             */
            catch (InterruptedException x) {
                if (self.lifecycle == ActorRef.LC_RESUME) {
                    self.lifecycle = ActorRef.LC_START;
                    onResume();
                } else {
                    running = false;
                }
                continue;
            }

            /*
             * If I'm holding a message then process it. If not and I got here I was probably interrupted via
             * a context.stop()
             */
           //if (sentMessage != null) {
                try {
                    if (null == sentMessage.message) {
                        log.warn("%s sent null message to %s. Ignored".formatted(sender.toString(), self.path));
                    }
                    else switch (sentMessage.message)
                    {
                        /*
                         * _ResolveActorMsg is used to resolve a path. If nothing is left then the path is me and I complete
                         * the future with my reference. If not then I look up the next segment (a child?)
                         * and if found I send the trimmed message down to it. If I can't look it up I
                         * return null - and let the resolver replace it with a cloned dead letter actor
                         */
                        case ActorContext._ResolveActorMsg res -> {
                            if (res.path.isEmpty()) {
                                if (!res.ref.complete(self)) {
                                    log.error("Resolved path " + self.path + " did not complete properly");
                                }
                            }
                            else {
                                String next = res.path.remove(0);
                                ActorRef child = children.get(next);

                                if (null == child) {
                                    // log.info(String.format("%s is not a child of %s", next, self.path));
                                    res.ref.complete(null);
                                }
                                else {
                                    // log.info(String.format("Forwarding resolve to %s which is  a child of %s", next, self.path));
                                    child.tell(res, self);
                                }
                            }
                        }

                        case WatchMsg ignored -> watchers.add(sender);

                        case UnWatchMsg ignored ->  watchers.remove(sender);

                        /*
                         * Sent to me when my parent is stopping. I stop but don't send a Terminating msg
                         */
                        case _ParentStoppingMsg ignored -> {
                            tellParentOnStop = false;
                            running = false;
                        }

                        case PoisonPill ignored -> running = false;

                        case DeleteChildMsg msg -> context.stop(actorSelection("./" + msg.getName()).getRef());

                        /*
                         * Support actorOf in ActorContext.
                         */
                        case ActorContext._CreateChildMsg msg -> msg.ref.complete(actorOf(msg.props, msg.name));

                        // A child has stopped
                        case _Stopped ignored -> {
                            if (null == children.remove(sender.name))
                                log.warn(self.path + ": child: " + sender.name + " was not in children list");
                        }

                        // If I am a request do it - if a Response to my request of another Actor pass it to my behavior
                        case GetChildrenMsg msg -> {
                            if (msg.getIsResponse()) {
                                behavior.onMessage(msg);
                            }
                            else {
                                msg.setIsResponse(true);
                                msg.setChildren(children.values().stream().toList());
                                sender.tell(msg, self);
                            }
                        }

                        /*
                         * Child threw Throwable.
                         */
                        case _Throwable msg -> {

                            SupervisionStrategy supervisionStrategy = supervisor.strategy(sender, msg.thrown);
                            Collection<ActorRef> flock;

                            log.warn(String.format("%s: got error %s from %s", self, msg.thrown.getMessage(), sender));
                            log.info(supervisor.toString());

                            if (supervisionStrategy.getMode() == MODE_ALL_FOR_ONE)
                                flock = children.values();
                            else {
                                LinkedList<ActorRef> list = new LinkedList<ActorRef>();
                                list.add(sender);
                                flock = list;
                            }

                            for(ActorRef child : flock) {
                                child.restartCause = msg.thrown;
                                child.lifecycle = supervisionStrategy.strategy(child, msg.thrown).getStrategy();
                                /*
                                 * If the child is the original thrower it will be sat waiting on the lock,
                                 * if not we can interrupt it anyway having set its lifecycle state
                                 */
                                child.thread.interrupt();
                                /*
                                 * Restarting is tricky - we want the sender ref to still be valid since it is held by many
                                 * other Actors, so we need to just 'adjust' it to match the new conditions
                                 */
                                if (child.lifecycle == ActorRef.LC_RESTART) {
                                    children.put(child.name,  restart(child));
                                }
                                self.tell(new ChildExceptionMsg(sender, msg.thrown), self);
                            }
                        }
                        //
                        case _ChildProxyMsg msg -> children.values().forEach(child -> child.tell(msg.message, sender));
                        //
                        default -> {
                            if (null != behavior) {
                                behavior.onMessage(sentMessage.message);
                            }
                            else {
                                log.warn(String.format("%s: cannot handle message %s from %s", self.path, sentMessage.message, sender));
                            }
                        }
                    }
                }
                /*
                 * Something has gone wrong. Tell my parent and then wait on the Lock. My parent will make
                 * a recovery strategy decision, set my lifecycle appropriately then interrupt my thread.
                 */
                catch (Throwable t)
                {
                    try { // Sometimes toString()ing the message can throw an Exception !!
                        log.info(self.path + ": Exception when processing: " + sentMessage.message + " :" + t.getMessage());
                    } catch (Exception e) {
                        log.info(self.path + ": Exception when processing un-displayable message: " + t.getMessage());
                    }
                    if (null != parent) {
                        parent.tell(new _Throwable(t), self);
                        try {
                            synchronized (LOCK) {
                                LOCK.wait();
                            }
                        }
                        /*
                         * My parent will set what I have to do in my self.lifecycle field
                         */
                        catch (InterruptedException e)
                        {
                            switch (self.lifecycle)
                            {
                                case ActorRef.LC_RESTART, ActorRef.LC_STOP -> running = false;

                                case ActorRef.LC_RESUME -> {
                                    try {
                                        onResume();
                                    } catch (Exception x) {
                                        log.error("{} onResume() exception: {}", self, x.getMessage());
                                        // Todo: what ???
                                    }
                                }
                                default ->
                                    log.error("{}: unknown lifecycle state in resumption: {}", self, self.lifecycle);
                            }
                        }
                    }
                }
           // }
        }
        /*
         * We have stopped - flush any cached reference in context
         */
        context.decache(self.path);
        /*
         * If I'm exiting because of a restart then don't tell parent, keep mailbox and don't call postStop()
         */
        if (self.lifecycle != ActorRef.LC_RESTART)
        {
           try {
                postStop();
                if (null != deadMansHandle)
                    deadMansHandle.cancel();
            }
            catch (Exception e) {
                log.error(String.format("%s postStop() exception: %s", self.path, e.getMessage()));
                // Todo: what ???
            }
            self.mailBox.queue = null;
            self.mailBox.dead = true;
            if (null != parent && tellParentOnStop)
                parent.tell(new _Stopped(), self);
        }

        children.values().forEach((ref) -> ref.tell(new _ParentStoppingMsg(), null));

        watchers.forEach((w) -> w.tell(new Terminated(self.name), self));
    }

    /**
     * Replace current behaviour with specified one
     * @param behavior new behavior
     */
    public void become(ActorBehavior behavior) {
        this.behavior = behavior;
    }

    /**
     * Stashes current behavior and replaces it with newBehavior
     * @param newBehavior the new behavior to follow
     */
    public void stashBecome(ActorBehavior newBehavior) {
        log.trace("Stashing %s and becoming %s".formatted(this.behavior, newBehavior));
        behaviors.push(this.behavior);
        become(newBehavior);
    }

    /**
     * Pops last stashed behavior and makes it current behavior
     * @return Stashed behavior
     * @throws Exception if stash is empty
     */
    public ActorBehavior unBecome() throws Exception {
        ActorBehavior behavior;

        if (! behaviors.isEmpty()) {
            behavior = behaviors.pop();
            log.trace("Popping behavior = %s".formatted(behavior));
            become(behavior);
        } else
            throw new Exception("Empty behavior queue on unBecome");
        return behavior;
    }

    /**
     * Delegate all messages, except instances of NonDelegatedMsg, to the target Actor.
     * After it stops become thenBecome behavior and send myself the thenMsg
     *
     * @param target the delegated Actor
     * @param thenBecome behavior to become when target finished or behavior at time of delegation if nyll
     * @param thenMsg message to send myself (after thenBecome) or nothing if null
     */
    protected void delegateTo(ActorRef target, ActorBehavior thenBecome, Serializable thenMsg) {
        watch(target);
        stashBecome(delegateBehavior(target, thenBecome, thenMsg));
    }
    /**
     * target takes over all (not NonDelegatedMsg) message processing until stops after which we continue on
     * by stashBecoming thenBecome and sending it thenMsg.
     *
     * The delegated Actor can also send us a Delegating msg which wraps a message. In this case we
     * stop the delegating Actor, send ourselves the wrapped message if it is not null
     * otherwise we  send ourselves thenMsg
     *
     * @param target Actor to delegate to
     * @param thenBecome if not null stashBecome this, otherwise just unBecome()
     * @param thenMsg sent to myself on undelegating if not null
     * @return the new behavior
     */
    protected ActorBehavior delegateBehavior(ActorRef target, ActorBehavior thenBecome, Serializable thenMsg) {
        return message -> {
            switch(message) {
                case Terminated ignored -> {
                    if (sender == target) { // Might be getting terminated message from somewhere else
                        unBecome();
                        if (null != thenBecome)
                            stashBecome(thenBecome);
                        if (null != thenMsg)
                            tellSelf(thenMsg);
                    }
                }
                case DelegatingMsg dm -> {
                    context.stop(sender);
                    unBecome();
                    if (null != thenBecome)
                        stashBecome(thenBecome);
                    if (null != dm.getMsg())
                        tellSelf(dm.getMsg());
                    else if (null != thenMsg)
                        tellSelf(thenMsg);
                }
                default  ->  {
                    if (message instanceof NonDelegatedMsg)
                        self.tell(message, sender);
                    else
                        target.tell(message, sender);
                }
            }
        };
    }

    /**
     * Add the message to the stash
     * @param msg the message
     */
    protected void stash(Serializable msg) {
        stashed.add(new SentMessage(msg, sender));
    }

    /**
     * Add all stashed messages, in order, to our mailbox. These are added to the end of the mailbox
     */
    protected void unstashAll() {
        self.mailBox.queue.addAll(stashed);
        stashed = new LinkedList<>();
    }
    /**
     * Sends the message to me in ~millis milliseconds
     * @param msg the message
     * @param millis time in millis to delay
     * @return a Cancellable
     */
    protected Cancellable scheduleIn(Serializable msg, Long millis) {
        return scheduleIn(msg, millis, self);
    }

    /**
     * Sends the message to target in ~millis milliseconds
     * @param msg the message
     * @param millis time in millis to delay
     * @param target recipient of message
     * @return a Cancellable
     */
    protected Cancellable scheduleIn(Serializable msg, Long millis, ActorRef target) {
        if (null == target) {
            log.error("Scheduling to a null target");
            return null;
        }
        return new Cancellable(Thread.startVirtualThread(
                () -> {
                    try {
                        Thread.sleep(millis);
                        target.tell(msg, self);
                    }
                    catch (InterruptedException e) { // Cancel !!

                    }
                    catch (Throwable t) {
                        log.warn(t.getMessage());
                    }
                }
        ));
    }

    /**
     * Create a child Actor from the props under a random name.
     *
     * When this returns the mailbox for the new Actor is available and can be sent message but there is no guarantee
     * preStart()/postRecovery() have been called yet.
     *
     * @param props of the Actor
     * @return ActorRef or null on failure
     * @exception ActorInstantiationException if cannot instantiate
     */
    protected ActorRef actorOf(Props props) throws ActorInstantiationException {
        return actorOf(props, UUID.randomUUID().toString());
    }

    /**
     * Create a child Actor from the props under the given name
     * When this returns the mailbox for the new Actor is available and can be sent message but there is no guarantee
     * preStart()/postRecovery() have been called yet.
     *
     * Note - it is possible to have race conditions where an Actor is being created and another comes in and
     * tries to create it as well. In these collisions we log the collision but return the already existing ActorRef.
     *
     * @param props of the Actor
     * @param name of the Actor
     * @return ActorRef or null on failure.
     * @exception ActorInstantiationException if cannot instantiate
     */
    protected ActorRef actorOf(Props props, String name) throws ActorInstantiationException {
        try {
            ActorRef exists;
            assert name != null: "Child of " + self.path + " given null name!";
            if (null != (exists = children.get(name))) {
                log.warn("Child %s of %s already exists !!".formatted(name, self.path));
                return exists;
            }
            Actor actor = createInstanceWithParameters(props.actorClass, props.actorArgs);
            ActorRef ref = new ActorRef(self.path, name, context);

            ref.props = props;
            ref.mailBox = new MailBox();

            actor.context = context;
            actor.parent = self;
            actor.grandParent = parent;
            actor.self = ref;
            children.put(name, ref);

            ref.thread = Thread.startVirtualThread(actor);

            return actor.self;
        }
        catch (Exception e) {
            log.error("Could not create Actor {} error: {}", props.actorClass, e.getMessage());
            throw new ActorInstantiationException(e.getMessage());
        }
    }

    Actor createInstanceWithParameters(Class<?> actorClass, Object[] args) throws Exception {
        ArrayList<Class<?>> classList = new ArrayList<>();
        classList.add(actorClass);
        for (Object obj : args) {
            if (obj != null) {
                Class clazz = obj.getClass();
                if (clazz.isPrimitive()) {
                    if (clazz == int.class) {
                        classList.add(Integer.class);
                    } else if (clazz == boolean.class) {
                        classList.add(Boolean.class);
                    } else if (clazz == byte.class) {
                        classList.add(Byte.class);
                    } else if (clazz == char.class) {
                        classList.add(Character.class);
                    } else if (clazz == double.class) {
                        classList.add(Double.class);
                    } else if (clazz == float.class) {
                        classList.add(Float.class);
                    } else if (clazz == long.class) {
                        classList.add(Long.class);
                    } else if (clazz == short.class) {
                        classList.add(Short.class);
                    } else if (clazz == void.class) {
                        classList.add(Void.class);
                    }
                } else {
                    classList.add(clazz);
                }
            } else {
                classList.add(null);
            }
        }
        Constructor cons = context.actorConstructors.get(classList);
        return (Actor) cons.newInstance(args);
    }

    private  List<Class<?>> autoboxPrimitiveTypes(List<Class<?>> classList) {
        List<Class<?>> autoboxedList = new ArrayList<>();

        for (Class<?> clazz : classList) {
            // Check if the class is a primitive type, and if so, add the corresponding wrapper class
            if (clazz.isPrimitive()) {
                if (clazz == int.class) {
                    autoboxedList.add(Integer.class);
                } else if (clazz == boolean.class) {
                    autoboxedList.add(Boolean.class);
                } else if (clazz == byte.class) {
                    autoboxedList.add(Byte.class);
                } else if (clazz == char.class) {
                    autoboxedList.add(Character.class);
                } else if (clazz == double.class) {
                    autoboxedList.add(Double.class);
                } else if (clazz == float.class) {
                    autoboxedList.add(Float.class);
                } else if (clazz == long.class) {
                    autoboxedList.add(Long.class);
                } else if (clazz == short.class) {
                    autoboxedList.add(Short.class);
                } else if (clazz == void.class) {
                    autoboxedList.add(Void.class);
                }
            } else {
                // If it's not a primitive, just add the class as-is
                autoboxedList.add(clazz);
            }
        }

        return autoboxedList;
    }


    /**
     * Convert a path into an ActorRef. Path can be of the form ..
     *      /.... in which case is absolute
     *      ./... which means the path is relative to me
     *      ../... which means the path is relative to my parent
     *      None of the above - which we take to mean relative to me
     * If path contains ':' it is expected to be a remote path, which, for now if of the form
     *      dust://host:port/remote-system-name/path
     * In this case the path must be absolute
     *
     * @param path The path to the required Actor
     * @return ActorSelection for the the Actor
     */
    protected ActorSelection actorSelection(String path)
    {
        ActorSelection selection = new ActorSelection();

        path = path.trim();

        if (! path.contains(":"))
        {         // Must be absolute if contains :
            if (! path.startsWith("/"))
            {    // Not absolute so may be funky
                if (path.startsWith("../"))
                {
                    // segments is of the form [a, b, c, d, e]
                    // path is ../../../foo
                    List<String> segments = new ArrayList<>(Arrays.stream(self.path.split("/")).toList());

                    while (path.startsWith("../")) {
                        segments.remove(segments.size() - 1);
                        path = path.substring(3);
                    }
                    // ../../..  case
                    if (path.startsWith("..")) {
                        segments.remove(segments.size() - 1);
                        path = path.substring(2);
                    }

                    path = String.join("/", segments) + "/" + path;
                }
                else if (path.startsWith("./")) {
                    /*
                     * We have an issue here. If I am sending a message to a child of mine and that child does not
                     * exist then I will get _ResolveActorMsg - but I cannot process that because I am sat in my tell
                     * (i.e. I've blocked myself). So we have to check for that situation here.
                     *
                     * If I would go to dead letters then mark the selection as such and its tell() will
                     * wrap the message in a DeadLetter and send it off.
                     */
                    String childName = path.substring(2);

                    if (getChildren().stream().noneMatch(child -> child.name == childName)) {
                        selection.setDead(true);
                    }
                    path = self.path + childName;
                }
                else
                    path = self.path + path;
            }
        }
        else
            selection.setRemote(true);

        selection.path = path;
        selection.context = context;
        return selection;
    }

    /**
     * Restart the Actor at ref. Note this means keeping the ref almost as was. We update the lifecycle and
     * create a thread for the restart but that is it.
     *
     * @param ref Actor to be restarted
     * @return original ref
     * @exception ActorInstantiationException if cannot instantiate
     */
    private ActorRef restart(ActorRef ref) throws ActorInstantiationException {
        try {
            Constructor<?> cons = ref.props.actorClass.getConstructors()[0];
            Actor actor = (Actor)cons.newInstance(ref.props.actorArgs);

            actor.context = context;
            actor.parent = self;
            actor.grandParent = parent;
            actor.self = ref;

            ref.lifecycle = ActorRef.LC_RESTART;
            ref.thread = Thread.startVirtualThread(actor);
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new ActorInstantiationException(e.getMessage());
        }
        return ref;
    }

    /**
     * Time saver for self.tell(.., self)
     * @param message to send
     * @return true if no error in send, else false
     */
    public boolean tellSelf(Serializable message) {
        return self.tell(message, self);
    }

    /**
     * Time saver for stopSelf()
     */
    protected void stopSelf() { context.stop(self); }

    /**
     * We wrap the message Q which ActorRef's have references to. So when we stop an Actor we simply replace the
     * reference to the Actor's Q with a reference to the dead letter Q.
     */
    public static class MailBox {
        @Getter
        @Setter
        Boolean dead = false;
        @Getter
        LinkedBlockingQueue<SentMessage> queue = new LinkedBlockingQueue<>();

        /**
         * Create mailbox
         */
        public MailBox() {}
    }

    /*
     * Internal messages
     */
    static class _Stopped implements Serializable { }

    // Special message to stop children without them notifying the parent they are stopping
    // (because the parent is probably already gone ....)
    static class _ParentStoppingMsg implements Serializable { }

    static class _Throwable implements Serializable {
        final Throwable thrown;

        public _Throwable(Throwable thrown) {
            this.thrown = thrown;
        }
    }

    /**
     * Wraps a message to be sent to all my children
     */
    static class _ChildProxyMsg implements Serializable {
        final Serializable message;
        public  _ChildProxyMsg(Serializable msg) {
            this.message = msg;
        }
    }

    // Misc developer helpers

    /**
     * Useful print for debugging
     * @param s string to print preceded by Actor path
     */
    protected void println(String s) { System.out.println(self.path + ": " + s); }
}

