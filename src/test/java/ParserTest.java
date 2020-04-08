import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 *
 */
public class ParserTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParserTest.class);

    Parser parser = new Parser();
    private JSONObject before;
    private JSONObject after;
    private JSONObject expected;
    private JSONObject beforeWithWrongId;
    private JSONObject afterModified;
    private JSONObject beforeWithoutMetaTitle;
    private JSONObject afterWithMissedMeta;
    private JSONObject afterWithMissedCandidates;


    @Before
    public void setup() {
        try {
            this.before = readJSONFile("src/test/resources/before.json");
            this.after = readJSONFile("src/test/resources/after.json");
            this.expected = readJSONFile("src/test/resources/diff.json");

            this.beforeWithWrongId = readJSONFile("src/test/resources/before_with_wrong_id.json");
            this.afterModified = readJSONFile("src/test/resources/after_modified.json");
            this.beforeWithoutMetaTitle = readJSONFile("src/test/resources/before_without_meta_title.json");
            this.afterWithMissedMeta = readJSONFile("src/test/resources/after_with_missed_meta.json");
            this.afterWithMissedCandidates = readJSONFile("src/test/resources/after_with_missed_candidates.json");

        } catch (IOException e) {
            LOGGER.error("Unable to configure tests. Json Error parsing. Caused: ", e);
        }
    }

    @Test
    public void testDiffJsonsExpected() throws Exception {
        JSONObject result = parser.parse(this.before, this.after);
        Assert.assertTrue(expected.equals(result));
        LOGGER.info("expected json report: " + expected.toString());
        LOGGER.info("similar result: " + result.toString());
        System.out.println();
    }

    @Test
    public void testDiffJsonsUnexpected() throws Exception {
        JSONObject result = parser.parse(this.before, this.afterModified);
        Assert.assertFalse(expected.equals(result));
        LOGGER.info("expected json report: " + expected.toString());
        LOGGER.info("different result: " + result.toString());
        System.out.println();
    }

    @Test
    public void testWithWrongId() {
        Exception exception = null;
        try {
            JSONObject result = parser.parse(this.beforeWithWrongId, this.after);
        } catch (Exception e) {
            exception = e;
        }
        Assert.assertNotNull(exception);
        Assert.assertEquals("json objects have different identifiers", exception.getMessage());
    }

    @Test
    public void testWithOneNullJson() {
        NullPointerException exception = null;
        try {
            JSONObject result = parser.parse(null, this.after);
        } catch (Exception e) {
            exception = (NullPointerException) e;
        }
        Assert.assertNotNull(exception);
        Assert.assertEquals("first input json object must not be null", exception.getMessage());
    }

    @Test
    public void testWithMissedMetaTitle() {
        Exception exception = null;
        try {
            JSONObject result = parser.parse(this.beforeWithoutMetaTitle, this.after);
        } catch (Exception e) {
            exception = e;
        }
        Assert.assertNotNull(exception);
        Assert.assertEquals("meta data has missed fields", exception.getMessage());
    }

    @Test
    public void testWithMissedMeta() {
        Exception exception = null;
        try {
            JSONObject result = parser.parse(this.before, this.afterWithMissedMeta);
        } catch (Exception e) {
            exception = e;
        }
        Assert.assertNotNull(exception);
        Assert.assertEquals("meta field is missed", exception.getMessage());
    }

    @Test
    public void testWithMissedCandidates() {
        Exception exception = null;
        try {
            JSONObject result = parser.parse(this.before, this.afterWithMissedCandidates);
        } catch (Exception e) {
            exception = e;
        }
        Assert.assertNotNull(exception);
        Assert.assertEquals("candidates field is missed", exception.getMessage());
    }

    private JSONObject readJSONFile(String filename) throws JSONException, IOException {
        String jsonString = new String(Files.readAllBytes(Paths.get(filename)));
        JSONObject json = new ObjectMapper().readValue(jsonString, JSONObject.class);
        return json;
    }

}
