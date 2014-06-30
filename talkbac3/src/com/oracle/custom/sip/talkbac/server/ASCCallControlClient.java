package com.oracle.custom.sip.talkbac.server;

import java.io.IOException;
import java.util.List;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;

import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.codehaus.jackson.map.DeserializationConfig.Feature;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize;

import com.acmepacket.asc.ws.common.CallControlCallResultType;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.core.util.MultivaluedMapImpl;

public class ASCCallControlClient {
	static String username;
	static String password;
	
	static String ENDPOINT;

	final static String CALL_RESOURCE = "cms/action/call-control-call";
	final static String DISCONNECT_RESOURCE = "cms/action/call-control-disconnect";
	final static String TRANSFER_RESOURCE = "cms/action/call-control-transfer";
	final static String HOLD_RESOURCE = "cms/action/call-control-hold";
	final static String RETRIEVE_RESOURCE = "cms/action/call-control-retrieve";
	final static String DIAL_RESOURCE = "cms/action/call-control-dial";
	final static String MUTE_RESOURCE = "cms/action/call-control-mute-on";
	final static String UNMUTE_RESOURCE = "cms/action/call-control-mute-off";
	final static String REDIRECT_RESOURCE = "cms/action/call-control-redirect";
	final static String ACCEPT_RESOURCE = "cms/action/call-control-accept";
	final static String REJECT_RESOURCE = "cms/action/call-control-reject";

	ClientConfig clientConfig;

	static Logger logger = Logger.getLogger(ASCCallControlClient.class);

	private static NewCookie JSESSIONID_WS = null;

	public ASCCallControlClient(String host, int port) {
		ENDPOINT = "http://" + host + ":" + port + "/";

		clientConfig = new DefaultClientConfig();
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
		JacksonJsonProvider provider = new JacksonJsonProvider(mapper);
		clientConfig.getSingletons().add(provider);
	}
	
	public String authenticate(String uname, String pwd) throws Exception {
		logger.info("Authenticating user: " + username);
		username = uname;
		password = pwd;
		

		ClientConfig clientConfig = new DefaultClientConfig();
		Client client = Client.create(clientConfig);
		client.addFilter(new HTTPBasicAuthFilter(username, password));
		WebResource resource = client.resource(ENDPOINT + "cms?login=true&username=" + username + "&password=" + password + "&output=json");
		Builder builder = resource.getRequestBuilder();
		builder = builder.accept(javax.ws.rs.core.MediaType.APPLICATION_JSON);
		builder = builder.type(javax.ws.rs.core.MediaType.APPLICATION_JSON);

		ClientResponse response = builder.get(ClientResponse.class);

		List<NewCookie> cookies = response.getCookies();

		for (NewCookie cookie : cookies) {
			if (cookie.getName().matches("JSESSIONID_WS")) {
				JSESSIONID_WS = cookie;
			}
		}

		if(JSESSIONID_WS != null){
			return JSESSIONID_WS.getValue();
		}else{
			return null;
		}
		

	}

	public CallControlCallResultType disconnect(long handle) throws JsonGenerationException, JsonMappingException, IOException, Exception {
		MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
		queryParams.add("handle", handle+"");

		Client client = Client.create(clientConfig);
		WebResource resource = client.resource(ENDPOINT + DISCONNECT_RESOURCE);
		CallControlCallResultType result = parseResult(queryParams, resource, false);
		return result;
	}
	
	/********************************

	public CallControlCallResultType transfer(String requestId, String endpoint) throws JsonGenerationException, JsonMappingException, IOException {
		MultivaluedMap queryParams = new MultivaluedMapImpl();
		queryParams.add("requestId", requestId);
		queryParams.add("endpoint", endpoint);

		Client client = Client.create(clientConfig);
		WebResource resource = client.resource(ENDPOINT + TRANSFER_RESOURCE);
		CallControlCallResultType result = parseResult(queryParams, resource);
		return result;
	}

	public CallControlCallResultType hold(String requestId) throws JsonGenerationException, JsonMappingException, IOException {
		MultivaluedMap queryParams = new MultivaluedMapImpl();
		queryParams.add("requestId", requestId);

		Client client = Client.create(clientConfig);
		WebResource resource = client.resource(ENDPOINT + HOLD_RESOURCE);
		CallControlCallResultType result = parseResult(queryParams, resource);
		return result;

	}

	public CallControlCallResultType retrieve(String requestId) throws JsonGenerationException, JsonMappingException, IOException {
		MultivaluedMap queryParams = new MultivaluedMapImpl();
		queryParams.add("requestId", requestId);

		Client client = Client.create(clientConfig);
		WebResource resource = client.resource(ENDPOINT + RETRIEVE_RESOURCE);
		CallControlCallResultType result = parseResult(queryParams, resource);
		return result;
	}

	public CallControlCallResultType dial(String requestId, String digits) throws JsonGenerationException, JsonMappingException, IOException {
		MultivaluedMap queryParams = new MultivaluedMapImpl();
		queryParams.add("requestId", requestId);
		queryParams.add("digits", digits);

		Client client = Client.create(clientConfig);
		WebResource resource = client.resource(ENDPOINT + DIAL_RESOURCE);
		CallControlCallResultType result = parseResult(queryParams, resource);
		return result;

	}

	public CallControlCallResultType mute(String requestId) throws JsonGenerationException, JsonMappingException, IOException {
		MultivaluedMap queryParams = new MultivaluedMapImpl();
		queryParams.add("requestId", requestId);

		Client client = Client.create(clientConfig);
		WebResource resource = client.resource(ENDPOINT + MUTE_RESOURCE);
		CallControlCallResultType result = parseResult(queryParams, resource);
		return result;

	}

	public CallControlCallResultType un_mute(String requestId) throws JsonGenerationException, JsonMappingException, IOException {
		MultivaluedMap queryParams = new MultivaluedMapImpl();
		queryParams.add("requestId", requestId);

		Client client = Client.create(clientConfig);
		WebResource resource = client.resource(ENDPOINT + UNMUTE_RESOURCE);
		CallControlCallResultType result = parseResult(queryParams, resource);
		return result;

	}

	public CallControlCallResultType redirect(String requestId, String endpoint) throws JsonGenerationException, JsonMappingException, IOException {
		MultivaluedMap queryParams = new MultivaluedMapImpl();
		queryParams.add("requestId", requestId);
		queryParams.add("endpoint", endpoint);

		Client client = Client.create(clientConfig);
		WebResource resource = client.resource(ENDPOINT + REDIRECT_RESOURCE);
		CallControlCallResultType result = parseResult(queryParams, resource);
		return result;
	}

	public CallControlCallResultType accept(String requestId, String endpoint) throws JsonGenerationException, JsonMappingException, IOException {
		MultivaluedMap queryParams = new MultivaluedMapImpl();
		queryParams.add("requestId", requestId);
		queryParams.add("endpoint", endpoint);

		Client client = Client.create(clientConfig);
		WebResource resource = client.resource(ENDPOINT + ACCEPT_RESOURCE);
		CallControlCallResultType result = parseResult(queryParams, resource);
		return result;

	}

	public CallControlCallResultType reject(String requestId, String endpoint) throws JsonGenerationException, JsonMappingException, IOException {
		MultivaluedMap queryParams = new MultivaluedMapImpl();
		queryParams.add("requestId", requestId);
		queryParams.add("endpoint", endpoint);

		Client client = Client.create(clientConfig);
		WebResource resource = client.resource(ENDPOINT + REJECT_RESOURCE);
		CallControlCallResultType result = parseResult(queryParams, resource);
		return result;

	}
	********************************/

	public CallControlCallResultType call(String requestId, String origin, String destination) throws JsonGenerationException, JsonMappingException,
			IOException {

		MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
		queryParams.add("to", destination);
		queryParams.add("from", origin);
		queryParams.add("requestId", requestId);
		queryParams.add("async", "enabled");

		Client client = Client.create(clientConfig);
		WebResource resource = client.resource(ENDPOINT + CALL_RESOURCE);
		CallControlCallResultType result = parseResult(queryParams, resource, true);
		return result;
	}

	private CallControlCallResultType parseResult(MultivaluedMap<String, String> queryParams, WebResource resource, boolean returnResult) throws JsonProcessingException, IOException {
		queryParams.add("output", "json");

		Builder builder = resource.queryParams(queryParams).getRequestBuilder();

		builder = builder.accept(javax.ws.rs.core.MediaType.APPLICATION_JSON);
		builder = builder.type(javax.ws.rs.core.MediaType.APPLICATION_JSON);
		builder = builder.cookie(JSESSIONID_WS);

		String resultStr = builder.get(String.class);

		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
		JsonNode rootNode = mapper.readTree(resultStr);
		if(returnResult)
		{
			JsonNode ccr = rootNode.path("ExtActionResponse").path("structure").path("CallControlCallResult");
			return mapper.readValue(ccr, CallControlCallResultType.class);
		}
		else return null;
	}

	public static void main(String[] args) throws Exception {
		// ASCCallControlClient client = new
		// ASCCallControlClient("192.168.1.200", 8080);
		// client.authenticate("talkbac", "talkbac");
		// client.call("1234", "sip:mini@vorpal.net", "sip:iphone@vorpal.net");

		String respStr = "" + "{\"ExtActionResponse\": {" + "	  \"info\": \"343287974350384476-13155499:13155500\"," + "	  \"resultCode\": \"0\","
				+ "	  \"resultStr\": \"Success\"," + "	  \"structure\": {\"CallControlCallResult\": {" + "	    \"inCallLegHandle\": \"13155500\","
				+ "	   \"outCallLegHandle\": \"13155499\"," + "	    \"requestId\": \"123XYZ\"," + "	    \"sessionId\": \"343287974350384476\"" + "	  }}"
				+ "	}}	";

		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
		JsonNode rootNode = mapper.readTree(respStr);
		// String info =
		// rootNode.path("ExtActionResponse").path("info").asText();
		// String resultCode =
		// rootNode.path("ExtActionResponse").path("resultCode").asText();
		// String resultStr =
		// rootNode.path("ExtActionResponse").path("resultStr").asText();
		// String requestId =
		// rootNode.path("ExtActionResponse").path("structure").path("CallControlCallResult").path("requestId").asText();
		// String sessionId =
		// rootNode.path("ExtActionResponse").path("structure").path("CallControlCallResult").path("sessionId").asText();

		// String ccr =
		// rootNode.path("ExtActionResponse").path("info").asText();
		// // String ccr =
		// rootNode.path("ExtActionResponse").path("structure").asText();
		// // String ccr =
		// rootNode.path("ExtActionResponse").path("structure").path("CallControlCallResult").asText();
		// System.out.println(ccr);

		JsonNode ccr = rootNode.path("ExtActionResponse").path("structure").path("CallControlCallResult");
		CallControlCallResultType result = mapper.readValue(ccr, CallControlCallResultType.class);

		System.out.println(mapper.writeValueAsString(result));

		/*
		 * System.out.println("info: "+info);
		 * System.out.println("resultCode: "+resultCode);
		 * System.out.println("resultStr: "+resultStr);
		 * System.out.println("requestId: "+requestId);
		 * System.out.println("sessionId: "+sessionId);
		 */

	}

}
