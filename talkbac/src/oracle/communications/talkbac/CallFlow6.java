/*
 * Ringback Tone during Blind Transfer
 * w/DTMF & Keep Alive
 *
 *             A                 Controller                  B
 *             |(1) INVITE w/SDP      |                      |
 *             |<---------------------|                      |
 *             |(2) 200 OK            |                      |
 *             |--------------------->|                      |
 *             |(3) ACK               |                      |
 *             |<---------------------|                      |
 *             |              (4) 250ms delay                |             
 *             |(5) REFER             |                      |
 *             |<---------------------|                      |
 *             |(6) 202 Accepted      |                      |
 *             |--------------------->|                      |
 *             |(7) NOTIFY            |                      |
 *             |--------------------->|                      |
 *             |(8) 200 OK            |                      |
 *             |<---------------------|                      |
 *             |(9) BYE               |                      |
 *             |<---------------------|                      |
 *             |(10) 200 OK           |                      |
 *             |--------------------->|                      |
 *             |(11) INVITE w/SDP     |                      |
 *             |--------------------->|                      |
 *             |                      |(12) INVITE w/o SDP   |
 *             |                      |--------------------->|
 *             |                      |(13a) 180 Ringing     |
 *             |                      |<---------------------|
 *             |(14a) 180 Ringing     |                      |
 *             |<---------------------|                      |
 *             |                      |(13b) 200 OK          |
 *             |                      |<---------------------|
 *             |(13b) 200 OK          |                      |
 *             |<---------------------|                      |
 *             |(14) ACK              |                      |
 *             |--------------------->|                      |
 *             |                      |(15) ACK              |
 *             |                      |--------------------->|
 *             |.............................................|    
 *                                ReINVITE
 *
 */

package oracle.communications.talkbac;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class CallFlow6 extends CallFlowHandler {
	private static final long serialVersionUID = 1L;
	private Address origin;
	private Address destination;
	// private String destinationUser;
	// private String originUser;

	// String callId;
	// String toTag;
	// String fromTag;

	SipServletRequest destinationRequest;
	SipServletResponse destinationResponse;

	SipServletRequest originRequest;
	SipServletResponse originResponse;
	SipServletRequest originPrack;

	CallFlow6(Address origin, Address destination) {
		this.origin = origin;
		this.destination = destination;
	}

	CallFlow6(CallFlow6 that) {
		this.origin = that.origin;
		this.destination = that.destination;
		this.destinationRequest = that.destinationRequest;
		this.destinationResponse = that.destinationResponse;
		this.originRequest = that.originRequest;
		this.originResponse = that.originResponse;

	}

	@Override
	public void processEvent(SipApplicationSession appSession, MessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {
		TalkBACMessage msg;
		int status = (null != response) ? response.getStatus() : 0;

		// Deal with unexpected requests
		if (request != null) {
			switch (TalkBACSipServlet.SipMethod.valueOf(request.getMethod())) {
			case CANCEL:
			case OPTIONS:
			case REGISTER:
			case SUBSCRIBE:
			case PUBLISH:
			case INFO:
			case REFER:
			case UPDATE:
				SipServletResponse updateResponse = request.createResponse(200);
				updateResponse.send();
				this.printOutboundMessage(updateResponse);
				return;
			default:
				// do nothing
			}
		}

		switch (state) {
		case 1: // send INVITE
			appSession.setAttribute(TalkBACSipServlet.ORIGIN_ADDRESS, origin);
			appSession.setAttribute(TalkBACSipServlet.DESTINATION_ADDRESS, destination);

			msg = new TalkBACMessage(appSession, "call_created");
			msg.setParameter("origin", origin.getURI().toString());
			msg.setParameter("destination", destination.getURI().toString());
			this.printOutboundMessage(msgUtility.send(msg));

			originRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", destination, origin);
			destinationRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", origin, destination);

			// Save this info in case we need to terminate
			destinationRequest.getSession().setAttribute(REQUEST_DIRECTION, "OUTBOUND");
			destinationRequest.getSession().setAttribute(INITIAL_INVITE_REQUEST, destinationRequest);
			originRequest.getSession().setAttribute(REQUEST_DIRECTION, "OUTBOUND");
			originRequest.getSession().setAttribute(INITIAL_INVITE_REQUEST, originRequest);

			destinationRequest.getSession().setAttribute(PEER_SESSION_ID, originRequest.getSession().getId());
			originRequest.getSession().setAttribute(PEER_SESSION_ID, destinationRequest.getSession().getId());
			originRequest.getSession().setAttribute(INITIAL_INVITE_REQUEST, originRequest);

//			originRequest.setHeader("Allow", ORIGIN_ALLOW);
			originRequest.setHeader("Allow", DESTINATION_ALLOW);
			originRequest.setHeader("Call-Info", TalkBACSipServlet.callInfo);
			originRequest.setHeader("Allow-Events", "kpml");
			originRequest.setHeader("Supported", "100rel, timer, resource-priority, replaces");
			// originRequest.setHeader("Require", "100rel");

			// originRequest.setContent(blackhole.getBytes(), "application/sdp");
			originRequest.send();
			this.printOutboundMessage(originRequest);

			state = 2;
			originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			appSession.setAttribute(DESTINATION_SESSION_ID, destinationRequest.getSession().getId());
			appSession.setAttribute(ORIGIN_SESSION_ID, originRequest.getSession().getId());

			break;

		case 2: // receive 200 OK
		case 3: // send ack

			if (response != null && response.getMethod().equals("INVITE")) {
				String require = response.getHeader("Require");

				if (status < 200 && require != null && require.equals("100rel")) {
					SipServletRequest prack = response.createPrack();
					prack.setContent(blackhole.getBytes(), "application/sdp");
					prack.send();
					this.printOutboundMessage(prack);

				} else if (status == 200) {

					// callId = response.getCallId();
					// toTag = response.getTo().getParameter("tag");
					// fromTag = response.getFrom().getParameter("tag");

					discoverOptions(response);

					originResponse = response;

					SipServletRequest originAck = response.createAck();
					originAck.send();
					this.printOutboundMessage(originAck);

					// set timer
					state = 4;
					ServletTimer t = TalkBACSipServlet.timer.createTimer(appSession, 250, false, this);
					this.printTimer(t);

					msg = new TalkBACMessage(appSession, "source_connected");
					msg.setParameter("origin", origin.getURI().toString());
					msg.setParameter("destination", destination.getURI().toString());
					msg.setStatus(183, "Session Progress");
					this.printOutboundMessage(msgUtility.send(msg));

				} else if (status >= 400) {
					msg = new TalkBACMessage(appSession, "call_failed");
					msg.setParameter("origin", origin.getURI().toString());
					msg.setParameter("destination", destination.getURI().toString());
					msg.setStatus(response.getStatus(), response.getReasonPhrase());
					this.printOutboundMessage(msgUtility.send(msg));
				}

			}
			break;

		case 4: // receive timeout
		case 5: // send REFER
			SipServletRequest refer = originRequest.getSession().createRequest("REFER");

			String key = (String) appSession.getAttribute(KEY);
			Address refer_to = TalkBACSipServlet.factory.createAddress("<sip:" + key + "@" + TalkBACSipServlet.listenAddress + ">");

			refer.setAddressHeader("Refer-To", refer_to);
			refer.setAddressHeader("Referred-By", destination);
			refer.send();
			this.printOutboundMessage(refer);

			state = 6;
			refer.getSession().setAttribute(CALL_STATE_HANDLER, this);
			break;

		case 6: // receive 202 Accepted
		case 7: // receive NOTIFY
		case 8: // send 200 OK
		case 9: // send BYE
		case 10: // receive BYE 200 OK

			if (request != null && (request.getMethod().equals("NOTIFY"))) {
				SipServletResponse rsp = request.createResponse(200);
				rsp.send();
				this.printOutboundMessage(rsp);

				if (request.getContent() != null) {
					String sipfrag = new String((byte[]) request.getContent());
					if (sipfrag.contains("100")) {
						SipServletRequest bye = request.getSession().createRequest("BYE");
						bye.send();
						this.printOutboundMessage(bye);
					}
				}
			} else if (response != null && response.getStatus() == 202) {
				// Prepare for that INVITE
				CallStateHandler csh = new CallFlow6(this);
				csh.state = 11;
				appSession.setAttribute(CALL_STATE_HANDLER, csh);
				appSession.setAttribute(TalkBACSipServlet.MESSAGE_UTILITY, msgUtility);
			}

			break;

		case 11: // receive INVITE
		case 12: // send INVITE
			// Save this info in case we need to terminate.
			request.getSession().setAttribute(REQUEST_DIRECTION, "INBOUND");
			request.getSession().setAttribute(INITIAL_INVITE_REQUEST, request);

			appSession.removeAttribute(CALL_STATE_HANDLER);

			if (false == request.getCallId().equals(originResponse.getCallId())) {
				appSession.setAttribute("IGNORE_BYE", originResponse.getCallId());
			}

			originRequest = request;

			// originRequest.getSession().setInvalidateWhenReady(false);
			// destinationRequest.getSession().setInvalidateWhenReady(false);

			appSession.setAttribute(ORIGIN_SESSION_ID, request.getSession().getId());
			request.getSession().setAttribute(PEER_SESSION_ID, destinationRequest.getSession().getId());
			destinationRequest.getSession().setAttribute(PEER_SESSION_ID, request.getSession().getId());

			copyHeadersAndContent(request, destinationRequest);
			destinationRequest.setHeader("Allow", DESTINATION_ALLOW);
			destinationRequest.setHeader("Call-Info", TalkBACSipServlet.callInfo);

			destinationRequest.send();
			printOutboundMessage(destinationRequest);

			state = 13;
			destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			break;

		case 13: // receive 180 / 183 / 200
		case 14: // send 180 / 183 / 200

			if (request != null && request.getMethod().equals("PRACK")) {
				originPrack = request;

				// SipServletRequest prack = destinationRequest.getSession().createRequest("PRACK");
				SipServletRequest prack = destinationResponse.createPrack();
				copyHeadersAndContent(request, prack);
				prack.send();
				this.printOutboundMessage(prack);
				prack.getSession().setAttribute(CALL_STATE_HANDLER, this);
				return;
			}

			if (response != null && response.getMethod().equals("PRACK")) {
				SipServletResponse prackResponse = originPrack.createResponse(response.getStatus());
				prackResponse.send();
				this.printOutboundMessage(prackResponse);
				return;
			}

			if (response != null) {
				destinationResponse = response;

				if (status < 400) {
					originResponse = originRequest.createResponse(response.getStatus());
					copyHeadersAndContent(response, originResponse);					
					originResponse.setHeader("Allow", ORIGIN_ALLOW);
					

					if (status < 200) {
						if (response.getHeader("Require") != null && response.getHeader("Require").equals("100rel")) {
							originResponse.sendReliably();
						} else {
							originResponse.send();
						}
						originResponse.getSession().setAttribute(CALL_STATE_HANDLER, this);
					} else {
						originResponse.send();
						state = 15;
						originResponse.getSession().setAttribute(CALL_STATE_HANDLER, this);
					}
					this.printOutboundMessage(originResponse);

					if (status == 200) {

						msg = new TalkBACMessage(appSession, "destination_connected");
						msg.setParameter("origin", origin.getURI().toString());
						msg.setParameter("destination", destination.getURI().toString());
						msg.setStatus(response.getStatus(), response.getReasonPhrase());
						this.printOutboundMessage(msgUtility.send(msg));
					}

				} else {

					response.getSession().removeAttribute(CALL_STATE_HANDLER);
					originResponse.getSession().removeAttribute(CALL_STATE_HANDLER);

					msg = new TalkBACMessage(appSession, "call_failed");
					msg.setParameter("origin", origin.getURI().toString());
					msg.setParameter("destination", destination.getURI().toString());
					msg.setStatus(response.getStatus(), response.getReasonPhrase());
					this.printOutboundMessage(msgUtility.send(msg));

					TerminateCall terminate = new TerminateCall();
					terminate.processEvent(appSession, msgUtility, request, response, timer);
				}

			}

			break;

		case 15: // receive ACK
		case 16: // send ACK
			if (request != null && request.getMethod().equals("ACK")) {
				SipServletRequest destAck = destinationResponse.createAck();
				copyHeadersAndContent(request, destAck);
				destAck.setHeader("Call-Info", TalkBACSipServlet.callInfo);
				destAck.setHeader("Allow", DESTINATION_ALLOW);				
				destAck.send();
				this.printOutboundMessage(destAck);

//				// Launch KPML Subscribe
//				if (this.kpml_supported) {
//					KpmlRelay kpmlRelay = new KpmlRelay(3600);
//					kpmlRelay.delayedSubscribe(appSession, 2000);
//				}

				// Launch Keep Alive Timer
				if (this.update_supported) {
					UpdateKeepAlive ka = new UpdateKeepAlive(60 * 1000);
					ka.startTimer(appSession);
				}

				msg = new TalkBACMessage(appSession, "call_connected");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				this.printOutboundMessage(msgUtility.send(msg));

				destAck.getSession().removeAttribute(CALL_STATE_HANDLER);
				request.getSession().removeAttribute(CALL_STATE_HANDLER);

			}

			break;

		}

	}
}
