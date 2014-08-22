package oracle.communications.talkbac;

import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class KeepAlive extends CallStateHandler {

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response) throws Exception {

		SipApplicationSession appSession = request.getApplicationSession();
		int expires = request.getExpires();
		appSession.setExpires(expires);
		request.getProxy().proxyTo(request.getRequestURI());

	}

}
