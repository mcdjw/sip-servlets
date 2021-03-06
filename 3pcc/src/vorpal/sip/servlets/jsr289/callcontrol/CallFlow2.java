/*
 * 
4.2.  Flow II

             A              Controller               B
             |(1) INVITE bh sdp1 |                   |
             |<------------------|                   |
             |(2) 200 sdp2       |                   |
             |------------------>|                   |
             |                   |(3) INVITE sdp2    |
             |                   |------------------>|
             |(4) ACK            |                   |
             |<------------------|                   |
             |                   |(5) 200 OK sdp3    |
             |                   |<------------------|
             |                   |(6) ACK            |
             |                   |------------------>|
             |(7) INVITE sdp3    |                   |
             |<------------------|                   |
             |(8) 200 OK sdp2    |                   |
             |------------------>|                   |
             |(9) ACK            |                   |
             |<------------------|                   |
             |(10) RTP           |                   |
             |.......................................|

 */

package vorpal.sip.servlets.jsr289.callcontrol;

import javax.servlet.sip.Address;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class CallFlow2 extends CallStateHandler {

	Address origin;
	Address destination;

	SipServletRequest destinationRequest;
	SipServletResponse destinationResponse;

	SipServletRequest originRequest;
	SipServletResponse originResponse;

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;
		SipApplicationSession appSession;

		switch (state) {
		case 1: {// Send INVITE (black hole)

			this.origin = request.getFrom();
			this.destination = request.getTo();
			this.initiator = request;

			appSession = request.getApplicationSession();

			destinationRequest = ThirdPartyCallControlServlet.factory.createRequest(appSession, "INVITE", origin, destination);
			if (ThirdPartyCallControlServlet.callInfo != null) {
				destinationRequest.setHeader("Call-Info", ThirdPartyCallControlServlet.callInfo);
			}

			originRequest = ThirdPartyCallControlServlet.factory.createRequest(appSession, "INVITE", destination, origin);

			if (ThirdPartyCallControlServlet.outboundProxy != null) {
				destinationRequest.pushRoute(ThirdPartyCallControlServlet.outboundProxy);
				originRequest.pushRoute(ThirdPartyCallControlServlet.outboundProxy);
			}

			destinationRequest.getSession().setAttribute(PEER_SESSION_ID, originRequest.getSession().getId());
			originRequest.getSession().setAttribute(PEER_SESSION_ID, destinationRequest.getSession().getId());

			originRequest.setContent(blackhole2, "application/sdp");
			originRequest.send();

			state = 2;
			originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			appSession.setAttribute(DESTINATION_SESSION_ID, destinationRequest.getSession().getId());
			appSession.setAttribute(ORIGIN_SESSION_ID, originRequest.getSession().getId());
			appSession.setAttribute(INITIATOR_SESSION_ID, initiator.getSession().getId());

		}
			break;

		case 2:
		case 3:
		case 4: {
			// receive 200 OK
			// send invite
			// send ack
			if (status == 200) {
				originResponse = response;

				destinationRequest.setContent(originResponse.getContent(), originResponse.getContentType());
				destinationRequest.send();

				SipServletRequest originAck = originResponse.createAck();
				originAck.send();

				state = 5;
				destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

				initiator.createResponse(183).send();
			} else {
				initiator.createResponse(response.getStatus(), response.getReasonPhrase()).send();
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
				destinationResponse.createAck().send();

				originRequest = originRequest.getSession().createRequest("INVITE");
				originRequest.setContent(destinationResponse.getContent(), destinationResponse.getContentType());
				originRequest.send();

				state = 8;
				destinationResponse = response;
				originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}
			if (status >= 300) {
				originResponse.getSession().createRequest("BYE").send();

				response.getSession().removeAttribute(CALL_STATE_HANDLER);
				originResponse.getSession().removeAttribute(CALL_STATE_HANDLER);
			}

			if (status != 200) {
				initiator.createResponse(response.getStatus(), response.getReasonPhrase()).send();
			}
		}
			break;

		case 8:
		case 9:
			// receive 200 ok
			// send ack
			SipServletResponse initResponse = initiator.createResponse(response.getStatus(), response.getReasonPhrase());

			if (status == 200) {

				response.createAck().send();

				destinationRequest.getSession().removeAttribute(CALL_STATE_HANDLER);
				originRequest.getSession().removeAttribute(CALL_STATE_HANDLER);
			}

			if (status >= 300) {
				originResponse.getSession().createRequest("BYE").send();

				response.getSession().removeAttribute(CALL_STATE_HANDLER);
				originResponse.getSession().removeAttribute(CALL_STATE_HANDLER);

				state = 10;
				initResponse.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}

			initResponse.send();
			break;
		case 10: // ACK from initiator
			request.getSession().removeAttribute(CALL_STATE_HANDLER);
			break;

		}

	}

	static final String blackhole = ""
			+ "v=0\n"
			+ "o=- 15474517 1 IN IP4 172.16.45.94\n"
			+ "s=cpc_med\n"
			+ "c=IN IP4 0.0.0.0\n"
			+ "t=0 0\n"
			+ "m=audio 23348 RTP/AVP 0\n"
			+ "a=rtpmap:0 pcmu/8000\n"
			+ "a=sendrecv\n ";

	static final String blackhole2 = ""
			+ "v=0\n"
			+ "o=- 3615877054 3615877054 IN IP4 192.168.1.80\n"
			+ "s=cpc_med\n"
			+ "c=IN IP4 0.0.0.0\n"
			+ "t=0 0\n"
			+ "m=audio 4004 RTP/AVP 111 110 109 9 0 8 101\n"
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
