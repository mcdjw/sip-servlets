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
		} else if (response != null) {
			appSession = response.getApplicationSession();
		} else if (timer != null) {
			appSession = timer.getApplicationSession();
		}

		switch (state) {
		case 1: // set timer
			state = 2;
			ServletTimer t = TalkBACSipServlet.timer.createTimer(appSession, TalkBACSipServlet.keepAlive, false, this);
			break;
		case 2: // receive timeout
			originSession.createRequest("INVITE").send();

			state = 3;
			originSession.setAttribute(CALL_STATE_HANDLER, this);
			break;
		case 3:
			if (status == 200) {
				originResponse = response;

				SipServletRequest dstRqst = destinationSession.createRequest("INVITE");
				dstRqst.setContent(response.getContent(), response.getContentType());
				dstRqst.send();

				state = 4;
				destinationSession.setAttribute(CALL_STATE_HANDLER, this);
			}
			break;
		case 4:
			if (status == 200) {
				response.createAck().send();
				SipServletRequest ackRqst = originResponse.createAck();
				ackRqst.setContent(response.getContent(), response.getContentType());
				ackRqst.send();

				state = 1;
				this.processEvent(request, response, timer);
			}
			break;
		}

	}

}
