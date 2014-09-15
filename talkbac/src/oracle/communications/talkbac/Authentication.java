package oracle.communications.talkbac;

import java.util.logging.Logger;

import javax.naming.NamingEnumeration;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchResult;
import javax.servlet.sip.Proxy;
import javax.servlet.sip.ServletTimer;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;

import weblogic.kernel.KernelLogManager;

public class Authentication extends CallStateHandler {
	static Logger logger;
	{
		logger = Logger.getLogger(Authentication.class.getName());
		logger.setParent(KernelLogManager.getLogger());
	}

	@Override
	public void processEvent(SipServletRequest request, SipServletResponse response, ServletTimer timer) throws Exception {
		boolean proxyOn = false;

		SipApplicationSession appSession = request.getApplicationSession();

		if (request.isInitial()) {

			String auth = request.getHeader("Authorization");
			if (auth == null) {
				SipServletResponse authResponse = request.createResponse(401);
				authResponse.setHeader("WWW-Authenticate",
						"Digest realm=\"oracle.com\", qop=\"auth\",nonce=\"ea9c8e88df84f1cec4341ae6cbe5a359\",opaque=\"\", stale=FALSE, algorithm=MD5");
				authResponse.send();
			} else {

				if (TalkBACSipServlet.disableAuth != false) {

					// do LDP lookup.

					String strUserId = "username=\"";
					int begin = auth.indexOf(strUserId, 0);
					begin = begin + strUserId.length();
					int end = auth.indexOf("\"", begin);
					String userId = auth.substring(begin, end);

					if (userId.contains("@")) {
						userId = userId.substring(0, userId.indexOf("@"));
					}

					logger.fine("userId: " + userId);

					String strSid = "opaque=\"";
					begin = auth.indexOf(strSid, 0);
					begin = begin + strSid.length();
					end = auth.indexOf("\"", begin);
					String objectSid = auth.substring(begin, end);
					logger.fine("objectSid: " + objectSid);

					DirContext ldapCtx = TalkBACSipServlet.connectLdap();

					NamingEnumeration results = TalkBACSipServlet.ldapSearch(ldapCtx, userId, objectSid);
					if (results.hasMoreElements()) {
						proxyOn = true;

						SearchResult sr = (SearchResult) results.nextElement();

						logger.fine("TalkBACSipServlet.ldapLocationParameter: " + TalkBACSipServlet.ldapLocationParameter);

						String pbx = (String) sr.getAttributes().get(TalkBACSipServlet.ldapLocationParameter).get();

						logger.fine("Authentication pbx: " + pbx + ", " + appSession.getId().hashCode());

						if (pbx != null) {
							appSession.setAttribute("PBX", pbx);
						}

					} else {
						SipServletResponse authResponse = request.createResponse(403);
						authResponse.send();
					}

					TalkBACSipServlet.disconnectLdap(ldapCtx, results);

				} else {
					proxyOn = true;
				}

				if (proxyOn) {
					int expires = request.getExpires();
					appSession.setExpires(expires);
					if (expires > 0) {
						appSession.setInvalidateWhenReady(false);
					} else {
						appSession.setInvalidateWhenReady(true);
					}

					Proxy proxy = request.getProxy();
					proxy.proxyTo(request.getRequestURI());

				}

			}

		}

	}

}
