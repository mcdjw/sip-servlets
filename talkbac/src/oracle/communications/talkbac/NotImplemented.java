package oracle.communications.talkbac;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class NotImplemented extends CallStateHandler {

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		if (request.getMethod().equals("ACK") || request.getMethod().equals("PRACK")) {
		} else {
			request.createResponse(501).send();
		}
	}

}
