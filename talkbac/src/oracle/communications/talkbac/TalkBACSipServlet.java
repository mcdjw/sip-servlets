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
import java.util.logging.Logger;

import javax.annotation.Resource;
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

	public final static org.apache.logging.log4j.Logger cdr = org.apache.logging.log4j.LogManager.getLogger(TalkBACSipServlet.class.getName());

	private enum SipMethod {
		INVITE, ACK, BYE, CANCEL, OPTIONS, REGISTER, PRACK, SUBSCRIBE, NOTIFY, PUBLISH, INFO, REFER, MESSAGE, UPDATE
	}

	private enum CallControl {
		call, disconnect, terminate, transfer, hold, retrieve, dial, mute, unmute, redirect, accept, reject
	}

	public final static String REQUEST_ID = "request_id";
	public final static String CALL_CONTROL = "call_control";
	public final static String CLIENT_ADDRESS = "CLIENT_ADDRESS";
	public final static String APPLICATION_ADDRESS = "APPLICATION_ADDRESS";

	// private final static String MESSAGE_FROM_URI = "MESSAGE_FROM_URI";
	// private final static String MESSAGE_TO_URI = "MESSAGE_TO_URI";

	private final static String TPCC_SESSION_ID = "TPCC_SESSION_ID";
	// private final static String DTMF_RELAY = "application/dtmf-relay";
	private final static String TELEPHONE_EVENT = "audio/telephone-event";
	// private final static String DIGITS_TO_DIAL = "DIGITS_TO_DIAL";

	private final static String DIGITS_REMAINING = "DIGITS_REMAINING";
	private final static String DIGIT_DIALED = "DIGIT_DIALED";
	private final static String ORIGIN = "ORIGIN";
	private final static String DESTINATION = "DESTINATION";

	public final static String ORIGIN_SESSION_ID = "ORIGIN_SESSION_ID";
	public final static String DESTINATION_SESSION_ID = "DESTINATION_SESSION_ID";

	public static String callInfo=null;
	
	public static String strOutboundProxy = null;
	public static Address outboundProxy = null;

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
		SipURI uri = (SipURI) req.getFrom().getURI();
		String key = uri.getUser().toLowerCase() + "@" + uri.getHost().toLowerCase();
		return key;
	}

	@Override
	public void servletInitialized(SipServletContextEvent event) {
		logger.info(event.getSipServlet().getServletName() + " initialized.");
		callInfo = event.getServletContext().getInitParameter("CALL_INFO");
		
		
		strOutboundProxy = event.getServletContext().getInitParameter("OUTBOUND_PROXY");

		logger.info("Setting Outbound Proxy: " + strOutboundProxy);

		if (strOutboundProxy != null) {
			try {
				this.outboundProxy = factory.createAddress("sip:" + strOutboundProxy);
				((SipURI) this.outboundProxy.getURI()).setLrParam(true);
			} catch (ServletParseException e) {
				e.printStackTrace();
			}
		}

	}

	@Override
	protected void doRequest(SipServletRequest request) throws ServletException, IOException {

		CallStateHandler handler = null;
		TalkBACMessage msg = null;

		SipApplicationSession appSession = request.getApplicationSession();
		try {

			handler = (CallStateHandler) request.getSession().getAttribute(CallStateHandler.CALL_STATE_HANDLER);
			if(handler==null){
				System.out.println("hander: "+handler);
			}else{
				System.out.println("handler: "+handler.getClass().getName());
			}
			
			if (handler == null) {

				switch (SipMethod.valueOf(request.getMethod())) {

				case MESSAGE:

					cdr.info(request.getContent().toString().replaceAll("[\n\r]", ""));
					request.createResponse(200).send();

					ObjectMapper objectMapper = new ObjectMapper();
					JsonNode rootNode = objectMapper.readTree(request.getContent().toString());
					String requestId = rootNode.path(REQUEST_ID).asText();
					String cc = rootNode.path(CALL_CONTROL).asText();

					appSession.setAttribute(REQUEST_ID, requestId);
					appSession.setAttribute(CALL_CONTROL, requestId);
					appSession.setAttribute(CLIENT_ADDRESS, request.getFrom());
					appSession.setAttribute(APPLICATION_ADDRESS, request.getTo());

					System.out.println("Call Control: "+cc);
					
					switch (CallControl.valueOf(cc)) {
					case call:
						String origin = rootNode.path("origin").asText();
						appSession.setAttribute(ORIGIN, origin);
						String destination = rootNode.path("destination").asText();
						appSession.setAttribute(DESTINATION, destination);

						if (request.isInitial()) {
							Address originAddress = factory.createAddress(origin);
							Address destinationAddress = factory.createAddress(destination);

							int callflow = 4;
							String callflowHeader = request.getHeader("Callflow");
							if (callflowHeader != null) {
								callflow = Integer.parseInt(callflowHeader);
							}
							switch (callflow) {
							case 1:
								handler = new CallFlow1(originAddress, destinationAddress);
								break;
							case 2:
								handler = new CallFlow2(originAddress, destinationAddress);
								break;
							case 3:
								handler = new CallFlow3(originAddress, destinationAddress);
								break;
							case 4:
								handler = new CallFlow4(originAddress, destinationAddress);
								break;
							}
						} else {
							handler = new Reinvite();
						}

						break;
					case disconnect:	
					case terminate:
						handler = new TerminateCall();
						msg = new TalkBACMessage(request.getApplicationSession(), "call_completed");
						msg.send();
						break;
					case dial:
						String digits = rootNode.path("digits").asText();
						handler = new DtmfRelay(digits);
						break;

					case transfer:
					case hold:
					case retrieve:
					case mute:
					case unmute:
					case redirect:
					case accept:
					case reject:
					default:
						request.createResponse(200).send();
						msg = new TalkBACMessage(request.getApplicationSession(), "request_failed");
						msg.setStatus(500, "Method Not Implemented");
						msg.send();
						break;
					}
					break;
					
				case REGISTER:
					handler = new KeepAlive();
					break;

				case INVITE:
					handler = new Reinvite();
					break;
				case BYE:
					request.createResponse(200).send();

					handler = new TerminateCall();
					msg = new TalkBACMessage(request.getApplicationSession(), "call_completed");
					msg.send();

					break;
				case ACK:
				case CANCEL:
				case OPTIONS:
				case PRACK:
				case SUBSCRIBE:
				case NOTIFY:
				case PUBLISH:
				case INFO:
				case REFER:
				case UPDATE:
				default:
					handler = new NotImplemented();
					break;
				}
			}

			// if handler still null
			if (handler == null) {
				handler = new NotImplemented();
			}

			System.out.println("TalkBACSipServlet  REQUEST: " + request.getMethod() + " " + handler.getClass().getSimpleName() + " " + handler.state);
			handler.processEvent(request, null);

		} catch (Exception e) {
			if (appSession != null) {
				msg = new TalkBACMessage(appSession, "exception");
				msg.setStatus(500, e.getClass().getSimpleName());
				msg.send();
			}
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
		CallStateHandler handler = (CallStateHandler) response.getSession().getAttribute(CallStateHandler.CALL_STATE_HANDLER);

		System.out.println("TalkBACSipServlet RESPONSE: " + response.getMethod() + " " + response.getStatus() + " " + response.getReasonPhrase());

		try {
			if (handler != null) {
				handler.processEvent(null, response);
			}
		} catch (Exception e) {
			throw new ServletException(e);
		}

	}

}
