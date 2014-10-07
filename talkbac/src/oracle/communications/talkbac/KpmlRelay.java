/*
 * DTMF Relay w/Unsolicited Notify
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

	private final String kpmlRequest = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
			+ "<kpml-request xmlns=\"urn:ietf:params:xml:ns:kpml-request\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"urn:ietf:params:xml:ns:kpml-request kpml-request.xsd\" version=\"1.0\">"
			+ "<pattern persist=\"persist\">"
			+ "<regex tag=\"dtmf\">[x*#ABCD]</regex>"
			+ "</pattern>"
			+ "</kpml-request>";

	public void subscribe(SipSession session) throws Exception {
		SipServletRequest subscribe = session.createRequest("SUBSCRIBE");
		subscribe.setHeader("Event", "kpml");
		subscribe.setExpires(7200);
		subscribe.send();
		this.printOutboundMessage(subscribe);

		session.setAttribute(CALL_STATE_HANDLER, this);
	}

	// <?xml version="1.0" encoding="UTF-8"?><kpml-response version="1.0"
	// code="200" text="OK" digits="1" tag="dtmf"/>

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {

		if (response != null) {
			// receive 200 ok
			// do nothing;
		} else {
			if (request != null && request.getMethod().equals("NOTIFY")) {
				SipApplicationSession appSession = request.getApplicationSession();

				SipServletResponse ok = request.createResponse(200);
				ok.send();
				this.printOutboundMessage(ok);

				// String kpmlResponse = request.getContent().toString();
				//
				// String begin = "digits=\"";
				// String end = "\"";
				//
				// int beginIndex = kpmlResponse.indexOf(begin) +
				// begin.length();
				// int endIndex = kpmlResponse.indexOf(end, beginIndex);
				//
				// String digits = kpmlResponse.substring(beginIndex, endIndex);
				//
				// CallStateHandler handler = new DtmfRelay(digits);
				// handler.processEvent(request, response, timer);

				String destinationSessionId = (String) request.getSession().getAttribute(PEER_SESSION_ID);
				SipSession destSession = appSession.getSipSession(destinationSessionId);

				SipServletRequest destRequest = destSession.createRequest("NOTIFY");
				destRequest.setContent(request.getContent(), request.getContentType());
				destRequest.send();
				this.printOutboundMessage(destRequest);

				destRequest.getSession().setAttribute(CALL_STATE_HANDLER, this);
			}
		}
	}
}
