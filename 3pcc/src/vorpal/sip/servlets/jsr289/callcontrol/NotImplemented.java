package vorpal.sip.servlets.jsr289.callcontrol;

import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class NotImplemented extends CallStateHandler {

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response) throws Exception {
		request.createResponse(501).send();
	}

}
