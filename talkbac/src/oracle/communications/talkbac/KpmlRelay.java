/*
 * KPML/DTMF Relay w/Unsolicited Notify
 * RFC 2833 encoded payload
 *
 *             A                 Controller                  B
 *             | Established RTP      |                      |
 *             |.............................................|
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

import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;

public class KpmlRelay extends CallStateHandler {
	SipSession originSession;

	private final String kpmlRequest = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
			+ "<kpml-request xmlns=\"urn:ietf:params:xml:ns:kpml-request\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"urn:ietf:params:xml:ns:kpml-request kpml-request.xsd\" version=\"1.0\">\r\n"
			+ "<pattern interdigittimer=\"7260000\" persist=\"persist\">\r\n"
			+ "<regex tag=\"dtmf\">[x*#ABCD]</regex>\r\n"
			+ "</pattern>\r\n"
			+ "</kpml-request>";

	public void subscribe(SipSession session) throws Exception {
		originSession = session;
		SipServletRequest subscribe = session.createRequest("SUBSCRIBE");
		subscribe.setHeader("Event", "kpml");
		subscribe.setExpires(7200);
		subscribe.setHeader("Accept", "application/kpml-response+xml");
		subscribe.setContent(kpmlRequest.getBytes(), "application/kpml-request+xml");
		subscribe.send();
		this.printOutboundMessage(subscribe);

		session.setAttribute(CALL_STATE_HANDLER, this);
	}

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {

		if (response != null && response.getMethod().equals("SUBSCRIBE")) {

			String peerSessionId = (String) response.getSession().getAttribute(PEER_SESSION_ID);
			SipSession peerSession = response.getApplicationSession().getSipSession(peerSessionId);
			KeepAlive keepAlive = new KeepAlive(response.getSession(), peerSession);
			keepAlive.processEvent(request, response, timer);

		} else {
			if (request != null && request.getMethod().equals("NOTIFY")) {
				SipApplicationSession appSession = request.getApplicationSession();

				SipServletResponse ok = request.createResponse(200);
				ok.send();
				this.printOutboundMessage(ok);

				if (request.getContent() != null) {

					String kpmlResponse = new String((byte[]) request.getContent());

					String begin = "digits=\"";
					String end = "\"";

					int beginIndex = kpmlResponse.indexOf(begin) + begin.length();
					int endIndex = kpmlResponse.indexOf(end, beginIndex);
					String digits = kpmlResponse.substring(beginIndex, endIndex);

					if (digits != null && digits.length() > 0) {
						CallStateHandler handler = new DtmfRelay(digits);
						handler.processEvent(request, response, timer);
					}
				}

			}
		}
	}
}
