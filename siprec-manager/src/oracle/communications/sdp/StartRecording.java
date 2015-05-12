/*
 * SBC                   OCCAS                  VSRPa                  VSRPb
 *  |(1) INVITE            |                      |                      |
 *  |--------------------->| (2) INVITE           |                      |
 *  |                      |--------------------->|                      |
 *  |                      | (3) INVITE           |                      |
 *  |                      |-------------------------------------------->|
 *  |                      | (4) 200 OK (active)  |                      |
 *  |                      |<---------------------|                      |
 *  |                      | (5) 200 OK (inactive)|                      |
 *  |                      |<--------------------------------------------|
 *  | (6) 200 OK (active)  |                      |                      |
 *  |<---------------------|                      |                      |
 *  | (7) ACK              |                      |                      |
 *  |--------------------->|                      |                      |
 *  |                      | (8) ACK              |                      |
 *  |                      |--------------------->|                      |
 *  |                      | (9) ACK              |                      |
 *  |                      |-------------------------------------------->|
 *  |                      |                      |                      |
 */

package oracle.communications.sdp;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.URI;

public class StartRecording extends CallStateHandler {
	SipServletRequest origRequest;
	SipServletResponse activeResponse = null;
	SipServletResponse inactiveResponse = null;

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		SipApplicationSession appSession = (request != null) ? request.getApplicationSession() : response.getApplicationSession();

		int status = (response != null) ? response.getStatus() : 0;

		switch (state) {
		case 1: // receive INVITE
		case 2: // send INVITE
		case 3: // send INVITE
			origRequest = request;

			state = 4;

			URI vsrp1URI = null;
			URI vsrp2URI = null;

			String to = request.getTo().getURI().toString();

			for (VrspPair vsrpPair : SiprecServlet.dialPlans) {
				if (to.matches(vsrpPair.getDialPlan())) {
					vsrp1URI = SiprecServlet.factory.createURI(vsrpPair.getPrimary());
					vsrp2URI = SiprecServlet.factory.createURI(vsrpPair.getSecondary());
				}
			}

			SipServletRequest vsrp1 = SiprecServlet.factory.createRequest(appSession, "INVITE", request.getFrom().getURI(), vsrp1URI);

			vsrp1.setContent(request.getContent(), request.getContentType());
			vsrp1.send();
			this.printOutboundMessage(vsrp1);
			vsrp1.getSession().setAttribute(CALL_STATE_HANDLER, this);

			SipServletRequest vsrp2 = SiprecServlet.factory.createRequest(appSession, "INVITE", request.getFrom().getURI(), vsrp2URI);
			vsrp2.setContent(request.getContent(), request.getContentType());
			vsrp2.send();
			this.printOutboundMessage(vsrp2);
			vsrp2.getSession().setAttribute(CALL_STATE_HANDLER, this);

			break;

		case 4: // receive 200 OK
		case 5: // receive 200 OK
		case 6: // send 200 OK

			if (status >= 200 && status < 300) {
				String body = response.getContent().toString();
				if (body.contains("a=inactive")) {
					inactiveResponse = response;
				} else {
					activeResponse = response;
				}

				if (activeResponse != null && inactiveResponse != null) {

					// save these valuse for later
					appSession.setAttribute(ACTIVE_VSRP_SESSION_ID, activeResponse.getSession().getId());
					appSession.setAttribute(INACTIVE_VSRP_SESSION_ID, inactiveResponse.getSession().getId());

					state = 7;
					SipServletResponse ok = origRequest.createResponse(200);
					ok.setContent(activeResponse.getContent(), activeResponse.getContentType());
					ok.send();
					this.printOutboundMessage(ok);
					ok.getSession().setAttribute(CALL_STATE_HANDLER, this);
				}
			} else if (status >= 400) {

				if (activeResponse == null && inactiveResponse == null) {
					inactiveResponse = response;
				} else {
					activeResponse = response;
					SipServletResponse errorResponse = origRequest.createResponse(response.getStatus(), response.getReasonPhrase());
					copyHeaders(response, errorResponse);
					errorResponse.setContent(response.getContent(), response.getContentType());
					errorResponse.send();
					this.printOutboundMessage(errorResponse);
					errorResponse.getSession().removeAttribute(CALL_STATE_HANDLER);
				}
			}

			break;

		case 7: // receive ACK
		case 8: // send ACK
		case 9: // send ACK

			request.getSession().removeAttribute(CALL_STATE_HANDLER);

			SipServletRequest activeAck = activeResponse.createAck();
			activeAck.setContent(request.getContent(), request.getContentType());
			activeAck.send();
			this.printOutboundMessage(activeAck);
			activeAck.getSession().removeAttribute(CALL_STATE_HANDLER);

			SipServletRequest inactiveAck = inactiveResponse.createAck();
			inactiveAck.setContent(request.getContent(), request.getContentType());
			inactiveAck.send();
			this.printOutboundMessage(inactiveAck);
			inactiveAck.getSession().removeAttribute(CALL_STATE_HANDLER);

			break;

		}

	}

}
