package oracle.communications.talkbac;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

public class InvalidateSession extends CallStateHandler {

	@Override
	public void processEvent(SipApplicationSession appSession,  TalkBACMessageUtility msgUtility,SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		SipSession sipSession = null;
		if (request != null) {
			sipSession = request.getSession();
		} else if (response != null) {
			sipSession = response.getSession();
		}

		sipSession.invalidate();

	}

}
