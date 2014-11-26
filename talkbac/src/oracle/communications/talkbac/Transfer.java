/*
 *
 *  A              Controller               B                   C
 *  | RTP               |                   |                   |
 *  |.......................................|                   |
 *  |                   | (1) INVITE        |                   |
 *  |                   |-------------------------------------->|
 *  |                   | (2) 200 OK        |                   |
 *  |                   |<--------------------------------------|
 *  |                   | (3) INVITE        |                   |
 *  |                   |------------------>|                   |
 *  |                   | (4) 200 OK        |                   |
 *  |                   |<------------------|                   |
 *  |                   | (5) ACK           |                   |
 *  |                   |------------------>|                   |
 *  |                   | (6) ACK           |                   |
 *  |                   |-------------------------------------->|
 *  | (7) BYE           |                   |                   |
 *  |<------------------|                   |                   |
 *  | (8) 200 OK        |                   |                   |
 *  |------------------>|                   |                   |
 *  |                   |                   | RTP               |
 *  |                   |                   |...................|
 *
 */

package oracle.communications.talkbac;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

public class Transfer extends CallStateHandler {
	Address origin;
	Address destination;
	Address target;
	SipServletResponse targetResponse;
	SipServletResponse destinationResponse;
	SipSession destinationSession;
	SipSession originSession;

	public Transfer(Address origin, Address destination, Address target) {
		this.origin = origin;
		this.destination = destination;
		this.target = target;
	}

	@Override
	public void processEvent(SipApplicationSession appSession, TalkBACMessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		switch (state) {
		case 1: // Send INVITE
			appSession.setAttribute(TalkBACSipServlet.ORIGIN_ADDRESS, destination);
			appSession.setAttribute(TalkBACSipServlet.DESTINATION_ADDRESS, target);

			this.destinationSession = this.findSession(appSession, destination);
			this.originSession = this.findSession(appSession, origin);

			SipServletRequest targetRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", destination, target);
			targetRequest.send();

			state = 2;
			targetRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			break;
		case 2: // Receive 200 OK
		case 3: // Send INVITE

			if (status == 200) {
				targetResponse = response;
				SipServletRequest destinationRequest = destinationSession.createRequest("INVITE");
				destinationRequest.setContent(targetResponse.getContent(), targetResponse.getContentType());
				destinationRequest.send();

				state = 4;
				destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}
			
			if (status >= 400) {
				TalkBACMessage msg = new TalkBACMessage(appSession, "call_transferred");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msg.setParameter("target", target.getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msgUtility.send(msg);
			}
			

			break;
		case 4: // 200 OK
		case 5: // ACK
		case 6: // ACK
		case 7: // BYE
			if (status == 200) {
				destinationResponse = response;

				appSession.setAttribute(TalkBACSipServlet.ORIGIN_ADDRESS, destinationResponse.getSession().getRemoteParty());
				appSession.setAttribute(TalkBACSipServlet.DESTINATION_ADDRESS, targetResponse.getSession().getRemoteParty());

				SipServletRequest destinationAck = destinationResponse.createAck();
				destinationAck.send();
				this.printOutboundMessage(destinationAck);

				SipServletRequest targetAck = targetResponse.createAck();
				targetAck.setContent(destinationResponse.getContent(), destinationResponse.getContentType());
				targetAck.send();
				this.printOutboundMessage(targetAck);

				destinationAck.getSession().removeAttribute(CALL_STATE_HANDLER);
				targetAck.getSession().removeAttribute(CALL_STATE_HANDLER);

				SipServletRequest byeRequest = originSession.createRequest("BYE");
				byeRequest.send();
				this.printOutboundMessage(byeRequest);
				msgUtility.removeEndpoint(originSession.getRemoteParty());
				byeRequest.getSession().setAttribute(CALL_STATE_HANDLER, new InvalidateSession());

				response.getSession().removeAttribute(CALL_STATE_HANDLER);

			}

			if (status >= 200) {
				TalkBACMessage msg = new TalkBACMessage(appSession, "call_transferred");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msg.setParameter("target", target.getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msgUtility.send(msg);
			}

			break;

		}

	}
}
