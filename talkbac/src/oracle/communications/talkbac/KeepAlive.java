package oracle.communications.talkbac;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

public class KeepAlive extends CallStateHandler {

	SipSession originSession;
	SipSession destinationSession;
	SipServletResponse originResponse;

	KeepAlive(SipSession originSession, SipSession destinationSession) {
		this.originSession = originSession;
		this.destinationSession = destinationSession;
	}

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		int status = (null != response) ? response.getStatus() : 0;

		SipApplicationSession appSession = null;
		if (request != null) {
			appSession = request.getApplicationSession();

			if (request.getMethod().equals("NOTIFY")) {
				SipServletResponse okNotify = request.createResponse(200);
				okNotify.send();
				this.printOutboundMessage(okNotify);
				return;
			}

		} else if (response != null) {
			appSession = response.getApplicationSession();
		} else if (timer != null) {
			appSession = timer.getApplicationSession();
		}

		switch (state) {
		case 1: // receive timeout
			SipServletRequest invite = originSession.createRequest("INVITE");
			invite.send();
			this.printOutboundMessage(invite);

			state = 2;
			originSession.setAttribute(CALL_STATE_HANDLER, this);
			break;
		case 2:
			if (status == 200) {
				originResponse = response;

				SipServletRequest dstRqst = destinationSession.createRequest("INVITE");
				dstRqst.setContent(response.getContent(), response.getContentType());
				dstRqst.send();
				this.printOutboundMessage(dstRqst);

				state = 3;
				destinationSession.setAttribute(CALL_STATE_HANDLER, this);
			}
			break;
		case 3:
			if (status == 200) {
				SipServletRequest ack = response.createAck();
				ack.send();
				this.printOutboundMessage(ack);
				SipServletRequest ackRqst = originResponse.createAck();
				ackRqst.setContent(response.getContent(), response.getContentType());
				ackRqst.send();
				this.printOutboundMessage(ackRqst);

				state = 1;
				ServletTimer t = TalkBACSipServlet.timer.createTimer(appSession, TalkBACSipServlet.keepAlive, false, this);
				
			}
			break;
		}

	}

}
