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

import java.lang.reflect.InvocationTargetException;
import java.net.BindException;

/**
 * Build for an ActorSystem
 */
public class ActorSystemBuilder {
    String host = "localhost";
    String name = null;
    Integer port = null;
    Integer maxIncomingConnections = 16;
    Integer maxOutgoingConnections = 16;
    boolean logDeadLetters = true;

    public ActorSystemBuilder() {}

    /**
     * @param host
     * @return this
     */
    public ActorSystemBuilder host(String host) {
        this.host = host;
        return this;
    }

    /**
     *
     * @param name
     * @return this
     */
    public ActorSystemBuilder name(String name) {
        this.name = name;
        return this;
    }

    /**
     *
     * @param port
     * @return this
     */
    public ActorSystemBuilder port(int port) {
        this.port = port;
        return this;
    }

    /**
     *
     * @param logDeadLetters
     * @return this
     */
    public ActorSystemBuilder logDeadLetters(boolean logDeadLetters) {
        this.logDeadLetters = logDeadLetters;
        return this;
    }

    /**
     *
     * @param maxOutgoingConnection
     * @return this
     */
    public ActorSystemBuilder maxOutgoingConnections(int maxOutgoingConnection) {
        this.maxOutgoingConnections = maxOutgoingConnection;
        return this;
    }

    /**
     *
     * @param maxIncomingConnection
     * @return this
     */
    public ActorSystemBuilder maxIncomingConnections(int maxIncomingConnection) {
        this.maxIncomingConnections = maxIncomingConnection;
        return this;
    }

    /**
     * Build the ActorSystem
     * @return this
     * @throws InvocationTargetException
     * @throws NoSuchMethodException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    public ActorSystem build() throws
            InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, BindException
    {
        assert(null != name);
        return new ActorSystem(
            host,
            name,
            port,
            logDeadLetters,
            maxOutgoingConnections,
            maxIncomingConnections
        );
    }
}
