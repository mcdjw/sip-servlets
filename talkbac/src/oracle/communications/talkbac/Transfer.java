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
	SipServletRequest transferRequest;
	SipServletRequest destinationRequest;
	SipServletResponse transferResponse;

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {

		int status = (null != response) ? response.getStatus() : 0;

		SipApplicationSession appSession;

		switch (state) {
		case 1: {
			origin = request.getFrom();
			destination = request.getTo();
			appSession = request.getApplicationSession();
			transferRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", origin, destination);
			transferRequest.send();

			state = 2;
			transferRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
		}
			break;
		case 2:
		case 3: {
			if (status == 200) {
				appSession = response.getApplicationSession();
				String destinationSessionId = (String) appSession.getAttribute(DESTINATION_SESSION_ID);

				SipSession destinationSession = appSession.getSipSession(destinationSessionId);
				destinationRequest = destinationSession.createRequest("INVITE");
				destinationRequest.setContent(response.getContent(), response.getContentType());
				destinationRequest.send();

				state = 4;
				transferResponse = response;
				destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}
		}
			break;
		case 4: // 200 OK
		case 5: // ACK
		case 6: // ACK
		case 7: // BYE
			if (status == 200) {
				response.createAck().send();
				SipServletRequest transferAck = transferResponse.createAck();
				transferAck.setContent(response.getContent(), response.getContentType());
				transferAck.send();

				appSession = response.getApplicationSession();
				String originSessionId = (String) response.getApplicationSession().getAttribute(ORIGIN_SESSION_ID);
				SipSession originSession = appSession.getSipSession(originSessionId);
				SipServletRequest byeRequest = originSession.createRequest("BYE");
				byeRequest.send();

				destinationRequest.getSession().setAttribute(PEER_SESSION_ID, transferRequest.getSession().getId());
				transferRequest.getSession().setAttribute(PEER_SESSION_ID, destinationRequest.getSession().getId());

				state = 8;
				byeRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			}
			break;
		case 8:

			response.getSession().removeAttribute(CALL_STATE_HANDLER);
			transferRequest.getSession().removeAttribute(CALL_STATE_HANDLER);
			destinationRequest.getSession().removeAttribute(CALL_STATE_HANDLER);
			break;

		}

	}
}
