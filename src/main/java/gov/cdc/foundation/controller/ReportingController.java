package gov.cdc.foundation.controller;

import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import gov.cdc.foundation.helper.KafkaHelper;
import gov.cdc.foundation.helper.LoggerHelper;
import gov.cdc.foundation.helper.MessageHelper;
import gov.cdc.foundation.security.Authz;
import gov.cdc.helper.CombinerHelper;
import gov.cdc.helper.ErrorHandler;
import gov.cdc.helper.ObjectHelper;
import gov.cdc.helper.common.ServiceException;
import io.swagger.annotations.ApiParam;
import springfox.documentation.annotations.ApiIgnore;

@Controller
@EnableAutoConfiguration
@RequestMapping("/api/1.0/")
public class ReportingController {

	@Value("${version}")
	private String version;

	private static final Logger logger = Logger.getLogger(ReportingController.class);

	@Autowired
	private Authz auth;

	private String kafkaTopicArchive;
	private String kafkaTopicCombine;

	private ReportingController(
		@Value("${kafka.topic.archive}") String kafkaTopicArchive,
		@Value("${kafka.topic.combine}") String kafkaTopicCombine
	) {
		this.kafkaTopicArchive = kafkaTopicArchive;
		this.kafkaTopicCombine = kafkaTopicCombine;
	}

	@RequestMapping(method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<?> index() throws IOException {
		ObjectMapper mapper = new ObjectMapper();

		Map<String, Object> log = MessageHelper.initializeLog(MessageHelper.METHOD_INDEX);

		try {
			JSONObject json = new JSONObject();
			json.put("version", version);
			return new ResponseEntity<>(mapper.readTree(json.toString()), HttpStatus.OK);
		} catch (Exception e) {
			logger.error(e);
			LoggerHelper.log(MessageHelper.METHOD_INDEX, log);

			return ErrorHandler.getInstance().handle(e, log);
		}
	}

	@RequestMapping(
		value = "/jobs",
		method = RequestMethod.POST,
		produces = MediaType.APPLICATION_JSON_VALUE,
		consumes = MediaType.APPLICATION_JSON_VALUE
	)
	@ResponseBody
	public ResponseEntity<?> request(
		Principal principal,
		@ApiIgnore @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
		@RequestBody String reportRequest
	) throws IOException {
		Map<String, Object> log = MessageHelper.initializeLog(MessageHelper.METHOD_REQUESTREPORT, reportRequest);
		ObjectMapper mapper = new ObjectMapper();

		try {

			JSONObject request = new JSONObject(reportRequest);
			if (!request.has("query"))
				throw new ServiceException(MessageHelper.ERROR_QUERY_PARAMETER_REQUIRED);
			if (!request.has("type") || request.getString("type").isEmpty())
				throw new ServiceException(MessageHelper.ERROR_TYPE_PARAMETER_REQUIRED);
			if (!request.has("format") || request.getString("format").isEmpty())
				throw new ServiceException(MessageHelper.ERROR_FORMAT_PARAMETER_REQUIRED);

			// check type value
			if (!(request.getString("type").equalsIgnoreCase("object") || request.getString("type").equalsIgnoreCase("indexing")))
				throw new ServiceException(String.format(MessageHelper.ERROR_TYPE_NOT_VALID, request.getString("type")));

			// check for type based requirements
			if (request.getString("type").equalsIgnoreCase("object")) {
				if (!request.has("database") || request.getString("database").isEmpty())
					throw new ServiceException(MessageHelper.ERROR_DATABASE_PARAMETER_REQUIRED);
				if (!request.has("collection") || request.getString("collection").isEmpty())
					throw new ServiceException(MessageHelper.ERROR_COLLECTION_PARAMETER_REQUIRED);
			} else {
				if (!request.has("index") || request.getString("index").isEmpty())
					throw new ServiceException(MessageHelper.ERROR_INDEX_PARAMETER_REQUIRED);
			}

			request.put("format", request.getString("format").toLowerCase());
			String kafkaTopic;
			if (request.getString("format").equalsIgnoreCase("json") || request.getString("format").equalsIgnoreCase("xml"))
				kafkaTopic = kafkaTopicArchive;
			else if (request.getString("format").equalsIgnoreCase("csv") || request.getString("format").equalsIgnoreCase("xlsx"))
				kafkaTopic = kafkaTopicCombine;
			else
				throw new ServiceException(String.format(MessageHelper.ERROR_FORMAT_NOT_VALID, request.getString("format")));

			try {
				checkAuthorization(request, principal != null ? principal.getName() : "");
			} catch (Exception e) {
				logger.error(e);
				MessageHelper.append(log, e);
				LoggerHelper.log(MessageHelper.METHOD_REQUESTREPORT, log);
				JsonNode error = null;
				try {
					error = mapper.readTree(new JSONObject(log).toString());
				} catch (Exception e2) {
					// Do nothing
					logger.error(e2);
				}
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
			}

			if (request.getString("format").equalsIgnoreCase("csv") || request.getString("format").equalsIgnoreCase("xlsx")) {
				boolean configProvided = true;
				boolean mappingProvided = true;
				if (!request.has("config") || request.getString("config").isEmpty())
					configProvided = false;
				if (!request.has("mapping") || !(request.get("mapping") instanceof JSONArray))
					mappingProvided = false;

				if (!configProvided && !mappingProvided)
					throw new ServiceException(MessageHelper.ERROR_CONFIG_OR_MAPPING_PARAMETER_REQUIRED);

				if (!configProvided && mappingProvided) {
					// We need to create the temporary config in combiner
					String config = "public-" + UUID.randomUUID().toString();
					JSONObject json = new JSONObject();
					json.put("file", new JSONObject());
					json.getJSONObject("file").put("template", "report");
					json.put("rows", new JSONArray());
					JSONArray rows = json.getJSONArray("rows");

					// Create the config
					JSONArray mapping = request.getJSONArray("mapping");
					for (int i = 0; i < mapping.length(); i++) {
						JSONObject row = new JSONObject();
						row.put("label", mapping.getJSONObject(i).getString("label"));
						row.put("jsonPath", mapping.getJSONObject(i).getString("path"));
						rows.put(row);
					}

					// Save the config
					CombinerHelper.getInstance(authorizationHeader).createOrUpdateConfig(config, json);

					// Update the query
					request.put("config", config);

					// Mark that the config needs to be deleted at the end
					request.put("deleteConfig", true);
				}

			}

			// Create the job ID
			String jobId = UUID.randomUUID().toString();
			request.put("_id", jobId);

			// Initialize the progress
			request.put("progress", 0);
			request.put("status", "RUNNING");

			// Initialize the list of events
			JSONObject event = new JSONObject();
			event.put("timestamp", Instant.now().toString());
			event.put("event", "JOB_CREATED");
			request.put("events", new JSONArray());
			request.getJSONArray("events").put(event);

			// Push to Kafka
			KafkaHelper.getInstance().sendMessage(request.toString(), kafkaTopic);

			// Save it in Mongo
			ObjectHelper.getInstance(authorizationHeader).createObject(request, jobId);

			// Return the JSON object
			return new ResponseEntity<>(mapper.readTree(request.toString()), HttpStatus.OK);

		} catch (Exception e) {
			logger.error(e);
			LoggerHelper.log(MessageHelper.METHOD_REQUESTREPORT, log);

			return ErrorHandler.getInstance().handle(e, log);
		}
	}

	private void checkAuthorization(
		JSONObject request,
		String username
	) throws Exception {
		if (auth.isSecured()) {
			// Get scopes
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			OAuth2Authentication oAuth2Authentication;

			if (authentication instanceof OAuth2Authentication) {
				oAuth2Authentication = (OAuth2Authentication) authentication;
			} else {
				throw new IllegalStateException("Authentication not supported!");
			}

			// Define the list of required scopes
			Set<String> requiredScopes = new HashSet<>();
			Set<String> extraScopes = new HashSet<>();
			requiredScopes.add("object");
			requiredScopes.add("storage");
			if (request.getString("format").equalsIgnoreCase("csv")) {
				requiredScopes.add("combiner");
			}
			if (request.getString("format").equalsIgnoreCase("xlsx")) {
				requiredScopes.add("combiner");
				requiredScopes.add("msft-utils");
			}

			// We need the scope to use the index or object
			if (request.getString("type").equalsIgnoreCase("object")) {
				requiredScopes.add("object." + request.getString("database") + "." + request.getString("collection"));
			} else {
				requiredScopes.add("indexing");
				requiredScopes.add("indexing." + request.getString("index"));
			}

			// check export scope
			if (request.has("export") && request.getJSONObject("export").has("drawerName"))
				if (request.getJSONObject("export").getString("drawerName").equalsIgnoreCase(username))
					extraScopes.add("storage." + request.getJSONObject("export").getString("drawerName"));
				else
					requiredScopes.add("storage." + request.getJSONObject("export").getString("drawerName"));

			logger.debug("Required list of scopes: " + requiredScopes);
			logger.debug("Available list of scopes: " + oAuth2Authentication.getOAuth2Request().getScope());

			if (!oAuth2Authentication.getOAuth2Request().getScope().containsAll(requiredScopes)) {
				Set<String> missingScopes = new HashSet<>(requiredScopes);
				missingScopes.removeAll(oAuth2Authentication.getOAuth2Request().getScope());
				throw new Exception("The following scope(s) are missing: " + missingScopes);
			}

			// Provide the list of required scopes to Kafka
			HashSet<String> allScopes = new HashSet<String>(oAuth2Authentication.getOAuth2Request().getScope());
			allScopes.addAll(extraScopes);
			request.put("scopes", new JSONArray(allScopes));
		}
	}

	@RequestMapping(
		value = "/jobs/{id}",
		method = RequestMethod.PUT,
		produces = MediaType.APPLICATION_JSON_VALUE
	)
	@ResponseBody
	public ResponseEntity<?> restart(
		@ApiIgnore @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
		@ApiParam(value = "Job Id") @PathVariable(value = "id") String id
	) throws IOException {
		Map<String, Object> log = new HashMap<>();
		log.put(MessageHelper.CONST_METHOD, MessageHelper.METHOD_RESTART);
		log.put("jobId", id);

		ObjectMapper mapper = new ObjectMapper();

		try {

			JSONObject job = ObjectHelper.getInstance(authorizationHeader).getObject(id);

			String kafkaTopic;
			if (job.getString("format").equalsIgnoreCase("json") || job.getString("format").equalsIgnoreCase("xml"))
				kafkaTopic = kafkaTopicArchive;
			else if (job.getString("format").equalsIgnoreCase("csv") || job.getString("format").equalsIgnoreCase("xlsx"))
				kafkaTopic = kafkaTopicCombine;
			else
				throw new ServiceException(String.format(MessageHelper.ERROR_FORMAT_NOT_VALID, job.getString("format")));

			// Initialize the progress
			job.put("progress", 0);
			job.put("status", "RUNNING");

			// Initialize the list of events
			JSONObject event = new JSONObject();
			event.put("timestamp", Instant.now().toString());
			event.put("event", "RESTARTED");
			job.getJSONArray("events").put(event);

			// Push to Kafka
			KafkaHelper.getInstance().sendMessage(job.toString(), kafkaTopic);

			// Save it in Mongo
			ObjectHelper.getInstance(authorizationHeader).updateObject(id, job);

			// Return the JSON object
			return new ResponseEntity<>(mapper.readTree(job.toString()), HttpStatus.OK);

		} catch (Exception e) {
			logger.error(e);
			LoggerHelper.log(MessageHelper.METHOD_REQUESTREPORT, log);

			return ErrorHandler.getInstance().handle(e, log);
		}
	}

	@RequestMapping(
		value = "/jobs/{id}",
		method = RequestMethod.GET,
		produces = MediaType.APPLICATION_JSON_VALUE
	)
	@ResponseBody
	public ResponseEntity<?> getProgress(
		@ApiIgnore @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
		@ApiParam(value = "Job Id") @PathVariable(value = "id") String id
	) throws IOException {
		Map<String, Object> log = MessageHelper.initializeLog(MessageHelper.METHOD_REQUESTREPORT, id);
		ObjectMapper mapper = new ObjectMapper();

		try {
			// Return the JSON object
			return new ResponseEntity<>(
				mapper.readTree(
					ObjectHelper.getInstance(authorizationHeader).getObject(id).toString()
				),
				HttpStatus.OK
			);
		} catch (Exception e) {
			logger.error(e);
			LoggerHelper.log(MessageHelper.METHOD_REQUESTREPORT, log);

			return ErrorHandler.getInstance().handle(e, log);
		}
	}

}