package gov.cdc.foundation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

@RunWith(SpringRunner.class)
@SpringBootTest(
		webEnvironment = WebEnvironment.RANDOM_PORT, 
		properties = { 
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
public class ReportingApplicationErrorTests {

	@Autowired
	private MockMvc mvc;
	private String baseUrlPath = "/api/1.0/";
	

	@Before
	public void setup() {
		// Define the object URL
		System.setProperty("OBJECT_URL", "http://fdns-ms-object:8083");
		// Define the combiner URL
		System.setProperty("COMBINER_URL", "http://fdns-ms-combiner:8085");
	}
	
	@Test
	public void failQuery1() throws Exception {
		String jsonQuery = "{ \"format\":\"json\", \"index\":\"config\" }";
		mvc.perform(post(baseUrlPath + "/jobs")
				.content(jsonQuery)
                .contentType("application/json"))
				.andExpect(status().isBadRequest());
	}
	
	@Test
	public void failQuery2() throws Exception {
		String jsonQuery = "{ \"query\":\"\", \"index\":\"config\" }";
		mvc.perform(post(baseUrlPath + "/jobs")
				.content(jsonQuery)
				.contentType("application/json"))
		.andExpect(status().isBadRequest());
	}
	
	@Test
	public void failQuery3() throws Exception {
		String jsonQuery = "{ \"query\":\"\", \"format\":\"json\"}";
		mvc.perform(post(baseUrlPath + "/jobs")
				.content(jsonQuery)
				.contentType("application/json"))
		.andExpect(status().isBadRequest());
	}
	
	@Test
	public void failQuery4() throws Exception {
		String jsonQuery = "{ \"query\":\"\", \"index\":\"config\" }";
		mvc.perform(post(baseUrlPath + "/jobs")
				.content(jsonQuery)
				.contentType("application/json"))
		.andExpect(status().isBadRequest());
	}
	
	@Test
	public void failQuery5() throws Exception {
		String jsonQuery = "{ \"query\":\"\", \"format\":\"csv\", \"index\":\"config\" }";
		mvc.perform(post(baseUrlPath + "/jobs")
				.content(jsonQuery)
				.contentType("application/json"))
		.andExpect(status().isBadRequest());
	}
	
	@Test
	public void failQuery6() throws Exception {
		String jsonQuery = "{ \"query\":\"\", \"format\":\"xlsx\", \"index\":\"config\" }";
		mvc.perform(post(baseUrlPath + "/jobs")
				.content(jsonQuery)
				.contentType("application/json"))
		.andExpect(status().isBadRequest());
	}
	
	@Test
	public void failQuery7() throws Exception {
		String jsonQuery = "{ }";
		mvc.perform(post(baseUrlPath + "/jobs")
				.content(jsonQuery)
				.contentType("application/json"))
		.andExpect(status().isBadRequest());
	}
	
	@Test
	public void failQuery8() throws Exception {
		mvc.perform(get(baseUrlPath + "/jobs/n-a"))
			.andExpect(status().isBadRequest());
	}
	
}
