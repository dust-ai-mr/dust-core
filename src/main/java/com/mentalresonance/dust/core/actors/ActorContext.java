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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.mentalresonance.dust.core.system.exceptions.ActorInstantiationException;
import com.mentalresonance.dust.core.system.exceptions.ActorSelectionException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import com.mentalresonance.dust.core.system.SystemActor;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An ActorContext manages the tree of Actors rooted at RootActor. It manages the creation of an ActorRef from
 * an ActorPath
 *
 *  @author alanl
 */
@Slf4j
public class ActorContext {

    @Setter
    ActorRef guardianActor;  //   /

    ActorRef userActor;  //   /user

    @Getter
    final ActorSystem system;

    /**
     * If remoting this is dust://localhost:port/actor-system
     */
    public String hostContext = null;

    ActorRef deadletterActor; // /system/deadletters

    final Cache<String, ActorRef> resolvePaths = Caffeine.newBuilder()
        .maximumSize(16384)
        .build();

    /**
     * Constructor
     * @param system {@link ActorSystem} this context is in
     */
    public ActorContext(ActorSystem system) {
        this.system = system;
    }

    /**
     * Cache of Actor constructors. This is self loading and will get constructors on demand. Purely for
     * speed.
     * Key List<Class> is [ActorClass, Arg1Class, Arg2Class ..... ]
     * Value is the constructor matching this signature
     */
    public final LoadingCache<List<Class<?>>, Constructor> actorConstructors = Caffeine.newBuilder()
            .maximumSize(4096)
            .build(k -> constructorFor(k));

    /**
     * Appropriate constructor was not cached, so take list of [ActorClass, Arg1Class, Arg2Class..] and
     * find the appropriate constructor.
     * @param classAndArgs List of (already boxed) classes defining the call
     * @return appropriate constructor
     * @throws Exception if we cannot do this
     */
    private Constructor<?> constructorFor(List<Class<?>> classAndArgs) throws Exception {
        try {
            List<Constructor<?>> constructors = Arrays.stream(classAndArgs.get(0).getConstructors()).toList();
            List<Class<?>> argClasses = classAndArgs.size() > 1 ? classAndArgs.subList(1, classAndArgs.size()) : List.of();
            List<Constructor> matchingConstructors = new ArrayList<>();

            for (Constructor cons : constructors ) {
                List<Class<?>> parameterTypes = autoboxPrimitiveTypes(cons.getParameterTypes());
                if (areParametersMatching(parameterTypes, argClasses)) {
                    return cons;
                }
            }

            throw new Exception("No constructor found for " + classAndArgs.get(0));
        }
        catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Do parameter types in constructor match the classes of the arguments
     * @param parameterTypes constructor types
     * @param argsClasses argument types
     * @return true or false
     */
    private static boolean areParametersMatching(List<Class<?>> parameterTypes, List<Class<?>> argsClasses) {
        if (parameterTypes.size() != argsClasses.size()) {
            return false;
        }
        for (int i = 0; i < parameterTypes.size(); i++) {
            Class<?> clazz = argsClasses.get(i);
            if (clazz != null && !(parameterTypes.get(i).isAssignableFrom(clazz))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Primitive types (!) cannot be checked by isAssignableFrom, so we box them here if necessary
     * @param classList array of types - possible some primitive
     * @return list of types - boxed where necessary
     */
    private  List<Class<?>> autoboxPrimitiveTypes(Class<?>[] classList) {
        List<Class<?>> autoboxedList = new ArrayList<>();

        for (Class<?> clazz : classList) {
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
     * Resolve a path to an ActorRef. The path must be rooted at '/'
     * @param path - full path
     * @return the ActorRef
     * @throws InterruptedException if interrupted
     * @throws ActorSelectionException all other failures
     */

    public ActorRef actorSelection(String path) throws InterruptedException,  ActorSelectionException {
        ActorRef ref = null;

        try {
            if (! path.endsWith("/"))
                path = path + "/";

            if (null != (ref = resolvePaths.getIfPresent(path))) {
                return ref;
            }
            else if (path.contains(":")) {
                ActorRef remoteRef =  new ActorRef(path, this, null);
                resolvePaths.put(path, remoteRef);
                return remoteRef;
            }
            else  {
                /*
                 * So now we need to resolve by asking parents about their children. Rather than always begin
                 * at the guardian we work backwards going up the path to find the closest we have a reference to
                 * and start from there.
                 *
                 * So, say we want to resolve /a/b/c/d/ and we have already resolved b, then we want to send a request
                 * to resolve /c/d to /a/b.
                 *
                 */

                // Java way to get mutable list from array :(
                List<String> parts = Stream.of(path.split("/")).collect(Collectors.toCollection(ArrayList::new));
                List<String> post = new LinkedList<>();
                int left;

                while (((left = parts.size()) != 0) && null == ref) {
                    String lastPart = parts.remove(left - 1);
                    post.add(lastPart);
                    ref = resolvePaths.getIfPresent(String.join("/", parts) + "/");
                }
                if (0 == left) {
                    ref = guardianActor;
                }
                // So now post is a list of actor path segments and ref is the Actor which is the parent of the first of
                // the segments. Note that post will have picked up a trailing "" from the last "/". Also the list is
                // in reverse order to what we need ...
                if (post.getLast().isEmpty())
                    post.removeLast();
                Collections.reverse(post);
                log.trace("Resolving {} from {}", path, ref);

                // Crazy long timeout to handle startup issues
                ActorRef resolved = null;

                try {
                    resolved = ref.resolve(post).get(10, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    log.warn("Resolving {} timed out", path);
                } catch (Exception e) {
                    log.warn("Resolving {} got error {}", path, e.getMessage());
                }

                /*
                 * To cache or not to cache dead letters. We don't since if the Actor is (re) started
                 * we'd have to flush the cache ... the cost is repeated resolutions which might fail - a penalty that
                 * (so far) is worth it.
                 */
                if (null == resolved) {
                    log.trace("{} not found", path);
                    resolved = deadLetterRef(path);
                } else {
                    log.trace("{} resolved", path);
                    resolvePaths.put(path, resolved);
                }

                return resolved;
            }
        }
        catch (InterruptedException x) {
            throw x;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new ActorSelectionException(e.getMessage());
        }
    }

    ActorRef deadLetterRef(String path) throws ActorSelectionException, InterruptedException {
        ActorRef deadLetter = getDeadLetterActor();
        ActorRef ref = new ActorRef(path, this, deadLetter.actor);
        ref.isDeadLetter = true;
        ref.thread = deadLetter.thread;
        ref.mailBox = deadLetter.mailBox;
        return ref;
    }

    /**
     * Stop the actor at the ref by interrupting his mailbox
     * @param ref the Actor to stop
     */
    public void stop(ActorRef ref) {
        ref.thread.interrupt();
    }

    /**
     * Shut down the whole system
     */
    public void stop() { system.stop(); }

    /**
     * Call when the Actor has stopped to remove it from the path resolution cache
     * @param path of stopped Actor
     */
    public void decache(String path) {
        resolvePaths.invalidate(path);
    }

    /**
     * Add path to cache. We need this when resolving "./...." to ensure we don't send a _ResolveMsg to
     * the Actor doing the resolving (which would block the resolve).
     * @param path
     * @param ref
     */
    public void encache(String path, ActorRef ref) {
        resolvePaths.put(path, ref);
    }

    /**
     * Get /user reference
     * @return /user reference
     * @throws InterruptedException if interrupted
     * @throws ActorSelectionException on exception
     */
    public ActorRef getUserActor() throws InterruptedException, ActorSelectionException {
        if (null == userActor)
            userActor = actorSelection("/user");
        return userActor;
    }

    /**
     * Get /system/deadletters reference
     * @return /system/deadletters reference
     * @throws InterruptedException if interrupted
     * @throws ActorSelectionException on exception
     */
    public ActorRef getDeadLetterActor() throws InterruptedException, ActorSelectionException {
        if (null == deadletterActor)
            deadletterActor = actorSelection("/system/" + SystemActor.DEAD_LETTERS);
        return deadletterActor;
    }

    /**
     * Create a randomly named Actor under /user from props
     * @param props the props
     * @return ActorRef ref to created actor
     * @throws ActorInstantiationException on error
     */
    public ActorRef actorOf(Props props) throws ActorInstantiationException {
        return actorOf(props, UUID.randomUUID().toString());
    }
    /**
     * Create a named Actor under /user from props
     * @param props the props
     * @param name the name to use
     * @return ActorRef ref to created Actor
     * @throws ActorInstantiationException on error
     */
    public ActorRef actorOf(Props props, String name) throws ActorInstantiationException {
        try {
            var ref = getUserActor();
            var msg = new _CreateChildMsg(props, name);

            ref.props = props;
            ref.tell(msg, null);
            return msg.ref.get();
        }
        catch (Exception e) {
            log.error(e.getMessage());
            throw new ActorInstantiationException();
        }
    }

    /**
     * 'Hardwired' create child message for context.actorOf()
     */
    static class _CreateChildMsg implements Serializable {
        @Getter
        final
        Props props;
        @Getter
        final
        String name;

        @Getter
        final
        CompletableFuture<ActorRef> ref;

        public _CreateChildMsg(Props props, String name) {
            this.props = props;
            this.name = name;
            this.ref = new CompletableFuture<>();
        }
    }
}
