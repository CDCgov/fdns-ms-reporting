package gov.cdc.foundation.helper;

import java.util.HashMap;
import java.util.Map;

import gov.cdc.helper.AbstractMessageHelper;

public class MessageHelper extends AbstractMessageHelper {

	public static final String CONST_REQUEST = "request";

	public static final String METHOD_REQUESTREPORT = "request";
	public static final String METHOD_RESTART = "restart";
	public static final String METHOD_INDEX = "index";

	public static final String ERROR_QUERY_PARAMETER_REQUIRED = "The parameter `query` is required in the JSON body.";
	public static final String ERROR_INDEX_PARAMETER_REQUIRED = "The parameter `index` is required in the JSON body.";
	public static final String ERROR_DATABASE_PARAMETER_REQUIRED = "The parameter `database` is required in the JSON body.";
	public static final String ERROR_COLLECTION_PARAMETER_REQUIRED = "The parameter `collection` is required in the JSON body.";
	public static final String ERROR_TYPE_PARAMETER_REQUIRED = "The parameter `type` is required in the JSON body.";
	public static final String ERROR_FORMAT_PARAMETER_REQUIRED = "The parameter `format` is required in the JSON body.";
	public static final String ERROR_CONFIG_OR_MAPPING_PARAMETER_REQUIRED = "At least, the parameter `config` representing the Combiner config or `mapping` is required when you want to export CSV or XLSX.";
	public static final String ERROR_FORMAT_NOT_VALID = "The following `format` is not valid: %s.";
	public static final String ERROR_TYPE_NOT_VALID = "The following `type` is invalid: %s. Valid `type` values are `object` and `indexing`.";

	private MessageHelper() {
		throw new IllegalAccessError("Helper class");
	}

	public static Map<String, Object> initializeLog(String method, String request) {
		Map<String, Object> log = new HashMap<>();
		log.put(MessageHelper.CONST_METHOD, method);
		log.put(MessageHelper.CONST_REQUEST, request);
		return log;
	}

	public static Map<String, Object> initializeLog(String method) {
		Map<String, Object> log = new HashMap<>();
		log.put(MessageHelper.CONST_METHOD, method);
		return log;
	}

}
