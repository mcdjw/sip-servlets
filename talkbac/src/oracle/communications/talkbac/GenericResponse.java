package oracle.communications.talkbac;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class GenericResponse extends CallStateHandler {
	private static final long serialVersionUID = 1L;

	@Override
	public void processEvent(SipApplicationSession appSession, TalkBACMessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {

		if (request != null) {
			if (request.getMethod().equals("ACK") || request.getMethod().equals("PRACK")) {
			} else {
				SipServletResponse rspn = request.createResponse(200);
				rspn.send();
				this.printOutboundMessage(rspn);
			}
		} else {
			if (response != null && response.getMethod().equals("INVITE") && response.getStatus() < 300) {
				SipServletRequest ack = response.createAck();
				ack.send();
				this.printOutboundMessage(ack);
			}
		}

	}
}
