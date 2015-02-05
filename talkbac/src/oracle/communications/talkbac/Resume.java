/*
 *   A                 Controller                  B
 *   |                      |(1) INVITE            |
 *   |                      |--------------------->|
 *   |                      |(2) 200 OK            |
 *   |                      |<---------------------|
 *   |(3) INVITE            |                      |
 *   |<---------------------|                      |
 *   |(4) 200 OK            |                      |
 *   |--------------------->|                      |
 *   |(5) ACK               |                      |
 *   |<---------------------|                      |
 *   |                      |(6) ACK               |
 *   |                      |--------------------->|
 *   |.............................................|
 *
 */

package oracle.communications.talkbac;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

public class Resume extends CallStateHandler {
	private static final long serialVersionUID = 1L;
	Address origin;
	Address destination;
	SipSession originSession = null;
	SipSession destinationSession = null;
	SipServletResponse destinationResponse;
	SipServletResponse originResponse;

	protected String success_message = "call_resumed";

	Resume(Address origin, Address destination) {
		this.origin = origin;
		this.destination = destination;
	}

	@Override
	public void processEvent(SipApplicationSession appSession, MessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		switch (state) {
		case 1: // send INVITE

			this.originSession = findSession(appSession, origin);
			this.destinationSession = findSession(appSession, destination);

			if (null == this.originSession || null == this.destinationSession) {
				TalkBACMessage msg = new TalkBACMessage(appSession, success_message);
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msg.setStatus(501, "Origin or destination not part of an existing call leg.");
				this.printOutboundMessage(msgUtility.send(msg));
				return;
			}

			SipServletRequest destinationRequest;
			if (destinationSession != null) {
				destinationRequest = destinationSession.createRequest("INVITE");
			} else {
				destinationRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", origin, destination);
				this.destinationSession = destinationRequest.getSession();
			}
			destinationRequest.send();
			this.printOutboundMessage(destinationRequest);

			state = 2;
			destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			break;

		case 2: // receive 200 OK
		case 3: // send INVITE

			if (status == 200) {
				this.destinationResponse = response;

				SipServletRequest originRequest = originSession.createRequest("INVITE");
				String content = destinationResponse.getContent().toString();

				originRequest.setContent(content, destinationResponse.getContentType());
				originRequest.send();
				this.printOutboundMessage(originRequest);

				state = 4;
				originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			} else if (status >= 400) {
				TalkBACMessage msg = new TalkBACMessage(appSession, success_message);
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				this.printOutboundMessage(msgUtility.send(msg));
			}

			break;

		case 4: // receive 200 OK
		case 5: // send ACK
		case 6: // send ACK

			if (status >= 200) {
				this.originResponse = response;

				SipServletRequest originAck = originResponse.createAck();
				originAck.send();
				this.printOutboundMessage(originAck);

				SipServletRequest destinationAck = destinationResponse.createAck();

				String content = originResponse.getContent().toString();

				destinationAck.setContent(content, originResponse.getContentType());
				destinationAck.send();
				this.printOutboundMessage(destinationAck);

				destinationAck.getSession().removeAttribute(CALL_STATE_HANDLER);
				originAck.getSession().removeAttribute(CALL_STATE_HANDLER);

				TalkBACMessage msg = new TalkBACMessage(appSession, success_message);
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				this.printOutboundMessage(msgUtility.send(msg));
			} else if (status >= 400) {
				TalkBACMessage msg = new TalkBACMessage(appSession, success_message);
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				this.printOutboundMessage(msgUtility.send(msg));
			}

			break;
		}

	}

}
