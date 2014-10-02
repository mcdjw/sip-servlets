package oracle.communications.talkbac;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class GenericResponse extends CallStateHandler {

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		if (request.getMethod().equals("ACK") || request.getMethod().equals("PRACK")) {
		} else {
			SipServletResponse rqst = request.createResponse(200);
			rqst.send();
			this.printOutboundMessage(rqst);
		}
	}

}