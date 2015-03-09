package oracle.communications.talkbac;

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletMessage.HeaderForm;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipURI;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import weblogic.kernel.KernelLogManager;

import com.bea.wcp.sip.engine.server.header.HeaderUtils;

public abstract class CallStateHandler implements Serializable {
	private static final long serialVersionUID = 1L;
	static Logger logger;
	{
		logger = Logger.getLogger(CallStateHandler.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	public final static String CALL_STATE_HANDLER = "CALL_STATE_HANDLER";
	public final static String PEER_SESSION_ID = "PEER_SESSION_ID";
	public final static String ORIGIN_SESSION_ID = "ORIGIN_SESSION_ID";
	public final static String DESTINATION_SESSION_ID = "DESTINATION_SESSION_ID";
	public final static String INITIATOR_SESSION_ID = "INITIATOR_SESSION_ID";
	public final static String INITIAL_INVITE_REQUEST = "INITIAL_INVITE_REQUEST";
	public final static String REQUEST_DIRECTION = "REQUEST_DIRECTION";
	public final static String KEY = "KEY";
	public final static String ALLOW = "Allow: INVITE, OPTIONS, INFO, BYE, CANCEL, ACK, PRACK, UPDATE, REFER, SUBSCRIBE, NOTIFY";

	protected int state = 1;

	protected String getPrintableName() {
		String name = this.getClass().getSimpleName();
		name = name.concat(" ");
		name = name.concat(Integer.toString(this.state));

		int spaces = 20 - name.length();
		for (int i = 0; i < spaces; i++) {
			name = name.concat(" ");
		}
		return name;
	}

	public abstract void processEvent(SipApplicationSession appSession, MessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception;

	public void printOutboundMessage(SipServletMessage message) throws UnsupportedEncodingException, IOException {
		if (logger.isLoggable(Level.FINE)) {

			try {

				if (message != null) {

					String event = message.getHeader("Event");
					if (event != null && event.equals("refer")) {
						event += " " + new String((byte[]) message.getContent()).trim();
					}
					if (event == null) {
						event = (message.getContent() != null) ? "w/ SDP" : "w/o SDP";
					}

					if (message instanceof SipServletRequest) {
						SipServletRequest rqst = (SipServletRequest) message;

						if (rqst.getMethod().equals("MESSAGE")) {
							ObjectMapper objectMapper = new ObjectMapper();
							JsonNode rootNode = objectMapper.readTree(rqst.getContent().toString());
							event = rootNode.path("event").asText();
							event += " " + rootNode.path("status").asInt();
							event += " " + rootNode.path("reason").asText();
						}

						String output = getPrintableName()
								+ " "
								+ ((SipURI) rqst.getTo().getURI()).getUser()
								+ " <-- "
								+ rqst.getMethod()
								+ " "
								+ event
								+ ", "
								+ TalkBACSipServlet.hexHash(message)
								+ " "
								+ rqst.getSession().getState().toString();

						logger.fine(output);
						System.out.println(output);

					} else {
						SipServletResponse rspn = (SipServletResponse) message;
						String output = getPrintableName()
								+ " "
								+ ((SipURI) rspn.getFrom().getURI()).getUser()
								+ " <-- "
								+ rspn.getStatus()
								+ " "
								+ rspn.getReasonPhrase()
								+ " ("
								+ rspn.getMethod()
								+ ") "
								+ event
								+ ", "
								+ TalkBACSipServlet.hexHash(message)
								+ " "
								+ rspn.getSession().getState().toString();

						logger.fine(output);
						System.out.println(output);

					}
				}

			} catch (Exception e) {
				logger.fine("logging error: " + e.getMessage());
				System.out.println("logging error: " + e.getMessage());
			}

		}

	}

	public void printInboundMessage(SipServletMessage message) throws UnsupportedEncodingException, IOException {
		if (logger.isLoggable(Level.FINE)) {

			try {

				String event = message.getHeader("Event");
				if (event != null && event.equals("refer")) {
					event += " " + new String((byte[]) message.getContent()).trim();
				}
				if (event == null) {
					event = (message.getContent() != null) ? "w/ SDP" : "w/o SDP";
				}

				if (message instanceof SipServletRequest) {
					SipServletRequest rqst = (SipServletRequest) message;
					String output = null;
					if (rqst.getMethod().equals("MESSAGE")) {
						ObjectMapper objectMapper = new ObjectMapper();
						JsonNode rootNode = objectMapper.readTree(rqst.getContent().toString());
						event = rootNode.path(TalkBACSipServlet.CALL_CONTROL).asText();
					}

					output = getPrintableName()
							+ " "
							+ ((SipURI) rqst.getFrom().getURI()).getUser()
							+ " --> "
							+ rqst.getMethod()
							+ " "
							+ event
							+ ", "
							+ TalkBACSipServlet.hexHash(message)
							+ " "
							+ rqst.getSession().getState().toString();

					logger.fine(output);
					System.out.println(output);
				} else {
					SipServletResponse rspn = (SipServletResponse) message;
					String output = getPrintableName()
							+ " "
							+ ((SipURI) rspn.getTo().getURI()).getUser()
							+ " --> "
							+ rspn.getStatus()
							+ " "
							+ rspn.getReasonPhrase()
							+ " ("
							+ rspn.getMethod()
							+ ") "
							+ event
							+ ", "
							+ TalkBACSipServlet.hexHash(message)
							+ " "
							+ rspn.getSession().getState().toString();
					logger.fine(output);
					System.out.println(output);
				}

			} catch (Exception e) {
				logger.fine("logging error: " + e.getMessage());
				System.out.println("logging error: " + e.getMessage());
			}

		}
	}

	public void printTimer(ServletTimer timer) {
		if (logger.isLoggable(Level.FINE)) {

			try {

				String output = getPrintableName()
						+ " "
						+ " timer id: "
						+ timer.getId()
						+ ", time remaining: "
						+ (int) timer.getTimeRemaining()
						/ 1000
						+ ", "
						+ TalkBACSipServlet.hexHash(timer.getApplicationSession());

				logger.fine(output);
				// System.out.println(output);

			} catch (Exception e) {
				logger.fine("logging error: " + e.getMessage());
				// System.out.println("logging error: " + e.getMessage());
			}

		}
	}

	public SipSession findSession(SipApplicationSession appSession, Address address) {

		SipSession session = null;
		SipSession endpointSession = null;
		@SuppressWarnings("unchecked")
		Iterator<SipSession> itr = (Iterator<SipSession>) appSession.getSessions();

		String remoteUser = null;
		String user = ((SipURI) address.getURI()).getUser().toLowerCase();

		while (itr.hasNext()) {
			session = itr.next();
			if (session.isValid()) {
				remoteUser = ((SipURI) session.getRemoteParty().getURI()).getUser().toLowerCase();
				if (user.equals(remoteUser)) {
					endpointSession = session;
				}
			}
		}

		return endpointSession;
	}

	public static void copyHeadersAndContent(SipServletMessage origin, SipServletMessage destination) throws UnsupportedEncodingException, IOException {

		String headerName, headerValue;
		Iterator<String> itr = origin.getHeaderNames();
		while (itr.hasNext()) {
			headerName = itr.next();
			if (false == HeaderUtils.isSystemHeader(headerName, true)) {
				destination.setHeaderForm(HeaderForm.LONG);
				// destination.setHeaderForm(origin.getHeaderForm());
				ListIterator<String> headers = origin.getHeaders(headerName);

				while (headers.hasNext()) {
					headerValue = headers.next();

					if (HeaderUtils.isUnique(headerName)) {
						destination.setHeader(headerName, headerValue);
					} else {
						destination.addHeader(headerName, headerValue);
					}

				}
			}
		}

		if (destination.getMethod().equals("INVITE")) {
			destination.setHeader("Call-Info", TalkBACSipServlet.callInfo);
			// destination.removeHeader("Allow");
			destination.setHeader("Allow", ALLOW);

			// destination.setHeader("Allow", "INVITE, OPTIONS, MESSAGE, INFO, BYE, CANCEL, ACK, UPDATE, NOTIFY");
			// destination.setHeader("Allow-Events", "telephone-event");
		}

		destination.setContent(origin.getContent(), origin.getContentType());

	}

}
