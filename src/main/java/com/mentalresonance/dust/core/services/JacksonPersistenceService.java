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

package com.mentalresonance.dust.core.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentalresonance.dust.core.utils.StringUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Semaphore;

/**
 * Convenient persistence mechanism. Pros include the fact that Jackson is forgiving if the class being deserialized
 * has changed (which often happens during development) as opposed to FST deserialization. As opposed to Gson
 * Jackson can handle type erase by annotations on the data. Jackson is also lenient with added/removed fields between
 * serialization and deserialization.
 *
 * Jackson is somewhat slower than Gson, so use the appropriate one.
 *
 * @author alanl
 */
@Slf4j
public class JacksonPersistenceService implements PersistenceService {

    private static final int MAX_CONCURRENT = 16;

    static final Semaphore available = new Semaphore(MAX_CONCURRENT, true);

    private static JacksonPersistenceService jacksonPersistenceService = null;

    /**
     * Where snapshots live in the file system
     */
    public static Path SNAPSHOTS;

    private static String fileType; // extension

    private static ObjectMapper objectMapper;

    public boolean isHashedId = true;  // Backward compatability
    /**
     * Constructor
     */
    private JacksonPersistenceService() {}

    /**
     * Factory - we only want one instance
     * Figure out where to put snapshots by default
     * (user-home/dust-snapshots)
     *
     * Snapshots go in directory specified by, in order:
     * Property config.snapshots, Env variable SNAPSHOTS or ~
     *
     * @param directory the directory to place snapshots
     * @param extension file extension to use for snapshots
     * @throws IOException on error
     * @return common DBPersistenceService
     */
    public static JacksonPersistenceService create(String directory, String extension) throws IOException {
        if (null == jacksonPersistenceService)
        {
            if (! extension.startsWith(".")) extension = ".".concat(extension);

            Path p = Paths.get(directory);

            fileType = extension;
            if (Files.notExists(p)) {
                Files.createDirectory(p);
            }
            SNAPSHOTS = p;

            log.info("Snapshots are in: %s".formatted(p.toString()));

            objectMapper = new ObjectMapper();
            jacksonPersistenceService = new JacksonPersistenceService();
        }
        return jacksonPersistenceService;
    }

    /**
     * Create a PersistenceService storing persisted objects in directory
     * @param directory to store object in
     * @return the persistenceService
     * @throws IOException on file system errors
     */
    public static JacksonPersistenceService create(String directory) throws IOException {
        return create(directory, "json");
    }

    /**
     * Default. Use first of property config.snapshots, env SNAPSHOTS or ~ as the directory and .json
     * as the extension
     * @return the persistence service
     * @throws IOException on error
     */
    public static JacksonPersistenceService create() throws IOException {
        String directory =
                null != System.getProperty("config.snapshots") ? System.getProperty("config.snapshots") :
                null != System.getenv("SNAPSHOTS") ? System.getenv("SNAPSHOTS") :
                null != System.getProperty("user.home") ? "%s/dust_snapshots".formatted(System.getProperty("user.home")) : null;

        if (null == directory)
            throw new IOException("Cannot determine snapshots directory");
        return create(directory, "json");
    }

    private String hashId(String id) throws NoSuchAlgorithmException {
        return StringUtils.hash(id, "MD5");
    }
    /**
     * Write the object under the given id. Filenames are md5(id).snap
     *
     * @param id identifier of object doing the writing
     * @param object data to be saved.
     * @throws InterruptedException on interruption
     * @throws NoSuchAlgorithmException bad hash algorithm name
     * @throws IOException on errors
     */
    public void write(String id, Serializable object) throws InterruptedException, NoSuchAlgorithmException, IOException
    {
        available.acquire();

        try {
            byte[] blob = objectMapper.writeValueAsBytes(object);
            Path p = Paths.get(SNAPSHOTS + "/" + (isHashedId ? hashId(id) : id) + fileType);
            Files.write(p, blob, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        catch (Throwable e) {
            log.error(String.format("Write Snapshot: %s", e.getMessage()));
            e.printStackTrace();
            throw e;
        }
        finally {
            available.release();
        }
    }
    /**
     * Read the object at the given Id. This is here to match the interface but simply throws an exception since
     * we need to know the class we are de serializing to
     * @param id of object
     * @return deserialized object if serialization exists else null
     * @throws InterruptedException if interrupted
     * @throws IOException on error
     * @throws ClassNotFoundException Cannot find class to instantiate
     */
    public Serializable read(String id) throws Exception {
        throw new Exception("Generic read not supported. Use read(String id, Class<?> clz)");
    }

    /**
     * deSerialize object from file
     * @param id file identifier
     * @param clz to be created
     * @return deSerialized object
     * @throws Exception on error
     */
    @Override
    public Serializable read(String id, Class<?> clz) throws Exception {
        Serializable object;

        available.acquire();

        try {
            Path p = Paths.get(SNAPSHOTS + "/" + (isHashedId ? hashId(id) : id) + fileType);
            if (! Files.exists(p))
                object = null;
            else
                object = (Serializable) objectMapper.readValue(Files.readAllBytes(p), clz);
        }
        catch (Throwable e) {
            log.error(String.format("Read Snapshot: %s", e.getMessage()));
            throw e;
        }
        finally {
            available.release();
        }
        return object;
    }

    /**
     * Delete the object at id if it exists.
     * @param id of the object
     * @throws IOException on error
     * @throws InterruptedException if interrupted
     * @throws NoSuchAlgorithmException security
     */
    public void delete(String id) throws InterruptedException, IOException, NoSuchAlgorithmException {

        try {
            available.acquire();
            Path p = Paths.get(SNAPSHOTS + "/" + (isHashedId ? hashId(id) : id) + fileType);
            if (Files.exists(p))
                Files.delete(p);
        }
        catch (Exception e) {
            log.error("Delete Snapshot: %s".formatted(e.getMessage()));
            throw e;
        }
        finally {
            available.release();
        }
    }
}
