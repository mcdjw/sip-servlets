package oracle.communications.talkbac;

import javax.servlet.sip.Address;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class CallFlow5 extends CallStateHandler {
	Address origin;
	Address destination;

	SipServletRequest destinationRequest;
	SipServletResponse destinationResponse;

	SipServletRequest originRequest;
	SipServletResponse originResponse;

	CallFlow5(Address origin, Address destination) {
		this.origin = origin;
		this.destination = destination;
	}

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		SipApplicationSession appSession;
		TalkBACMessage msg;

		switch (state) {
		case 1: {
			appSession = request.getApplicationSession();

			msg = new TalkBACMessage(appSession, "call_created");
			msg.send();

			destinationRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", origin, destination);
			if (TalkBACSipServlet.callInfo != null) {
				destinationRequest.setHeader("Call-Info", TalkBACSipServlet.callInfo);
			}

			originRequest = TalkBACSipServlet.factory.createRequest(appSession, "INVITE", destination, origin);

			if (TalkBACSipServlet.outboundProxy != null) {
				destinationRequest.pushRoute(TalkBACSipServlet.outboundProxy);
				originRequest.pushRoute(TalkBACSipServlet.outboundProxy);
			}

			destinationRequest.getSession().setAttribute(PEER_SESSION_ID, originRequest.getSession().getId());
			originRequest.getSession().setAttribute(PEER_SESSION_ID, destinationRequest.getSession().getId());

			originRequest.setContent(blackhole, "application/sdp");
			originRequest.send();

			state = 2;
			originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			appSession.setAttribute(DESTINATION_SESSION_ID, destinationRequest.getSession().getId());
			appSession.setAttribute(ORIGIN_SESSION_ID, originRequest.getSession().getId());
			// appSession.setAttribute(INITIATOR_SESSION_ID,
			// initiator.getSession().getId());

		}
			break;

		case 2:
		case 3:
		case 4: {
			if (status == 200) {
				SipServletRequest originAck = response.createAck();
				originAck.send();

				
				SipServletRequest refer = response.getSession().createRequest("REFER");				
				refer.setHeader("Refer-To", destination.toString());
				refer.send();
				

				state = 5;
				originResponse = response;
				refer.getSession().setAttribute(CALL_STATE_HANDLER, this);

				// initiator.createResponse(183).send();
				msg = new TalkBACMessage(response.getApplicationSession(), "source_connected");
				msg.setStatus(183, "Session Progress");
				msg.send();
			}

			if (status >= 300) {
				msg = new TalkBACMessage(response.getApplicationSession(), "call_failed");
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msg.send();
			}

		}
			break;

		case 5:
		case 6:

//			if (status >= 200 && status < 300) {
//				destinationResponse = response;
//
//				originRequest = originRequest.getSession().createRequest("INVITE");
//				originRequest.setContent(destinationResponse.getContent(), destinationResponse.getContentType());
//				originRequest.send();
//
//				state = 7;
//				originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
//
//				msg = new TalkBACMessage(response.getApplicationSession(), "destination_connected");
//				msg.setStatus(response.getStatus(), response.getReasonPhrase());
//				msg.send();
//			}
//
//			if (status >= 300) {
//				originResponse.getSession().createRequest("BYE").send();
//
//				response.getSession().removeAttribute(CALL_STATE_HANDLER);
//				originResponse.getSession().removeAttribute(CALL_STATE_HANDLER);
//
//				msg = new TalkBACMessage(response.getApplicationSession(), "call_failed");
//				msg.setStatus(response.getStatus(), response.getReasonPhrase());
//				msg.send();
//
//			}

			break;

		case 7:
		case 8:
		case 9: {
			if (status == 200) {
				originResponse = response;
				SipServletRequest destinationAck = destinationResponse.createAck();
				destinationAck.setContent(originResponse.getContent(), originResponse.getContentType());
				destinationAck.send();

				SipServletRequest originAck = originResponse.createAck();
				originAck.send();

				destinationAck.getSession().removeAttribute(CALL_STATE_HANDLER);
				originAck.getSession().removeAttribute(CALL_STATE_HANDLER);

				msg = new TalkBACMessage(response.getApplicationSession(), "call_connected");
				msg.send();
			}

			if (status >= 300) {
				originResponse.getSession().createRequest("BYE").send();

				response.getSession().removeAttribute(CALL_STATE_HANDLER);
				originResponse.getSession().removeAttribute(CALL_STATE_HANDLER);

				msg = new TalkBACMessage(response.getApplicationSession(), "call_failed");
				msg.setStatus(response.getStatus(), response.getReasonPhrase());
				msg.send();
			}

			// initiator.createResponse(response.getStatus(),
			// response.getReasonPhrase()).send();

		}
			break;

		// case 10: //misc. cleanup
		// response.getSession().removeAttribute(CALL_STATE_HANDLER);
		// break;

		}

	}

	// media line has a range of zero ports "4002/0"
	static final String blackhole = ""
			+ "v=0\n"
			+ "o=- 3614531588 3614531588 IN IP4 192.168.1.202\n"
			+ "s=cpc_med\n"
			+ "c=IN IP4 192.168.1.202\n"
			+ "t=0 0\n"
			+ "m=audio 4002/0 RTP/AVP 111 110 109 9 0 8 101"
			+ "a=sendrecv\n"
			+ "a=rtpmap:111 OPUS/48000\n"
			+ "a=fmtp:111 maxplaybackrate=32000;useinbandfec=1\n"
			+ "a=rtpmap:110 SILK/24000\n"
			+ "a=fmtp:110 useinbandfec=1\n"
			+ "a=rtpmap:109 SILK/16000\n"
			+ "a=fmtp:109 useinbandfec=1\n"
			+ "a=rtpmap:9 G722/8000\n"
			+ "a=rtpmap:0 PCMU/8000\n"
			+ "a=rtpmap:8 PCMA/8000\n"
			+ "a=rtpmap:101 telephone-event/8000\n"
			+ "a=fmtp:101 0-16\n";

}
