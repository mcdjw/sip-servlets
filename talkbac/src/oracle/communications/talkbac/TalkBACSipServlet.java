package oracle.communications.talkbac;

import java.io.IOException;
import java.util.logging.Logger;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.sip.Address;
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

import weblogic.kernel.KernelLogManager;

@SipListener
public class TalkBACSipServlet extends SipServlet implements SipServletListener {
	static Logger logger;
	{
		logger = Logger.getLogger(TalkBACSipServlet.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

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

	// @Override
	// protected void doInfo(SipServletRequest request) throws ServletException,
	// IOException {
	// logger.fine("TalkBACSipServlet.doInfo...");
	// logger.fine(request.toString());
	//
	//
	// request.createResponse(200);
	//
	// SipApplicationSession appSession = request.getApplicationSession();
	//
	// URI fromUri = (URI) appSession.getAttribute(FROM_URI);
	// URI toUri = (URI) appSession.getAttribute(TO_URI);
	//
	// SipServletRequest message =
	// factory.createRequest(factory.createApplicationSession(), "MESSAGE",
	// toUri, fromUri);
	// message.setContent(request.getContent(), request.getContentType());
	// message.send();
	// }

	@Override
	protected void doMessage(SipServletRequest request) throws ServletException, IOException {

		logger.fine("doMessage...");
		logger.fine(request.getContent().toString());

		request.createResponse(200).send();

		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode rootNode = objectMapper.readTree(request.getContent().toString());
		String requestId = rootNode.path("request_id").asText();

		logger.fine("Invoking " + rootNode.path("call_control").asText());
		logger.fine(rootNode.toString());

		String origin, destination, endpoint;

		switch (rootNode.path("call_control").asText().hashCode()) {
		case 3045982: // call
			// connect(requestId, rootNode.path("origin").asText(),
			// rootNode.path("destination").asText());

			origin = rootNode.path("origin").asText();
			destination = rootNode.path("destination").asText();
			SipApplicationSession appSession = util.getApplicationSessionByKey(requestId, true);
			SipServletRequest connectRequest = factory.createRequest(appSession, "INVITE", origin, destination);
			connectRequest.send();
			appSession.setAttribute(TPCC_SESSION_ID, connectRequest.getSession().getId());
			appSession.setAttribute(FROM_URI, request.getFrom().getURI());
			appSession.setAttribute(TO_URI, request.getTo().getURI());
			connectRequest.getSession().setAttribute(REQUEST_ID, requestId);

			break;
		case 530405532: // disconnect
			disconnect(requestId);
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
			logger.severe("Unknown call control command: " + rootNode.path("call_control").asText());
		}

	}

	protected void connect(String requestId, String origin, String destination) throws ServletException, IOException {
		SipApplicationSession appSession = util.getApplicationSessionByKey(requestId, true);
		SipServletRequest connectRequest = factory.createRequest(appSession, "INVITE", origin, destination);
		connectRequest.send();
		appSession.setAttribute(TPCC_SESSION_ID, connectRequest.getSession().getId());
	}

	protected void disconnect(String requestId) throws ServletException, IOException {
		SipApplicationSession appSession = util.getApplicationSessionByKey(requestId, false);
		SipSession sipSession = appSession.getSipSession((String) appSession.getAttribute(TPCC_SESSION_ID));
		SipServletRequest disconnectRequest = sipSession.createRequest("BYE");
		disconnectRequest.send();
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

		if (response.getMethod().equals("INVITE")) {
			if (response.getStatus() >= 200 && response.getStatus() < 300) {
				response.createAck().send();
			}

			SipApplicationSession appSession = response.getApplicationSession();

			URI fromUri = (URI) appSession.getAttribute(FROM_URI);
			URI toUri = (URI) appSession.getAttribute(TO_URI);
			String requestId = (String) response.getSession().getAttribute(REQUEST_ID);

			String content = "" + "{\"event\": \"call_connected\",\n" + "\"request_id\": \"" + requestId + "\",\n" + "\"status\": " + response.getStatus()
					+ ",\n" + "\"reason\": " + response.getReasonPhrase() + "}";

			SipServletRequest message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
			message.setContent(content, "text/plain");
			message.send();

		}

	}

}
