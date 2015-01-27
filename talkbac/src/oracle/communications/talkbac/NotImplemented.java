package oracle.communications.talkbac;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class NotImplemented extends CallStateHandler {
	private static final long serialVersionUID = 1L;

	@Override
	public void processEvent(SipApplicationSession appSession, MessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {
		if (request.getMethod().equals("ACK") || request.getMethod().equals("PRACK")) {
		} else {
			SipServletResponse rspn = request.createResponse(501);
			rspn.send();
			this.printOutboundMessage(rspn);

			TalkBACMessage msg = new TalkBACMessage(appSession, "not_implemented");
			msg.setStatus(rspn.getStatus(), rspn.getReasonPhrase());
			msgUtility.send(msg);
		}
	}

}
