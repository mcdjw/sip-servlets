/*
 * http://tools.ietf.org/html/rfc3725
 *
 * 4.1.  Flow I
 *
 *  A              Controller               B
 *  |(1) INVITE         |                   |
 *  |------------------>|                   |
 *  |                   |(2) INVITE         |
 *  |                   |------------------>|
 *  |                   |(3) 200 OK         |
 *  |                   |<------------------|
 *  |(4) 200 OK         |                   |
 *  |<------------------|                   |
 *  |(5) ACK            |                   |
 *  |------------------>|                   |
 *  |                   |(6) ACK            |
 *  |                   |------------------>|
 *  | RTP               |                   |
 *  |.......................................|
 *
 */

package oracle.communications.talkbac;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class AcceptCall extends CallStateHandler {

	SipServletRequest originRequest;
	SipServletResponse destinationResponse;
	TalkBACMessageUtility msgUtility = new TalkBACMessageUtility();

	@Override
	public void processEvent(SipApplicationSession appSession, SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		if (request != null) {
			if (request.isInitial()) {
				originRequest = request;

				msgUtility.addClient(request.getFrom());
				msgUtility.addClient(request.getTo());
				TalkBACMessage msg = new TalkBACMessage(request.getApplicationSession(), "call_incoming");
				msg.setParameter("origin", request.getFrom().getURI().toString());
				msg.setParameter("destination", request.getTo().getURI().toString());
				msgUtility.send(msg);

				SipServletRequest destinationRequest = TalkBACSipServlet.factory.createRequest(request.getApplicationSession(), "INVITE", request.getFrom(),
						request.getTo());

				destinationRequest.setContent(request.getContent(), request.getContentType());
				destinationRequest.send();
				destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
				this.printOutboundMessage(destinationRequest);

			} else {

				if (request.getMethod().equals("ACK")) {
					SipServletRequest destinationAck = destinationResponse.createAck();
					destinationAck.setContent(request.getContent(), request.getContentType());
					destinationAck.send();
					this.printOutboundMessage(destinationAck);
					destinationAck.getSession().removeAttribute(CALL_STATE_HANDLER);

				} else if (request.getMethod().equals("PRACK")) {
					SipServletRequest destinationPrack = destinationResponse.createPrack();
					destinationPrack.setContent(request.getContent(), request.getContentType());
					destinationPrack.send();
					this.printOutboundMessage(destinationPrack);
					destinationPrack.getSession().setAttribute(CALL_STATE_HANDLER, this);
				} else {
					SipServletRequest destRequest = destinationResponse.getSession().createRequest(request.getMethod());
					destRequest.setContent(request.getContent(), request.getContentType());
					destRequest.send();
					this.printOutboundMessage(destRequest);
				}

			}

		} else {
			if (response.getStatus() >= 200 && response.getStatus() < 400) {
				TalkBACMessage msg = new TalkBACMessage(appSession, "call_answered");
				msg.setParameter("origin", response.getFrom().getURI().toString());
				msg.setParameter("destination", response.getTo().getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msgUtility.send(msg);
			}
			if (response.getStatus() >= 400) {
				TalkBACMessage msg = new TalkBACMessage(appSession, "call_rejected");
				msg.setParameter("origin", response.getFrom().getURI().toString());
				msg.setParameter("destination", response.getTo().getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msgUtility.send(msg);
			}

			destinationResponse = response;
			SipServletResponse originResponse = originRequest.createResponse(destinationResponse.getStatus());
			originResponse.setContent(destinationResponse.getContent(), destinationResponse.getContentType());
			originResponse.send();
			this.printOutboundMessage(originResponse);
			originResponse.getSession().setAttribute(CALL_STATE_HANDLER, this);
		}

	}

}
