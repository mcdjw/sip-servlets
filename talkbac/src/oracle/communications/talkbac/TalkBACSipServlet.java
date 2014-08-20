/*
 *
 * WSC                             Controller               3pcc
 *  | MESSAGE "call"                   |                      |
 *  |--------------------------------->|                      |
 *  | 200 OK                           |                      |
 *  |<---------------------------------|                      |
 *  | MESSAGE "call_created"           |                      |
 *  |<---------------------------------|                      |
 *  | 200 OK                           |                      |
 *  |--------------------------------->|                      |
 *  |                                  | INVITE               |
 *  |                                  |--------------------->|
 *  |                                  | 180 Ringing          |
 *  |                                  |<---------------------|
 *  |                                  | 183 Session Progress |
 *  |                                  |<---------------------|
 *  | MESSAGE "source_connected"       |                      |
 *  |<---------------------------------|                      |
 *  | 200 OK                           |                      |
 *  |--------------------------------->|                      |
 *  |                                  | 200 OK               |
 *  |                                  |<---------------------|
 *  | MESSAGE "destination_connected"  |                      |
 *  |<---------------------------------|                      |
 *  | 200 OK                           |                      |
 *  |--------------------------------->|                      |
 *  |                                  | ACK                  |
 *  |                                  |--------------------->|
 *  | MESSAGE "call_connected"         |                      |
 *  |<---------------------------------|                      |
 *  | 200 OK                           |                      |
 *  |--------------------------------->|                      |
 *  |                                  | BYE                  |
 *  |                                  |<---------------------|
 *  | MESSAGE "call_completed"         |                      |
 *  |<---------------------------------|                      |
 *  | 200 OK                           |                      |
 *  |--------------------------------->|                      |
 *  |                                  |                      |
 *
 */

package oracle.communications.talkbac;

import java.io.IOException;
import java.nio.ByteBuffer;
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

	public static org.apache.logging.log4j.Logger cdr;

	public final static String CALL_CONTROL = "CALL_CONTROL";
	public final static String REQUEST_ID = "REQUEST_ID";
	public final static String CLIENT_ADDRESS = "CLIENT_ADDRESS";
	public final static String APPLICATION_ADDRESS = "APPLICATION_ADDRESS";
	private final static String FROM_URI = "FROM_URI";
	private final static String TO_URI = "TO_URI";
	private final static String TPCC_SESSION_ID = "TPCC_SESSION_ID";
	// private final static String DTMF_RELAY = "application/dtmf-relay";
	private final static String TELEPHONE_EVENT = "audio/telephone-event";
	// private final static String DIGITS_TO_DIAL = "DIGITS_TO_DIAL";

	private final static String DIGITS_REMAINING = "DIGITS_REMAINING";
	private final static String DIGIT_DIALED = "DIGIT_DIALED";

	private static String callInfo;

	// public enum DTMF_STYLE {
	// RFC_2833, RFC_2976
	// };

	// public static DTMF_STYLE dtmf_style;

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
		callInfo = event.getServletContext().getInitParameter("CALL_INFO");

		cdr = org.apache.logging.log4j.LogManager.getLogger(TalkBACSipServlet.class.getName());
	}

	@Override
	protected void doBye(SipServletRequest request) throws ServletException, IOException {
		request.createResponse(200).send();

		TalkBACMessage msg = new TalkBACMessage(request, "call_completed");
		msg.send();
	}

	private byte[] encodeRFC2833(char digit, boolean end, int duration) {
		int payload = 0;
		byte tone = 0;

		switch (digit) {
		case '0':
			tone = 0x00;
			break;
		case '1':
			tone = 0x01;
			break;
		case '2':
			tone = 0x02;
			break;
		case '3':
			tone = 0x03;
			break;
		case '4':
			tone = 0x04;
			break;
		case '5':
			tone = 0x05;
			break;
		case '6':
			tone = 0x06;
			break;
		case '7':
			tone = 0x07;
			break;
		case '8':
			tone = 0x08;
			break;
		case '9':
			tone = 0x09;
			break;
		case '*': // 10
			tone = 0x0A;
			break;
		case '#': // 11
			tone = 0x0B;
			break;
		case 'A': // 12
			tone = 0x0C;
			break;
		case 'B': // 13
			tone = 0x0D;
			break;
		case 'C': // 14
			tone = 0x0E;
			break;
		case 'D': // 15
			tone = 0x0F;
			break;
		case 'F': // 16 for 'flash'
			tone = 0x10;
			break;
		}

		ByteBuffer buf = ByteBuffer.allocate(4);
		buf.put(tone);

		if (end) {
			buf.put((byte) 0x80);
		} else {
			buf.put((byte) 0x00);
		}

		buf.putShort((short) duration);

		return buf.array();
	}

	@Override
	protected void doMessage(SipServletRequest request) throws ServletException, IOException {
		
		cdr.info(request.getContent().toString().replaceAll("[\n\r]", ""));
		
		
		TalkBACMessage msg;
		String requestId = null;
		String callControl = null;
		SipApplicationSession appSession;

		request.createResponse(200).send();

		ObjectMapper objectMapper = new ObjectMapper();
		JsonNode rootNode = objectMapper.readTree(request.getContent().toString());
		requestId = rootNode.path("request_id").asText();

		// In case of Exception, save these values
		request.getApplicationSession().setAttribute(CLIENT_ADDRESS, request.getFrom());
		request.getApplicationSession().setAttribute(APPLICATION_ADDRESS, request.getTo());
		request.getApplicationSession().setAttribute(REQUEST_ID, requestId);

		appSession = util.getApplicationSessionByKey(requestId, true);
		appSession.setAttribute(CLIENT_ADDRESS, request.getFrom());
		appSession.setAttribute(APPLICATION_ADDRESS, request.getTo());
		appSession.setAttribute(REQUEST_ID, requestId);

		try {
			String origin, destination, endpoint;
			callControl = rootNode.path("call_control").asText();

			switch (callControl.hashCode()) {
			case 3045982: // call
				// connect(requestId, rootNode.path("origin").asText(),
				// rootNode.path("destination").asText());

				origin = rootNode.path("origin").asText();
				destination = rootNode.path("destination").asText();

				SipServletRequest connectRequest = factory.createRequest(appSession, "INVITE", origin, destination);

				appSession.setAttribute(TPCC_SESSION_ID, connectRequest.getSession().getId());
				appSession.setAttribute(FROM_URI, request.getFrom().getURI());
				appSession.setAttribute(TO_URI, request.getTo().getURI());

				connectRequest.getSession().setAttribute(REQUEST_ID, requestId);
				connectRequest.getSession().setAttribute(CALL_CONTROL, callControl);

				String callflow = rootNode.path("callflow").asText();
				if (callflow != null) {
					connectRequest.setHeader("Callflow", callflow);
				}

				connectRequest.send();

				msg = new TalkBACMessage(connectRequest, "call_created");
				msg.send();

				break;
			case 2035990113: // terminate
			case 530405532: // disconnect
				appSession = util.getApplicationSessionByKey(requestId, false);
				SipSession sipSession = appSession.getSipSession((String) appSession.getAttribute(TPCC_SESSION_ID));
				SipServletRequest disconnectRequest = sipSession.createRequest("BYE");

				sipSession.setAttribute(CALL_CONTROL, callControl);
				sipSession.setAttribute(REQUEST_ID, requestId);
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
				sipSession = appSession.getSipSession((String) appSession.getAttribute(TPCC_SESSION_ID));
				sipSession.setAttribute(CALL_CONTROL, callControl);

				String digits = rootNode.path("digits").asText();

				SipServletRequest digitRequest = sipSession.createRequest("NOTIFY");

				char digit = digits.charAt(0);
				sipSession.setAttribute(DIGIT_DIALED, digit);
				if (digits.length() > 1) {
					digits = digits.substring(1);
					sipSession.setAttribute(DIGITS_REMAINING, digits);
				}

				digitRequest.setHeader("Subscription-State", "active");
				digitRequest.setHeader("Event", "telephone-event;rate=1000");

				digitRequest.setContent(encodeRFC2833(digit, false, 500), "audio/telephone-event");
				digitRequest.send();

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

			msg = new TalkBACMessage(request, "exception");
			msg.setParameter("status", "500");
			msg.setParameter("reason", e.getClass().getSimpleName());
			msg.send();

			e.printStackTrace();

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
		int status = response.getStatus();
		TalkBACMessage msg;

		SipApplicationSession appSession = response.getApplicationSession();
		SipSession sipSession = response.getSession();
		String callControl = (String) sipSession.getAttribute(CALL_CONTROL);
		String requestId = (String) sipSession.getAttribute(REQUEST_ID);
		URI fromUri = (URI) appSession.getAttribute(FROM_URI);
		URI toUri = (URI) appSession.getAttribute(TO_URI);

		String content;
		SipServletRequest message;

		logger.fine(requestId + " " + callControl + " " + response.getMethod() + " " + response.getStatus() + " " + response.getReasonPhrase());

		if (callControl != null) {
			logger.fine(callControl + " " + callControl.hashCode());
			switch (callControl.hashCode()) {
			case 3045982: // call
				switch (status) {
				case 183:
					msg = new TalkBACMessage(response, "source_connected");
					msg.send();
					break;
				case 200:
					msg = new TalkBACMessage(response, "destination_connected");
					msg.send();

					response.createAck().send();
					msg = new TalkBACMessage(response, "call_connected");
					msg.send();
					break;
				default:
					if (status > 200) {
						msg = new TalkBACMessage(response, "call_failed");
						msg.send();
					}
				}

				break;
			case 2035990113: // terminate
			case 530405532: // disconnect

				msg = new TalkBACMessage(response, "call_completed");
				msg.send();

				break;
			case 1280882667: // transfer
				switch (response.getStatus()) {
				case 200:
					msg = new TalkBACMessage(response, "call_transferred");
					break;
				}

				content = ""
						+ "{\"event\": \"call_transferred\",\n"
						+ "\"request_id\": \""
						+ requestId
						+ "\",\n"
						+ "\"status\": "
						+ response.getStatus()
						+ ",\n"
						+ "\"reason\": "
						+ response.getReasonPhrase()
						+ "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();
				break;
			case 3208383: // hold
				content = ""
						+ "{\"event\": \"call_held\",\n"
						+ "\"request_id\": \""
						+ requestId
						+ "\",\n"
						+ "\"status\": "
						+ response.getStatus()
						+ ",\n"
						+ "\"reason\": "
						+ response.getReasonPhrase()
						+ "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();

				break;
			case -310034372: // retrieve
				content = ""
						+ "{\"event\": \"call_retrieved\",\n"
						+ "\"request_id\": \""
						+ requestId
						+ "\",\n"
						+ "\"status\": "
						+ response.getStatus()
						+ ",\n"
						+ "\"reason\": "
						+ response.getReasonPhrase()
						+ "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();

				break;
			case 3083120: // dial

				Character digit_dialed = (Character) sipSession.getAttribute(DIGIT_DIALED);
				if (digit_dialed != null) {
					SipServletRequest digitRequest = sipSession.createRequest("NOTIFY");
					digitRequest.setHeader("Subscription-State", "active");
					digitRequest.setHeader("Event", "telephone-event;rate=1000");
					digitRequest.setContent(encodeRFC2833(digit_dialed, true, 250), "audio/telephone-event");
					digitRequest.send();

					sipSession.removeAttribute(DIGIT_DIALED);
				} else {
					String digits = (String) sipSession.getAttribute(DIGITS_REMAINING);

					if (digits != null && digits.length() > 0) {
						char digit = digits.charAt(0);
						sipSession.setAttribute(DIGIT_DIALED, digit);
						if (digits.length() > 1) {
							digits = digits.substring(1);
							sipSession.setAttribute(DIGITS_REMAINING, digits);
						} else {
							sipSession.removeAttribute(DIGITS_REMAINING);
						}

						SipServletRequest digitRequest = sipSession.createRequest("NOTIFY");
						digitRequest.setHeader("Subscription-State", "active");
						digitRequest.setHeader("Event", "telephone-event;rate=1000");
						digitRequest.setContent(encodeRFC2833(digit, false, 500), "audio/telephone-event");
						digitRequest.send();

					} else {

						content = ""
								+ "{\"event\": \"digits_dialed\",\n"
								+ "\"request_id\": \""
								+ requestId
								+ "\",\n"
								+ "\"status\": "
								+ response.getStatus()
								+ ",\n"
								+ "\"reason\": "
								+ response.getReasonPhrase()
								+ "}";

						message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
						message.setContent(content, "text/plain");
						message.send();
					}

				}

				break;
			case 3363353: // mute
				content = ""
						+ "{\"event\": \"call_muted\",\n"
						+ "\"request_id\": \""
						+ requestId
						+ "\",\n"
						+ "\"status\": "
						+ response.getStatus()
						+ ",\n"
						+ "\"reason\": "
						+ response.getReasonPhrase()
						+ "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();

				break;
			case -295947265: // un_mute
				content = ""
						+ "{\"event\": \"call_unmuted\",\n"
						+ "\"request_id\": \""
						+ requestId
						+ "\",\n"
						+ "\"status\": "
						+ response.getStatus()
						+ ",\n"
						+ "\"reason\": "
						+ response.getReasonPhrase()
						+ "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();

				break;
			case -776144932: // redirect
				content = ""
						+ "{\"event\": \"call_redirected\",\n"
						+ "\"request_id\": \""
						+ requestId
						+ "\",\n"
						+ "\"status\": "
						+ response.getStatus()
						+ ",\n"
						+ "\"reason\": "
						+ response.getReasonPhrase()
						+ "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();

				break;
			case -1423461112: // accept
				content = ""
						+ "{\"event\": \"call_accepted\",\n"
						+ "\"request_id\": \""
						+ requestId
						+ "\",\n"
						+ "\"status\": "
						+ response.getStatus()
						+ ",\n"
						+ "\"reason\": "
						+ response.getReasonPhrase()
						+ "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();

				break;
			case -934710369: // reject
				content = "{\"event\": \"call_rejected\",\n"
						+ "\"request_id\": \""
						+ requestId
						+ "\",\n"
						+ "\"status\": "
						+ response.getStatus()
						+ ",\n"
						+ "\"reason\": "
						+ response.getReasonPhrase()
						+ "}";

				message = factory.createRequest(factory.createApplicationSession(), "MESSAGE", toUri, fromUri);
				message.setContent(content, "text/plain");
				message.send();

				break;

			}

		}

	}

}
