/*
 *
 *  A              Controller               B                   C
 *  | RTP               |                   |                   |
 *  |.......................................|                   |
 *  | (1) BYE           |                   |                   |
 *  |<------------------|                   |                   |
 *  | (2) 200 OK        |                   |                   |
 *  |------------------>|                   |                   |
 *  |                   | (3) REFER         |                   |
 *  |                   |------------------>|                   |
 *  |                   | (4) 202 Accepted  |                   |
 *  |                   |<------------------|                   |
 *  |                   | (5) NOTIFY(s)     |                   |
 *  |                   |<------------------|                   |
 *  |                   | (6) 200 OK        |                   |
 *  |                   |------------------>|                   |
 *  |                   |                   | (7) INVITE        |
 *  |                   |                   |------------------>|
 *  |                   |                   | (8) 200 OK        |
 *  |                   |                   |<------------------|
 *  |                   |                   | (9) ACK           |
 *  |                   |                   |------------------>|
 *  |                   |                   | RTP               |
 *  |                   |                   |...................|
 *  |                   |                   |                   |
 */

package oracle.communications.talkbac;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

public class Transfer extends CallStateHandler {
	private static final long serialVersionUID = 1L;
	Address origin;
	Address destination;
	Address target;
	SipSession destinationSession;
	SipSession originSession;

	public Transfer(Address origin, Address destination, Address target) {
		this.origin = origin;
		this.destination = destination;
		this.target = target;
	}

	@Override
	public void processEvent(SipApplicationSession appSession, MessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		// I don't want NOTIFY in my state machine
		if (request != null && request.getMethod().equals("NOTIFY")) {
			SipServletResponse notifyResponse = request.createResponse(200);
			notifyResponse.send();
			this.printOutboundMessage(notifyResponse);
			return;
		}

		switch (state) {
		case 1: // Send BYE
			this.destinationSession = this.findSession(appSession, destination);
			this.originSession = this.findSession(appSession, origin);

			SipServletRequest bye = this.originSession.createRequest("BYE");
			bye.send();
			this.printOutboundMessage(bye);

			state = 2;
			this.originSession.setAttribute(CALL_STATE_HANDLER, this);

			break;
		case 2: // Receive BYE 200 OK
		case 3: // Send REFER

			if (status == 200) {
				SipServletRequest refer = this.destinationSession.createRequest("REFER");
				refer.setAddressHeader("Refer-To", target);
				refer.setAddressHeader("Referred-By", origin);
				refer.send();
				this.printOutboundMessage(refer);

				state = 4;
				destinationSession.setAttribute(CALL_STATE_HANDLER, this);
				originSession.removeAttribute(CALL_STATE_HANDLER);
			}

			TalkBACMessage msg = new TalkBACMessage(appSession, "call_transferred");
			msg.setParameter("origin", origin.getURI().toString());
			msg.setParameter("destination", destination.getURI().toString());
			msg.setParameter("target", target.getURI().toString());
			msg.setStatus(status, response.getReasonPhrase());
			msgUtility.send(msg);

			break;
		case 4: // Receive 200 OK

			for (ServletTimer t : appSession.getTimers()) {
				t.cancel();
			}

			appSession.invalidate();
			break;
		}

	}
}
