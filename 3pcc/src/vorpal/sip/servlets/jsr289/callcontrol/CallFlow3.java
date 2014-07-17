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

public class CallFlow3 extends CallStateHandler{

	Address origin;
	Address destination;

	SipServletRequest destinationRequest;
	SipServletResponse destinationResponse;

	SipServletRequest originRequest;
	SipServletResponse originResponse;

	public SipApplicationSession makeCall(Address origin, Address destination) throws Exception {
		this.origin = origin;
		this.destination = destination;

		SipApplicationSession appSession = ThirdPartyCallControlServlet.factory.createApplicationSession();
		destinationRequest = ThirdPartyCallControlServlet.factory.createRequest(appSession, "INVITE", origin, destination);
		originRequest = ThirdPartyCallControlServlet.factory.createRequest(appSession, "INVITE", destination, origin);
			
		destinationRequest.getSession().setAttribute(PEER_SESSION_ID, originRequest.getSession().getId());
		originRequest.getSession().setAttribute(PEER_SESSION_ID, destinationRequest.getSession().getId());

		
		state = 1;		
		processEvent(originRequest, null);	
		return appSession;
	}

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		switch (state) {
		case 1: {
			originRequest.send();

			state = 2;
			originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
		}
			break;

		case 2:
		case 3:
		case 4: {
			if (status == 200) {
				SipServletRequest originAck = response.createAck();
				originAck.setContent(blackhole, "application/sdp");
				originAck.send();

				destinationRequest.send();

				state = 5;
				originResponse = response;
				destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}
		}
			break;

		case 5:
		case 6: {
			if (status == 200) {



				originRequest = originRequest.getSession().createRequest("INVITE");
				originRequest.setContent(response.getContent(), response.getContentType());
				originRequest.send();

				state = 7;
				destinationResponse = response;
				originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
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

				SipServletRequest originAck = originResponse.createAck();
				originAck.send();

				destinationAck.getSession().removeAttribute(CALL_STATE_HANDLER);
				originAck.getSession().removeAttribute(CALL_STATE_HANDLER);
			}
		}
			break;

		}

	}

	
//	static final String blackhole = ""
//	+ "v=0\n"
//	+ "o=- 3614531588 3614531588 IN IP4 0.0.0.0\n"
//	+ "s=cpc_med\n"
//	+ "c=IN IP4 0.0.0.0\n"
//	+ "t=0 0\n"
//	+ "a=sendrecv\n"
//	+ "a=rtpmap:111 OPUS/48000\n"
//	+ "a=fmtp:111 maxplaybackrate=32000;useinbandfec=1\n"
//	+ "a=rtpmap:110 SILK/24000\n"
//	+ "a=fmtp:110 useinbandfec=1\n"
//	+ "a=rtpmap:109 SILK/16000\n"
//	+ "a=fmtp:109 useinbandfec=1\n"
//	+ "a=rtpmap:9 G722/8000\n"
//	+ "a=rtpmap:0 PCMU/8000\n"
//	+ "a=rtpmap:8 PCMA/8000\n"
//	+ "a=rtpmap:101 telephone-event/8000\n"
//	+ "a=fmtp:101 0-16\n";
	
	static final String blackhole = ""
	 +"v=0\n"
		+ "o=- 3614531588 3614531588 IN IP4 192.168.1.202\n"
		+ "s=cpc_med\n"
		+ "c=IN IP4 0.0.0.0\n"
		+ "t=0 0\n"
		+ "m=audio 4002 RTP/AVP 111 110 109 9 0 8 101";
	
/*
v=0
o=- 3614533537 3614533537 IN IP4 192.168.1.8
s=cpc_med
c=IN IP4 192.168.1.8
t=0 0
m=audio 4002 RTP/AVP 111 110 109 9 0 8 101
a=sendrecv
a=rtpmap:111 OPUS/48000
a=fmtp:111 maxplaybackrate=32000;useinbandfec=1
a=rtpmap:110 SILK/24000
a=fmtp:110 useinbandfec=1
a=rtpmap:109 SILK/16000
a=fmtp:109 useinbandfec=1
a=rtpmap:9 G722/8000
a=rtpmap:0 PCMU/8000
a=rtpmap:8 PCMA/8000
a=rtpmap:101 telephone-event/8000
a=fmtp:101 0-16	
 */

}
