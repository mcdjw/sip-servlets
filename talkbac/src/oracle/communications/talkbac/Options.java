package oracle.communications.talkbac;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class Options extends CallStateHandler {

	@Override
	public void processEvent(SipApplicationSession appSession, SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		if (request != null) {
			SipServletResponse optionsResponse = request.createResponse(200);
			optionsResponse.setHeader("Allow", "INVITE, BYE, OPTIONS, CANCEL, ACK, REGISTER, NOTIFY, PRACK, MESSAGE");
			optionsResponse.send();
			this.printOutboundMessage(optionsResponse);
		}
	}

}
