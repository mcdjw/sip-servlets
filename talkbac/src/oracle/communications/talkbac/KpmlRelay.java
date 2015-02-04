/*
 * KPML/DTMF Relay w/Unsolicited Notify
 * RFC 2833 encoded payload
 * Periodic timers for refreshing subscription
 * Delayed start (3 seconds) to account for reINVITE handshaking, etc.
 *
 *             A                 Controller                  B
 *             |                      |                      |
 *             |                  timer(3s)                  |
 *             | (1) SUBSCRIBE        |                      |
 *             |<---------------------|                      |
 *             | (2) 200 OK           |                      |
 *             |--------------------->|                      |
 *             | (1) NOTIFY           |                      |
 *             |--------------------->|                      |
 *             |                      | (2) NOTIFY           |
 *             |                      |--------------------->|
 *             |                      | 200 OK               |
 *             |                      |<---------------------|
 *             | 200 OK               |                      |
 *             |<---------------------|                      |
 *             |           (repeat until finished)           |
 *             |.............................................|
 *
 */

package oracle.communications.talkbac;

import java.util.Iterator;

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

public class KpmlRelay extends CallStateHandler {

	private static final long serialVersionUID = 1L;
	private int period;

	private final String kpmlRequest = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
			+ "<kpml-request xmlns=\"urn:ietf:params:xml:ns:kpml-request\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"urn:ietf:params:xml:ns:kpml-request kpml-request.xsd\" version=\"1.0\">\r\n"
			+ "<pattern interdigittimer=\"7260000\" persist=\"persist\">\r\n"
			+ "<regex tag=\"dtmf\">[x*#ABCD]</regex>\r\n"
			+ "</pattern>\r\n"
			+ "</kpml-request>";

	KpmlRelay(int period) {
		this.period = period;
	}

	public void cancelTimers(SipApplicationSession appSession) {
		CallStateHandler handler;
		for (ServletTimer timer : appSession.getTimers()) {
			handler = (CallStateHandler) timer.getInfo();
			if (handler instanceof KpmlRelay) {
				timer.cancel();
			}
		}
	}

	public void delayedSubscribe(SipApplicationSession appSession, int delay) {
		if (period > 0) {
			ServletTimer timer = TalkBACSipServlet.timer.createTimer(appSession, delay, false, this);
			this.printTimer(timer);
		}
	}

	public void subscribe(SipApplicationSession appSession) throws Exception {
		// Cancel any existing timers
		cancelTimers(appSession);

		SipSession sipSession;
		SipServletRequest kpmlSubscribe;
		@SuppressWarnings("unchecked")
		Iterator<SipSession> itr = (Iterator<SipSession>) appSession.getSessions();
		while (itr.hasNext()) {
			sipSession = itr.next();

			if (sipSession.isValid() && sipSession.getState().equals(SipSession.State.CONFIRMED)) {

				kpmlSubscribe = sipSession.createRequest("SUBSCRIBE");
				kpmlSubscribe.setHeader("Event", "kpml");
				kpmlSubscribe.setExpires(period);
				kpmlSubscribe.setHeader("Accept", "application/kpml-response+xml");
				if (period > 0) {
					kpmlSubscribe.setContent(kpmlRequest.getBytes(), "application/kpml-request+xml");
				}
				kpmlSubscribe.send();
				this.printOutboundMessage(kpmlSubscribe);
				kpmlSubscribe.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}
		}

		if (period > 0) {
			ServletTimer timer = TalkBACSipServlet.timer.createTimer(appSession, period * 1000, false, this);
			this.printTimer(timer);
		} else {
			for (ServletTimer timer : appSession.getTimers()) {
				timer.cancel();
			}
		}

	}

	@Override
	public void processEvent(SipApplicationSession appSession, MessageUtility msgUtility, SipServletRequest request, SipServletResponse response,
			ServletTimer timer) throws Exception {

		if (response != null) {
			response.getSession().removeAttribute(CALL_STATE_HANDLER);
		} else if (timer != null) {
			subscribe(appSession);
		} else if (request != null && request.getMethod().equals("MESSAGE")) {
			subscribe(appSession);
		} else if (request != null && request.getMethod().equals("NOTIFY")) {
			SipServletResponse ok = request.createResponse(200);
			ok.send();
			this.printOutboundMessage(ok);

			SipSession sipSession;
			@SuppressWarnings("unchecked")
			Iterator<SipSession> itr = (Iterator<SipSession>) appSession.getSessions();
			while (itr.hasNext()) {
				sipSession = itr.next();

				if (sipSession.isValid() && sipSession.getId().equals(request.getSession().getId()) == false) {
					if (request.getContent() != null) {

						String kpmlResponse = new String((byte[]) request.getContent());

						String begin = "digits=\"";
						String end = "\"";

						int beginIndex = kpmlResponse.indexOf(begin) + begin.length();
						int endIndex = kpmlResponse.indexOf(end, beginIndex);
						String digits = kpmlResponse.substring(beginIndex, endIndex);

						if (digits != null && digits.length() > 0) {
							CallStateHandler handler = new DtmfRelay(sipSession.getRemoteParty(), digits);
							handler.processEvent(appSession, msgUtility, request, response, timer);
						}
					}
				}
			}

		}

	}
}
