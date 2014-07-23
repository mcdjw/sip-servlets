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

package vorpal.sip.servlets.jsr289.callcontrol;

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

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		switch (state) {
		case 1: {
			// Send to origin

			this.origin = request.getFrom();
			this.destination = request.getTo();

			SipApplicationSession appSession = ThirdPartyCallControlServlet.factory.createApplicationSession();
			destinationRequest = ThirdPartyCallControlServlet.factory.createRequest(appSession, "INVITE", origin, destination);
			originRequest = ThirdPartyCallControlServlet.factory.createRequest(appSession, "INVITE", destination, origin);

			destinationRequest.getSession().setAttribute(PEER_SESSION_ID, originRequest.getSession().getId());
			originRequest.getSession().setAttribute(PEER_SESSION_ID, destinationRequest.getSession().getId());

			originRequest.setContent(blackhole, "application/sdp");
			originRequest.send();

			state = 2;
			originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
		}
			break;

		case 2:
		case 3:
		case 4: {
			// Receive response from origin
			// Send ack to origin
			// Send invite to destination

			if (status == 200) {
				SipServletRequest originAck = response.createAck();
				// originAck.setContent(blackhole, "application/sdp");
				originAck.send();

				// destinationRequest.setContent(blackhole, "application/sdp");
				destinationRequest.send();

				state = 5;
				originResponse = response;
				destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}
		}
			break;

		case 5:
		case 6:
			// receive response from destination
			// send ack to destination
			// send invite to origin

			if (status == 200) {

				response.createAck().send();

				originRequest = originRequest.getSession().createRequest("INVITE");
				originRequest.setContent(response.getContent(), response.getContentType());
				originRequest.send();

				state = 7;
				destinationResponse = response;
				originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}

			break;

		case 7:
		case 8:
		case 9: {
			// receive response from origin
			// send ack to origin

			if (status == 200) { // response from origin

				// originResponse = response;
				response.createAck().send();

				// originResponse = response;
				// SipServletRequest destinationAck =
				// destinationResponse.createAck();
				// destinationAck.setContent(originResponse.getContent(),
				// originResponse.getContentType());
				// destinationAck.send();
				//
				// SipServletRequest originAck = originResponse.createAck();
				// originAck.send();

				destinationRequest.getSession().removeAttribute(CALL_STATE_HANDLER);
				originRequest.getSession().removeAttribute(CALL_STATE_HANDLER);
			}
		}
			break;

		}

	}

	static final String blackhole = ""

			+ "v=0\n"
			+ "o=- 3615040858 3615040858 IN IP4 192.168.1.8\n"
			+ "s=cpc_med\n"
			+ "c=IN IP4 192.168.1.8\n"
			+ "t=0 0\n"
			+ "m=audio 4068 RTP/AVP 111 110 109 9 0 8 101\n"
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


	// static final String blackhole = ""
	// + "v=0\n"
	// + "o=- 3614531588 3614531588 IN IP4 192.168.1.202\n"
	// + "s=cpc_med\n"
	// + "c=IN IP4 0.0.0.0\n"
	// + "t=0 0\n"
	// + "m=audio 4002 RTP/AVP 111 110 109 9 0 8 101";

}
