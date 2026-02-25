package nl.appsource.cardserver.service;

import com.couchbase.client.java.json.JsonObject;
import lombok.RequiredArgsConstructor;
import org.springframework.data.couchbase.core.convert.MappingCouchbaseConverter;
import org.springframework.data.couchbase.core.mapping.CouchbaseDocument;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CouchbaseConversionService {

    private final MappingCouchbaseConverter converter;

    public <T> T convertStringToEntity(final byte[] jsonString, final String id, final Class<T> targetClass) {
        // 1. Parse String to SDK JsonObject
        JsonObject jsonObject = JsonObject.fromJson(jsonString);

        // 2. Convert to standard Java Map (resolves the JsonArray error)
        Map<String, Object> standardMap = jsonObject.toMap();

        // 3. Populate CouchbaseDocument
        CouchbaseDocument source = new CouchbaseDocument(id);

        // FIX: Iterate and use put() since putAll() does not exist
        standardMap.forEach(source::put);

        // 4. Use the converter
        return converter.read(targetClass, source);
    }
}
