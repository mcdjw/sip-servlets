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

public class CallFlow2 extends CallStateHandler{

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

			originRequest.setContent(blackhole, "application/sdp");
			originRequest.send();

			state = 2;
			originRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
		}
			break;

		case 2:
		case 3:
		case 4: {
			if (status == 200) {
				originResponse = response;
				
				destinationRequest.setContent(originResponse.getContent(), originResponse.getContentType());
				destinationRequest.send();

				SipServletRequest originAck = originResponse.createAck();
				originAck.send();


				state = 5;
				destinationRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}
		}
			break;

		case 5:
		case 6: 		
		case 7:{
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
		}
			break;


		case 8:
		case 9: {
			if (status == 200) {

				response.createAck().send();


				destinationRequest.getSession().removeAttribute(CALL_STATE_HANDLER);
				originRequest.getSession().removeAttribute(CALL_STATE_HANDLER);
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
		+ "m=audio 4002 RTP/AVP 111 110 109 9 0 8 101"
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
