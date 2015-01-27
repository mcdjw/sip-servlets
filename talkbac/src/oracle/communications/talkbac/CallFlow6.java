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

	String callId;
	String toTag;
	String fromTag;

	SipServletRequest destinationRequest;
	SipServletResponse destinationResponse;

	SipServletRequest originRequest;
	SipServletResponse originResponse;

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
		int status = (null != response) ? response.getStatus() : 0;

		TalkBACMessage msg;

		switch (state) {
		case 1: // send INVITE
			appSession.setAttribute(TalkBACSipServlet.ORIGIN_ADDRESS, origin);
			appSession.setAttribute(TalkBACSipServlet.DESTINATION_ADDRESS, destination);

			msg = new TalkBACMessage(appSession, "call_created");
			msg.setParameter("origin", origin.getURI().toString());
			msg.setParameter("destination", destination.getURI().toString());
			msgUtility.send(msg);

			originRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", destination, origin);
			destinationRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", origin, destination);

			// Save this info in case we need to terminate
			destinationRequest.getSession().setAttribute(REQUEST_DIRECTION, "OUTBOUND");
			destinationRequest.getSession().setAttribute(INITIAL_INVITE_REQUEST, destinationRequest);
			originRequest.getSession().setAttribute(REQUEST_DIRECTION, "OUTBOUND");
			originRequest.getSession().setAttribute(INITIAL_INVITE_REQUEST, originRequest);

			destinationRequest.getSession().setAttribute(PEER_SESSION_ID, originRequest.getSession().getId());
			originRequest.getSession().setAttribute(PEER_SESSION_ID, destinationRequest.getSession().getId());
			originRequest.setHeader("Allow-Events", "telephone-event");

			originRequest.getSession().setAttribute(INITIAL_INVITE_REQUEST, originRequest);
			originRequest.setHeader("Call-Info", TalkBACSipServlet.callInfo);
			originRequest.setHeader("Allow", "INVITE, OPTIONS, INFO, BYE, CANCEL, ACK, PRACK, UPDATE, REFER, SUBSCRIBE, NOTIFY");
			originRequest.setHeader("Allow-Events", "telephone-event");
			originRequest.setContent(blackhole.getBytes(), "application/sdp");
			originRequest.send();
			this.printOutboundMessage(originRequest);

			state = 2;
			originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			appSession.setAttribute(DESTINATION_SESSION_ID, destinationRequest.getSession().getId());
			appSession.setAttribute(ORIGIN_SESSION_ID, originRequest.getSession().getId());

			break;

		case 2: // receive 200 OK
		case 3: // send ack

			if (status >= 200 && status < 300) {

				callId = response.getCallId();
				toTag = response.getTo().getParameter("tag");
				fromTag = response.getFrom().getParameter("tag");

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
				msgUtility.send(msg);

			}

			if (status >= 300) {
				msg = new TalkBACMessage(appSession, "call_failed");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msgUtility.send(msg);
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

			appSession.setAttribute(ORIGIN_SESSION_ID, request.getSession().getId());
			request.getSession().setAttribute(PEER_SESSION_ID, destinationRequest.getSession().getId());
			destinationRequest.getSession().setAttribute(PEER_SESSION_ID, request.getSession().getId());

			copyHeaders(originRequest, destinationRequest);

			destinationRequest.setHeader("Allow", "INVITE, OPTIONS, INFO, BYE, CANCEL, ACK, REFER, SUBSCRIBE, NOTIFY");
			destinationRequest.setHeader("Call-Info", TalkBACSipServlet.callInfo);

			destinationRequest.setContent(request.getContent(), request.getContentType());
			destinationRequest.send();
			printOutboundMessage(destinationRequest);

			state = 13;
			destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			break;

		case 13: // receive 180 / 183 / 200
		case 14: // send 180 / 183 / 200

			if (response != null) {

				if (status < 300) {
					originResponse = originRequest.createResponse(response.getStatus());
					originResponse.setContent(response.getContent(), response.getContentType());
					originResponse.send();
					this.printOutboundMessage(originResponse);

					if (status == 200) {
						destinationResponse = response;

						state = 15;
						originResponse.getSession().setAttribute(CALL_STATE_HANDLER, this);

						msg = new TalkBACMessage(appSession, "destination_connected");
						msg.setParameter("origin", origin.getURI().toString());
						msg.setParameter("destination", destination.getURI().toString());
						msg.setStatus(response.getStatus(), response.getReasonPhrase());
						msgUtility.send(msg);
					}

				} else {

					response.getSession().removeAttribute(CALL_STATE_HANDLER);
					originResponse.getSession().removeAttribute(CALL_STATE_HANDLER);

					msg = new TalkBACMessage(appSession, "call_failed");
					msg.setParameter("origin", origin.getURI().toString());
					msg.setParameter("destination", destination.getURI().toString());
					msg.setStatus(response.getStatus(), response.getReasonPhrase());
					msgUtility.send(msg);

					TerminateCall terminate = new TerminateCall();
					terminate.processEvent(appSession, msgUtility, request, response, timer);
				}

			}

			break;

		case 15: // receive ACK
		case 16: // send ACK
			if (request != null && request.getMethod().equals("ACK")) {
				SipServletRequest destAck = destinationResponse.createAck();
				destAck.setContent(request.getContent(), request.getContentType());
				destAck.send();
				this.printOutboundMessage(destAck);

				// Launch KPML Subscribe
				if (this.kpml_supported) {
					KpmlRelay kpmlRelay = new KpmlRelay(3600);
					kpmlRelay.subscribe(appSession);
				}

				// Launch Keep Alive Timer
				if (this.update_supported) {
					UpdateKeepAlive ka = new UpdateKeepAlive(60 * 1000);
					ka.startTimer(appSession);
				}

				msg = new TalkBACMessage(appSession, "call_connected");
				msg.setParameter("origin", origin.getURI().toString());
				msg.setParameter("destination", destination.getURI().toString());
				msgUtility.send(msg);

				destAck.getSession().removeAttribute(CALL_STATE_HANDLER);
				request.getSession().removeAttribute(CALL_STATE_HANDLER);

			}

			break;

		}

	}

}
