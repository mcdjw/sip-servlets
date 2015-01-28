/*
 *
 *  A                 Controller                  B
 *  |                      |(1) INVITE            |
 *  |                      |--------------------->|
 *  |                      |(2) 200 OK            |
 *  |                      |<---------------------|
 *  |(3) INVITE            |                      |
 *  |<---------------------|                      |
 *  |(4) 200 OK            |                      |
 *  |--------------------->|                      |
 *  |(5) ACK               |                      |
 *  |<---------------------|                      |
 *  |                      |(6) ACK               |
 *  |                      |--------------------->|
 *  |.............................................|
 */

package oracle.communications.talkbac;

import javax.servlet.sip.Address;

public class Unmute extends Resume {
	private static final long serialVersionUID = 1L;

	Unmute(Address origin, Address destination) {
		super(origin, destination);
		this.success_message = "call_unmuted";
	}
	
}
