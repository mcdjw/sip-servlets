/*
 * SBC                   OCCAS                  VRSPa                  VRSPb
 *  |(1) BYE               |                      |                      |
 *  |--------------------->| (2) BYE              |                      |
 *  |                      |--------------------->|                      |
 *  |                      | (3) BYE              |                      |
 *  |                      |-------------------------------------------->|
 *  |                      | (4) 200 OK (active)  |                      |
 *  |                      |<---------------------|                      |
 *  |                      | (5) 200 OK (inactive)|                      |
 *  |                      |<--------------------------------------------|
 *  | (6) 200 OK (active)  |                      |                      |
 *  |<---------------------|                      |                      |
 */

package oracle.communications.sdp;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

public class EndRecording extends CallStateHandler {
	SipServletRequest byeRequest = null;
	SipServletRequest activeBye;
	SipServletRequest inactiveBye;

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		SipApplicationSession appSession = (request != null) ? request.getApplicationSession() : response.getApplicationSession();

		switch (state) {
		case 1:
		case 2:
		case 3:
			byeRequest = request;
			state = 4;

			String activeSessionId = (String) appSession.getAttribute(ACTIVE_VSRP_SESSION_ID);
			if (activeSessionId != null) {
				SipSession activeSession = appSession.getSipSession(activeSessionId);
				activeBye = activeSession.createRequest("BYE");
				copyHeaders(byeRequest, activeBye);
				activeBye.setContent(request.getContent(), request.getContentType());
				activeBye.send();
				this.printOutboundMessage(activeBye);
				activeSession.setAttribute(CALL_STATE_HANDLER, this);
			}

			String inactiveSessionId = (String) appSession.getAttribute(INACTIVE_VSRP_SESSION_ID);
			if (inactiveSessionId != null) {
				SipSession inactiveSession = appSession.getSipSession(inactiveSessionId);
				inactiveBye = inactiveSession.createRequest("BYE");
				copyHeaders(byeRequest, inactiveBye);
				inactiveBye.setContent(request.getContent(), request.getContentType());
				inactiveBye.send();
				this.printOutboundMessage(inactiveBye);
				inactiveSession.setAttribute(CALL_STATE_HANDLER, this);
			}

			break;
		case 4:
		case 5:
		case 6:
			if (response.getRequest() == activeBye) {
				SipServletResponse byeResponse = byeRequest.createResponse(response.getStatus(), response.getReasonPhrase());
				copyHeaders(response, byeResponse);
				byeResponse.setContent(response.getContent(), byeResponse.getContentType());
				byeResponse.send();
				this.printOutboundMessage(byeResponse);
				byeResponse.getSession().removeAttribute(CALL_STATE_HANDLER);
			}

			response.getSession().removeAttribute(CALL_STATE_HANDLER);
		}

	}

}
