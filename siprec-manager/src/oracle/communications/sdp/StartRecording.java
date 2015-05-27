/*
 * SBC                   OCCAS                  VRSPa                  VRSPb
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

	StartRecording() {
	}

	StartRecording(StartRecording that) {
		super(that);
		this.origRequest = that.origRequest;
		this.activeResponse = that.activeResponse;
		this.inactiveResponse = that.inactiveResponse;
	}

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

			VrspPair pair = VrspPair.findMatchingVrspPair(request);
			if (pair == null) {
				// if no dial plan found, return 404 error code
				SipServletResponse rsp404 = request.createResponse(404);
				rsp404.send();
				this.printOutboundMessage(rsp404);
				return;
			}

			String vsrp1URI = pair.getPrimary();
			String vsrp2URI = pair.getSecondary();

			SipServletRequest vrsp1 = SiprecServlet.factory.createRequest(appSession, "INVITE", request.getFrom().getURI().toString(), vsrp1URI);
			copyHeaders(request, vrsp1);
			vrsp1.setContent(request.getContent(), request.getContentType());
			vrsp1.send();
			this.printOutboundMessage(vrsp1);
			vrsp1.getSession().setAttribute(CALL_STATE_HANDLER, this);

			SipServletRequest vrsp2 = SiprecServlet.factory.createRequest(appSession, "INVITE", request.getFrom().getURI().toString(), vsrp2URI);
			copyHeaders(request, vrsp2);
			vrsp2.setContent(request.getContent(), request.getContentType());
			vrsp2.send();
			this.printOutboundMessage(vrsp2);
			vrsp2.getSession().setAttribute(CALL_STATE_HANDLER, this);

			break;

		case 4: // receive 200 OK
		case 5: // receive 200 OK
		case 6: // send 200 OK

			if (status >= 200 && status < 300) {
				String body = response.getContent().toString();
				if (body.contains("a=inactive")) {
					inactiveResponse = response;
					appSession.setAttribute(INACTIVE_VSRP_SESSION_ID, inactiveResponse.getSession().getId());
					SipServletRequest inactiveAck = inactiveResponse.createAck();
					inactiveAck.send();
					this.printOutboundMessage(inactiveAck);
					inactiveAck.getSession().removeAttribute(CALL_STATE_HANDLER);
				} else {
					activeResponse = response;
					appSession.setAttribute(ACTIVE_VSRP_SESSION_ID, activeResponse.getSession().getId());
					state = 7;
					SipServletResponse ok = origRequest.createResponse(200);
					ok.setContent(activeResponse.getContent(), activeResponse.getContentType());
					ok.send();
					this.printOutboundMessage(ok);
					ok.getSession().setAttribute(CALL_STATE_HANDLER, this);
				}

			} else if (status >= 400) {

				if (inactiveResponse == null) {
					// forget about this one
					inactiveResponse = response;
					inactiveResponse.getSession().removeAttribute(CALL_STATE_HANDLER);
				} else if (inactiveResponse != null) {
					// both errored out
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

			break;

		}

	}

}
