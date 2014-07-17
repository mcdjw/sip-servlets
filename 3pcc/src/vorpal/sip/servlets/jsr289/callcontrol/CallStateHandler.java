package vorpal.sip.servlets.jsr289.callcontrol;

import java.io.Serializable;

import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public abstract class CallStateHandler implements Serializable{
	final static String CALL_STATE_HANDLER = "CALL_STATE_HANDLER";
	final static String PEER_SESSION_ID = "PEER_SESSION_ID";
	int state=0;
	SipServletRequest initiator=null;

	public abstract void processEvent(SipServletRequest request, SipServletResponse response) throws Exception;
	
	
}
