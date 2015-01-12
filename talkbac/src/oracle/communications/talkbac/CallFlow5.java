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
 *             | NOTIFY               |                      |
 *             |--------------------->|                      |
 *             | 200 OK               |                      |
 *             |<---------------------|                      |
 *             |(7) INVITE w/SDP      |                      |
 *             |--------------------->|                      |
 *             |                      |(8) INVITE w/o SDP    |
 *             |                      |--------------------->|
 *             |                      |(9a) 180 Ringing      |
 *             |                      |<---------------------|
 *             |(10a) 180 Ringing     |                      |
 *             |<---------------------|                      |
 *             |                      |(9b) 200 OK           |
 *             |                      |<---------------------|
 *             |(10b) 200 OK          |                      |
 *             |<---------------------|                      |
 *             |(11) ACK              |                      |
 *             |--------------------->|                      |
 *             |                      |(12) ACK              |
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
import javax.servlet.sip.SipURI;

public class CallFlow5 extends CallFlowHandler {
	private static final long serialVersionUID = 1L;
	private Address origin;
	private Address destination;
	private String destinationUser;
	// private String originUser;

	SipServletRequest destinationRequest;
	SipServletResponse destinationResponse;

	SipServletRequest originRequest;
	SipServletResponse originResponse;

	CallFlow5(Address origin, Address destination) {
		this.origin = origin;
		this.destination = destination;
	}

	CallFlow5(CallFlow5 that) {
		this.origin = that.origin;
		this.destination = that.destination;
		this.destinationRequest = that.destinationRequest;
		this.destinationResponse = that.destinationResponse;
		this.originRequest = that.originRequest;
		this.originResponse = that.originResponse;

	}

	@Override
	public void processEvent(SipApplicationSession appSession, TalkBACMessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		TalkBACMessage msg;

		// I don't want to deal with this in my state machine
		if (request != null && (request.getMethod().equals("NOTIFY") || request.getMethod().equals("UPDATE"))) {
			SipServletResponse rsp = request.createResponse(200);
			rsp.send();
			this.printOutboundMessage(rsp);
		}

		switch (state) {
		case 1: // send INVITE

			// Save this for REFER
			// String key = ((SipURI) origin.getURI()).getUser().toLowerCase();
			// SipApplicationSession tmpAppSession = TalkBACSipServlet.util.getApplicationSessionByKey(key, true);
			// tmpAppSession.setAttribute(TalkBACSipServlet.DESTINATION_ADDRESS, destination);

			appSession.setAttribute(TalkBACSipServlet.ORIGIN_ADDRESS, origin);
			appSession.setAttribute(TalkBACSipServlet.DESTINATION_ADDRESS, destination);

			msg = new TalkBACMessage(appSession, "call_created");
			msg.setParameter("origin", origin.getURI().toString());
			msg.setParameter("destination", destination.getURI().toString());
			msgUtility.send(msg);

			this.destinationUser = ((SipURI) destination.getURI()).getUser().toLowerCase();
			// this.originUser = ((SipURI) origin.getURI()).getUser().toLowerCase();
			// SipApplicationSession originAppSession = TalkBACSipServlet.util.getApplicationSessionByKey(originUser,
			// true);
			// originAppSession.setAttribute("DESTINATION", destination);

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
			originRequest.setContent(blackhole, "application/sdp");
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

			Address refer_to = TalkBACSipServlet.factory.createAddress("<sip:" + destinationUser + "@" + TalkBACSipServlet.listenAddress+ ">");

			// appSession.encodeURI(refer_to.getURI());

			refer.setAddressHeader("Refer-To", refer_to);
			// refer.setAddressHeader("Referred-By", TalkBACSipServlet.talkBACAddress);
			// refer.setAddressHeader("Referred-By", refer_to);
			refer.setAddressHeader("Referred-By", destination);
			refer.send();
			this.printOutboundMessage(refer);

			state = 6;
			refer.getSession().setAttribute(CALL_STATE_HANDLER, this);

			// Prepare for that INVITE
			CallStateHandler csh = new CallFlow5(this);
			csh.state = 7;
			appSession.setAttribute(CALL_STATE_HANDLER, csh);
			appSession.setAttribute(TalkBACSipServlet.MESSAGE_UTILITY, msgUtility);
			break;

		case 6: // receive 202 Accepted
		case 7: // receive INVITE
		case 8: // send INVITE

			if (response != null && response.getMethod().equals("REFER")) {
				// do nothing;
			} else if (request != null && request.getMethod().equals("INVITE")) {

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

				state = 9;
				destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}

			break;

		case 9: // receive 180 / 183 / 200
		case 10: // send 180 / 183 / 200

			if (response != null) {

				if (status < 300) {
					originResponse = originRequest.createResponse(response.getStatus());
					originResponse.setContent(response.getContent(), response.getContentType());
					originResponse.send();
					this.printOutboundMessage(originResponse);

					if (status == 200) {
						destinationResponse = response;

						state = 11;
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

		case 11: // receive ACK
		case 12: // send ACK
			if (request != null && request.getMethod().equals("ACK")) {
				SipServletRequest destAck = destinationResponse.createAck();
				destAck.setContent(request.getContent(), request.getContentType());
				destAck.send();
				this.printOutboundMessage(destAck);

				// Launch KPML Subscribe (wait for SDP to be negotiated)

				if (this.kpml_supported) {
					KpmlRelay kpmlRelay = new KpmlRelay(3600);
					kpmlRelay.delayedSubscribe(appSession, 2);
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

	static final String blackhole = ""
			+ "v=0\r\n"
			+ "o=- 15474517 1 IN IP4 127.0.0.1\r\n"
			+ "s=cpc_med\r\n"
			+ "c=IN IP4 0.0.0.0\r\n"
			+ "t=0 0\r\n"
			+ "m=audio 23348 RTP/AVP 0\r\n"
			+ "a=rtpmap:0 pcmu/8000\r\n"
			+ "a=rtpmap:101 telephone-event/8000\r\n";
	// + "a=inactive \r\n";

}
