/*
 * SBC                   OCCAS                  VSRPa                  VSRPb
 *  |                      |                      |                      |
 *  |                      | ( ) INVITE (inactive)|                      |
 *  |                      |<---------------------|                      |
 *  |                      | ( ) INVITE (active)  |                      |
 *  |                      |<--------------------------------------------|
 *  | ( ) INVITE (active)  |                      |                      |
 *  |<---------------------|                      |                      |
 *  | ( ) 200 OK           |                      |                      |
 *  |--------------------->|                      |                      |
 *  |                      | ( ) 200 OK           |                      |
 *  |                      |--------------------->|                      |
 *  |                      | ( ) 200 OK           |                      |
 *  |                      |-------------------------------------------->|
 *  |                      | ( ) ACK              |                      |
 *  |                      |<---------------------|                      |
 *  |                      | ( ) ACK              |                      |
 *  |                      |<--------------------------------------------|
 *  |                      |                      |                      |
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
