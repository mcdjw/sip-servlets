package oracle.communications.talkbac;

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

import weblogic.kernel.KernelLogManager;

public abstract class CallStateHandler implements Serializable {
	static Logger logger;
	{
		logger = Logger.getLogger(CallFlow1.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	final static String CALL_STATE_HANDLER = "CALL_STATE_HANDLER";
	final static String PEER_SESSION_ID = "PEER_SESSION_ID";
	final static String ORIGIN_SESSION_ID = "ORIGIN_SESSION_ID";
	final static String DESTINATION_SESSION_ID = "DESTINATION_SESSION_ID";
	final static String INITIATOR_SESSION_ID = "INITIATOR_SESSION_ID";

	int state = 1;

	public abstract void processEvent(SipServletRequest request, SipServletResponse response) throws Exception;

	public void printOutboundMessage(SipServletMessage message) throws UnsupportedEncodingException, IOException {
		String sdp;

		if (message.getContent() != null) {
			sdp = "w/ SDP";
		} else {
			sdp = "w/o SDP";
		}

		if (message instanceof SipServletRequest) {
			SipServletRequest rqst = (SipServletRequest) message;

			if (logger.isLoggable(Level.FINE)) {
				logger.fine("<-- " + this.getClass().getSimpleName() + " " + state + " " + rqst.getMethod() + " " + sdp
						+ " " + rqst.getTo());
			}
		} else {
			SipServletResponse rspn = (SipServletResponse) message;
			if (logger.isLoggable(Level.FINE)) {
				logger.fine("--> " + this.getClass().getSimpleName() + " " + state + " " + rspn.getMethod() + " "
						+ rspn.getReasonPhrase() + " " + sdp + " " + rspn.getTo());
			}
		}

	}

	public void printInboundMessage(SipServletMessage message) throws UnsupportedEncodingException, IOException {
		String sdp;

		if (message.getContent() != null) {
			sdp = "w/ SDP";
		} else {
			sdp = "w/o SDP";
		}

		if (message instanceof SipServletRequest) {
			SipServletRequest rqst = (SipServletRequest) message;
			if (logger.isLoggable(Level.FINE)) {
				logger.fine("--> " + this.getClass().getSimpleName() + " " + state + " " + rqst.getMethod() + " " + sdp
						+ " " + rqst.getTo());
			}
		} else {
			SipServletResponse rspn = (SipServletResponse) message;
			if (logger.isLoggable(Level.FINE)) {
				logger.fine("--> " + this.getClass().getSimpleName() + " " + state + " " + rspn.getMethod() + " "
						+ rspn.getStatus() + " " + rspn.getReasonPhrase() + " " + sdp + " " + rspn.getTo());
			}
		}
	}

}
