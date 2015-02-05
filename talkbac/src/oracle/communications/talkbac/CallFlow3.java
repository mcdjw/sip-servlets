/*
 * http://tools.ietf.org/html/rfc3725
 * 4.3.  Flow III
 *
 *           A                 Controller                  B
 *           |(1) INVITE no SDP     |                      |
 *           |<---------------------|                      |
 *           |(2) 200 offer1        |                      |
 *           |--------------------->|                      |
 *           |(3) ACK answer1 (bh)  |                      |
 *           |<---------------------|                      |
 *           |                      |(4) INVITE no SDP     |
 *           |                      |--------------------->|
 *           |                      |(5) 200 OK offer2     |
 *           |                      |<---------------------|
 *           |(6) INVITE offer2'    |                      |
 *           |<---------------------|                      |
 *           |(7) 200 answer2'      |                      |
 *           |--------------------->|                      |
 *           |                      |(8) ACK answer2       |
 *           |                      |--------------------->|
 *           |(9) ACK               |                      |
 *           |<---------------------|                      |
 *           |(10) RTP              |                      |
 *           |.............................................|
 *
 */

package oracle.communications.talkbac;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class CallFlow3 extends CallFlowHandler {
	private static final long serialVersionUID = 1L;
	Address origin;
	Address destination;

	SipServletRequest destinationRequest;
	SipServletResponse destinationResponse;

	SipServletRequest originRequest;
	SipServletResponse originResponse;

	CallFlow3(Address origin, Address destination) {
		this.origin = origin;
		this.destination = destination;
	}

	@Override
	public void processEvent(SipApplicationSession appSession, MessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		TalkBACMessage msg;

		switch (state) {
		case 1: {
			msg = new TalkBACMessage(appSession, "call_created");
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
			if (status == 200) {
				discoverOptions(response);

				SipServletRequest originAck = response.createAck();
				originAck.setContent(blackhole.getBytes(), "application/sdp");
				originAck.send();
				this.printOutboundMessage(originAck);

				destinationRequest.send();
				this.printOutboundMessage(destinationRequest);

				state = 5;
				originResponse = response;
				destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

				msg = new TalkBACMessage(response.getApplicationSession(), "source_connected");
				msg.setStatus(183, "Session Progress");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				this.printOutboundMessage(msgUtility.send(msg));

			}

			if (status >= 300) {
				msg = new TalkBACMessage(response.getApplicationSession(), "call_failed");
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				this.printOutboundMessage(msgUtility.send(msg));
			}

		}
			break;

		case 5:
		case 6: {
			if (status == 200) {
				originRequest = originRequest.getSession().createRequest("INVITE");
				originRequest.setContent(response.getContent(), response.getContentType());
				originRequest.send();
				this.printOutboundMessage(originRequest);

				state = 7;
				destinationResponse = response;
				originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

				msg = new TalkBACMessage(response.getApplicationSession(), "destination_connected");
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				this.printOutboundMessage(msgUtility.send(msg));

			}

			if (status >= 300) {
				SipServletRequest bye = originResponse.getSession().createRequest("BYE");
				bye.send();
				this.printOutboundMessage(bye);

				response.getSession().removeAttribute(CALL_STATE_HANDLER);
				originResponse.getSession().removeAttribute(CALL_STATE_HANDLER);

				msg = new TalkBACMessage(response.getApplicationSession(), "call_failed");
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				this.printOutboundMessage(msgUtility.send(msg));
			}

		}
			break;

		case 7:
		case 8:
		case 9: {
			if (status == 200) {
				originResponse = response;
				SipServletRequest destinationAck = destinationResponse.createAck();
				destinationAck.setContent(originResponse.getContent(), originResponse.getContentType());
				destinationAck.send();
				this.printOutboundMessage(destinationAck);

				SipServletRequest originAck = originResponse.createAck();
				originAck.send();
				this.printOutboundMessage(originAck);

				destinationAck.getSession().removeAttribute(CALL_STATE_HANDLER);
				originAck.getSession().removeAttribute(CALL_STATE_HANDLER);

				msg = new TalkBACMessage(response.getApplicationSession(), "call_connected");
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

				break;
			}

			if (status >= 300) {
				SipServletRequest bye = originResponse.getSession().createRequest("BYE");
				bye.send();
				this.printOutboundMessage(bye);

				response.getSession().removeAttribute(CALL_STATE_HANDLER);
				originResponse.getSession().removeAttribute(CALL_STATE_HANDLER);
			}

			// initiator.createResponse(response.getStatus(),
			// response.getReasonPhrase()).send();
		}

		}

	}

}
