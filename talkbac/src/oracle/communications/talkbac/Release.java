/*
 *
 *             A                 Controller                  B
 *             | (1) BYE              |                      |
 *             |<---------------------|                      |
 *             | (2) 200 OK           |                      |
 *             |--------------------->|                      |
 *
 */

package oracle.communications.talkbac;

import java.util.Iterator;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

public class Release extends CallStateHandler {
	private static final long serialVersionUID = 1L;
	Address target;

	Release(Address target) {
		this.target = target;
	}

	@Override
	public void processEvent(SipApplicationSession appSession, TalkBACMessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {

		int status = (null != response) ? response.getStatus() : 0;

		switch (state) {
		case 1: // Send BYE

			SipSession targetSession = this.findSession(appSession, target);

			SipServletRequest byeRequest = targetSession.createRequest("BYE");
			byeRequest.send();
			this.printOutboundMessage(byeRequest);

			state = 2;
			byeRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);

			break;
		case 2: // Receive 200 OK
			TalkBACMessage msg = new TalkBACMessage(appSession, "call_released");
			msg.setParameter("target", target.getURI().toString());
			msg.setStatus(response.getStatus(), response.getReasonPhrase());
			msgUtility.send(msg);
			
			response.getSession().removeAttribute(CALL_STATE_HANDLER);

			msgUtility.removeEndpoint(response.getSession().getRemoteParty());
			break;
		}

	}

}
