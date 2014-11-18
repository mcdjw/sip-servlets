package oracle.communications.talkbac;

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
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

public abstract class CallStateHandler implements Serializable {
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

	protected int state = 1;
	public TalkBACMessageUtility msgUtility;

	public abstract void processEvent(SipApplicationSession appSession, SipServletRequest request, SipServletResponse response, ServletTimer timer)
			throws Exception;

	public void printOutboundMessage(SipServletMessage message) throws UnsupportedEncodingException, IOException {
		if (logger.isLoggable(Level.FINE)) {
			String sdp;

			if (message.getContent() != null) {
				sdp = "w/ SDP";
			} else {
				sdp = "w/o SDP";
			}

			if (message instanceof SipServletRequest) {
				SipServletRequest rqst = (SipServletRequest) message;
				System.out.println(this.getClass().getSimpleName()
						+ " "
						+ state
						+ " "
						+ ((SipURI) rqst.getTo().getURI()).getUser()
						+ " <-- "
						+ rqst.getMethod()
						+ " "
						+ sdp
						+ ", ["
						+ rqst.getApplicationSession().hashCode()
						+ ":"
						+ rqst.getSession().hashCode()
						+ "] "
						+ rqst.getSession().getState().toString());
			} else {
				SipServletResponse rspn = (SipServletResponse) message;
				System.out.println(this.getClass().getSimpleName()
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
						+ sdp
						+ ", ["
						+ rspn.getApplicationSession().hashCode()
						+ ":"
						+ rspn.getSession().hashCode()
						+ "] "
						+ rspn.getSession().getState().toString());
			}
		}
	}

	public void printInboundMessage(SipServletMessage message) throws UnsupportedEncodingException, IOException {
		if (logger.isLoggable(Level.FINE)) {

			String sdp;

			if (message.getContent() != null) {
				sdp = "w/ SDP";
			} else {
				sdp = "w/o SDP";
			}

			if (message instanceof SipServletRequest) {
				SipServletRequest rqst = (SipServletRequest) message;
				System.out.println(this.getClass().getSimpleName()
						+ " "
						+ state
						+ " "
						+ ((SipURI) rqst.getFrom().getURI()).getUser()
						+ " --> "
						+ rqst.getMethod()
						+ " "
						+ sdp
						+ ", ["
						+ rqst.getApplicationSession().hashCode()
						+ ":"
						+ rqst.getSession().hashCode()
						+ "] "
						+ rqst.getSession().getState().toString());
			} else {
				SipServletResponse rspn = (SipServletResponse) message;
				System.out.println(this.getClass().getSimpleName()
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
						+ sdp
						+ ", ["
						+ rspn.getApplicationSession().hashCode()
						+ ":"
						+ rspn.getSession().hashCode()
						+ "] "
						+ rspn.getSession().getState().toString());
			}

		}
	}

	public SipSession findSession(SipApplicationSession appSession, Address address) {

		SipSession session = null;
		SipSession endpointSession = null;
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

}
