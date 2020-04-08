import net.sf.json.JSONObject;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The <code>Parser</code> class contains one method 'parse' that returns a generated json with the fields that have differences.
 *
 * <p>The method expects that the json schema of input objects is constant and contains specified fields: <code>id, meta, candidates<code>.
 * Whatever additional fields are presented in the input jsons  - report will ignore that and include to the result only predefined fields.
 *
 * <p>Also, the method expects that the "meta" field has constant structure: <code>title, startTime, endTime<code>. Otherwise, will throw exception - "meta data has missed fields".
 */
public class Parser {

    private static final String DATE_TIME_FORMAT_PATTERN = "yyyy-MM-dd'T'HH:mm:ssX";
    private static final String TIME_ZONE = "UTC+2";

    public JSONObject parse(JSONObject before, JSONObject after) throws Exception {
        Objects.requireNonNull(before, "first input json object must not be null");
        Objects.requireNonNull(after, "second input json object must not be null");

        JSONObject diffResult = new JSONObject();

        //we don't generate report for elements with different ids
        if (!before.get(Fields.id.name()).equals(after.get(Fields.id.name()))) {
            throw new Exception("json objects have different identifiers");
        }

        //find and add diffs if exist for "meta" block
        List<Map<String, Object>> metaDiffs = getMetaDataDiff((Map)before.get(Fields.meta.name()), (Map)after.get(Fields.meta.name()));
        if (!metaDiffs.isEmpty()) {
            diffResult.put(Fields.meta.name(), metaDiffs);
        }

        //find and add diffs if exists for "candidates" block
        Map<String, List<Object>> candidatesDiffs = getCandidatesDataDiff((List)before.get(Fields.candidates.name()), (List)after.get(Fields.candidates.name()));
        if (!candidatesDiffs.isEmpty()) {
            diffResult.put(Fields.candidates.name(), candidatesDiffs);
        }
        return diffResult;
    }

    private List<Map<String, Object>> getMetaDataDiff(Map<String, Object> beforeMeta, Map<String, Object> afterMeta) throws Exception {
        //meta fields must have constant structure with "title", "start time", "end time"
        verifyMetaFields(beforeMeta);
        verifyMetaFields(afterMeta);

        List<Map<String, Object>> result = new ArrayList<>();
        beforeMeta.forEach((key, value) -> {
            if (!value.equals(afterMeta.get(key))) {
                Map<String, Object> diff = new LinkedHashMap<>();
                diff.put(Fields.field.name(), key);
                //for time fields the timezone should be converted to CEST (Oslo - UTC+2).
                //not the best way of definition for time fields but for this case is ok imo
                if (key.toString().contains("Time")) {
                    diff.put(Fields.before.name(), convertTime(value));
                    diff.put(Fields.after.name(), convertTime(afterMeta.get(key)));
                } else {
                    diff.put(Fields.before.name(), value);
                    diff.put(Fields.after.name(), afterMeta.get(key));
                }
                result.add(diff);
            }
        });
        return result;
    }

    private void verifyMetaFields(Map<String, Object> meta) throws Exception {
        Objects.requireNonNull(meta, "meta field is missed");
        Set<String> fields = meta.keySet();
        List<String> constFields = Arrays.asList(Fields.title.name(), Fields.startTime.name(), Fields.endTime.name());
        if(!fields.containsAll(constFields)) {
            throw new Exception("meta data has missed fields");
        }
    }

    private String convertTime(Object dateString) {
        OffsetDateTime date = OffsetDateTime.parse(dateString.toString());
        DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                .appendPattern(DATE_TIME_FORMAT_PATTERN)
                .toFormatter(Locale.ENGLISH);
        return formatter.withZone(ZoneId.of(TIME_ZONE)).format(date);
    }

    private Map<String, List<Object>> getCandidatesDataDiff(List<Map<String, Object>> beforeCandidates,
                                                            List<Map<String, Object>> afterCandidates) {
        Objects.requireNonNull(beforeCandidates, "candidates field is missed");
        Objects.requireNonNull(afterCandidates, "candidates field is missed");

        Map<String, List<Object>> result = new LinkedHashMap<>();

        //we need convert "after" collection to Map for faster find elements there (by id).
        Map<Integer, Object> afterCandidatesMap = afterCandidates.stream()
                .collect(Collectors.toMap(e -> (Integer) e.get(Fields.id.name()), e -> e));

        //iterate over first collection and try find these elements in converted map by Id
        beforeCandidates.forEach(map -> {
            Map<String, Object> elemFromAfterById = (Map)afterCandidatesMap.get(map.get(Fields.id.name()));
            if (elemFromAfterById == null) {
                //it means that element not presented in second collection and can be marked as "removed".
                List<Object> removedElements = result.computeIfAbsent(Fields.removed.name(), value -> new ArrayList<>());
                removedElements.add(new IdRow(map.get(Fields.id.name())));
            } else {
                //otherwise an element presents in both collections and we need compare if they are similar. if not - mark it as "edited".
                if (hasDifferentFieldValues(map, elemFromAfterById)) {
                    List<Object> editedElements = result.computeIfAbsent(Fields.edited.name(), value -> new ArrayList<>());
                    editedElements.add(new IdRow(map.get(Fields.id.name())));
                }
            }
        });

        /*
        now we need convert "before" collection to Map for faster find elements there (by id).
        for find and mark items as "added" if they presented in "after" collection and missed in "before"
        */
        Map<Integer, Object> beforeCandidatesMap = beforeCandidates.stream()
                .collect(Collectors.toMap(e -> (Integer) e.get(Fields.id.name()), e -> e));
        afterCandidates.forEach(map -> {
            Map<String, Object> elemFromBeforeById = (Map) beforeCandidatesMap.get(map.get(Fields.id.name()));
            if (elemFromBeforeById == null) {
                List<Object> addedElements = result.computeIfAbsent(Fields.added.name(), value -> new ArrayList<>());
                addedElements.add(new IdRow(map.get(Fields.id.name())));
            }
        });

        return result;
    }

    private boolean hasDifferentFieldValues(Map<String, Object> map, Map<String, Object> elemFromAfterById) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!value.equals(elemFromAfterById.get(key))) {
                return true;
            }
        }
        return false;
    }

    private enum Fields {
        id,
        meta,
        candidates,
        field,
        before,
        after,
        removed,
        edited,
        added,
        title,
        startTime,
        endTime
    }

    public class IdRow {
        private Object id;

        protected IdRow(Object id) {
            this.id = id;
        }

        public Object getId() {
            return id;
        }


    }

}
