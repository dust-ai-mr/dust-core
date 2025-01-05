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

package com.mentalresonance.dust.core.services;

import java.io.Serializable;

/**
 * Actor persistence uses a uniform Persistence service defined by this interface. We have DB and file system
 * persistence services out of the box.
 *
 * @author alanl
 */
public interface PersistenceService {

    /**
     * Persist the object
     * @param id under which to locate the object
     * @param object to persist
     * @throws Exception on error
     */
    void write(String id, Serializable object) throws Exception;

    /**
     * Delete the object with id if it exists. This allows us to exit cleanly even when a snapshot has not
     * been saved yet.
     * @param id id of state to delete
     * @throws Exception if things go wrong
     */
    void delete(String id) throws Exception;

    /**
     * Create the object with given id
     * @param id object id
     * @return the object
     * @throws Exception if things go hoof up
     */
    Serializable read(String id) throws Exception;

    /**
     * Create the object with given id
     * @param id object id
     * @param clz the class that the returned object is an instance of. May be required by some serialization mechanisms.
     * @return the object
     * @throws Exception if things go hoof up
     */
    Serializable read(String id, Class<?> clz) throws Exception;
}
