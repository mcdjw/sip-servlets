/*
 * http://tools.ietf.org/html/rfc3725
 * 4.4.  Flow IV
 *
 *             A                 Controller                  B
 *             |(1) INVITE offer1     |                      |
 *             |no media              |                      |
 *             |<---------------------|                      |
 *             |(2) 200 answer1       |                      |
 *             |no media              |                      |
 *             |--------------------->|                      |
 *             |(3) ACK               |                      |
 *             |<---------------------|                      |
 *             |                      |(4) INVITE no SDP     |
 *             |                      |--------------------->|
 *             |                      |(5) 200 OK offer2     |
 *             |                      |<---------------------|
 *             |(6) INVITE offer2'    |                      |
 *             |<---------------------|                      |
 *             |(7) 200 answer2'      |                      |
 *             |--------------------->|                      |
 *             |                      |(8) ACK answer2       |
 *             |                      |--------------------->|
 *             |(9) ACK               |                      |
 *             |<---------------------|                      |
 *             |(10) RTP              |                      |
 *             |.............................................|
 *
 */

package vorpal.sip.servlets.jsr289.callcontrol;

import javax.servlet.sip.Address;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class CallFlow4 extends CallStateHandler {
	Address origin;
	Address destination;

	SipServletRequest destinationRequest;
	SipServletResponse destinationResponse;

	SipServletRequest originRequest;
	SipServletResponse originResponse;

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		switch (state) {
		case 1: {

			this.origin = request.getFrom();
			this.destination = request.getTo();
			this.initiator = request;

			SipApplicationSession appSession = ThirdPartyCallControlServlet.factory.createApplicationSession();
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

			originRequest.setContent(blackhole, "application/sdp");
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
			if (status == 200) {
				SipServletRequest originAck = response.createAck();
				originAck.send();

				destinationRequest.send();

				state = 5;
				originResponse = response;
				destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

				initiator.createResponse(183).send();
			} else {
				initiator.createResponse(response.getStatus(), response.getReasonPhrase()).send();
			}
		}
			break;

		case 5:
		case 6:
			SipServletResponse initResponse = initiator.createResponse(response.getStatus(), response.getReasonPhrase());

			if (status >= 200 && status < 300) {
				destinationResponse = response;

				originRequest = originRequest.getSession().createRequest("INVITE");
				originRequest.setContent(destinationResponse.getContent(), destinationResponse.getContentType());
				originRequest.send();

				state = 7;
				originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}
			
			
			if (status >= 300) {
				originResponse.getSession().createRequest("BYE").send();

				response.getSession().removeAttribute(CALL_STATE_HANDLER);
				originResponse.getSession().removeAttribute(CALL_STATE_HANDLER);

//				state = 7;
//				initResponse.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}

			initResponse.send();
			
	
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
			}
		}
			break;

		case 10: //misc. cleanup
			response.getSession().removeAttribute(CALL_STATE_HANDLER);
			break;
			
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
