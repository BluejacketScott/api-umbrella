package gov.nrel.apiumbrella;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.avro.Schema;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.google.gson.JsonElement;

import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.indices.aliases.GetAliases;

public class App {
  protected static String DIR = System.getProperty("apiumbrella.dir",
    Paths.get("/tmp", "api-umbrella-logs").toString());
  protected static String ELASTICSEARCH_URL = System.getProperty("apiumbrella.elasticsearch_url",
    "http://localhost:9200");
  protected static int PAGE_SIZE = Integer
    .parseInt(System.getProperty("apiumbrella.page_size", "5000"));
  protected static int CONCURRENCY = Integer
    .parseInt(System.getProperty("apiumbrella.concurrency", "4"));
  protected static String START_DATE = System.getProperty("apiumbrella.start_date");

  // Define fields we won't migrate to the new database.
  protected static final Set<String> SKIP_FIELDS = new HashSet<String>(Arrays.asList(new String[] {
    // These URL-related fields are handled specially when dealing with the
    // request_url field.
    "request_scheme",
    "request_host",
    "request_path",
    "request_query",

    // We're no longer storing the special hierarchy field.
    "request_hierarchy",
    "request_path_hierarchy",

    // We're only storing the user_id, and not other fields that can be derived
    // from it (lookups on these fields will need to first query the user table,
    // and then perform queries base don user_id).
    "user_registration_source",
    "user_email",
    "api_key",

    // Old timer field from the nodejs stack, that's not relevant anymore, and
    // we don't need to migrate over (it was always somewhat duplicative).
    "internal_response_time",

    // Junk field we've seen on some old data.
    "_type" }));

  private Schema schema;
  private HashSet<String> schemaIntFields;
  private HashSet<String> schemaDoubleFields;
  private HashSet<String> schemaBooleanFields;
  private BigInteger globalHits = BigInteger.valueOf(0);

  public App() {
    ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);

    DateTime date = this.getStartDate();
    DateTime now = new DateTime();
    getSchema();
    detectSchemaFields();
    while(date.isBefore(now)) {
      Runnable worker = new DayWorker(this, date);
      executor.execute(worker);

      date = date.plus(Period.days(1));
    }

    executor.shutdown();
    while(!executor.isTerminated()) {
    }
    System.out.println("Finished all threads");
  }

  protected Schema getSchema() {
    if(this.schema == null) {
      InputStream is = App.class.getClassLoader().getResourceAsStream("log.avsc");
      try {
        this.schema = new Schema.Parser().parse(is);
      } catch(IOException e) {
        e.printStackTrace();
        System.exit(1);
      }
    }
    return this.schema;
  }

  protected HashSet<String> getSchemaIntFields() {
    return this.schemaIntFields;
  }

  protected HashSet<String> getSchemaDoubleFields() {
    return this.schemaDoubleFields;
  }

  protected HashSet<String> getSchemaBooleanFields() {
    return this.schemaBooleanFields;
  }

  private void detectSchemaFields() {
    this.schemaIntFields = new HashSet<String>();
    this.schemaDoubleFields = new HashSet<String>();
    this.schemaBooleanFields = new HashSet<String>();

    for(Schema.Field field : getSchema().getFields()) {
      Schema.Type type = field.schema().getType();
      if(type == Schema.Type.UNION) {
        for(Schema unionSchema : field.schema().getTypes()) {
          if(unionSchema.getType() != Schema.Type.NULL) {
            type = unionSchema.getType();
            break;
          }
        }
      }

      if(type == Schema.Type.INT) {
        this.schemaIntFields.add(field.name());
      } else if(type == Schema.Type.DOUBLE) {
        this.schemaDoubleFields.add(field.name());
      } else if(type == Schema.Type.BOOLEAN) {
        this.schemaBooleanFields.add(field.name());
      }
    }
  }

  protected synchronized BigInteger incrementGlobalHits(Integer total) {
    this.globalHits = this.globalHits.add(BigInteger.valueOf(total));
    return this.globalHits;
  }

  private DateTime getStartDate() {
    if(START_DATE != null) {
      DateTimeFormatter dateParser = ISODateTimeFormat.dateParser();
      return dateParser.parseDateTime(START_DATE);
    }

    JestClientFactory factory = new JestClientFactory();
    factory.setHttpClientConfig(
      new HttpClientConfig.Builder(ELASTICSEARCH_URL).multiThreaded(true).build());
    GetAliases aliases = new GetAliases.Builder().build();
    JestClient client = factory.getObject();
    DateTime first = null;
    try {
      JestResult result = client.execute(aliases);
      if(!result.isSucceeded()) {
        System.out.println(result.getErrorMessage());
        System.exit(1);
      }

      for(Entry<String, JsonElement> entry : result.getJsonObject().entrySet()) {
        Pattern pattern = Pattern.compile("^api-umbrella.*-([0-9]{4})-([0-9]{2})$");
        Matcher matches = pattern.matcher(entry.getKey());
        if(matches.find()) {
          DateTime indexDate = new DateTime(Integer.parseInt(matches.group(1)),
            Integer.parseInt(matches.group(2)), 1, 0, 0, 0, DateTimeZone.UTC);
          if(first == null || indexDate.isBefore(first)) {
            first = indexDate;
          }
        }
      }
    } catch(IOException e) {
      e.printStackTrace();
      System.exit(1);
    }

    return first;
  }

  public static void main(String[] args) {
    new App();
  }
}
