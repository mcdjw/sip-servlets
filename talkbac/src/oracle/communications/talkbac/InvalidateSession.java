package oracle.communications.talkbac;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

public class InvalidateSession extends CallStateHandler {
	private static final long serialVersionUID = 1L;

	@Override
	public void processEvent(SipApplicationSession appSession, TalkBACMessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {

		if (response != null && response.getMethod().equals("BYE")) {
			SipSession sipSession = response.getSession();
			sipSession.removeAttribute(CALL_STATE_HANDLER);
			//TEST!!!
			//sipSession.invalidate();
			//appSession.invalidate();
		} else if (request != null && request.getMethod().equals("NOTIFY")) {
			// just to handle stray NOTIFY messages
			SipServletResponse notifyResponse = request.createResponse(200);
			notifyResponse.send();
			this.printOutboundMessage(notifyResponse);
		}

	}

}
