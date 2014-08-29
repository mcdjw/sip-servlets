package oracle.communications.talkbac;

import javax.servlet.sip.Proxy;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

public class Authentication extends CallStateHandler {

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response) throws Exception {

		if (request.isInitial()) {

			String auth = request.getHeader("Authorization");
			if (auth == null && false==TalkBACSipServlet.disableAuth) {
				SipServletResponse authResponse = request.createResponse(401);
				authResponse.setHeader("WWW-Authenticate", "Digest realm=\"oracle.com\", qop=\"auth\",nonce=\"ea9c8e88df84f1cec4341ae6cbe5a359\",opaque=\"\", stale=FALSE, algorithm=MD5");
				authResponse.send();
			} else {
				// do LDP lookup.

				SipApplicationSession appSession = request.getApplicationSession();

				int expires = request.getExpires();
				if (expires != 0) {
					appSession.setExpires(expires);
				} else {
					// request.getApplicationSession().invalidate();
					CallStateHandler handler = new TerminateCall();
					handler.processEvent(request, response);
				}

				Proxy proxy = request.getProxy();
				proxy.setAddToPath(true);
				proxy.setRecordRoute(true);
				proxy.setRecurse(true);
				proxy.setSupervised(true);
				proxy.proxyTo(request.getRequestURI());

			}

		}

	}

}
