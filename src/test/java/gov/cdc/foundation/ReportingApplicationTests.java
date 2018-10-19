package gov.cdc.foundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.CoreMatchers;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import gov.cdc.helper.CombinerHelper;
import gov.cdc.helper.common.ServiceException;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = { 
		"logging.fluentd.host=fluentd", 
		"logging.fluentd.port=24224", 
		"kafka.brokers=kafka:29092", 
		"proxy.hostname=localhost",
		"security.oauth2.resource.user-info-uri=",
		"security.oauth2.protected=",
		"security.oauth2.client.client-id=",
		"security.oauth2.client.client-secret=",
		"ssl.verifying.disable=false"
	})
@AutoConfigureMockMvc
public class ReportingApplicationTests {

	@Autowired
	private TestRestTemplate restTemplate;
	@Autowired
	private MockMvc mvc;
	private String baseUrlPath = "/api/1.0/";
	private String configName = "fdnsmsreporting-junittests";

	@Before
	public void setup() throws JSONException, ServiceException {
		ObjectMapper objectMapper = new ObjectMapper();
		JacksonTester.initFields(this, objectMapper);
		
		// Define the object URL
		System.setProperty("OBJECT_URL", "http://fdns-ms-object:8083");
		// Define the combiner URL
		System.setProperty("COMBINER_URL", "http://fdns-ms-combiner:8085");

		// Add a config in combiner
		int retries = 10;
		int current = 0;
		boolean created = false;

		while (current < retries && !created)
			try {
				CombinerHelper.getInstance().createOrUpdateConfig(configName, new JSONObject("{\"file\":{\"template\":\"listOfCaseIdentifiers_$DATE$\",\"date-format\":\"YYYYMMddHHmmss\"},\"rows\":[{\"label\":\"CaseID\",\"jsonPath\":\"$.extractor.UCID.id\"},{\"label\":\"CaseIDHash\",\"jsonPath\":\"$.extractor.UCID.hash\"}]}"));
				created = true;
			} catch (Exception e) {
				e.printStackTrace();
				current++;
			}
	}

	@Test
	public void indexPage() {
		ResponseEntity<String> response = this.restTemplate.getForEntity("/", String.class);
		assertThat(response.getStatusCodeValue()).isEqualTo(200);
		assertThat(response.getBody(), CoreMatchers.containsString("FDNS Reporting Microservice"));
	}

	@Test
	public void indexAPI() {
		ResponseEntity<String> response = this.restTemplate.getForEntity(baseUrlPath, String.class);
		assertThat(response.getStatusCodeValue()).isEqualTo(200);
		assertThat(response.getBody(), CoreMatchers.containsString("version"));
	}

	@Test
	public void testAll() throws Exception {
		String jsonQuery = "{ \"query\":\"\", \"format\":\"json\", \"type\":\"indexing\", \"index\":\"" + configName + "\" }";
		String jobId = createJob(jsonQuery);
		getJobProgress(jobId, jsonQuery);
		jsonQuery = "{ \"query\":\"\", \"format\":\"xml\", \"type\":\"indexing\", \"index\":\"" + configName + "\" }";
		jobId = createJob(jsonQuery);
		getJobProgress(jobId, jsonQuery);
		jsonQuery = "{ \"query\":\"\", \"format\":\"csv\", \"type\":\"indexing\", \"index\":\"" + configName + "\", \"config\":\"combiner-config\" }";
		jobId = createJob(jsonQuery);
		getJobProgress(jobId, jsonQuery);
		jsonQuery = "{ \"query\":\"\", \"format\":\"xlsx\", \"type\":\"indexing\", \"index\":\"" + configName + "\", \"config\":\"combiner-config\" }";
		jobId = createJob(jsonQuery);
		getJobProgress(jobId, jsonQuery);
		jsonQuery = "{ \"query\":\"\", \"format\":\"csv\", \"type\":\"indexing\", \"index\":\"" + configName
				+ "\", \"mapping\":[{\"path\":\"$.extractor.version\",\"label\":\"MessageVersion\"},{\"path\":\"$.extractor.hash\",\"label\":\"MessageHash\"},{\"path\":\"$.extractor.UCID.hash\",\"label\":\"CaseID\"},{\"path\":\"$.extractor.timestamp.$numberLong\",\"label\":\"Timestamp\"},{\"path\":\"$.sourceType\",\"label\":\"Type\"},{\"path\":\"$.patient.name\",\"label\":\"PatientName\"},{\"path\":\"$.patient.dob\",\"label\":\"PatientDOB\"},{\"path\":\"$.patient.sex\",\"label\":\"PatientSex\"},{\"path\":\"$.observation.name\",\"label\":\"Condition\"},{\"path\":\"$.observation.code\",\"label\":\"ConditionCode\"},{\"path\":\"$.jurisdiction.name\",\"label\":\"ReportingFacility\"},{\"path\":\"$.jurisdiction.id\",\"label\":\"JurisdictionCode\"},{\"path\":\"$.ingestion.payloadName\",\"label\":\"MessagePayload\"},{\"path\":\"$.ingestion.fromPartyId\",\"label\":\"MessageFrom\"},{\"path\":\"$.ingestion.localFileName\",\"label\":\"MessageFileName\"},{\"path\":\"$.ingestion.receivedTime\",\"label\":\"MessageReceived\"},{\"path\":\"$.ingestion.messageRecipient\",\"label\":\"MessageRecipient\"}] }";
		jobId = createJob(jsonQuery);
		getJobProgress(jobId, jsonQuery);
		jsonQuery = "{ \"query\":\"\", \"format\":\"xlsx\", \"type\":\"indexing\", \"index\":\"" + configName
				+ "\", \"mapping\":[{\"path\":\"$.extractor.version\",\"label\":\"MessageVersion\"},{\"path\":\"$.extractor.hash\",\"label\":\"MessageHash\"},{\"path\":\"$.extractor.UCID.hash\",\"label\":\"CaseID\"},{\"path\":\"$.extractor.timestamp.$numberLong\",\"label\":\"Timestamp\"},{\"path\":\"$.sourceType\",\"label\":\"Type\"},{\"path\":\"$.patient.name\",\"label\":\"PatientName\"},{\"path\":\"$.patient.dob\",\"label\":\"PatientDOB\"},{\"path\":\"$.patient.sex\",\"label\":\"PatientSex\"},{\"path\":\"$.observation.name\",\"label\":\"Condition\"},{\"path\":\"$.observation.code\",\"label\":\"ConditionCode\"},{\"path\":\"$.jurisdiction.name\",\"label\":\"ReportingFacility\"},{\"path\":\"$.jurisdiction.id\",\"label\":\"JurisdictionCode\"},{\"path\":\"$.ingestion.payloadName\",\"label\":\"MessagePayload\"},{\"path\":\"$.ingestion.fromPartyId\",\"label\":\"MessageFrom\"},{\"path\":\"$.ingestion.localFileName\",\"label\":\"MessageFileName\"},{\"path\":\"$.ingestion.receivedTime\",\"label\":\"MessageReceived\"},{\"path\":\"$.ingestion.messageRecipient\",\"label\":\"MessageRecipient\"}] }";
		jobId = createJob(jsonQuery);
		getJobProgress(jobId, jsonQuery);
	}

	public String createJob(String jsonQuery) throws Exception {
		JSONObject query = new JSONObject(jsonQuery);
		MvcResult result = mvc.perform(post(baseUrlPath + "/jobs").content(jsonQuery).contentType("application/json")).andExpect(status().isOk()).andReturn();
		JSONObject body = new JSONObject(result.getResponse().getContentAsString());
		assertThat(body.getString("query").equals(query.getString("query")));
		assertThat(body.getString("format").equals(query.getString("format")));
		assertThat(body.getString("index").equals(query.getString("index")));
		assertThat(body.getString("status").equals("RUNNING"));
		assertThat(body.getInt("progress") == 0);
		assertThat(body.has("_id"));
		assertThat(body.has("events"));
		assertThat(body.getJSONArray("events").length() == 1);
		return body.getString("_id");
	}

	public void getJobProgress(String jobId, String jsonQuery) throws Exception {
		JSONObject query = new JSONObject(jsonQuery);
		MvcResult result = mvc.perform(get(baseUrlPath + "/jobs/" + jobId)).andExpect(status().isOk()).andReturn();
		JSONObject body = new JSONObject(result.getResponse().getContentAsString());
		assertThat(body.getString("query").equals(query.getString("query")));
		assertThat(body.getString("format").equals(query.getString("format")));
		assertThat(body.getString("index").equals(query.getString("index")));
		assertThat(body.getString("_id").equals(jobId));
	}

}
