/*
 * SBC                   OCCAS                  VRSPa                  VRSPb
 *  |                      |                      x                      |
 *  |                      | ( ) INVITE (active)  x                      |
 *  |                      |<--------------------------------------------|
 *  | ( ) INVITE (active)  |                      x                      |
 *  |<---------------------|                      x                      |
 *  | ( ) 200 OK           |                      x                      |
 *  |--------------------->|                      x                      |
 *  |                      | ( ) 200 OK           x                      |
 *  |                      |-------------------------------------------->|
 *  |                      | ( ) ACK              x                      |
 *  |                      |<--------------------------------------------|
 *  | ( ) ACK              |                      x                      |
 *  |<---------------------|                      x                      |
 *  |                      |                      x                      | 
 */

package oracle.communications.sdp;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class FailoverRecording extends CallStateHandler {

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		// TODO Auto-generated method stub

	}

}
