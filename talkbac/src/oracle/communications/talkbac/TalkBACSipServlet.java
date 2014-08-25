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

	public static String callInfo = null;

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

		String listenAddress = System.getProperty("listenAddress");
		listenAddress = (listenAddress != null) ? listenAddress : event.getServletContext().getInitParameter("listenAddress");
		callInfo = "<sip:" + listenAddress + ">;method=\"NOTIFY;Event=telephone-event;Duration=500\"";
		logger.info("Setting Listen Address: " + listenAddress);

		String strOutboundProxy = System.getProperty("outboundProxy");
		strOutboundProxy = (strOutboundProxy != null) ? strOutboundProxy : event.getServletContext().getInitParameter("outboundProxy");
		if (strOutboundProxy != null) {
			logger.info("Setting Outbound Proxy: " + strOutboundProxy);
			try {
				outboundProxy = factory.createAddress("sip:" + strOutboundProxy);
				((SipURI) outboundProxy.getURI()).setLrParam(true);
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

			// handler = (CallStateHandler)
			// request.getSession().getAttribute(CallStateHandler.CALL_STATE_HANDLER);

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

					switch (CallControl.valueOf(cc)) {
					case call:
						String origin = rootNode.path("origin").asText();
						appSession.setAttribute(ORIGIN, origin);
						String destination = rootNode.path("destination").asText();
						appSession.setAttribute(DESTINATION, destination);

						int cf = 4;
						String callflow = rootNode.path("callflow").asText();

						if (callflow != null) {
							cf = Integer.parseInt(callflow);
						}

						Address originAddress = factory.createAddress(origin);
						Address destinationAddress = factory.createAddress(destination);

						switch (cf) {
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
						case 5:
							handler = new CallFlow5(originAddress, destinationAddress);
							break;

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
					handler = (CallStateHandler) request.getSession().getAttribute(CallStateHandler.CALL_STATE_HANDLER);
					break;
				}
			}

			// if handler still null
			if (handler == null) {
				handler = new NotImplemented();
			}

			System.out.println("REQUEST : " + request.getMethod() + " " + handler.getClass().getSimpleName() + " " + handler.state);
			handler.processEvent(request, null);

		} catch (Exception e) {
			if (appSession != null) {
				msg = new TalkBACMessage(appSession, "exception");
				msg.setStatus(500, e.getClass().getSimpleName());
				msg.send();
			}
			e.printStackTrace();

			try {
				handler = new TerminateCall();
				handler.processEvent(request, null);
			} catch (Exception e2) {
				// do nothing;
			}
		}

	}

	@Override
	protected void doResponse(SipServletResponse response) throws ServletException, IOException {
		CallStateHandler handler = (CallStateHandler) response.getSession().getAttribute(CallStateHandler.CALL_STATE_HANDLER);

		try {
			if (handler != null) {
				System.out.println("RESPONSE: "
						+ response.getMethod()
						+ " "
						+ response.getStatus()
						+ " "
						+ response.getReasonPhrase()
						+ " "
						+ handler.getClass().getSimpleName()
						+ " "
						+ handler.state);
				handler.processEvent(null, response);
			} else {
				System.out.println("RESPONSE: " + response.getMethod() + " " + response.getStatus() + " " + response.getReasonPhrase());
			}
		} catch (Exception e) {
			e.printStackTrace();
			handler = new TerminateCall();
			try {
				handler.processEvent(null, response);
			} catch (Exception e1) {
				// do nothing;
			}
		}

	}

}
