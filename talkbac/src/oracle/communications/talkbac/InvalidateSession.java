package oracle.communications.talkbac;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

public class InvalidateSession extends CallStateHandler {

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		SipApplicationSession appSession = null;
		SipSession sipSession = null;
		if (request != null) {
			appSession = request.getApplicationSession();
			sipSession = request.getSession();
		} else if (response != null) {
			appSession = response.getApplicationSession();
			sipSession = response.getSession();
		}

		sipSession.invalidate();

	}

}
