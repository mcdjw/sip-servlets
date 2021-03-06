/*
 *   A              Controller               B
 *   |(1) INVITE         |                   |
 *   |------------------>|                   |
 *   |                   |(2) INVITE         |
 *   |                   |------------------>|
 *   |                   |(3) 200 OK         |
 *   |                   |<------------------|
 *   |(4) 200 OK         |                   |
 *   |<------------------|                   |
 *   |(5) ACK            |                   |
 *   |------------------>|                   |
 *   |                   |(6) ACK            |
 *   |                   |------------------>|
 *   |(7) RTP            |                   |
 *   |.......................................|
 */

package oracle.communications.talkbac;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

public class Reinvite extends CallStateHandler {
	private static final long serialVersionUID = 1L;
	SipServletRequest originalRequest;
	SipServletResponse peerResponse;

	@Override
	public void processEvent(SipApplicationSession appSession, MessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {

		switch (state) {
		case 1:
		case 2:
			// SipApplicationSession appSession =
			// request.getApplicationSession();
			String peerSessionId = (String) request.getSession().getAttribute(PEER_SESSION_ID);
			SipSession peerSession = appSession.getSipSession(peerSessionId);
			SipServletRequest reinvite = peerSession.createRequest(request.getMethod());

			copyHeadersAndContent(request, reinvite);
	
			reinvite.send();
			this.printOutboundMessage(reinvite);

			state = 3;
			originalRequest = request;
			reinvite.getSession().setAttribute(CALL_STATE_HANDLER, this);
			break;
		case 3:
		case 4:

			SipServletResponse newResponse = originalRequest.createResponse(response.getStatus(), response.getReasonPhrase());

			copyHeadersAndContent(response, newResponse);
			
			newResponse.send();
			this.printOutboundMessage(newResponse);

			state = 5;
			peerResponse = response;
			newResponse.getSession().setAttribute(CALL_STATE_HANDLER, this);

			break;
		case 5:
		case 6:

			SipServletRequest peerAck = peerResponse.createAck();

			copyHeadersAndContent(request, peerAck);

			peerAck.send();
			this.printOutboundMessage(peerAck);

			peerAck.getSession().removeAttribute(CALL_STATE_HANDLER);
			request.getSession().removeAttribute(CALL_STATE_HANDLER);

			break;
		}

	}

}
