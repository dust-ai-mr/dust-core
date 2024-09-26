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

package com.mentalresonance.dust.core.services;

import com.mentalresonance.dust.core.utils.StringUtils;
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
 * Services to store/retrieve Actor state to the filesystem using FST serialization.
 *
 * @author alanl
 */
@Slf4j
public class FSTPersistenceService implements PersistenceService {

    private static final int MAX_CONCURRENT = 16;

    static final Semaphore available = new Semaphore(MAX_CONCURRENT, true);

    private static FSTPersistenceService fstPersistenceService = null;

    private static final String HOME = System.getProperty("user.home");

    /**
     * Path to snapshot storage
     */
    public static Path SNAPSHOTS;

    /**
     * Constructor
     */
    private FSTPersistenceService() {}

    /**
     * Factory - we only want one instance
     * Figure out where to put snapshots
     * (user-home/snapshots)
     *
     * @throws IOException on IO errors in the file system
     * @return common DBPersistenceService
     */
    public static PersistenceService create() throws IOException {
        return create(HOME + "/snapshots");
    }

    /**
     * Factory - we only want one instance
     * @param path path to snapshot storage
     * @throws IOException on IO errors in the file system
     * @return common DBPersistenceService
     */
    public static PersistenceService create(String path) throws IOException {
        if (null == fstPersistenceService) {
            Path p = Paths.get(path);
            if (Files.notExists(p)) {
                Files.createDirectory(p);
            }
            SNAPSHOTS = p;
            fstPersistenceService = new FSTPersistenceService();
        }
        return fstPersistenceService;
    }

    private String hashId(String id) throws NoSuchAlgorithmException {
        return StringUtils.hash(id, "MD5");
    }
    /**
     * Write the object under the given id. Filenames are "md5(id).snap"
     *
     * @param id identifier of object doing the writing
     * @param object data to be saved.
     *
     * @author alanl
     */
    public void write(String id, Serializable object) throws InterruptedException, NoSuchAlgorithmException, IOException
    {
        available.acquire();

        try {
            byte[] blob = SerializationService.write(object);
            Path p = Paths.get(SNAPSHOTS + "/" + hashId(id) + ".snap");
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
     * Read the object at the given Id
     * @param id of object
     * @return deserialized object if serialization exists else null
     * @throws InterruptedException if interrupted while reading
     * @throws IOException if File system errros
     * @throws ClassNotFoundException cannot find class to deSerialize to
     */
    public Serializable read(String id) throws InterruptedException, IOException, ClassNotFoundException, NoSuchAlgorithmException
    {
        Serializable object;

        available.acquire();

        try {
            Path p = Paths.get(SNAPSHOTS + "/" + hashId(id) + ".snap");
            if (! Files.exists(p))
                object = null;
            else
                object = SerializationService.read(Files.readAllBytes(p));
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

    @Override
    public Serializable read(String id, Class<?> clz) throws Exception {
        Serializable object;

        available.acquire();

        try {
            Path p = Paths.get(SNAPSHOTS + "/" + hashId(id) + ".snap");
            if (! Files.exists(p))
                object = null;
            else
                object = SerializationService.read(Files.readAllBytes(p), clz);
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
     * Delete the object at id
     * @param id of the object
     * @throws InterruptedException if interrupted during delete
     */
    public void delete(String id) throws InterruptedException, NoSuchAlgorithmException, IOException {

        available.acquire();

        try {
            Path p = Paths.get(SNAPSHOTS + "/" + hashId(id) + ".snap");
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
