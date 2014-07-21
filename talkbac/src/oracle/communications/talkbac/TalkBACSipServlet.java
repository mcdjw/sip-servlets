package oracle.communications.talkbac;

import java.io.IOException;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.sip.Address;
import javax.servlet.sip.ServletParseException;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletContextEvent;
import javax.servlet.sip.SipServletListener;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipSessionsUtil;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.URI;
import javax.servlet.sip.annotation.SipApplicationKey;
import javax.servlet.sip.annotation.SipListener;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import weblogic.kernel.KernelLogManager;

@SipListener
public class TalkBACSipServlet extends SipServlet implements SipServletListener {
	static Logger logger;
	{
		logger = Logger.getLogger(TalkBACSipServlet.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	private final static String CALL_CONTROL = "CALL_CONTROL";
	private final static String REQUEST_ID = "REQUEST_ID";
	private final static String FROM_URI = "FROM_URI";
	private final static String TO_URI = "TO_URI";
	private final static String TPCC_SESSION_ID = "TPCC_SESSION_ID";
	private final static String DTMF_RELAY = "application/dtmf-relay";

	
	
	@Resource
	public static SipFactory factory;

	@Resource
	public static SipSessionsUtil util;

	@SipApplicationKey
	public static String sessionKey(SipServletRequest req) {
		return generateKey(req.getFrom());
	}

	public static String generateKey(Address address) {
		SipURI uri = (SipURI) address.getURI();
		String key = uri.getUser().toLowerCase() + "@" + uri.getHost().toLowerCase();
		return key;
	}

	@Override
	protected void doRegister(SipServletRequest request) throws ServletException, IOException {
		// The REGISTER method servers as a keep-alive for the client web socket
		// connection.
		SipApplicationSession appSession = request.getApplicationSession();
		int expires = request.getExpires();
		appSession.setExpires(expires);
		request.getProxy().proxyTo(request.getRequestURI());
	}

	@Override
	public void servletInitialized(SipServletContextEvent event) {
		logger.info(event.getSipServlet().getServletName() + " initialized.");





	}


	@Override
	protected void doMessage(SipServletRequest request) throws ServletException, IOException {
		String requestId = null;
		String callControl = null;
		SipApplicationSession appSession;


		System.out.println("TalkBACSipServlet.doMessage");
		System.out.println(request);
		System.out.println("-------------------------");
		
		
		
		request.createResponse(200).send();

		try {

			ObjectMapper objectMapper = new ObjectMapper();
			JsonNode rootNode = objectMapper.readTree(request.getContent().toString());
			requestId = rootNode.path("request_id").asText();

			String origin, destination, endpoint;
			callControl = rootNode.path("call_control").asText();

			switch (callControl.hashCode()) {
			case 3045982: // call
				// connect(requestId, rootNode.path("origin").asText(),
				// rootNode.path("destination").asText());

				origin = rootNode.path("origin").asText();
				destination = rootNode.path("destination").asText();
				appSession = util.getApplicationSessionByKey(requestId, true);
				SipServletRequest connectRequest = factory.createRequest(appSession, "INVITE", origin, destination);
				
				appSession.setAttribute(TPCC_SESSION_ID, connectRequest.getSession().getId());
				appSession.setAttribute(FROM_URI, request.getFrom().getURI());
				appSession.setAttribute(TO_URI, request.getTo().getURI());
				
				connectRequest.getSession().setAttribute(REQUEST_ID, requestId);
				connectRequest.getSession().setAttribute(CALL_CONTROL, callControl);
				
				connectRequest.send();

				break;
			case 2035990113: // terminate
			case 530405532: // disconnect
				System.out.println("terminate...");
				appSession = util.getApplicationSessionByKey(requestId, false);
				System.out.println("appSession: "+appSession.getId());
				SipSession sipSession = appSession.getSipSession((String) appSession.getAttribute(TPCC_SESSION_ID));
				System.out.println("sipSession: "+sipSession.getId());
				SipServletRequest disconnectRequest = sipSession.createRequest("BYE");
				
				logger.fine("Setting call_control: " + callControl);

				sipSession.setAttribute(CALL_CONTROL, callControl);
				sipSession.setAttribute(REQUEST_ID, requestId);
				System.out.println("Sending BYE");
				disconnectRequest.send();

				break;
			case 1280882667: // transfer
				transfer(requestId, rootNode.path("endpoint").asText());
				break;
			case 3208383: // hold
				hold(requestId);
				break;
			case -310034372: // retrieve
				retrieve(requestId);
				break;
			case 3083120: // dial
				dial(requestId, rootNode.path("digits").asText());
				break;
			case 3363353: // mute
				mute(requestId);
				break;
			case -295947265: // un_mute
				unmute(requestId);
				break;
			case -776144932: // redirect
				redirect(requestId, rootNode.path("endpoint").asText());
				break;
			case -1423461112: // accept
				accept(requestId, rootNode.path("endpoint").asText());
				break;
			case -934710369: // reject
				reject(requestId, rootNode.path("endpoint").asText());
				break;

			default:
				logger.severe("Unknown call control command: " + callControl + " " + callControl.hashCode());
			}

		} catch (Exception e) {

			String content = "" + "{\"event\": \"" + callControl + "\",\n" + "\"request_id\": \"" + requestId + "\",\n" + "\"status\": " + 500 + ",\n"
					+ "\"reason\": " + e.getClass().getSimpleName() + "}";

			SipServletRequest message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", request.getTo(), request.getFrom());
			message.setContent(content, "text/plain");
			message.send();

		}

	}

	protected void connect(String requestId, String origin, String destination) throws ServletException, IOException {
		SipApplicationSession appSession = util.getApplicationSessionByKey(requestId, true);
		SipServletRequest connectRequest = factory.createRequest(appSession, "INVITE", origin, destination);
		connectRequest.send();
		appSession.setAttribute(TPCC_SESSION_ID, connectRequest.getSession().getId());
	}

	protected void transfer(String requestId, String endpoint) throws ServletException, IOException {
		SipApplicationSession appSession = util.getApplicationSessionByKey(requestId, false);
		SipSession sipSession = appSession.getSipSession((String) appSession.getAttribute(TPCC_SESSION_ID));
		SipServletRequest transferRequest = sipSession.createRequest("INVITE");
		transferRequest.setHeader("To", endpoint);
		transferRequest.send();
	}

	protected void hold(String requestId) throws ServletException, IOException {
	}

	protected void retrieve(String requestId) throws ServletException, IOException {
	}

	protected void dial(String requestId, String digit) throws ServletException, IOException {
		SipApplicationSession appSession = util.getApplicationSessionByKey(requestId, false);
		SipSession sipSession = appSession.getSipSession((String) appSession.getAttribute(TPCC_SESSION_ID));
		SipServletRequest digitRequest = sipSession.createRequest("INFO");

		String content = "Signal=" + digit + "\n" + "Duration=160";
		digitRequest.setContent(content, DTMF_RELAY);
		digitRequest.send();
	}

	protected void mute(String requestId) throws ServletException, IOException {
	}

	protected void unmute(String requestId) throws ServletException, IOException {
	}

	protected void redirect(String requestId, String endpoint) throws ServletException, IOException {
	}

	protected void accept(String requestId, String endpoint) throws ServletException, IOException {
	}

	protected void reject(String requestId, String endpoint) throws ServletException, IOException {
	}

	@Override
	protected void doResponse(SipServletResponse response) throws ServletException, IOException {
		SipApplicationSession appSession = response.getApplicationSession();
		String callControl = (String) response.getSession().getAttribute(CALL_CONTROL);
		String requestId = (String) response.getSession().getAttribute(REQUEST_ID);
		URI fromUri = (URI) appSession.getAttribute(FROM_URI);
		URI toUri = (URI) appSession.getAttribute(TO_URI);

		String content;
		SipServletRequest message;

		logger.fine(requestId + " " + callControl + " " + response.getMethod() + " " + response.getStatus() + " "
				+ response.getReasonPhrase());

		System.out.println("TalkBACSipServlet RESPONSE "+response.getMethod()+" "+response.getStatus()+" "+response.getReasonPhrase());
		System.out.println(response);
		System.out.println("-------------------------");
		
		
		
		if (callControl != null) {
			logger.fine(callControl+" "+callControl.hashCode());
			switch (callControl.hashCode()) {
			case 3045982: // call
				if (response.getMethod().equals("INVITE") && response.getStatus() >= 200) {
					response.createAck().send();
				}

				content = "" + "{\"event\": \"call_connected\",\n" + "\"request_id\": \"" + requestId + "\",\n" + "\"status\": " + response.getStatus() + ",\n"
						+ "\"reason\": " + response.getReasonPhrase() + "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();

				break;
			case 2035990113: // terminate
			case 530405532: // disconnect

				content = "" + "{\"event\": \"call_terminated\",\n" + "\"request_id\": \"" + requestId + "\",\n" + "\"status\": " + response.getStatus()
						+ ",\n" + "\"reason\": " + response.getReasonPhrase() + "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();

				break;
			case 1280882667: // transfer
				content = "" + "{\"event\": \"call_transferred\",\n" + "\"request_id\": \"" + requestId + "\",\n" + "\"status\": " + response.getStatus()
						+ ",\n" + "\"reason\": " + response.getReasonPhrase() + "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();
				break;
			case 3208383: // hold
				content = "" + "{\"event\": \"call_held\",\n" + "\"request_id\": \"" + requestId + "\",\n" + "\"status\": " + response.getStatus() + ",\n"
						+ "\"reason\": " + response.getReasonPhrase() + "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();

				break;
			case -310034372: // retrieve
				content = "" + "{\"event\": \"call_retrieved\",\n" + "\"request_id\": \"" + requestId + "\",\n" + "\"status\": " + response.getStatus() + ",\n"
						+ "\"reason\": " + response.getReasonPhrase() + "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();

				break;
			case 3083120: // dial
				content = "" + "{\"event\": \"digit_dialed\",\n" + "\"request_id\": \"" + requestId + "\",\n" + "\"status\": " + response.getStatus() + ",\n"
						+ "\"reason\": " + response.getReasonPhrase() + "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();

				break;
			case 3363353: // mute
				content = "" + "{\"event\": \"call_muted\",\n" + "\"request_id\": \"" + requestId + "\",\n" + "\"status\": " + response.getStatus() + ",\n"
						+ "\"reason\": " + response.getReasonPhrase() + "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();

				break;
			case -295947265: // un_mute
				content = "" + "{\"event\": \"call_unmuted\",\n" + "\"request_id\": \"" + requestId + "\",\n" + "\"status\": " + response.getStatus() + ",\n"
						+ "\"reason\": " + response.getReasonPhrase() + "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();

				break;
			case -776144932: // redirect
				content = "" + "{\"event\": \"call_redirected\",\n" + "\"request_id\": \"" + requestId + "\",\n" + "\"status\": " + response.getStatus()
						+ ",\n" + "\"reason\": " + response.getReasonPhrase() + "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();

				break;
			case -1423461112: // accept
				content = "" + "{\"event\": \"call_accepted\",\n" + "\"request_id\": \"" + requestId + "\",\n" + "\"status\": " + response.getStatus() + ",\n"
						+ "\"reason\": " + response.getReasonPhrase() + "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();

				break;
			case -934710369: // reject
				content = "" + "{\"event\": \"call_rejected\",\n" + "\"request_id\": \"" + requestId + "\",\n" + "\"status\": " + response.getStatus() + ",\n"
						+ "\"reason\": " + response.getReasonPhrase() + "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();

				break;

			}

		}

	}

}
