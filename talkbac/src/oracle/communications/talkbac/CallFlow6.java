/*
 * Ringback Tone during Blind Transfer
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
 *             |(7) BYE               |                      |
 *             |<---------------------|                      |
 *             |(8) 200 OK            |                      |
 *             |--------------------->|                      |
 *             |(9) INVITE w/SDP      |                      |
 *             |--------------------->|                      |
 *             |                      |(10) INVITE           |
 *             |                      |--------------------->|
 *             |                      |(11) 180 / 200 OK     |
 *             |                      |<---------------------|
 *             |(12) 180 / 200        |                      |
 *             |<---------------------|                      |
 *             |(13) ACK              |                      |
 *             |--------------------->|                      |
 *             |                      |(14) ACK              |
 *             |                      |--------------------->|
 *             |.............................................|
 *
 */

package oracle.communications.talkbac;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;

public class CallFlow6 extends CallStateHandler {
	private Address origin;
	private Address destination;
	private String requestId;

	SipServletRequest destinationRequest;
	SipServletResponse destinationResponse;

	SipServletRequest originRequest;
	SipServletResponse originResponse;

	SipServletRequest originInviteRequest;
	SipServletResponse originInviteResponse;

	CallFlow6(CallFlow6 that) {
		this.origin = that.origin;
		this.destination = that.destination;
		this.requestId = that.requestId;
		this.destinationRequest = that.destinationRequest;
		this.destinationResponse = that.destinationResponse;
		this.originRequest = that.originRequest;
		this.originResponse = that.originResponse;
		this.originInviteRequest = that.originInviteRequest;
		this.originInviteResponse = that.originInviteResponse;
	}

	CallFlow6(String requestId, Address origin, Address destination) {

		this.requestId = requestId;
		this.origin = origin;
		this.destination = destination;
	}

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		SipApplicationSession appSession = null;
		if (request != null) {
			appSession = request.getApplicationSession();
		} else if (response != null) {
			appSession = response.getApplicationSession();
		} else if (timer != null) {
			appSession = timer.getApplicationSession();
		}

		TalkBACMessage msg;

		if (request != null) {
			if (request.getMethod().equals("NOTIFY")) {
				// I don't want to deal with this in my state machine
				SipServletResponse rsp = request.createResponse(200);
				rsp.send();
				this.printOutboundMessage(rsp);
			}
		}

		switch (state) {
		case 1: // send INVITE

			msg = new TalkBACMessage(appSession, "call_created");
			msg.send();

			appSession.setInvalidateWhenReady(false);

			originRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", destination, origin);
			destinationRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", origin, destination);
			if (TalkBACSipServlet.callInfo != null) {
				destinationRequest.setHeader("Call-Info", TalkBACSipServlet.callInfo);
				destinationRequest.setHeader("Session-Expires", "3600;refresher=uac");
				destinationRequest.setHeader("Allow", "INVITE, BYE, OPTIONS, CANCEL, ACK, REGISTER, NOTIFY, REFER, SUBSCRIBE, PRACK, MESSAGE, PUBLISH");
			}

			destinationRequest.getSession().setAttribute(PEER_SESSION_ID, originRequest.getSession().getId());
			originRequest.getSession().setAttribute(PEER_SESSION_ID, destinationRequest.getSession().getId());

			originRequest.setContent(blackhole3, "application/sdp");
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
				SipServletRequest originAck = response.createAck();
				originAck.send();
				this.printOutboundMessage(originAck);

				// set timer
				state = 4;
				ServletTimer t = TalkBACSipServlet.timer.createTimer(appSession, 250, false, this);

				msg = new TalkBACMessage(response.getApplicationSession(), "source_connected");
				msg.setStatus(183, "Session Progress");
				msg.send();

			}

			if (status >= 300) {
				msg = new TalkBACMessage(response.getApplicationSession(), "call_failed");
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msg.send();
			}
			break;

		case 4: // receive timeout
		case 5: // send REFER
			SipServletRequest refer = originRequest.getSession().createRequest("REFER");

			Address refer_to;
			Address referred_by;
			referred_by = TalkBACSipServlet.factory.createAddress("<sip:" + TalkBACSipServlet.servletName + "@" + TalkBACSipServlet.listenAddress + ">");
			if (TalkBACSipServlet.appName != null) {
				refer_to = TalkBACSipServlet.factory.createAddress("<sip:" + TalkBACSipServlet.appName + "?Replaces=" + requestId + ">");
			} else {
//				refer_to = TalkBACSipServlet.factory.createAddress("<sip:" + TalkBACSipServlet.servletName + "@" + TalkBACSipServlet.listenAddress
//						+ "?Request-ID=" + requestId + ">");

				refer_to = TalkBACSipServlet.factory.createAddress("<sip:" + TalkBACSipServlet.servletName + "@" + TalkBACSipServlet.listenAddress + ">");
			}

			refer.setAddressHeader("Refer-To", refer_to);
			refer.setAddressHeader("Referred-By", referred_by);
			refer.send();
			this.printOutboundMessage(refer);

			originResponse = response;
			state = 6;
			refer.getSession().setAttribute(CALL_STATE_HANDLER, this);

			// Prepare for the INVITE
			CallFlow6 cf6 = new CallFlow6(this);
			cf6.state = 9;
			appSession.setAttribute(CALL_STATE_HANDLER, cf6);

			break;

		case 6: // receive 202 Accepted
		case 7: // send BYE
			if (response.getStatus() == 202) {
				appSession.setInvalidateWhenReady(true);

				SipServletRequest bye6 = response.getSession().createRequest("BYE");
				bye6.send();
				this.printOutboundMessage(bye6);
				state = 8;
				bye6.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}

			break;

		case 8: // receive 200 OK (BYE)
			// response.getSession().removeAttribute(CALL_STATE_HANDLER);
			break;

		case 9: // receive INVITE
		case 10: // send INVITE

			if (request != null && request.getMethod().equals("INVITE")) {
				appSession.removeAttribute(CALL_STATE_HANDLER);

				originInviteRequest = request;
				destinationRequest.setContent(request.getContent(), request.getContentType());
				destinationRequest.send();
				printOutboundMessage(destinationRequest);

				state = 11;
				destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}

			break;

		case 11: // receive 180 / 183 / 200
		case 12: // send 180 / 183 / 200

			if (response != null) {
				originInviteResponse = originInviteRequest.createResponse(response.getStatus());
				originInviteResponse.setContent(response.getContent(), response.getContentType());
				originInviteResponse.send();
				this.printOutboundMessage(originInviteResponse);

				if (status == 200) {
					destinationResponse = response;

					state = 13;
					originInviteResponse.getSession().setAttribute(CALL_STATE_HANDLER, this);

					msg = new TalkBACMessage(response.getApplicationSession(), "destination_connected");
					msg.setStatus(response.getStatus(), response.getReasonPhrase());
					msg.send();
				}

				if (status > 300) {

					SipServletRequest bye = originRequest.getSession().createRequest("BYE");
					originRequest.send();
					this.printOutboundMessage(bye);

					response.getSession().removeAttribute(CALL_STATE_HANDLER);
					originResponse.getSession().removeAttribute(CALL_STATE_HANDLER);

					msg = new TalkBACMessage(response.getApplicationSession(), "call_failed");
					msg.setStatus(response.getStatus(), response.getReasonPhrase());
					msg.send();
				}

			}

			break;

		case 13: // Receive ACK
		case 14: // Send ACK

			if (request != null && request.getMethod().equals("ACK")) {
				destinationRequest = destinationResponse.createAck();
				destinationRequest.setContent(request.getContent(), request.getContentType());
				destinationRequest.send();
				this.printOutboundMessage(destinationRequest);

				msg = new TalkBACMessage(request.getApplicationSession(), "call_connected");
				msg.send();

				destinationRequest.getSession().removeAttribute(CALL_STATE_HANDLER);

				// Launch Keep Alive Timer
				KeepAlive ka = new KeepAlive(originRequest.getSession(), destinationRequest.getSession());
				ka.processEvent(request, response, timer);
			}

			break;

		}

	}

	// media line has a range of zero ports "4002/0"
	static final String blackhole = "" + "v=0\n" + "o=- 3614531588 3614531588 IN IP4 192.168.1.202\n" + "s=cpc_med\n" + "c=IN IP4 192.168.1.202\n" + "t=0 0\n"
			+ "m=audio 4002/0 RTP/AVP 111 110 109 9 0 8 101" + "a=sendrecv\n" + "a=rtpmap:111 OPUS/48000\n"
			+ "a=fmtp:111 maxplaybackrate=32000;useinbandfec=1\n" + "a=rtpmap:110 SILK/24000\n" + "a=fmtp:110 useinbandfec=1\n" + "a=rtpmap:109 SILK/16000\n"
			+ "a=fmtp:109 useinbandfec=1\n" + "a=rtpmap:9 G722/8000\n" + "a=rtpmap:0 PCMU/8000\n" + "a=rtpmap:8 PCMA/8000\n"
			+ "a=rtpmap:101 telephone-event/8000\n" + "a=fmtp:101 0-16\n";

	static final String blackhole3 = "" + "v=0\n" + "o=- 15474517 1 IN IP4 127.0.0.1\n" + "s=cpc_med\n" + "c=IN IP4 0.0.0.0\n" + "t=0 0\n"
			+ "m=audio 23348 RTP/AVP 0\n" + "a=rtpmap:0 pcmu/8000\n" + "a=sendrecv \n";

}