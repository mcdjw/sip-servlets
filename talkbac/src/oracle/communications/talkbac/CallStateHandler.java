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
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipURI;

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

	protected int state = 1;

	public abstract void processEvent(SipApplicationSession appSession, TalkBACMessageUtility msgUtility, SipServletRequest request,
			SipServletResponse response, ServletTimer timer) throws Exception;

	public void printOutboundMessage(SipServletMessage message) throws UnsupportedEncodingException, IOException {
		if (logger.isLoggable(Level.FINE)) {

			String event = message.getHeader("Event");
			if (event != null && event.equals("refer")) {
				event += " " + new String((byte[]) message.getContent()).trim();
			}
			if (event == null) {
				event = (message.getContent() != null) ? "w/ SDP" : "w/o SDP";
			}

			if (message instanceof SipServletRequest) {
				SipServletRequest rqst = (SipServletRequest) message;
				String output = this.getClass().getSimpleName()
						+ " "
						+ state
						+ " "
						+ ((SipURI) rqst.getTo().getURI()).getUser()
						+ " <-- "
						+ rqst.getMethod()
						+ " "
						+ event
						+ ", ["
						+ rqst.getApplicationSession().getId().hashCode()
						+ ":"
						+ rqst.getSession().getId().hashCode()
						+ "] "
						+ rqst.getSession().getState().toString();

				logger.fine(output);
				System.out.println(output);

			} else {
				SipServletResponse rspn = (SipServletResponse) message;
				String output = this.getClass().getSimpleName()
						+ " "
						+ state
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
						+ ", ["
						+ rspn.getApplicationSession().getId().hashCode()
						+ ":"
						+ rspn.getSession().getId().hashCode()
						+ "] "
						+ rspn.getSession().getState().toString();

				logger.fine(output);
				System.out.println(output);

			}
		}
	}

	public void printInboundMessage(SipServletMessage message) throws UnsupportedEncodingException, IOException {
		if (logger.isLoggable(Level.FINE)) {

			String event = message.getHeader("Event");
			if (event != null && event.equals("refer")) {
				event += " " + new String((byte[]) message.getContent()).trim();
			}
			if (event == null) {
				event = (message.getContent() != null) ? "w/ SDP" : "w/o SDP";
			}

			if (message instanceof SipServletRequest) {
				SipServletRequest rqst = (SipServletRequest) message;

				String output = this.getClass().getSimpleName()
						+ " "
						+ state
						+ " "
						+ ((SipURI) rqst.getFrom().getURI()).getUser()
						+ " --> "
						+ rqst.getMethod()
						+ " "
						+ event
						+ ", ["
						+ rqst.getApplicationSession().getId().hashCode()
						+ ":"
						+ rqst.getSession().getId().hashCode()
						+ "] "
						+ rqst.getSession().getState().toString();
				logger.fine(output);
				System.out.println(output);
			} else {
				SipServletResponse rspn = (SipServletResponse) message;
				String output = this.getClass().getSimpleName()
						+ " "
						+ state
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
						+ ", ["
						+ rspn.getApplicationSession().getId().hashCode()
						+ ":"
						+ rspn.getSession().getId().hashCode()
						+ "] "
						+ rspn.getSession().getState().toString();
				logger.fine(output);
				System.out.println(output);
			}

		}
	}

	public void printTimer(ServletTimer timer) {
		if (logger.isLoggable(Level.FINE)) {
			String output = this.getClass().getSimpleName()
					+ " "
					+ state
					+ " timer id: "
					+ timer.getId()
					+ ", time remaining: "
					+ (int) timer.getTimeRemaining()
					/ 1000
					+ ", ["
					+ timer.getApplicationSession().getId().hashCode()
					+ "]";

			logger.fine(output);
			System.out.println(output);
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
			remoteUser = ((SipURI) session.getRemoteParty().getURI()).getUser().toLowerCase();
			if (user.equals(remoteUser)) {
				endpointSession = session;
			}
		}

		return endpointSession;
	}

	public static void copyHeaders(SipServletRequest origin, SipServletRequest destination) {
		String headerName, headerValue;
		Iterator<String> itr = origin.getHeaderNames();
		while (itr.hasNext()) {
			headerName = itr.next();
			if (false == HeaderUtils.isSystemHeader(headerName, true)) {
				destination.setHeaderForm(origin.getHeaderForm());
				ListIterator<String> headers = origin.getHeaders(headerName);
				while (headers.hasNext()) {
					headerValue = headers.next();
					destination.setHeader(headerName, headerValue);
				}
				break;
			}
		}
	}

}
