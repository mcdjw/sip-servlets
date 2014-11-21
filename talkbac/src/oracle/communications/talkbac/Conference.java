/*
 *
 *  A              Controller               B                   C
 *  |..RTP..................................|                   |
 *  |                   | (1) INVITE        |                   |
 *  |                   |-------------------------------------->|
 *  |                   | (2) 200 OK        |                   |
 *  |                   |<--------------------------------------|
 *  | (3) INVITE        |                   |                   |
 *  |<------------------|                   |                   |
 *  | (4) 200 OK        |                   |                   |
 *  |------------------>|                   |                   |
 *  | (5) ACK           |                   |                   |
 *  |<------------------|                   |                   |
 *  |                   | (6) ACK           |                   |
 *  |                   |-------------------------------------->|
 *  |..RTP......................................................|
 *  |..RTP..................................|                   |
 *
 */

package oracle.communications.talkbac;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

public class Conference extends CallStateHandler {
	Address origin;
	Address target;
	SipServletResponse targetResponse;
	SipServletResponse destinationResponse;
	SipSession originSession;

	public Conference(Address origin, Address target) {
		this.origin = origin;
		this.target = target;
	}

	@Override
	public void processEvent(SipApplicationSession appSession,  TalkBACMessageUtility msgUtility,SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		switch (state) {
		case 1: // Send INVITE
			this.originSession = this.findSession(appSession, origin);

			SipServletRequest targetRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", origin, target);
			targetRequest.send();

			state = 2;
			targetRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			break;
		case 2: // Receive 200 OK
		case 3: // Send INVITE

			if (status == 200) {
				targetResponse = response;
				
				SipServletRequest originRequest = TalkBACSipServlet.factory.createRequest(appSession,  "INVITE", target, origin);				
				originRequest.setContent(targetResponse.getContent(), targetResponse.getContentType());
				originRequest.send();

				state = 4;
				originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}

			break;
		case 4: // 200 OK
		case 5: // ACK
		case 6: // ACK
			if (status == 200) {
				destinationResponse = response;

				SipServletRequest destinationAck = destinationResponse.createAck();
				destinationAck.send();
				this.printOutboundMessage(destinationAck);

				SipServletRequest targetAck = targetResponse.createAck();
				targetAck.setContent(destinationResponse.getContent(), destinationResponse.getContentType());
				targetAck.send();
				this.printOutboundMessage(targetAck);

				originSession.removeAttribute(CALL_STATE_HANDLER);
				targetAck.getSession().removeAttribute(CALL_STATE_HANDLER);

			}
			break;
		}
	}
}
