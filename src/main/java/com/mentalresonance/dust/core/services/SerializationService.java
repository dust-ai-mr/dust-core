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

import com.google.gson.Gson;
import com.mentalresonance.dust.core.actors.ActorRef;
import com.mentalresonance.dust.core.actors.SentMessage;
import com.mentalresonance.dust.core.msgs.PingMsg;
import lombok.Getter;
import org.nustaq.serialization.*;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * (de) serialize objects using the  (<a href="https://github.com/RuedigerMoeller/fast-serialization">
 *     nustaq serializer</a>)
 *
 * @author alanl
 */
public class SerializationService {

    /**
     * Constructor
     */
    private SerializationService() {}

    @Getter
    static final FSTConfiguration fstConfiguration = FSTConfiguration.createUnsafeBinaryConfiguration();
    static final FSTConfiguration fstJsonConfiguration = FSTConfiguration.createJsonConfiguration();

    static {
        fstConfiguration.registerClass(SentMessage.class, ActorRef.class, PingMsg.class);
        fstConfiguration.setShareReferences(false);
        fstConfiguration.registerSerializer(LinkedHashMap.class, new FSTLinkedHasMapSerializer(), true);
        fstJsonConfiguration.registerClass(SentMessage.class, ActorRef.class, PingMsg.class);
        fstJsonConfiguration.setShareReferences(false);

        // https://github.com/RuedigerMoeller/fast-serialization/issues/103
        // FSTUtil.unFlaggedUnsafe = null;
    }

    /**
     * Register classes with serialization. A registered class will probably convert faster since reflection will
     * not be involved. Perform once.
     *
     * @param classes - List of (Serializable) classes to register.
     */
    public static void registerClasses(Class<Serializable>[] classes) {
        for (Class<Serializable> clz: classes) {
            fstConfiguration.registerClass(clz);
            fstJsonConfiguration.registerClass(clz);
        }
    }

    /**
     * Serialize the object
     * @param object to be serialized
     * @return byte[] serialization.
     */
    public static byte[] write(Serializable object) {
        return fstConfiguration.asByteArray(object);
    }

    /**
     * Read the serialized object using the FST 'guess' for the class match
     * @param blob the byte[] serialized object
     * @return The Serializable object
     * @throws IOException on error
     * @throws ClassNotFoundException on error
     */
    public static Serializable read(byte[] blob) throws IOException, ClassNotFoundException {
        return (Serializable) fstConfiguration.getObjectInput(blob).readObject();
    }

    /**
     * Read object as a given class
     * @param clz Assumed class
     * @param blob the byte[] serialized object
     * @return The Serializable object
     * @throws Exception on error
     */
    public static Serializable read(byte[] blob, Class clz) throws Exception {
        return (Serializable) fstConfiguration.getObjectInput(blob).readObject(clz);
    }

    /**
     * FST serialization to Json -- simple objects (Map, Lists) do not go over cleanly to their JSON
     * counterparts so this can't be used for external APIs. Use write/read JsonAPI for that
     * @param object to serialize
     * @return JSOn string
     */
    public static String writeJson(Serializable object) { return fstJsonConfiguration.asJsonString(object); }

    /**
     * Parse object from json
     * @param json JSON string
     * @return the serialized object
     * @throws IOException on error
     * @throws ClassNotFoundException on error
     */
    public static Serializable readJson(String json) throws IOException, ClassNotFoundException {
        return (Serializable) fstConfiguration.getObjectInput(json.getBytes()).readObject();
    }

    /**
     * Gson serialization to Json
     * @param object to serialize
     * @return JSn string
     */
    public static String writeJsonAPI(Serializable object) { return new Gson().toJson(object); }

    /**
     * Parse object as a Hashmap from json
     * @param json JSON string
     * @return Hashmap rep of object
     * @throws IOException on error
     * @throws ClassNotFoundException on error
     */
    public static HashMap readJsonAPI(String json) throws IOException, ClassNotFoundException {
        return new Gson().fromJson(json, HashMap.class);
    }

    /**
     * FST has difficulty with LinkedHashSet if shared references is false. And if set to true performance suffers.
     * So do custom serializer.
     */
    static class FSTLinkedHasMapSerializer extends FSTBasicObjectSerializer {

        @Override
        public void writeObject(FSTObjectOutput out, Object toWrite, FSTClazzInfo clzInfo, FSTClazzInfo.FSTFieldInfo referencedBy, int streamPosition) throws IOException {
            LinkedHashMap map = (LinkedHashMap)toWrite;

            out.writeInt(map.size());

            for (Object o : map.entrySet()) {
                Map.Entry entry = (Map.Entry)o;
                out.writeObject(entry.getKey());
                out.writeObject(entry.getValue());
            }
        }

        public Object instantiate(Class objectClass, FSTObjectInput in, FSTClazzInfo serializationInfo, FSTClazzInfo.FSTFieldInfo referencee, int streamPosition) throws Exception {
            int size = in.readInt();
            LinkedHashMap map = new LinkedHashMap();

            for (int i = 0; i < size; ++i) {
                map.put(in.readObject(), in.readObject());
            }
            return map;
        }
    }
}
