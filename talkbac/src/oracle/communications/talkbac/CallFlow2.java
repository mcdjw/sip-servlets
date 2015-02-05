/*
 * http://tools.ietf.org/html/rfc3725
 *
 * 4.2.  Flow II
 *
 *           A              Controller               B
 *           |(1) INVITE bh sdp1 |                   |
 *           |<------------------|                   |
 *           |(2) 200 sdp2       |                   |
 *           |------------------>|                   |
 *           |                   |(3) INVITE sdp2    |
 *           |                   |------------------>|
 *           |(4) ACK            |                   |
 *           |<------------------|                   |
 *           |                   |(5) 200 OK sdp3    |
 *           |                   |<------------------|
 *           |                   |(6) ACK            |
 *           |                   |------------------>|
 *           |(7) INVITE sdp3    |                   |
 *           |<------------------|                   |
 *           |(8) 200 OK sdp2    |                   |
 *           |------------------>|                   |
 *           |(9) ACK            |                   |
 *           |<------------------|                   |
 *           |(10) RTP           |                   |
 *           |.......................................|
 *
 */

package oracle.communications.talkbac;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class CallFlow2 extends CallFlowHandler {
	private static final long serialVersionUID = 1L;
	Address origin;
	Address destination;

	SipServletRequest destinationRequest;
	SipServletResponse destinationResponse;

	SipServletRequest originRequest;
	SipServletResponse originResponse;

	CallFlow2(Address origin, Address destination) {
		this.origin = origin;
		this.destination = destination;
	}

	@Override
	public void processEvent(SipApplicationSession appSession, MessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;
		TalkBACMessage msg;

		switch (state) {
		case 1: {// Send INVITE (black hole)
			msg = new TalkBACMessage(appSession, "call_created");
			msg.setParameter("origin", origin.getURI().toString());
			msg.setParameter("destination", destination.getURI().toString());
			this.printOutboundMessage(msgUtility.send(msg));

			destinationRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", origin, destination);
			if (TalkBACSipServlet.callInfo != null) {
				destinationRequest.setHeader("Call-Info", TalkBACSipServlet.callInfo);
				destinationRequest.setHeader("Session-Expires", "3600;refresher=uac");
				destinationRequest.setHeader("Allow", "INVITE, BYE, OPTIONS, CANCEL, ACK, REGISTER, NOTIFY, REFER, SUBSCRIBE, PRACK, MESSAGE, PUBLISH");
			}

			originRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", destination, origin);

			destinationRequest.getSession().setAttribute(PEER_SESSION_ID, originRequest.getSession().getId());
			originRequest.getSession().setAttribute(PEER_SESSION_ID, destinationRequest.getSession().getId());

			originRequest.setContent(blackhole.getBytes(), "application/sdp");
			originRequest.send();
			this.printOutboundMessage(originRequest);

			state = 2;
			originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			appSession.setAttribute(DESTINATION_SESSION_ID, destinationRequest.getSession().getId());
			appSession.setAttribute(ORIGIN_SESSION_ID, originRequest.getSession().getId());

			destinationRequest.getSession().setAttribute(REQUEST_DIRECTION, "OUTBOUND");
			destinationRequest.getSession().setAttribute(INITIAL_INVITE_REQUEST, destinationRequest);
			originRequest.getSession().setAttribute(REQUEST_DIRECTION, "OUTBOUND");
			originRequest.getSession().setAttribute(INITIAL_INVITE_REQUEST, originRequest);

		}
			break;

		case 2:
		case 3:
		case 4: {
			// receive 200 OK
			// send invite
			// send ack
			if (status == 200) {

				discoverOptions(response);

				originResponse = response;

				destinationRequest.setContent(originResponse.getContent(), originResponse.getContentType());
				destinationRequest.send();
				this.printOutboundMessage(destinationRequest);

				SipServletRequest originAck = originResponse.createAck();
				originAck.send();
				this.printOutboundMessage(originAck);

				state = 5;
				destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

				// initiator.createResponse(183).send();
				msg = new TalkBACMessage(appSession, "source_connected");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msg.setStatus(183, "Session Progress");
				this.printOutboundMessage(msgUtility.send(msg));
			}

			if (status >= 300) {
				msg = new TalkBACMessage(appSession, "call_failed");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				this.printOutboundMessage(msgUtility.send(msg));
			}
		}
			break;

		case 5:
		case 6:
		case 7: {
			// receive 200 ok
			// send ack
			// send invite

			if (status == 200) {
				destinationResponse = response;
				SipServletRequest ack = destinationResponse.createAck();
				ack.send();
				this.printOutboundMessage(ack);

				originRequest = originRequest.getSession().createRequest("INVITE");
				originRequest.setContent(destinationResponse.getContent(), destinationResponse.getContentType());
				originRequest.send();
				this.printOutboundMessage(originRequest);

				state = 8;
				destinationResponse = response;
				originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

				msg = new TalkBACMessage(appSession, "destination_connected");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				this.printOutboundMessage(msgUtility.send(msg));

			}
			if (status >= 300) {
				SipServletRequest bye = originResponse.getSession().createRequest("BYE");
				bye.send();
				this.printOutboundMessage(bye);

				response.getSession().removeAttribute(CALL_STATE_HANDLER);
				originResponse.getSession().removeAttribute(CALL_STATE_HANDLER);

				msg = new TalkBACMessage(appSession, "call_failed");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				this.printOutboundMessage(msgUtility.send(msg));
			}

		}
			break;

		case 8:
		case 9:
			// receive 200 ok
			// send ack
			// SipServletResponse initResponse =
			// initiator.createResponse(response.getStatus(),
			// response.getReasonPhrase());

			if (status == 200) {

				SipServletRequest ack = response.createAck();
				ack.send();
				this.printOutboundMessage(ack);

				destinationRequest.getSession().removeAttribute(CALL_STATE_HANDLER);
				originRequest.getSession().removeAttribute(CALL_STATE_HANDLER);

				msg = new TalkBACMessage(appSession, "call_connected");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				this.printOutboundMessage(msgUtility.send(msg));

				if (kpml_supported) {
					KpmlRelay kpmlRelay = new KpmlRelay(3600);
					kpmlRelay.delayedSubscribe(appSession, 500);
				}

				// Launch Keep Alive Timer
				if (update_supported) {
					UpdateKeepAlive ka = new UpdateKeepAlive(60 * 1000);
					ka.startTimer(appSession);
				}

			}

			if (status >= 300) {
				SipServletRequest bye = originResponse.getSession().createRequest("BYE");
				bye.send();
				this.printOutboundMessage(bye);

				response.getSession().removeAttribute(CALL_STATE_HANDLER);
				originResponse.getSession().removeAttribute(CALL_STATE_HANDLER);

				msg = new TalkBACMessage(appSession, "call_failed");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				this.printOutboundMessage(msgUtility.send(msg));

				// state = 10;
				// initResponse.getSession().setAttribute(CALL_STATE_HANDLER,
				// this);
			}

			// initResponse.send();

			break;
		// case 10: // ACK from initiator
		// request.getSession().removeAttribute(CALL_STATE_HANDLER);
		// break;

		}

	}

}
